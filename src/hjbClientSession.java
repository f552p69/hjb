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
            String[] headerLines = request[0].replace("\r\n","\n").replace("\r","\n").split("\n");
            Map<String, String> headerParams = new HashMap<>();
            for (int i = 1; i < headerLines.length - 1; i++) {
                String[] duplet=headerLines[i].split(": ",2);
                if (duplet.length == 2) {
                    headerParams.put(duplet[0],duplet[1]);
                }
            }
            System.out.println("*** headerParams:\n" + headerParams.entrySet() + "\n");

            String[] mandatoryParams={"JMS-USR","JMS-PSW","JMS-URL","JMS-QU1"};
            String MandatoryParamsNotProvided="";
            for (String mandatoryParam : mandatoryParams) {
                if(!headerParams.containsKey(mandatoryParam)) {
                    MandatoryParamsNotProvided+=mandatoryParam + " doesn't exist in request header\n";
                }
            }

            if(MandatoryParamsNotProvided.isEmpty()) {
                System.out.println("*** Body:\n" + request[1] + "\n");
////////////////////////////////////////////////////////////////////////////////
                String Response;
                String status_code="200 OK";

                ConnectionFactory factory = new com.tibco.tibjms.TibjmsConnectionFactory(headerParams.get("JMS-URL"));
                try (JMSContext context= factory.createContext(headerParams.get("JMS-USR"), headerParams.get("JMS-URL"));) {
                    Queue queue2 = null; // Because of: error: variable queue2 might not have been initialized
                    try {
                        String queue1name = headerParams.get("JMS-QU1");
                        Queue queue1 = context.createQueue(queue1name);
                        System.out.println("*** Send to: " + queue1.getQueueName() + "\n");

                        String queue2name = headerParams.getOrDefault("JMS-QU2", null);
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
                            int timeout=Integer.parseInt(headerParams.getOrDefault("JMS-TIM", "5000"));
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
