package auth;

import java.io.*;
import java.util.*;
import java.security.KeyStore;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import javax.net.ssl.*;

public class AuthenticationHandler {
    private static final int MAX_ATTEMPTS = 10;
    private static final int WAIT_TIME = 1000;
    private static final String TRUSTSTORE_PATH = "certs/server-truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";

    public static SSLSocket connectWithRetry(String serverAddress, int port, String keystorePath, String keystorePassword, String truststorePath, String truststorePassword) {
        int attempt = 0;
        int waitTime = WAIT_TIME;

        while (attempt < MAX_ATTEMPTS) {
            try {
                System.out.println("Attempting to connect to server (Attempt " + (attempt + 1) + ")...");
                SSLSocket socket = createSSLSocket(serverAddress, port, keystorePath, keystorePassword, truststorePath, truststorePassword);
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

    private static SSLSocket createSSLSocket(String host, int port,
                                             String keystorePath, String keystorePassword,
                                             String truststorePath, String truststorePassword) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keystorePath), keystorePassword.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, keystorePassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream(truststorePath), truststorePassword.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket(host, port);
        socket.setEnabledProtocols(new String[] { "TLSv1.2" });

        return socket;
    }

    public static SSLServerSocket createSSLServerSocket(int port) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream("auth/certs/server-keystore.jks"), TRUSTSTORE_PASSWORD.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, TRUSTSTORE_PASSWORD.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream("auth/certs/server-truststore.jks"), TRUSTSTORE_PASSWORD.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);

        serverSocket.setNeedClientAuth(true); // exigir certificado do cliente
        return serverSocket;
    }

    public static String extractCN(String dn) {
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return "Unknown";
    }

    public static String extractClientCN(SSLSocket socket) throws SSLPeerUnverifiedException {
        SSLSession session = socket.getSession();
        X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
        String dn = cert.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return "Unknown";
    }

    // Função para rodar um comando keytool via ProcessBuilder
    public static void runKeytoolCommand(String[] command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO(); // Para que o output do comando apareça no terminal
        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("keytool command failed with exit code " + exitCode);
        }
    }

    public static void generateUserCertificate(String username, String password) throws Exception {
        String keystore = "auth/certs/keystore-" + username + ".jks";
        String truststore = "auth/certs/truststore-" + username + ".jks";
        String certFile = "auth/certs/cert-" + username + ".cer";
        String alias = username;

        // 1. Gera par de chaves e autoassina
        runKeytoolCommand(new String[] {
                "keytool", "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", keystore, "-storepass", password, "-keypass", password,
                "-validity", "365", "-dname", "CN=" + username + ", OU=Dev, O=FEUP, L=Porto, ST=Porto, C=PT",
        });

        // 2. Exporta o certificado
        runKeytoolCommand(new String[] {
                "keytool", "-exportcert", "-alias", alias, "-keystore", keystore,
                "-storepass", password, "-file", certFile
        });

        // 3. Cria truststore do cliente com seu próprio certificado
        runKeytoolCommand(new String[] {
                "keytool", "-importcert", "-noprompt", "-alias", alias, "-file", certFile,
                "-keystore", truststore, "-storepass", password
        });

        // 4. Certifica-se de que o alias do servidor não existe no truststore
        try {
            runKeytoolCommand(new String[] {
                    "keytool", "-delete", "-alias", "server-alias", "-keystore", truststore, "-storepass", password
            });
        } catch (IOException e) {
            // Ignora o erro, se o alias não existir, o comando falhará, mas não faz mal.
        }

        // 5. Atualiza o truststore do cliente com o certificado do servidor
        runKeytoolCommand(new String[] {
                "keytool", "-importcert", "-noprompt", "-alias", "server",
                "-file", "auth/certs/server-cert.cer", // Certificado do servidor que você precisa importar
                "-keystore", truststore, "-storepass", password
        });

        // 6. Atualiza o truststore do servidor com o certificado do cliente
        runKeytoolCommand(new String[] {
                "keytool", "-importcert", "-noprompt", "-alias", alias, "-file", certFile,
                "-keystore", "auth/certs/server-truststore.jks", "-storepass", TRUSTSTORE_PASSWORD
        });
    }

    // Add the client's certificate to the server's truststore
    public static void addCertificateToTruststore(SSLSocket sock) {
        try {
            SSLSession session = sock.getSession();
            Certificate[] certs = session.getPeerCertificates();
            X509Certificate clientCert = (X509Certificate) certs[0];

            // Load the truststore
            KeyStore truststore = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH);
            truststore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
            fis.close();

            // Add the client's certificate to the truststore
            X500Principal subjectPrincipal = clientCert.getSubjectX500Principal();
            String subjectName = subjectPrincipal.getName();
            String alias = subjectName.split(",")[0].split("=")[1]; // Extract CN
            truststore.setCertificateEntry(alias, clientCert);

            // Save the updated truststore
            FileOutputStream fos = new FileOutputStream(TRUSTSTORE_PATH);
            truststore.store(fos, TRUSTSTORE_PASSWORD.toCharArray());
            fos.close();

            System.out.println("Certificate added to truststore: " + alias);
        } catch (Exception e) {
            System.out.println("Failed to add certificate to truststore: " + e.getMessage());
        }
    }

    public static void reloadTruststore() {
        try {
            KeyStore truststore = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH);
            truststore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
            fis.close();
            // Reload or update the SSL context if needed (this can be specific to your use case)
            System.out.println("Truststore reloaded to recognize new certificates.");
        } catch (Exception e) {
            System.out.println("Failed to reload truststore: " + e.getMessage());
        }
    }
}
