import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import rooms.Session;
import rooms.ChatRoom;
import auth.Authenticator;
import data.DataUtils;
import data.DataParser;

public class Server {
    // --- Global shared data ---
    private static final ReentrantLock roomsLock = new ReentrantLock();
    private static final Map<String, ChatRoom> rooms = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        String usersFile = "data/data.json";

        // Initialize authenticator
        Authenticator authenticator;
        try {
            authenticator = new Authenticator(usersFile);
        } catch (IOException e) {
            System.err.println("Failed to load user database: " + e.getMessage());
            return;
        }

        // --- STARTUP: initialize rooms from data.json ---
        roomsLock.lock();
        try {
            DataParser data = DataUtils.loadData(); // Carrega os dados do arquivo JSON
            List<ChatRoom> chatroomsFromData = data.getChatrooms();  // Obtem as chatrooms do JSON
            for (ChatRoom roomData : chatroomsFromData) {
                // Cria a sala de chat com os dados carregados
                ChatRoom chatRoom = new ChatRoom(roomData.getName(), roomData.isAI(), roomData.getPrompt());
                rooms.put(chatRoom.getName(), chatRoom);  // Adiciona a sala ao mapa
            }
        } finally {
            roomsLock.unlock();
        }

        // --- Start listening for clients ---
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (true) {
                Socket clientSock = serverSocket.accept();
                Thread.startVirtualThread(() -> handleClientConnection(clientSock, authenticator));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClientConnection(Socket sock, Authenticator auth) {
        Session session = null;
        try {
            session = new Session(sock);

            // --- AUTHENTICATION PHASE ---
            String authLine;
            while ((authLine = session.in.readLine()) != null) {
                if (!authLine.startsWith("AUTH ")) {
                    session.out.println("AUTH_FAIL invalid command");
                    continue;
                }
                String[] tokens = authLine.split(" ", 3);
                if (tokens.length < 3) {
                    session.out.println("AUTH_FAIL missing credentials");
                    continue;
                }
                String user = tokens[1];
                String pass = tokens[2];
                if (!auth.authenticate(user, pass)) {
                    session.out.println("AUTH_FAIL invalid credentials");
                } else {
                    session.username = user;
                    session.out.println("AUTH_OK");
                    break;
                }
            }

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
                    default:
                        session.out.println("ERROR Unknown command: " + cmd);
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            if (session != null) session.close();
        }
    }

    // Utility: safely get or create a room
    private static ChatRoom getOrCreateRoom(String name, boolean isAI, String prompt) {
        roomsLock.lock();
        try {
            return rooms.computeIfAbsent(name, rn -> new ChatRoom(rn, isAI, prompt));
        } finally {
            roomsLock.unlock();
        }
    }

    // Utility: handle ENTER command
    private static void handleEnter(Session session, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            session.out.println("ERROR Room name required");
            return;
        }

        ChatRoom newRoom = getOrCreateRoom(roomName, false, null);

        // Leave old room if any
        if (session.currentRoom != null) {
            session.currentRoom.leave(session);
        }

        // Join new room
        newRoom.join(session);
        session.currentRoom = newRoom;

        session.out.println("ENTERED " + roomName);
    }

    // Utility: handle MSG command
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

    // Utility: handle CREATE_ROOM command
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
}
