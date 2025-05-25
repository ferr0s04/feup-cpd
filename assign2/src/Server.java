import javax.net.ssl.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.json.*;

import ai.PromptOut;
import ai.Prompter;
import rooms.Session;
import rooms.ChatRoom;
import data.DataUtils;
import data.DataParser;
import auth.AuthenticationHandler;

public class Server {
    private static final ReentrantLock roomsLock = new ReentrantLock();
    private static final Map<String, ChatRoom> rooms = new HashMap<>();
    private static final Map<String, Session> activeSessions = new HashMap<>();
    private static final Map<String, String> userTokens = new HashMap<>();
    private static final ReentrantLock sessionLock = new ReentrantLock();
    private static final Map<String, String> lastRoomMap = new HashMap<>();

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
                ChatRoom chatRoom = new ChatRoom(roomData.getName(), roomData.isAI(), roomData.getPrompt(), roomData.getAIContext());
                chatRoom.setHistory(roomData.getHistory());
                rooms.put(chatRoom.getName(), chatRoom);
            }
        } finally {
            roomsLock.unlock();
        }

        startSessionMonitor();

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
            sock.startHandshake();

            session = new Session(sock);
            String firstLine = session.in.readLine();

            if (firstLine == null || firstLine.trim().isEmpty()) {
                System.out.println("Client did not provide command");
                return;
            }

            String[] loginParts = firstLine.split(" ");

            // Processa LOGIN ou RESUME_SESSION
            if (loginParts[0].equals("LOGIN")) {
                if (loginParts.length < 3) {
                    session.out.println("AUTH_FAIL invalid login format");
                    session.close();
                    return;
                }

                String username = loginParts[1];
                String password = loginParts[2];

                AuthenticationHandler auth = new AuthenticationHandler("data/data.json");
                if (!auth.authenticate(username, password)) {
                    session.out.println("AUTH_FAIL invalid credentials");
                    return;
                }

                session.setUsername(username);
                System.out.println("User connected: " + username);

                // Gera novo token
                String token = UUID.randomUUID().toString();
                sessionLock.lock();
                try {
                    userTokens.put(username, token);
                    activeSessions.put(username, session);
                } finally {
                    sessionLock.unlock();
                }

                session.out.println("TOKEN:" + token);
                session.out.flush();
                session.out.println("AUTH_OK");
                session.out.flush();

            } else if (loginParts[0].equals("RESUME_SESSION")) {
                if (loginParts.length < 2) {
                    session.out.println("AUTH_FAIL invalid session format");
                    return;
                }

                String token = loginParts[1];
                sessionLock.lock();
                try {
                    String username = null;
                    for (Map.Entry<String, String> entry : userTokens.entrySet()) {
                        if (entry.getValue().equals(token)) {
                            username = entry.getKey();
                            break;
                        }
                    }

                    if (username != null) {
                        session.setUsername(username);
                        activeSessions.put(username, session);
                        session.out.println("AUTH_OK");

                        System.out.println("User reconnected: " + username);

                        // Try to rejoin last room
                        String lastRoomName = lastRoomMap.get(username);
                        if (lastRoomName != null) {
                            roomsLock.lock();
                            try {
                                ChatRoom room = rooms.get(lastRoomName);
                                if (room != null) {
                                    room.join(session);
                                    session.setCurrentRoom(room);
                                    System.out.println(username + " rejoined room: " + lastRoomName);
                                }
                            } finally {
                                roomsLock.unlock();
                            }
                        }
                    } else {
                        session.out.println("AUTH_FAIL invalid token");
                    }

                } finally {
                    sessionLock.unlock();
                }
            } else if (loginParts[0].equals("REGISTER")) {
                String username = loginParts[1];
                String password = loginParts[2];
                AuthenticationHandler authHandler = new AuthenticationHandler("data/data.json");
                if (!authHandler.register(username, password)) {
                    System.out.println("Registration failed. User already exists.\n");
                    session.out.println("REGISTER_FAIL user already exists");
                }
                session.out.println("REGISTER_OK");
                session.out.flush();
            } else {
                session.out.println("AUTH_FAIL invalid command");
                return;
            }

            // --- CHAT LOOP ---
            String line;
            while ((line = session.in.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                String cmd = parts[0];

                switch (cmd) {
                    case "LIST_ROOMS":
                        roomsLock.lock();
                        try {
                            StringBuilder response = new StringBuilder("ROOM_LIST ");
                            for (String roomName : rooms.keySet()) {
                                response.append(roomName).append(",");
                            }
                            if (response.charAt(response.length() - 1) == ',') {
                                response.setLength(response.length() - 1);
                            }
                            session.out.println(response.toString());
                            session.out.flush(); // Força envio imediato
                        } finally {
                            roomsLock.unlock();
                        }
                        break;

                    case "ENTER":
                        if (parts.length < 2) {
                            session.out.println("ERROR Room name required");
                            session.out.flush();
                        } else {
                            handleEnter(session, parts[1]);
                        }
                        break;

                    case "CREATE_ROOM":
                        if (parts.length < 2) {
                            session.out.println("ERROR Room name required");
                            session.out.flush();
                        } else {
                            handleCreateRoom(session, parts, false);
                        }
                        break;

                    case "CREATE_AI":
                        if (parts.length < 2) {
                            session.out.println("ERROR Room name required");
                            session.out.flush();
                        } else {
                            handleCreateRoom(session, parts, true);
                        }
                        break;

                    case "LEAVE":
                        handleLeave(session);
                        break;

                    case "MSG":
                        handleMsg(session, parts.length > 1 ? parts[1] : "");
                        break;

                    case "PING":
                        session.updatePongTime();
                        session.out.println("PONG");
                        session.out.flush();
                        break;


                    default:
                        session.out.println("ERROR Unknown command: " + cmd);
                        session.out.flush();
                }
            }

        } catch (Exception e) {
            System.out.println("Client connection failed: " + e.getMessage());
        } finally {
            if (session != null) {
                session.close();
                sessionLock.lock();
                try {
                    activeSessions.remove(session.getUsername());
                    lastRoomMap.put(session.getUsername(), session.getLastRoomName()); // <-- save it here
                } finally {
                    sessionLock.unlock();
                }
            }
        }
    }

    // Métodos auxiliares para manipular salas e mensagens
    private static ChatRoom getOrCreateRoom(String name, boolean isAI, String prompt, JSONArray context) {
        roomsLock.lock();
        try {
            return rooms.computeIfAbsent(name, rn -> new ChatRoom(rn, isAI, prompt, context));
        } finally {
            roomsLock.unlock();
        }
    }

    private static void handleEnter(Session session, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            session.out.println("ERROR Room name required");
            return;
        }

        ChatRoom newRoom = getOrCreateRoom(roomName, false, null, new JSONArray());
        if (session.getCurrentRoom() != null) session.getCurrentRoom().leave(session);
        newRoom.join(session);
        session.setCurrentRoom(newRoom);
        session.out.println("YOU HAVE ENTERED " + roomName);
        System.out.println("User " + session.getUsername() + " joined room: " + roomName);
    }

    private static void handleMsg(Session session, String message) {
        if (message == null || message.trim().isEmpty()) {
            session.out.println("ERROR Cannot send empty message");
            return;
        }

        ChatRoom room = session.getCurrentRoom();
        if (room == null) {
            session.out.println("ERROR You are not in any room");
            return;
        }

        // Envia a mensagem do usuário para a sala
        room.broadcast(session.getUsername() + ": " + message);

        // Verifica se a sala é uma sala de IA
        if (room.isAI()) {
            try {
                Prompter prompter = new Prompter();
                JSONArray context = room.getAIContext();

                PromptOut aiResponse = prompter.prompt(session.getUsername() + ": " + message, context);
                room.setAIContext(aiResponse.getContext());
                DataUtils.updateContext(room.name, aiResponse.getContext());

                String aiMessage = "AI: " + aiResponse.getResponse();
                room.broadcast(aiMessage);
            } catch (Exception e) {
                session.out.println("ERROR AI failed to respond: " + e.getMessage());
            }
        }
    }

    private static void handleCreateRoom(Session session, String[] parts, boolean isAI) {
        if (parts.length < 2) {
            session.out.println("ERROR Room name required");
            return;
        }

        String roomName = parts[1];
        String prompt = null;
        System.out.println(parts[2]);
        if (parts.length >= 3 && isAI) {
            isAI = true;
            prompt = parts[2];
        }

        roomsLock.lock();
        try {
            if (rooms.containsKey(roomName)) {
                session.out.println("ERROR Room already exists");
                return;
            }
            ChatRoom room = new ChatRoom(roomName, isAI, prompt, new JSONArray());
            rooms.put(roomName, room);
            session.setCurrentRoom(room);
            room.join(session);
            session.out.println("ROOM_CREATED " + roomName + (isAI ? " (AI)" : ""));

            DataUtils.addChatroom(roomName, isAI);

            if (prompt != null) {
                Prompter prompter = new Prompter();
                PromptOut promptOut = prompter.prompt(prompt, new JSONArray());
                room.setAIContext(promptOut.getContext());

                DataUtils.updateContext(roomName, promptOut.getContext());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            roomsLock.unlock();
        }
    }

    private static void handleLeave(Session session) {
        ChatRoom room = session.getCurrentRoom();
        if (room == null) {
            session.out.println("ERROR You’re not in any room");
            return;
        }
        room.leave(session);
        session.setCurrentRoom(null);
        session.out.println("YOU HAVE LEFT " + room.getName());
        System.out.println("User " + session.getUsername() + " left room: " + room.getName());
    }

    private static void startSessionMonitor() {
        Thread monitor = Thread
                .ofVirtual()
                .name("session-monitor", 0)
                .unstarted(() -> {
                    final long TIMEOUT = 15000;
                    while (true) {
                        sessionLock.lock();
                        try {
                            long now = System.currentTimeMillis();
                            List<String> toRemove = new ArrayList<>();
                            for (var entry : activeSessions.entrySet()) {
                                Session s = entry.getValue();
                                if (s.isClosed()) {
                                    toRemove.add(entry.getKey());
                                    continue;
                                }
                                if (now - s.getLastPongTime() > TIMEOUT) {
                                    System.out.println("User disconnected: " + s.getUsername());
                                    s.close();
                                    toRemove.add(entry.getKey());
                                }
                            }
                            for (String user : toRemove) {
                                activeSessions.remove(user);
                            }
                        } finally {
                            sessionLock.unlock();
                        }
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {}
                    }
                });
        monitor.setDaemon(true);
        monitor.start();
    }

}
