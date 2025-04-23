import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import rooms.Session;
import rooms.ChatRoom;
import data.DataUtils;
import data.DataParser;

public class Server {
    private static final ReentrantLock roomsLock = new ReentrantLock();
    private static final Map<String, ChatRoom> rooms = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        // Carrega salas a partir do JSON
        roomsLock.lock();
        try {
            DataParser data = DataUtils.loadData();
            for (ChatRoom roomData : data.getChatrooms()) {
                ChatRoom chatRoom = new ChatRoom(roomData.getName(), roomData.isAI(), roomData.getPrompt());
                rooms.put(chatRoom.getName(), chatRoom);
            }
        } finally {
            roomsLock.unlock();
        }

        // Cria o servidor TLS
        try {
            SSLServerSocket serverSocket = createSSLServerSocket(port);
            System.out.println("TLS server listening on port " + port);

            while (true) {
                SSLSocket clientSock = (SSLSocket) serverSocket.accept();
                Thread.startVirtualThread(() -> handleClientConnection(clientSock));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SSLServerSocket createSSLServerSocket(int port) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream("server-keystore.jks"), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "password".toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream("server-truststore.jks"), "password".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);

        serverSocket.setNeedClientAuth(true); // exigir certificado do cliente
        return serverSocket;
    }

    private static void handleClientConnection(SSLSocket sock) {
        Session session = null;

        try {
            sock.startHandshake(); // força handshake e valida o certificado do cliente
            String username = extractClientCN(sock);

            session = new Session(sock);
            session.username = username;
            session.out.println("AUTH_OK");

            // --- CHAT LOOP ---
            String line;
            while ((line = session.in.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                String cmd = parts[0];
                switch (cmd) {
                    case "LIST_ROOMS":
                        roomsLock.lock();
                        try {
                            session.out.println("ROOM_LIST " + String.join(",", rooms.keySet()));
                        } finally {
                            roomsLock.unlock();
                        }
                        break;
                    case "ENTER":
                        if (parts.length >= 2) handleEnter(session, parts[1]);
                        else session.out.println("ERROR Room name required");
                        break;
                    case "MSG":
                        handleMsg(session, parts.length > 1 ? parts[1] : "");
                        break;
                    case "CREATE_ROOM":
                        handleCreateRoom(session, parts);
                        break;
                    case "LEAVE":
                        handleLeave(session);
                        break;
                    default:
                        session.out.println("ERROR Unknown command: " + cmd);
                }
            }
        } catch (Exception e) {
            System.out.println("Client connection failed: " + e.getMessage());
        } finally {
            if (session != null) session.close();
        }
    }

    private static String extractClientCN(SSLSocket socket) throws SSLPeerUnverifiedException {
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

    // Métodos auxiliares para manipular salas e mensagens
    private static ChatRoom getOrCreateRoom(String name, boolean isAI, String prompt) {
        roomsLock.lock();
        try {
            return rooms.computeIfAbsent(name, rn -> new ChatRoom(rn, isAI, prompt));
        } finally {
            roomsLock.unlock();
        }
    }

    private static void handleEnter(Session session, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            session.out.println("ERROR Room name required");
            return;
        }

        ChatRoom newRoom = getOrCreateRoom(roomName, false, null);
        if (session.currentRoom != null) session.currentRoom.leave(session);
        newRoom.join(session);
        session.currentRoom = newRoom;
        session.out.println("YOU HAVE ENTERED " + roomName);
    }

    private static void handleMsg(Session session, String message) {
        if (message == null || message.trim().isEmpty()) {
            session.out.println("ERROR Cannot send empty message");
            return;
        }

        ChatRoom room = session.currentRoom;
        if (room == null) {
            session.out.println("ERROR You are not in any room");
            return;
        }

        room.broadcast(session.username + ": " + message);
    }

    private static void handleCreateRoom(Session session, String[] parts) {
        if (parts.length < 2) {
            session.out.println("ERROR Room name required");
            return;
        }

        String roomName = parts[1];
        boolean isAI = false;
        String prompt = null;

        if (parts.length >= 4 && "AI".equalsIgnoreCase(parts[2])) {
            isAI = true;
            prompt = parts[3];
        }

        roomsLock.lock();
        try {
            if (rooms.containsKey(roomName)) {
                session.out.println("ERROR Room already exists");
                return;
            }
            ChatRoom room = new ChatRoom(roomName, isAI, prompt);
            rooms.put(roomName, room);
            session.currentRoom = room;
            room.join(session);
            session.out.println("ROOM_CREATED " + roomName + (isAI ? " (AI)" : ""));
        } finally {
            roomsLock.unlock();
        }
    }

    private static void handleLeave(Session session) {
        ChatRoom room = session.currentRoom;
        if (room == null) {
            session.out.println("ERROR You’re not in any room");
            return;
        }
        room.leave(session);
        session.currentRoom = null;
        session.out.println("YOU HAVE LEFT " + room.getName());
    }
}
