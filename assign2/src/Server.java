import javax.net.ssl.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import rooms.Session;
import rooms.ChatRoom;
import data.DataUtils;
import data.DataParser;
import auth.AuthenticationHandler;

public class Server {
    private static final ReentrantLock roomsLock = new ReentrantLock();
    private static final Map<String, ChatRoom> rooms = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        // Load chat rooms data from JSON
        roomsLock.lock();
        try {
            DataParser data = DataUtils.loadData();
            for (ChatRoom roomData : data.getChatrooms()) {
                ChatRoom chatRoom = new ChatRoom(roomData.getName(), roomData.isAI(), roomData.getPrompt());
                chatRoom.setHistory(roomData.getHistory());
                rooms.put(chatRoom.getName(), chatRoom);
            }
        } finally {
            roomsLock.unlock();
        }

        // Create the TLS server
        try {
            SSLServerSocket serverSocket = AuthenticationHandler.createSSLServerSocket(port);
            System.out.println("Server listening on port " + port);

            while (true) {
                SSLSocket clientSock = (SSLSocket) serverSocket.accept();
                Thread.startVirtualThread(() -> handleClientConnection(clientSock));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleClientConnection(SSLSocket sock) {
        Session session = null;

        try {
            sock.startHandshake(); // Force handshake and validate the client's certificate
            String username = AuthenticationHandler.extractClientCN(sock);

            session = new Session(sock);
            session.username = username;
            session.out.println("AUTH_OK");

            // Add the client's certificate to the server's truststore if it's a new user
            AuthenticationHandler.addCertificateToTruststore(sock);

            // Reload the truststore to recognize new certificates dynamically
            AuthenticationHandler.reloadTruststore();

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

        DataUtils.addMessage(room.getName(), session.username + ": " + message);
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

            DataUtils.addChatroom(roomName, isAI);
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
