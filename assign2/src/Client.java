import javax.net.ssl.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import auth.AuthenticationHandler;

public class Client {

    private static SSLSocket socket;
    private static PrintWriter writer;
    private static BufferedReader reader;
    private static BufferedReader console;
    private static volatile String sessionToken = null;
    private static String username;
    private static String password;
    private static String serverAddress;
    private static int port;
    private static final String TRUSTSTORE_PATH = "./auth/certs/server-truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";
    private static volatile boolean running = true;

    private static final ReentrantLock ioLock = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        // REGISTRATION MODE
        if (args.length < 3) {
            boolean registrationSuccess = false;

            while (!registrationSuccess) {
                System.out.println("---- REGISTRATION MODE ----");
                System.out.print("Choose a username: ");
                username = scanner.nextLine().trim();

                System.out.print("Choose a password: ");
                String password = scanner.nextLine().trim();

                System.out.print("Confirm your password: ");
                String confirmPassword = scanner.nextLine().trim();

                if (!password.equals(confirmPassword)) {
                    System.out.println("Passwords do not match. Please try again.\n");
                    continue;
                }

                // Save user
                AuthenticationHandler authHandler = new AuthenticationHandler("data/data.json");
                if (!authHandler.register(username, password)) {
                    System.out.println("Registration failed. User already exists.\n");
                    continue;
                }

                registrationSuccess = true;
                System.out.println("Registration successful!");
                System.out.println("Now run the client with: java Client <serverAddr> <port> <username> <password>");
            }
            return;
        }

        // LOGIN MODE
        if (args.length < 4) {
            System.out.println("Usage: java Client <serverAddr> <port> <username> <password>");
            return;
        }

        serverAddress = args[0];
        port = Integer.parseInt(args[1]);
        username = args[2];
        password = args[3];

        console = new BufferedReader(new InputStreamReader(System.in));

        // Initial connection
        if (!connectAndAuthenticate(password, true)) return;

        // Start listener and command loop
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(Client::listenToServer);

        System.out.println("--------------------------------------------------------------------------------------------");
        System.out.println("Available commands:");
        System.out.println("/list - List all rooms");
        System.out.println("/enter <room> - Enter a room");
        System.out.println("/create <room> - Create a new room");
        System.out.println("/createai <room> <prompt> - Create an AI room");
        System.out.println("/leave - Leave current room");
        System.out.println("--------------------------------------------------------------------------------------------");
        System.out.println("Enter command:");
        String input;

        while ((input = console.readLine()) != null) {
            if (input.trim().isEmpty()) continue;

            if (input.startsWith("/")) {
                String[] parts = input.split(" ", 3);
                String command;

                switch (parts[0]) {
                    case "/list":
                        command = "LIST_ROOMS";
                        break;
                    case "/enter":
                        if (parts.length < 2) {
                            System.out.println("Usage: /enter <room_name>");
                            continue;
                        }
                        command = "ENTER " + parts[1];
                        break;
                    case "/create":
                        if (parts.length < 2) {
                            System.out.println("Usage: /create <room_name>");
                            continue;
                        }
                        command = "CREATE_ROOM " + parts[1];
                        break;
                    case "/createai":
                        if (parts.length != 2) {
                            System.out.println("Usage: /createai <room_name>");
                            continue;
                        }
                        command = "CREATE_AI " + parts[1];
                        break;
                    case "/leave":
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Available commands:");
                        System.out.println("/list - List all rooms");
                        System.out.println("/enter <room> - Enter a room");
                        System.out.println("/create <room> - Create a new room");
                        System.out.println("/createai <room> - Create an AI room");
                        System.out.println("/leave - Leave current room");
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Enter command:");
                        command = "LEAVE";
                        break;
                    default:
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Unknown command. Available commands:");
                        System.out.println("/list - List all rooms");
                        System.out.println("/enter <room> - Enter a room");
                        System.out.println("/create <room> - Create a new room");
                        System.out.println("/createai <room> - Create an AI room");
                        System.out.println("/leave - Leave current room");
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Enter command:");
                        continue;
                }

                // Lock apenas na escrita
                ioLock.lock();
                try {
                    writer.println(command);
                    writer.flush();
                } finally {
                    ioLock.unlock();
                }
            } else {
                ioLock.lock();
                try {
                    writer.println("MSG " + input);
                    writer.flush();
                } finally {
                    ioLock.unlock();
                }
            }
        }

        running = false;
        ioLock.lock();
        try {
            socket.close(); // force readLine() to unblock
        } catch (IOException ignored) {
        } finally {
            ioLock.unlock();
        }
        executor.shutdownNow();

    }

    private static boolean connectAndAuthenticate(String password, boolean initial) {
        try {
            // 1) Establish a fresh connection
            SSLSocket newSock = AuthenticationHandler.connectToServerWithTruststore(
                    serverAddress, port, TRUSTSTORE_PATH, TRUSTSTORE_PASSWORD
            );

            if (newSock == null) {
                System.out.println("Could not connect to the server.");
                return false;
            }

            // 2) Prepare new I/O objects
            PrintWriter newWriter = new PrintWriter(newSock.getOutputStream(), true);
            BufferedReader newReader = new BufferedReader(new InputStreamReader(newSock.getInputStream()));


            // 3) ATOMICALLY swap in the new socket, writer, and reader
            ioLock.lock();
            try {
                socket = newSock;
                writer = newWriter;
                reader = newReader;
            } finally {
                ioLock.unlock();
            }


            // 4) Send login or resume-session
            if (!initial && sessionToken != null) {
                writer.println("RESUME_SESSION " + sessionToken);
            } else {
                writer.println("LOGIN " + username + " " + password);
            }

            // 5) Read server’s response
            String serverResponse = reader.readLine();
            if (serverResponse == null) {
                System.out.println("Server closed connection unexpectedly.");
                return false;
            }

            // 6) Handle new token if issued
            if (serverResponse.startsWith("TOKEN:")) {
                sessionToken = serverResponse.substring(6).trim();
                serverResponse = reader.readLine(); // Expecting "AUTH_OK"
            }

            if (!"AUTH_OK".equals(serverResponse)) {
                System.out.println("Authentication failed: " + serverResponse);
                return false;
            }

            System.out.println("Authentication successful. Welcome, " + username + "!");
            return true;

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
            return false;
        }
    }

    private static void listenToServer() {
        while (running) {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(username + ": ")) continue;
                    System.out.println(line);
                }

                // If readLine() returns null, the server closed the connection.
                System.out.println("Disconnected from server. Attempting to reconnect...");

            } catch (SocketTimeoutException e) {
                System.out.println("Read timeout. Attempting reconnection...");
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
            }

            int retries = 0;
            final int maxRetries = 10;

            while (running && retries < maxRetries) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}

                ioLock.lock();
                try {
                    if (connectAndAuthenticate(password, false)) {
                        System.out.println("Reconnected.");
                        break;
                    } else {
                        retries++;
                        System.out.println("Retry " + retries + " of " + maxRetries);
                    }
                } finally {
                    ioLock.unlock();
                }
            }

            if (retries >= maxRetries) {
                System.out.println("Failed to reconnect after " + maxRetries + " attempts. Exiting.");
                running = false;
                System.exit(1);
            }

            // Reconnection logic
            if (!running) break;
        }
    }
}
