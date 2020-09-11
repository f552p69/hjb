/*
001 = 200412 = Initial workable version. Getting params from HTTP header.
002 = 200419 = Adding ability to provided params thru URL.
003 = 200624 = Fixing issue with providing params thru URL.
004 = 200629 = Extended logging funtionality
005 = 200910 = CURL "Expect: 100-continue" compartibility with POST requests > 1024 bytes 
               CURL starts to send stream in anycases, but __reader.ready() returns False
               Workaround fix: "%curl%" --request POST --data-binary @%1 "%url%" --header "Expect:"
*/
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class hjbServer {
    public static void main(String[] args) {
        System.out.println(
            "HJB = Http to Jms Bridge = v0.05 11 September 2020 = f552p69@gmail.com\n" +
            "Usage: java -jar hjbServer.jar [-p<port>] [-l]\n" +
            "Listening localhost:port (by default port is 9999), sending body of HTTP POST request to the queue JMS-QU1 at JMS-USR:JMS-PSW@JMS-URL, sending back via HTTP responce from queue JMS-QU2\n" +
            "Options:\n" +
            "    -p<port>    = Listen <port>\n" +
            "    -l          = Log all requests/responses\n" +
            "Connectivity parameters must be provided thru HTTP header or thru URL:\n" +
            "    Mandatory parameters:\n" +
            "        JMS-USR, JMS-PSW, JMS-URL, JMS-QU1\n" +
            "    Optional:\n" +
            "        JMS-QU2 = Response queue, if it is not set then the temporary queue will be used),\n" +
            "        JMS-TIM = Timeout, by default 5000 milliseconds\n" +
            "        JMS-UID = File sufix to segregate groups of saved requests/responces."
        );
        
        Boolean flagLog = false;
        int port = 9999; // DEFAULT_PORT; // private static final int DEFAULT_PORT = 9999;
        
        for (int i=0; i < args.length; i++) {
            if (args[i].charAt(0) != '-') {
                System.out.println("FATAL: Unknown option prefix '" + args[i].charAt(0) + "' in '" + args[i] + "'");
                System.exit(-1);
            }
            switch(args[i].charAt(1)) {
            case 'p':
                port = Integer.parseInt(args[0].substring(2));
                break;
            case 'l':
                flagLog = true;
                System.out.println("*** Logging is ENABLED");
                break;
            default:
                System.out.println("FATAL: Unknown option '" + args[i].charAt(1) + "' in '" + args[i] + "'");
                System.exit(-1);
            }
        }
        
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("*** Server started on port: " + serverSocket.getLocalPort());
        } catch (Exception e) {
            System.out.println("FATAL: Port " + port + " is blocked.");
            System.exit(-1);
        }
        
        long requestCount = -1;
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                if( flagLog ) requestCount++;
                hjbClientSession session = new hjbClientSession(clientSocket, requestCount);
                new Thread(session).start();
            } catch (Exception e) {
                System.out.println("FATAL: Failed to establish connection.");
                System.out.println(e.getMessage());
                System.exit(-1);
            }
        }
    }

}
