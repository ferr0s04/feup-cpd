import java.io.*;
import java.net.*;

public class Client {
    private static final int MAX_ATTEMPTS = 10;
    private static final int WAIT_TIME = 1000;

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java Client <addr> <port> <op> <id> [<val>]");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        String operation = args[2];
        int sensorId = Integer.parseInt(args[3]);

        Socket socket = connectWithRetry(serverAddress, port);
        if (socket == null) {
            System.out.println("Could not connect to the server after multiple attempts. Exiting...");
            return;
        }

        try (
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            if (operation.equals("put") && args.length == 5) {
                float value = Float.parseFloat(args[4]);
                String message = "put " + sensorId + " " + value;
                writer.println(message);
                System.out.println("Sent: " + message);
                Thread.sleep(1000);
            } else if (operation.equals("get")) {
                String message = "get " + sensorId;
                writer.println(message);
                System.out.println("Sent: " + message);
                String response = reader.readLine();
                if (response != null) {
                    System.out.println("Response: " + response);
                } else {
                    System.out.println("No response received from server.");
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private static Socket connectWithRetry(String serverAddress, int port) {
        int attempt = 0;
        int waitTime = WAIT_TIME;

        while (attempt < MAX_ATTEMPTS) {
            try {
                System.out.println("Attempting to connect to server (Attempt " + (attempt + 1) + ")...");
                return new Socket(serverAddress, port);
            } catch (IOException e) {
                System.out.println("Connection failed: " + e.getMessage());
                attempt++;
                if (attempt >= MAX_ATTEMPTS) {
                    return null;
                }
                try {
                    System.out.println("Retrying in " + waitTime + "ms...");
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                waitTime = waitTime * 2;
            }
        }
        return null;
    }
}
