/*
001 = 200412 = Initial workable version. Getting params from HTTP header.
002 = 200419 = Adding ability to provided params thru URL.
003 = 200624 = Fixing issue with providing params thru URL.
*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import javax.jms.*;
import java.net.URLDecoder;

public class hjbClientSession implements Runnable {
    private Socket socket;
    private InputStream socket_stream_request = null;
    private OutputStream socket_stream_response = null;

    public hjbClientSession(Socket socket) throws IOException {
        this.socket = socket;
        socket_stream_request = socket.getInputStream();
        socket_stream_response = socket.getOutputStream();
    }

    @Override
    public void run() {
        try {
            String request[] = readRequest(); // request[0] == header, request[1] == body
            System.out.println("Header:\n" + request[0] + "\n");
            System.out.println("Body:\n" + request[1] + "\n");
            
            // parse HTTP header parameters
            Map<String, String> ConnectivityParams = new HashMap<>();
            String[] headerLines = request[0].replace("\r\n","\n").replace("\r","\n").split("\n");
            String[] pair;
            for (int i = 1; i < headerLines.length - 1; i++) {
                pair=headerLines[i].split(": ",2);
                if (pair.length == 2) {
                    ConnectivityParams.put(pair[0],pair[1]);
                }
            }
            // parse parameters in URL 
            pair = headerLines[0].split("\\?", 2); // Remove leading /?
            if (pair.length == 2) {
                pair = pair[1].split(" ", 2); // Remove trailing HTTP/1.1
                String[] pairs = URLDecoder.decode(pair[0], "UTF-8").split("&");
                for (String url_param : pairs) {
                    pair = url_param.split("=", 2);
                    if (pair.length == 2) {
                        ConnectivityParams.put(pair[0],pair[1]);
                    }
                }
            }
            System.out.println("*** Params:\n" + ConnectivityParams.entrySet() + "\n");
            
            
            String[] mandatoryParams={"JMS-USR","JMS-PSW","JMS-URL","JMS-QU1"};
            String MandatoryParamsNotProvided="";
            for (String mandatoryParam : mandatoryParams) {
                if(!ConnectivityParams.containsKey(mandatoryParam)) {
                    MandatoryParamsNotProvided+=mandatoryParam + " doesn't provided\n";
                } else {
                    System.out.println("*** Param " + mandatoryParam + "='" + ConnectivityParams.get(mandatoryParam) + "'\n");
                }
            }

            if(MandatoryParamsNotProvided.isEmpty()) {
////////////////////////////////////////////////////////////////////////////////
                String Response;
                String status_code="200 OK";

                ConnectionFactory factory = new com.tibco.tibjms.TibjmsConnectionFactory(ConnectivityParams.get("JMS-URL"));
                try (JMSContext context= factory.createContext(ConnectivityParams.get("JMS-USR"), ConnectivityParams.get("JMS-PSW"));) {
                    Queue queue2 = null; // Because of: error: variable queue2 might not have been initialized
                    try {
                        String queue1name = ConnectivityParams.get("JMS-QU1");
                        Queue queue1 = context.createQueue(queue1name);
                        System.out.println("*** Send to: " + queue1.getQueueName() + "\n");

                        String queue2name = ConnectivityParams.getOrDefault("JMS-QU2", null);
                        if (queue2name == null) {
                            queue2 = context.createTemporaryQueue();
                        } else {
                            queue2 = context.createQueue(queue2name);
                        }
                        System.out.println("*** Receive from: " + queue2.getQueueName() + "\n");

                        TextMessage requestMessage = context.createTextMessage();
                        requestMessage.setText(request[1]);
                        requestMessage.setJMSReplyTo(queue2);
                        context.createProducer().send(queue1, requestMessage);

                    } catch (Exception e) {
                        Response = e.toString();
                        status_code="501 Not Implemented";
                    } finally {
                        try {
                            int timeout=Integer.parseInt(ConnectivityParams.getOrDefault("JMS-TIM", "5000"));
                            System.out.println("*** Waiting: " + timeout + " milliseconds\n");
                            Response = context.createConsumer(queue2).receiveBody(String.class, timeout);
                        } catch (Exception e) {
                            Response = e.toString();
                            status_code="504 Gateway Timeout";
                        }
                    }
                } catch (Exception e) {
                    Response = e.toString();
                    status_code="502 Bad Gateway";
                }
////////////////////////////////////////////////////////////////////////////////
                writeResponse(status_code, Response);
            } else {
                writeResponse("412 Precondition Failed", MandatoryParamsNotProvided);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String[] readRequest() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket_stream_request));
        String ln = null;

        StringBuilder header = new StringBuilder();
        while (true) {
            ln = reader.readLine();
            if (ln == null || ln.isEmpty()) break;
            header.append(ln + "\n");
        }

        StringBuilder body = new StringBuilder();
        while (reader.ready()) {
            body.append((char)reader.read());
        }

        return new String[] {header.toString(),body.toString()};
    }

    private void writeResponse(String status_code, String response_body) throws IOException {
        if (response_body == null) {
            status_code="504 Gateway Timeout";
            response_body = "<timeout>";
            System.out.println("*** Timeout!\n");
        }
        if (response_body.isEmpty()) {
            status_code = "404 Not Found";
            response_body = "<empty response>";
            System.out.println("*** EMPTY Response!\n");
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append("HTTP/1.1 " +  status_code + "\n");
        buffer.append("Date: " + new Date().toGMTString() + "\n");
        buffer.append("Accept-Ranges: none\n");
        buffer.append("Content-Type: text/xml; charset=UTF-8\n");
        buffer.append("\n");
        buffer.append(response_body);

        System.out.println("*** Response:\n" + buffer.toString() + "\n");

        PrintStream answer = new PrintStream(socket_stream_response, true, "UTF-8");
        answer.print(buffer.toString());
    }

}
