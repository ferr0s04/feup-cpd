import javax.net.ssl.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.Scanner;
import java.util.concurrent.Executors;

import auth.AuthenticationHandler;

public class Client {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        String username, password, confirmPassword;

        // REGISTRATION MODE: If less than 4 arguments, prompt for username and password
        if (args.length < 4) {
            System.out.println("---- REGISTRATION MODE ----");
            System.out.print("Choose a username: ");
            username = scanner.nextLine().trim();

            System.out.print("Choose a password for your keystore: ");
            password = scanner.nextLine().trim();

            System.out.print("Confirm your password: ");
            confirmPassword = scanner.nextLine().trim();

            if (!password.equals(confirmPassword)) {
                System.out.println("Passwords do not match. Please try again.");
                return;
            }

            String keystorePath = "keystore-" + username + ".jks";

            if (Files.exists(Paths.get(keystorePath))) {
                System.out.println("Username already exists. Please try again with a different one.");
                return;
            }

            System.out.println("Registering new user...");

            // Generate certificate, keystore and truststore
            AuthenticationHandler.generateUserCertificate(username, password);

            System.out.println("User registered successfully.");
            System.out.println("Now run the client with: java Client <serverAddr> <port> " + username + " " + password);
            return;
        }

        // LOGIN MODE: When sufficient arguments are provided
        if (args.length < 4) {
            System.out.println("Usage: java Client <serverAddr> <port> <username> <password>");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        username = args[2];
        password = args[3];

        String keystorePath = "auth/certs/keystore-" + username + ".jks";
        String truststorePath = "auth/certs/truststore-" + username + ".jks";

        SSLSocket socket = AuthenticationHandler.connectWithRetry(serverAddress, port, keystorePath, password, truststorePath, password);
        if (socket == null) {
            System.out.println("Could not connect to the server after multiple attempts. Exiting...");
            return;
        }

        try (
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in))
        ) {
            SSLSession session = socket.getSession();
            String clientCN = AuthenticationHandler.extractCN(session.getLocalPrincipal().getName());

            String serverResponse = reader.readLine();
            if (!"AUTH_OK".equals(serverResponse)) {
                System.out.println("Authentication failed: " + serverResponse);
                return;
            }

            System.out.println("Authentication successful. Welcome, " + clientCN + "!");

            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(clientCN + ": ")) continue;
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });

            System.out.println("Enter commands or messages:");
            String input;
            while ((input = console.readLine()) != null) {
                if (input.trim().isEmpty()) continue;

                if (input.startsWith("/")) {
                    String[] parts = input.split(" ", 3);
                    switch (parts[0]) {
                        case "/list": writer.println("LIST_ROOMS"); break;
                        case "/enter": if (parts.length >= 2) writer.println("ENTER " + parts[1]); break;
                        case "/create": if (parts.length >= 2) writer.println("CREATE_ROOM " + parts[1]); break;
                        case "/createai": if (parts.length == 3) writer.println("CREATE_ROOM " + parts[1] + " AI " + parts[2]); break;
                        case "/leave": writer.println("LEAVE"); break;
                        default: System.out.println("Unknown command.");
                    }
                } else {
                    writer.println("MSG " + input);
                }
            }

        } finally {
            socket.close();
        }
    }
}
