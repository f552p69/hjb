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

import java.net.URL;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.LinkedList;
import java.util.LinkedHashMap;

public class hjbClientSession implements Runnable {
    private Socket socket;
    private InputStream socket_stream_request = null;
    private OutputStream socket_stream_response = null;

    public hjbClientSession(Socket socket) throws IOException {
        this.socket = socket;
        socket_stream_request = socket.getInputStream();
        socket_stream_response = socket.getOutputStream();
    }

    public static Map<String, List<String>> splitQuery(URL url) throws UnsupportedEncodingException {
        final Map<String, List<String>> query_pairs = new LinkedHashMap<String, List<String>>();
        final String[] pairs = url.getQuery().split("&");
        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8").toUpperCase() : pair;
            if (!query_pairs.containsKey(key)) {
                query_pairs.put(key, new LinkedList<String>());
            }
            final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
            query_pairs.get(key).add(value);
        }
        return query_pairs;
    }

    @Override
    public void run() {
        try {
            String request[] = readRequest(); // request[0] == header, request[1] == body
            System.out.println("*** header:\n" + request[0] + "\n*** body:\n" + request[1] + "\n");
            String[] headerLines = request[0].replace("\r\n","\n").replace("\r","\n").split("\n");
            Map<String, String> headerParams = new HashMap<>();
            for (int i = 1; i < headerLines.length - 1; i++) {
                String[] duplet=headerLines[i].split(": ",2);
                if (duplet.length == 2) {
                    headerParams.put(duplet[0].trim().toUpperCase(),duplet[1].trim());
                }
            }
            System.out.println("*** headerParams:\n" + headerParams.entrySet() + "\n");
            String url="http://" + headerParams.get("Host") + headerLines[0].split(" ",3)[1];
            System.out.println("*** url:\n" + url + "\n");
            Map<String, List<String>> urlParams = splitQuery(new URL(url));
            System.out.println("*** urlParams:\n" + urlParams.entrySet() + "\n");

            Map<String, String> commonParams = new HashMap<>();
            String[] mandatoryParams={"JMS-USR","JMS-PSW","JMS-URL","JMS-QU1"};
            String MandatoryParamsNotProvided="";
            for (String mandatoryParam : mandatoryParams) {
                String paramValue = null;
                System.out.println("*** " + mandatoryParam + "?:1\n" + urlParams.containsKey(mandatoryParam) + "\n");
                if(urlParams.containsKey(mandatoryParam) == true) {
                    System.out.println("***1 " + mandatoryParam + ":\n" + urlParams.get(mandatoryParam) + "\n");
                    paramValue=urlParams.get(mandatoryParam).get(0);
                }
                System.out.println("*** " + mandatoryParam + "?:2\n" + headerParams.containsKey(mandatoryParam) + "\n");
                if(headerParams.containsKey(mandatoryParam) == true) {
                    System.out.println("***2 " + mandatoryParam + ":\n" + headerParams.get(mandatoryParam) + "\n");
                    paramValue=headerParams.get(mandatoryParam);
                }
                System.out.println("*** " + mandatoryParam + "='" + paramValue + "'\n");
                if(paramValue != null) {
                    commonParams.put(mandatoryParam, paramValue);
                } else {
                    MandatoryParamsNotProvided += "Param '" + mandatoryParam + "' doesn't exist in request's GET url or POST header.\n";
                }
            }

            System.out.println("*** commonParams(mandatory only):\n" + commonParams.entrySet() + "\n");
            if(MandatoryParamsNotProvided.isEmpty() == true) {
                String[] optionalParams={"JMS-TIM","JMS-QU2"};
                for (String optionalParam : optionalParams) {
                    String paramValue = null;
                    if(urlParams.containsKey(optionalParam) == true) {
                        paramValue=urlParams.get(optionalParam).get(0);
                    }
                    if(headerParams.containsKey(optionalParam) == true) {
                        paramValue=headerParams.get(optionalParam);
                    }
                    if(paramValue != null) {
                        commonParams.put(optionalParam, paramValue);
                    }
                }
                System.out.println("*** commonParams(+optional):\n" + commonParams.entrySet() + "\n");
////////////////////////////////////////////////////////////////////////////////
                System.out.println("*** Body:\n" + request[1] + "\n");
////////////////////////////////////////////////////////////////////////////////
                String Response;
                String status_code="200 OK";

                ConnectionFactory factory = new com.tibco.tibjms.TibjmsConnectionFactory(commonParams.get("JMS-URL"));
                try (JMSContext context= factory.createContext(commonParams.get("JMS-USR"), commonParams.get("JMS-URL"));) {
                    Queue queue2 = null; // Because of: error: variable queue2 might not have been initialized
                    try {
                        String queue1name = commonParams.get("JMS-QU1");
                        Queue queue1 = context.createQueue(queue1name);
                        System.out.println("*** Send to: " + queue1.getQueueName() + "\n");

                        String queue2name = commonParams.getOrDefault("JMS-QU2", null);
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
                            int timeout=Integer.parseInt(commonParams.getOrDefault("JMS-TIM", "5000"));
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
        if(status_code.startsWith("200 ") == true) {
            buffer.append("Content-Type: text/xml; charset=UTF-8\n");
        } else {
            buffer.append("Content-Type: text/plain; charset=UTF-8\n");
        }
        buffer.append("\n");
        buffer.append(response_body);

        System.out.println("*** Response:\n" + buffer.toString() + "\n");

        PrintStream answer = new PrintStream(socket_stream_response, true, "UTF-8");
        answer.print(buffer.toString());
    }

}
