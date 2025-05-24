package data;

import org.json.*;
import rooms.ChatRoom;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DataUtils {
    private static final String DATA_FILE = "data/data.json";

    public static DataParser loadData() {
        DataParser data = new DataParser();

        try {
            Path path = Paths.get(DATA_FILE);
            if (!Files.exists(path)) {
                saveData(data);
                return data;
            }

            String content = new String(Files.readAllBytes(path));
            JSONObject json = new JSONObject(content);

            // Carrega usuários
            JSONArray users = json.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.getJSONObject(i);
                data.addUser(new User(
                        user.getString("username"),
                        user.getString("passwordHash")
                ));
            }

            // Carrega salas de chat
            JSONArray rooms = json.getJSONArray("chatrooms");
            for (int i = 0; i < rooms.length(); i++) {
                JSONObject room = rooms.getJSONObject(i);
                ChatRoom chatRoom = new ChatRoom(
                        room.getString("name"),
                        room.getBoolean("isAI"),
                        room.optString("prompt", ""),
                        room.getJSONArray("context")
                );

                // Carrega mensagens
                JSONArray messages = room.getJSONArray("messages");
                List<String> messageList = new ArrayList<>();
                for (int j = 0; j < messages.length(); j++) {
                    messageList.add(messages.getString(j));
                }
                chatRoom.setHistory(messageList);

                data.getChatrooms().add(chatRoom);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }

    public static void saveData(DataParser data) {
        try {
            JSONObject json = new JSONObject();

            // Salva usuários
            JSONArray users = new JSONArray();
            for (User user : data.getUsers()) {
                JSONObject userJson = new JSONObject();
                userJson.put("username", user.getUsername());
                userJson.put("passwordHash", user.getPasswordHash());
                users.put(userJson);
            }
            json.put("users", users);

            // Salva salas
            JSONArray rooms = new JSONArray();
            for (ChatRoom room : data.getChatrooms()) {
                JSONObject roomJson = new JSONObject();
                roomJson.put("name", room.getName());
                roomJson.put("messages", new JSONArray(room.getHistory()));
                roomJson.put("isAI", room.isAI());
                if (!room.getPrompt().isEmpty()) {
                    roomJson.put("prompt", room.getPrompt());
                }
                roomJson.put("context", room.getAIContext());
                rooms.put(roomJson);
            }
            json.put("chatrooms", rooms);

            // Salva o arquivo formatado
            Files.write(
                    Paths.get(DATA_FILE),
                    json.toString(2).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void addChatroom(String roomName, boolean isAI) {
        DataParser data = loadData();

        // Verifica se a sala já existe
        if (data.getChatrooms().stream()
                .noneMatch(r -> r.getName().equals(roomName))) {
            ChatRoom newRoom = new ChatRoom(roomName, isAI, "", new JSONArray());
            data.getChatrooms().add(newRoom);
            saveData(data);
        }
    }

    public static synchronized void addMessage(String roomName, String message) {
        try {
            // Lê o arquivo atual
            String content = new String(Files.readAllBytes(Paths.get(DATA_FILE)));
            JSONObject json = new JSONObject(content);
            JSONArray rooms = json.getJSONArray("chatrooms");

            // Encontra e atualiza a sala específica
            for (int i = 0; i < rooms.length(); i++) {
                JSONObject room = rooms.getJSONObject(i);
                if (room.getString("name").equals(roomName)) {
                    JSONArray messages = room.getJSONArray("messages");
                    messages.put(message);
                    break;
                }
            }

            // Salva o arquivo atualizado
            Files.write(
                    Paths.get(DATA_FILE),
                    json.toString(2).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException e) {
            System.out.println("[DEBUG] Erro ao salvar mensagem: " + e.getMessage());
        }
    }

    public static synchronized void updateContext(String roomName, JSONArray context) {
        try {
            // Lê o arquivo atual
            String content = new String(Files.readAllBytes(Paths.get(DATA_FILE)));
            JSONObject json = new JSONObject(content);
            JSONArray rooms = json.getJSONArray("chatrooms");

            // Encontra e atualiza a sala específica
            for (int i = 0; i < rooms.length(); i++) {
                JSONObject room = rooms.getJSONObject(i);
                if (room.getString("name").equals(roomName)) {

                    room.remove("context");
                    room.put("context", context);
                    break;
                }
            }

            // Salva o arquivo atualizado
            Files.write(
                    Paths.get(DATA_FILE),
                    json.toString(2).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException e) {
            System.out.println("[DEBUG] Erro ao salvar contexto: " + e.getMessage());
        }
    }
}