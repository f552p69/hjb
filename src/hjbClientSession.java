/*
001 = 200412 = Initial workable version. Getting params from HTTP header.
002 = 200419 = Adding ability to provided params thru URL.
003 = 200624 = Fixing issue with providing params thru URL.
004 = 200629 = Extended logging funtionality
005 = 200910 = CURL "Expect: 100-continue" compartibility with POST requests > 1024 bytes 
               CURL starts to send stream in anycases, but __reader.ready() returns False
               Workaround fix: "%curl%" --request POST --data-binary @%1 "%url%" --header "Expect:"
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
    // private InputStream __socket_stream_request = null;
    // private OutputStream __socket_stream_response = null;
    private long __requestCount;
    private PrintStream __answer = null;
    private BufferedReader __reader = null;

// *****************************************************************************
// *****************************************************************************
    public hjbClientSession(Socket socket, long requestCount) throws IOException {
// *****************************************************************************
// *****************************************************************************
        this.__socket = socket;
        this.__requestCount = requestCount;
        this.__reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.__answer = new PrintStream(socket.getOutputStream(), true, "UTF-8");
    }

    @Override
// *****************************************************************************
// *****************************************************************************
    public void run() {
// *****************************************************************************
// *****************************************************************************
        try {
            if(__requestCount >= 0) {
                System.out.println("==================================================");
                System.out.println(__requestCount);
                System.out.println("==================================================");
            }

            String request[] = readRequest(); // request[0] == header, request[1] == body
            // System.out.println("+++ RECEIVE +++");

            System.out.println("+++ Header:\n" + request[0] + "\n");
            System.out.println("+++ Body:\n" + request[1] + "\n");

            // *****************************************************************
            // parse HTTP header parameters
            // *****************************************************************
            Map<String, String> ConnectivityParams = new HashMap<>();
            String[] headerLines = request[0].replace("\r\n","\n").replace("\r","\n").split("\n");
            String[] pair;
            for (int i = 1; i < headerLines.length; i++) {
                pair=headerLines[i].split(": ",2);
                if (pair.length == 2) {
                    ConnectivityParams.put(pair[0],pair[1]);
                }
            }
            // *****************************************************************
            // parse parameters in URL
            // *****************************************************************
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

            // *****************************************************************
            // Terminate execution by external command: Parameter "Terminate" in header
            // *****************************************************************
            if(ConnectivityParams.containsKey("Terminate")) {
                writeResponse("200 OK", "*** hjbServer is terminating...\n");
                __socket.close();
                System.exit(888);
            }
            // *****************************************************************
            // Check if message to send is empty
            // *****************************************************************
            int request_size = request[1].getBytes().length;
            if(request_size == 0) {
                writeResponse("415 Unsupported Media Type", "FATAL: EMPTY REQUEST");
                return; // ;-) sacral knowledge : will be routed to block finally {}
            }
            // *****************************************************************
            // Check JMS connectivity parameters
            // *****************************************************************
            String[] mandatoryParams={"JMS-USR","JMS-PSW","JMS-URL","JMS-QU1"};
            String MandatoryParamsNotProvided="";
            for (String mandatoryParam : mandatoryParams) {
                if(!ConnectivityParams.containsKey(mandatoryParam)) {
                    MandatoryParamsNotProvided+=mandatoryParam + " doesn't provided\n";
                } else {
                    System.out.println("*** Param " + mandatoryParam + "='" + ConnectivityParams.get(mandatoryParam) + "'");
                }
            }
            if(!MandatoryParamsNotProvided.isEmpty()) {
                writeResponse("412 Precondition Failed", MandatoryParamsNotProvided);
                return;
            }
            // *****************************************************************
            // Connect to Tibco EMS
            // *****************************************************************
            JMSContext context;
            try {
                ConnectionFactory factory = new com.tibco.tibjms.TibjmsConnectionFactory(ConnectivityParams.get("JMS-URL"));
                context = factory.createContext(ConnectivityParams.get("JMS-USR"), ConnectivityParams.get("JMS-PSW"));
            } catch (Exception e) {
                e.printStackTrace();
                writeResponse("502 Bad Gateway", "EXCEPTION: EMS connectivity details is not correct\n" + e.toString());
                return;
            }
            // *****************************************************************
            // Prepare log file name in advance
            // *****************************************************************
            String logFName = String.format("%08d", __requestCount);
            String requestUID = ConnectivityParams.getOrDefault("JMS-UID", "");
            if(!requestUID.isEmpty()) {
                logFName += "." + requestUID;
            }
            // *****************************************************************
            // Connect to Tibco EMS
            // *****************************************************************
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
                // *************************************************************
                // Save request into log files (if required: __requestCount >= 0)
                // *************************************************************
                if(__requestCount >= 0) {
                    try {
                        String dstFile;
                        dstFile = logFName + ".header";
                        System.out.println("Saving " + dstFile + "...");
                        Files.write(Paths.get(dstFile), request[0].getBytes());

                        dstFile = logFName + ".request";
                        System.out.println("Saving " + dstFile + "...");
                        Files.write(Paths.get(dstFile), request[1].getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                        writeResponse("500 Internal Server Error", "EXCEPTION: Request was not saved\n" + e.toString());
                        return;
                    }
                }
                
                // *************************************************************
                // Send to Tibco EMS queue
                // *************************************************************
                System.out.println("Sending " + request_size + " bytes...");
                TextMessage requestMessage = context.createTextMessage();
                requestMessage.setText(request[1]);
                requestMessage.setJMSReplyTo(queue2);
                context.createProducer().send(queue1, requestMessage);
                System.out.println("...Completed");
            } catch (Exception e) {
                e.printStackTrace();
                writeResponse("501 Not Implemented", "EXCEPTION: During sending to JMS queue\n" + e.toString());
                return;
            }
            try {
                // *************************************************************
                // Initiate timeout and listenning queue2
                // *************************************************************
                int timeout=Integer.parseInt(ConnectivityParams.getOrDefault("JMS-TIM", "5000"));
                System.out.println("*** Waiting: " + timeout + " milliseconds");
                String Response = context.createConsumer(queue2).receiveBody(String.class, timeout);
                // *************************************************************
                // Save response into log files (if required: __requestCount >= 0)
                // *************************************************************
                if(__requestCount >= 0) {
                    try {
                        String dstFile = logFName + ".response";
                        System.out.println("Saving " + dstFile + "...");
                        Files.write(Paths.get(dstFile), Response.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                        writeResponse("500 Internal Server Error", "EXCEPTION: Response was not saved\n" + e.toString());
                        return;
                    }
                }
                // *************************************************************
                // Send back (as http) response received from Tibco EMS
                // *************************************************************
                writeResponse("200 OK", Response);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                writeResponse("504 Gateway Timeout", "EXCEPTION: Timeout\n" + e.toString());
                return;
            }
////////////////////////////////////////////////////////////////////////////////
        } catch (Exception e) {
            e.printStackTrace();
            writeResponse("500 Internal Server Error", "EXCEPTION: Unexpectable exception\n" + e.toString());
            return;
        } finally {
            try {
                System.out.println("*** FINALLY ***");
                __socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
// *****************************************************************************
// *****************************************************************************
    private String[] readRequest() throws IOException {
// *****************************************************************************
// *****************************************************************************
        int headerContentLength = 0;
        String headerExpect = "";

        // *********************************************************************
        // Read header
        // *********************************************************************
        StringBuilder header = new StringBuilder();
        do {
            String ln = __reader.readLine(); // Header is always text
            if (ln == null || ln.isEmpty()) break;
            header.append(ln + "\n");
            if ( ln.startsWith("Content-Length: ") ) {
                try {
                    headerContentLength = Integer.valueOf(ln.split(": ",2)[1]);
                } catch (Exception e) {
                }
            }
            if ( ln.startsWith("Expect: ") ) {
                try {
                    headerExpect = ln.split(": ",2)[1];
                } catch (Exception e) {
                }
            }
        } while(__reader.ready());
        if (headerExpect.equals("100-continue")) {
            System.out.println("+++ Received Expect: 100-continue");
            // writeResponse("100 Continue", "\n"); // In real it's not mandatory, just to speed up CURL to send body
            writeResponse("100 Continue", "\0"); // In real it's not mandatory, just to speed up CURL to send body
        }

        // *********************************************************************
        // Read body
        // *********************************************************************
        StringBuilder body = new StringBuilder();
        while (__reader.ready() || headerContentLength > 0) { // Forced reading is mandatory for CURL "Expect: 100-continue" 
            body.append((char)__reader.read()); // Binary read
            headerContentLength--;
        }
        // System.out.println("+++ RETURN +++");
        return new String[] {header.toString(), body.toString()};
    }

// *****************************************************************************
// *****************************************************************************
    private String writeResponse(String status_code, String response_body) {
// *****************************************************************************
// *****************************************************************************
        if (response_body == null) {
            // System.out.println("*** Timeout!");
            status_code="504 Gateway Timeout";
            response_body = "<timeout>";
        }
        if (response_body.isEmpty()) {
            // System.out.println("*** EMPTY Response!");
            status_code = "404 Not Found";
            response_body = "<empty response>";
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append("HTTP/1.1 " +  status_code + "\n");
/*        
        if (response_body.charAt(0) != '\r' && response_body.charAt(0) != '\n') {
            buffer.append("Date: " + new Date().toGMTString() + "\n");
            // buffer.append("Accept-Ranges: none\n");
            buffer.append("Content-Type: text/xml; charset=UTF-8\n");
        }
*/        
        buffer.append("\n");
        if( !status_code.startsWith("100") && !status_code.startsWith("200")) {
            buffer.append("################################################################################\n");
            buffer.append("################################################################################\n");
            buffer.append("################################################################################\n");
        }
        if( response_body.charAt(0) != '\0')
            buffer.append(response_body);
        else {
            System.out.println("+++ Skip body for response");
        }
        if( !status_code.startsWith("100") && !status_code.startsWith("200")) {
            buffer.append("\n");
            buffer.append("################################################################################\n");
            buffer.append("################################################################################\n");
            buffer.append("################################################################################\n");
        }
        

        System.out.println("+++ Response:\n" + buffer.toString());
        __answer.print(buffer.toString());
        __answer.flush();

        return buffer.toString();
    }

}
