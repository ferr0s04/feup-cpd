import javax.net.ssl.*;
import java.io.*;
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
            System.out.println("---- REGISTRATION MODE ----");
            System.out.print("Choose a username: ");
            username = scanner.nextLine().trim();

            System.out.print("Choose a password: ");
            String password = scanner.nextLine().trim();

            System.out.print("Confirm your password: ");
            String confirmPassword = scanner.nextLine().trim();

            if (!password.equals(confirmPassword)) {
                System.out.println("Passwords do not match. Please try again.");
                return;
            }

            // Here you could store the user locally or notify the server to register (not shown)
            System.out.println("User registered successfully (client-side only).");
            System.out.println("Now run the client with: java Client <serverAddr> <port> <username> <password>");
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
        String password = args[3];

        console = new BufferedReader(new InputStreamReader(System.in));

        // Initial connection
        if (!connectAndAuthenticate(password, true)) return;

        // Start listener and command loop
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(Client::listenToServer);

        System.out.println("Enter commands or messages:");
        String input;
        while ((input = console.readLine()) != null) {
            if (input.trim().isEmpty()) continue;

            if (input.startsWith("/")) {
                String[] parts = input.split(" ", 3);
                switch (parts[0]) {
                    case "/list":
                        ioLock.lock();
                        try { writer.println("LIST_ROOMS"); } finally { ioLock.unlock(); }
                        break;
                    case "/enter":
                        if (parts.length >= 2) {
                            ioLock.lock();
                            try { writer.println("ENTER " + parts[1]); } finally { ioLock.unlock(); }
                        }
                        break;
                    case "/create":
                        if (parts.length >= 2) {
                            ioLock.lock();
                            try { writer.println("CREATE_ROOM " + parts[1]); } finally { ioLock.unlock(); }
                        }
                        break;
                    case "/createai":
                        if (parts.length == 3) {
                            ioLock.lock();
                            try { writer.println("CREATE_ROOM " + parts[1] + " AI " + parts[2]); }
                            finally { ioLock.unlock(); }
                        }
                        break;
                    case "/leave":
                        ioLock.lock();
                        try { writer.println("LEAVE"); } finally { ioLock.unlock(); }
                        break;
                    default:
                        System.out.println("Unknown command.");
                }
            } else {
                ioLock.lock();
                try { writer.println("MSG " + input); } finally { ioLock.unlock(); }
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
                System.out.println("Could not connect to the server after multiple attempts.");
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
        try {
            String line;
            while (true) {
                ioLock.lock();
                try {
                    // Lock while reading from the shared reader
                    line = reader.readLine();
                } finally {
                    ioLock.unlock();
                }

                if (line == null) break; // connection closed

                if (line.startsWith(username + ": ")) continue;
                System.out.println(line);
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Disconnected from server. Attempting to reconnect...");

                while (running) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}

                    ioLock.lock();
                    try {
                        // lock while reconnecting since it modifies writer/reader/socket
                        if (connectAndAuthenticate("password", false)) {
                            System.out.println("Reconnected.");
                            break; // reconnect successful, continue listening
                        } else {
                            System.out.println("Retrying connection...");
                        }
                    } finally {
                        ioLock.unlock();
                    }
                }

                if (running) {
                    // recursive re-entry into listen loop after successful reconnect
                    listenToServer();
                }
            }
        }
    }

}
