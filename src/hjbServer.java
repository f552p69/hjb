/*
001 = 200412 = Initial workable version. Getting params from HTTP header.
002 = 200419 = Adding ability to provided params thru URL.
003 = 200624 = Fixing issue with providing params thru URL.
004 = 200629 = Extended logging functionality
*/
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class hjbServer {
    private static final int DEFAULT_PORT = 9999;
    public static void main(String[] args) {
        System.out.println("HJB = Http to Jms Bridge = v0.04 29 June 2020 = f552p69@gmail.com");
        System.out.println("Usage: java -jar hjbServer.jar [-p<port>] [-l]");
        System.out.println("Listening localhost:port (by default port is 9999), sending body of HTTP POST request to the queue JMS-QU1 at JMS-USR:JMS-PSW@JMS-URL, sending back via HTTP responce from queue JMS-QU2");
        System.out.println("Options:");
        System.out.println("    -p<port>    = Listen <port>");
        System.out.println("    -l          = Log all requests/responses");
        System.out.println("Connectivity parameters must be provided thru HTTP header or thru URL:");
        System.out.println("    Mandatory parameters:");
        System.out.println("        JMS-USR, JMS-PSW, JMS-URL, JMS-QU1");
        System.out.println("    Optional:");
        System.out.println("        JMS-QU2 = Response queue, if it is not set then the temporary queue will be used),");
        System.out.println("        JMS-TIM = Timeout, by default 5000 milliseconds");
        System.out.println("        JMS-UID = File name for saving request/responces in separate files. By default counter of request will be used.");
        
        Boolean flagLog = false;
        int port = DEFAULT_PORT;
        
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
        } catch (IOException e) {
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
            } catch (IOException e) {
                System.out.println("FATAL: Failed to establish connection.");
                System.out.println(e.getMessage());
                System.exit(-1);
            }
        }
    }

}
