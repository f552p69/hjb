import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class hjbServer {
    private static final int DEFAULT_PORT = 9999;
    public static void main(String[] args) {
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
