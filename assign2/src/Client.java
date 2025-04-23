import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.concurrent.Executors;

public class Client {
    private static final int MAX_ATTEMPTS = 10;
    private static final int WAIT_TIME = 1000;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage: java Client <serverAddr> <port> <keystore> <password>");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        String keystorePath = args[2];
        String keystorePassword = args[3];


        SSLSocket socket = createSSLSocket(serverAddress, port, keystorePath, keystorePassword);
        if (socket == null) {
            System.out.println("Could not connect to the server after multiple attempts. Exiting...");
            return;
        }

        try (
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in))
        ) {
            // Identificação via certificado
            SSLSession session = socket.getSession();
            String clientName = extractCN(session.getLocalPrincipal().getName());
            System.out.println("Authenticated as: " + clientName);

            // Thread para mensagens recebidas
            // fix here, call executor once and call submit for loop
            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(clientName + ": ")) continue;
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });

            // Loop interativo
            System.out.println("Enter commands or messages:");
            String input;
            while ((input = console.readLine()) != null) {
                if (input.trim().isEmpty()) continue;

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
                        case "/leave":
                            writer.println("LEAVE");
                            break;
                        default:
                            System.out.println("Unknown command. Try again.");
                    }
                } else {
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

    private static SSLSocket connectWithRetry(String serverAddress, int port, String keystorePath, String keystorePassword) {
        int attempt = 0;
        int waitTime = WAIT_TIME;

        while (attempt < MAX_ATTEMPTS) {
            try {
                System.out.println("Attempting to connect to server (Attempt " + (attempt + 1) + ")...");
                SSLSocket socket = createSSLSocket(serverAddress, port, keystorePath, keystorePassword);
                socket.startHandshake(); // força o TLS handshake
                return socket;
            } catch (Exception e) {
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

    private static SSLSocket createSSLSocket(String host, int port, String keystorePath, String keystorePassword) throws Exception {
        // Load client keystore (for authentication)
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keystorePath), keystorePassword.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, keystorePassword.toCharArray());

        // Load truststore (to verify the server's certificate)
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream(keystorePath), keystorePassword.toCharArray()); // Use your actual truststore password

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        // Initialize SSL context with both
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        return (SSLSocket) socketFactory.createSocket(host, port);
    }

    private static String extractCN(String dn) {
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return "Unknown";
    }
}
