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
    private static final int WAIT_TIME = 5000;
    private static final String KEYSTORE_PATH = "auth/certs/server-keystore.jks";
    private static final String TRUSTSTORE_PATH = "auth/certs/server-truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";

    private final Map<String, String> userPasswords = new HashMap<>();      // username -> passwordHash
    private DataParser data;
    private final String filePath;

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

            // Conversion to hexadecimal string
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
            underlying.connect(new InetSocketAddress(host, port), WAIT_TIME);

            // Wrap with SSL
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    underlying,
                    host,
                    port,
                    true
            );

            // Start handshake
            sslSocket.startHandshake();

            return sslSocket;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates an SSLServerSocket that accepts connections using only server authentication.
     */
    public static SSLServerSocket createSSLServerSocket(int port) throws Exception {
        // Load server keystore and truststore
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(KEYSTORE_PATH), TRUSTSTORE_PASSWORD.toCharArray());

        KeyStore truststore = KeyStore.getInstance("JKS");
        truststore.load(new FileInputStream(TRUSTSTORE_PATH), TRUSTSTORE_PASSWORD.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keystore, TRUSTSTORE_PASSWORD.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(truststore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory factory = context.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);

        return serverSocket;
    }
}
