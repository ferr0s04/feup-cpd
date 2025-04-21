package data;

import data.DataParser;
import data.User;
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
                saveData(data); // cria estrutura vazia
                return data;
            }

            List<String> lines = Files.readAllLines(path);
            StringBuilder json = new StringBuilder();
            for (String line : lines) {
                json.append(line.trim());
            }

            String content = json.toString();

            // --- USERS ---
            if (content.contains("\"users\"")) {
                int start = content.indexOf("[", content.indexOf("\"users\""));
                int end = content.indexOf("]", start);
                if (start != -1 && end != -1) {
                    String userArray = content.substring(start + 1, end).trim();
                    String[] userBlocks = userArray.split("\\},\\s*\\{");

                    for (String userBlock : userBlocks) {
                        userBlock = userBlock.replace("{", "").replace("}", "").trim();
                        Map<String, String> fields = new HashMap<>();

                        String[] entries = userBlock.split(",");
                        for (String entry : entries) {
                            String[] pair = entry.split(":", 2);
                            if (pair.length != 2) continue;

                            String key = pair[0].replaceAll("\"", "").trim();
                            String value = pair[1].replaceAll("\"", "").trim();
                            fields.put(key, value);
                        }

                        String username = fields.get("username");
                        String passwordHash = fields.get("passwordHash");
                        if (username != null && passwordHash != null) {
                            User user = new User(username, passwordHash, new ArrayList<>());
                            data.addUser(user);
                        }
                    }
                }
            }

            // --- CHATROOMS ---
            if (content.contains("\"chatrooms\"")) {
                int start = content.indexOf("[", content.indexOf("\"chatrooms\""));
                int end = content.indexOf("]", start);
                if (start != -1 && end != -1) {
                    String roomArray = content.substring(start + 1, end).trim();
                    String[] roomBlocks = roomArray.split("\\},\\s*\\{");

                    for (String roomBlock : roomBlocks) {
                        roomBlock = roomBlock.replace("{", "").replace("}", "").trim();
                        Map<String, String> fields = new HashMap<>();

                        String[] entries = roomBlock.split(",");
                        for (String entry : entries) {
                            String[] pair = entry.split(":", 2);
                            if (pair.length != 2) continue;

                            String key = pair[0].replaceAll("\"", "").trim();
                            String value = pair[1].replaceAll("\"", "").trim();
                            fields.put(key, value);
                        }

                        String name = fields.get("name");
                        boolean isAI = Boolean.parseBoolean(fields.getOrDefault("isAI", "false"));
                        String prompt = fields.getOrDefault("prompt", "");

                        if (name != null) {
                            ChatRoom room = new ChatRoom(name, isAI, prompt);
                            data.getChatrooms().add(room);
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }


    public static void saveData(DataParser data) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(DATA_FILE))) {
            writer.write("{\n  \"users\": [\n");

            List<User> users = data.getUsers();
            for (int i = 0; i < users.size(); i++) {
                User user = users.get(i);
                writer.write("    {\n");
                writer.write("      \"username\": \"" + user.getUsername() + "\",\n");
                writer.write("      \"passwordHash\": \"" + user.getPasswordHash() + "\",\n");
                writer.write("      \"chatrooms\": [");

                List<String> rooms = user.getChatrooms();
                for (int j = 0; j < rooms.size(); j++) {
                    writer.write("\"" + rooms.get(j) + "\"");
                    if (j < rooms.size() - 1) writer.write(", ");
                }

                writer.write("]\n    }");
                if (i < users.size() - 1) writer.write(",");
                writer.write("\n");
            }

            writer.write("  ],\n  \"chatrooms\": [\n");

            List<ChatRoom> chatrooms = data.getChatrooms();
            for (int i = 0; i < chatrooms.size(); i++) {
                ChatRoom room = chatrooms.get(i);
                writer.write("    {\n");
                writer.write("      \"name\": \"" + room.getName() + "\",\n");
                writer.write("      \"isAI\": " + room.isAI() + ",\n");
                writer.write("      \"prompt\": \"" + room.getPrompt().replace("\"", "\\\"") + "\"\n");
                writer.write("    }");

                if (i < chatrooms.size() - 1) writer.write(",");
                writer.write("\n");
            }

            writer.write("  ]\n}");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
