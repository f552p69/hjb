/*
001 = 200412 = Initial workable version. Getting params from HTTP header.
002 = 200419 = Adding ability to provided params thru URL.
003 = 200624 = Fixing issue with providing params thru URL.
004 = 200629 = Extended logging functionality
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
import java.nio.file.Files;
import java.nio.file.Paths;

public class hjbClientSession implements Runnable {
    private Socket __socket;
    private InputStream __socket_stream_request = null;
    private OutputStream __socket_stream_response = null;
    private long __requestCount;

    public hjbClientSession(Socket socket, long requestCount) throws IOException {
        this.__socket = socket;
        __socket_stream_request = socket.getInputStream();
        __socket_stream_response = socket.getOutputStream();
        __requestCount = requestCount;
    }

    @Override
    public void run() {
        try {
            if(__requestCount >= 0) {
                System.out.println("==================================================");
                System.out.println(__requestCount);
                System.out.println("==================================================");
            }

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
                    System.out.println("*** Param " + mandatoryParam + "='" + ConnectivityParams.get(mandatoryParam) + "'");
                }
            }

            if(MandatoryParamsNotProvided.isEmpty()) {
////////////////////////////////////////////////////////////////////////////////
                String Response;
                String status_code;
                String logFName;

                status_code="200 OK";
                logFName = String.format("%08d", __requestCount);

                ConnectionFactory factory = new com.tibco.tibjms.TibjmsConnectionFactory(ConnectivityParams.get("JMS-URL"));
                try (JMSContext context= factory.createContext(ConnectivityParams.get("JMS-USR"), ConnectivityParams.get("JMS-PSW"));) {
                    Queue queue2 = null; // Because of: error: variable queue2 might not have been initialized
                    try {
                        String queue1name = ConnectivityParams.get("JMS-QU1");
                        Queue queue1 = context.createQueue(queue1name);
                        System.out.println("*** Sending to: " + queue1.getQueueName());

                        String queue2name = ConnectivityParams.getOrDefault("JMS-QU2", "");
                        if (queue2name.isEmpty()) {
                            queue2 = context.createTemporaryQueue();
                        } else {
                            queue2 = context.createQueue(queue2name);
                        }
                        System.out.println("*** Receiving from: " + queue2.getQueueName());

                        ////////////////////////////////////////////////////////////////////////////
                        if(__requestCount >= 0) {
                            String requestUID = ConnectivityParams.getOrDefault("JMS-UID", "");
                            if(!requestUID.isEmpty()) {
                                logFName += "." + requestUID;
                            }
                            try {
                                String dstFile;
                                dstFile = logFName + ".header"; System.out.println("Saving " + dstFile + "...");Files.write(Paths.get(dstFile), request[0].getBytes());
                                dstFile = logFName + ".request";System.out.println("Saving " + dstFile + "...");Files.write(Paths.get(dstFile), request[1].getBytes());

                            } catch (IOException e) {
                                System.out.println("EXCEPTION: Request was not saved");
                                e.printStackTrace();
                                Response = e.toString();
                                status_code="500 Internal Server Error";
                            }
                        }
                        ////////////////////////////////////////////////////////////////////////////

                        System.out.println("Sending...");
                        TextMessage requestMessage = context.createTextMessage();
                        requestMessage.setText(request[1]);
                        requestMessage.setJMSReplyTo(queue2);
                        context.createProducer().send(queue1, requestMessage);
                        System.out.println("...Completed");

                    } catch (Exception e) {
                        System.out.println("EXCEPTION: During sending");
                        e.printStackTrace();
                        Response = e.toString();
                        status_code="501 Not Implemented";
                    } finally {
                        try {
                            int timeout=Integer.parseInt(ConnectivityParams.getOrDefault("JMS-TIM", "5000"));
                            System.out.println("*** Waiting: " + timeout + " milliseconds");
                            Response = context.createConsumer(queue2).receiveBody(String.class, timeout);
                            ////////////////////////////////////////////////////////////////////////////
                            if(__requestCount >= 0) {
                                try {
                                    String dstFile = logFName + ".response";System.out.println("Saving " + dstFile + "...");Files.write(Paths.get(dstFile), Response.getBytes());
                                } catch (IOException e) {
                                    System.out.println("EXCEPTION: Response was not saved");
                                    e.printStackTrace();
                                    Response = e.toString();
                                    status_code="500 Internal Server Error";
                                }
                            }
                            ////////////////////////////////////////////////////////////////////////////
                        } catch (Exception e) {
                            System.out.println("EXCEPTION: Timeout");
                            Response = e.toString();
                            status_code="504 Gateway Timeout";
                        }
                    }
                } catch (Exception e) {
                    System.out.println("EXCEPTION: EMS connectivity details is not correct");
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
                __socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String[] readRequest() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(__socket_stream_request));
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

        PrintStream answer = new PrintStream(__socket_stream_response, true, "UTF-8");
        answer.print(buffer.toString());
    }

}
