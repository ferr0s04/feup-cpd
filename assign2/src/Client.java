import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;

public class Client {
    private static final int MAX_ATTEMPTS = 10;
    private static final int WAIT_TIME = 1000;

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java Client <serverAddr> <port> <username> <password>");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];
        String password = args[3];

        Socket socket = connectWithRetry(serverAddress, port);
        if (socket == null) {
            System.out.println("Could not connect to the server after multiple attempts. Exiting...");
            return;
        }

        try (
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in))
        ) {
            // 1) Authenticate
            writer.println("AUTH " + username + " " + password);
            String authResponse = reader.readLine();
            if (!"AUTH_OK".equals(authResponse)) {
                System.out.println("Authentication failed: " + authResponse);
                return;
            } else {
                System.out.println("Authentication successful.");
            }

            // 2) Start background thread to print incoming messages
            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });

            // 3) Interactive console loop
            System.out.println("Enter commands or messages:");
            String input;
            while ((input = console.readLine()) != null) {
                if (input.trim().isEmpty()) continue;
                // User can type:
                // /list                      -> LIST_ROOMS
                // /enter <room>              -> ENTER <room>
                // /create <room>             -> CREATE_ROOM <room>
                // /createai <room> <prompt>  -> CREATE_ROOM <room> AI <prompt>
                // otherwise, it's a message: MSG <currentRoom> <text>
                if (input.startsWith("/")) {
                    String[] parts = input.split(" ", 3);
                    switch (parts[0]) {
                        case "/list":
                            writer.println("LIST_ROOMS");
                            break;
                        case "/enter":
                            if (parts.length >= 2) writer.println("ENTER " + parts[1]);
                            break;
                        case "/create":
                            if (parts.length >= 2) writer.println("CREATE_ROOM " + parts[1]);
                            break;
                        case "/createai":
                            if (parts.length == 3) writer.println("CREATE_ROOM " + parts[1] + " AI " + parts[2]);
                            break;
                        default:
                            System.out.println("Unknown command");
                    }
                } else {
                    // default: send as chat message to current room (server tracks currentRoom)
                    writer.println("MSG " + input);
                }
            }

        } catch (IOException e) {
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
                waitTime *= 2;
            }
        }
        return null;
    }
}





/*
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
*/