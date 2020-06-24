/*
001 = 200412 = Initial workable version. Getting params from HTTP header.
002 = 200419 = Adding ability to provided params thru URL.
003 = 200624 = Fixing issue with providing params thru URL.
*/
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class hjbServer {
    private static final int DEFAULT_PORT = 9999;
    public static void main(String[] args) {
        System.out.println("HJB = Http to Jms Bridge = v0.03 24 June 2020 = f552p69@gmail.com\n");
        System.out.println("Usage: java -jar hjbServer.jar [port]\n");
        System.out.println("Listening localhost:port (by default port is 9999), sending body of HTTP POST request to the queue JMS-QU1 at JMS-USR:JMS-PSW@JMS-URL, sending back via HTTP responce from queue JMS-QU2\n");
        System.out.println("Parameners JMS-USR, JMS-PSW, JMS-URL, JMS-QU1, JMS-QU2 (Response queue is optional parameter, if it not set temporary queue will be used), JMS-TIM (Timeout is optional parameter, by default 5000 milliseconds) must be provided thru HTTP header or thru URL\n");
        
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + serverSocket.getLocalPort() + "\n");
        } catch (IOException e) {
            System.out.println("Port " + port + " is blocked.");
            System.exit(-1);
        }
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                hjbClientSession session = new hjbClientSession(clientSocket);
                new Thread(session).start();
            } catch (IOException e) {
                System.out.println("Failed to establish connection.");
                System.out.println(e.getMessage());
                System.exit(-1);
            }
        }
    }

}
