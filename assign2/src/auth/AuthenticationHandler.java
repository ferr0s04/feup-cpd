package auth;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.*;
import java.util.*;

import data.DataParser;
import data.DataUtils;
import data.User;

public class AuthenticationHandler {
    private static final int MAX_ATTEMPTS = 10;
    private static final int WAIT_TIME = 1000;
    private static final String SERVER_KEYSTORE_PATH = "auth/certs/server-keystore.jks";
    private static final String TRUSTSTORE_PATH = "auth/certs/server-truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";

    private final Map<String, String> userPasswords = new HashMap<>();      // username -> passwordHash
    private final Map<String, List<String>> userChatrooms = new HashMap<>(); // username -> chatrooms
    private final String filePath;
    private DataParser data;

    public AuthenticationHandler(String filePath) {
        this.filePath = filePath;
        loadUsers();
    }


    private void loadUsers() {
        data = DataUtils.loadData();

        for (User user : data.getUsers()) {
            userPasswords.put(user.getUsername(), user.getPasswordHash());
        }
    }

    public boolean authenticate(String username, String password) {
        if (!userPasswords.containsKey(username)) return false;

        String storedHash = userPasswords.get(username);
        String inputHash = hash(password);
        return storedHash.equals(inputHash);
    }

    public boolean register(String username, String password) {
        if (userPasswords.containsKey(username)) {
            return false;
        }

        String passwordHash = hash(password);
        userPasswords.put(username, passwordHash);

        User newUser = new User(username, passwordHash);
        data.getUsers().add(newUser);

        DataUtils.saveData(data);
        return true;
    }

    private String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());

            // converte para string hexadecimal
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found");
        }
    }


    public static SSLSocket connectToServerWithTruststore(String host, int port, String truststorePath, String truststorePassword) {
        try {
            // Load truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(new FileInputStream(truststorePath), truststorePassword.toCharArray());

            // Init TrustManager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(trustStore);

            // Init SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory factory = sslContext.getSocketFactory();

            // Create unconnected socket and connect with timeout
            Socket underlying = new Socket();
            int connectTimeout = 5000; // 5 seconds
            underlying.connect(new InetSocketAddress(host, port), connectTimeout);

            // Wrap with SSL
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    underlying,
                    host,
                    port,
                    true // autoClose underlying on close
            );

            // Set read timeout (optional)
            sslSocket.setSoTimeout(10000); // 10 seconds for read

            // Start handshake (can still timeout here)
            sslSocket.startHandshake();

            return sslSocket;

        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Connects to the TLS server using only the truststore (no client certificate).
     */
    public static SSLSocket connectWithRetry(String serverAddress, int port, String truststorePath, String truststorePassword) {
        int attempt = 0;
        int waitTime = WAIT_TIME;

        while (attempt < MAX_ATTEMPTS) {
            try {
                System.out.println("Attempting to connect to server (Attempt " + (attempt + 1) + ")...");
                SSLSocket socket = createSSLSocket(serverAddress, port, truststorePath, truststorePassword);
                socket.startHandshake(); // Force TLS handshake
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

    private static SSLSocket createSSLSocket(String host, int port,
                                             String truststorePath, String truststorePassword) throws Exception {

        // Only truststore (to validate server certificate)
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream(truststorePath), truststorePassword.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null); // No key managers (client has no cert)

        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket(host, port);
        socket.setEnabledProtocols(new String[]{"TLSv1.2"});

        return socket;
    }

    /**
     * Creates an SSLServerSocket that accepts connections using only server authentication.
     */
    public static SSLServerSocket createSSLServerSocket(int port) throws Exception {
        // Load server keystore and truststore
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream("auth/certs/server-keystore.jks"), "password".toCharArray());

        KeyStore truststore = KeyStore.getInstance("JKS");
        truststore.load(new FileInputStream("auth/certs/server-truststore.jks"), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keystore, "password".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(truststore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory factory = context.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);

        return serverSocket;
    }
}
