import javax.net.ssl.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.Scanner;
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
                String passwordIn = scanner.nextLine().trim();

                System.out.print("Confirm your password: ");
                String confirmPassword = scanner.nextLine().trim();

                if (!passwordIn.equals(confirmPassword)) {
                    System.out.println("Passwords do not match. Please try again.\n");
                    continue;
                }

                password = passwordIn;
                serverAddress = args[0];
                port = Integer.parseInt(args[1]);

                console = new BufferedReader(new InputStreamReader(System.in));

                if (connectAndAuthenticate(password, true, true)){
                    registrationSuccess = true;
                    return;
                }
            }
        }

        // LOGIN MODE
        if (args.length != 4 && args.length != 2) {
            System.out.println("Usage: java Client <serverAddr> <port> <username> <password>");
            return;
        }

        serverAddress = args[0];
        port = Integer.parseInt(args[1]);
        username = args[2];
        password = args[3];

        console = new BufferedReader(new InputStreamReader(System.in));

        // Initial connection
        if (!connectAndAuthenticate(password, true, false)) return;

        // Start listener and command loop
        Thread listenerThread = Thread.ofVirtual()
                .name("client-listener")
                .start(Client::listenToServer);

        System.out.println("--------------------------------------------------------------------------------------------");
        System.out.println("Available commands:");
        System.out.println("/list - List all rooms");
        System.out.println("/enter <room> - Enter a room");
        System.out.println("/create <room> - Create a new room");
        System.out.println("/createai <room> <prompt> - Create an AI room");
        System.out.println("/leave - Leave current room");
        System.out.println("/exit - Exit the system");
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
                        if (parts.length < 3) {
                            System.out.println("Usage: /createai <room_name> <prompt>");
                            continue;
                        }
                        command = "CREATE_AI " + parts[1] + " " + parts[2];
                        break;
                    case "/leave":
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Available commands:");
                        System.out.println("/list - List all rooms");
                        System.out.println("/enter <room> - Enter a room");
                        System.out.println("/create <room> - Create a new room");
                        System.out.println("/createai <room> - Create an AI room");
                        System.out.println("/leave - Leave current room");
                        System.out.println("/exit - Exit the system");
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Enter command:");
                        command = "LEAVE";
                        break;
                    case "/exit":
                        System.exit(0);
                    default:
                        System.out.println("--------------------------------------------------------------------------------------------");
                        System.out.println("Unknown command. Available commands:");
                        System.out.println("/list - List all rooms");
                        System.out.println("/enter <room> - Enter a room");
                        System.out.println("/create <room> - Create a new room");
                        System.out.println("/createai <room> - Create an AI room");
                        System.out.println("/leave - Leave current room");
                        System.out.println("/exit - Exit the system");
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

        listenerThread.interrupt();
        try {
            listenerThread.join(1000);
        } catch (InterruptedException ignored) {}
    }

    private static boolean connectAndAuthenticate(String password, boolean initial, boolean register) {
        try {
            // 1) Establish a fresh connection
            SSLSocket newSock = AuthenticationHandler.connectToServerWithTruststore(
                    serverAddress, port, TRUSTSTORE_PATH, TRUSTSTORE_PASSWORD
            );

            if (newSock == null) {
                System.out.println("Could not connect to the server.");
                return false;
            }

            newSock.setSoTimeout(15000); // 15 seconds timeout

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
            } else if (register) {
                writer.println("REGISTER " + username + " " + password);
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

            if (serverResponse.startsWith("AUTH_FAIL")) {
                System.out.println("Authentication failed: " + serverResponse);
                return false;
            } else if (serverResponse.startsWith("REGISTER_FAIL")) {
                System.out.println("Registration failed: " + serverResponse);
                return false;
            }

            if("AUTH_OK".equals(serverResponse)) {
                System.out.println("Authentication successful. Welcome, " + username + "!");
            } else if ("REGISTER_OK".equals(serverResponse)) {
                System.out.println("Registration successful!");
                System.out.println("Now hop in with: ");
                System.out.println("WINDOWS -> java --enable-preview -cp \".;lib/json-20250107.jar\" Client localhost <PORT> <USER> <PASS>");
                System.out.println("LINUX -> java --enable-preview -cp .:lib/json-20250107.jar Client localhost <PORT> <USER> <PASS>");
            }

            startHeartbeat(); // <-- Start it here
            return true;

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
            return false;
        }
    }

    private static void listenToServer() {
        boolean inHistory = true;
        while (running) {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("PONG".equals(line)) continue;
                    // Detect join banner
                    if (line.contains("JOINED ROOM: ")) {
                        inHistory = true; // Start history mode
                        System.out.println(line);
                        continue;
                    }
                    // Detect end of history
                    if (line.contains("YOU HAVE ENTERED")) {
                        inHistory = false;
                        System.out.println(line);
                        continue;
                    }
                    // Print all history (including own messages) while inHistory
                    if (inHistory) {
                        System.out.println(line);
                        continue;
                    }
                    // After history, suppress own messages
                    if (line.startsWith(username + ": ")) continue;
                    System.out.println(line);
                }
                System.out.println("[!] Lost connection to the server. Attempting to reconnect...");
            } catch (SocketTimeoutException e) {
                System.out.println("[!] Connection timeout. Attempting to reconnect...");
            } catch (IOException e) {
                System.out.println("[!] Connection error: " + e.getMessage());
            }

            // Try to reconnect
            int retries = 0;
            final int maxRetries = 10;
            boolean reconnected = false;

            while (running && retries < maxRetries) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}

                ioLock.lock();
                try {
                    if (connectAndAuthenticate(password, false, false)) {
                        System.out.println("[✓] Reconnected to the server.");
                        reconnected = true;
                        break;
                    } else {
                        retries++;
                        System.out.println("[…] Retry " + retries + " of " + maxRetries);
                    }
                } finally {
                    ioLock.unlock();
                }
            }

            if (!reconnected) {
                System.out.println("[✗] Failed to reconnect after " + maxRetries + " attempts. Exiting.");
                running = false;
                System.exit(1);
            }
        }

    }

    private static void startHeartbeat() {
        Thread heartbeat = Thread
                .ofVirtual()
                .name("heartbeat")
                .unstarted(() -> {
                    while (running) {
                        try {
                            Thread.sleep(10000); // every 10 seconds
                            ioLock.lock();
                            try {
                                writer.println("PING");
                                writer.flush();
                            } finally {
                                ioLock.unlock();
                            }
                        } catch (InterruptedException ignored) {}
                    }
                });
        heartbeat.setDaemon(true);
        heartbeat.start();
    }
}
