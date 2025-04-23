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

            // --- CHATROOMS ---
            if (content.contains("\"chatrooms\"")) {
                int chatroomStart = content.indexOf("\"chatrooms\"");
                if (chatroomStart != -1) {
                    int arrayStart = content.indexOf("[", chatroomStart);
                    int bracketCount = 0;
                    int arrayEnd = -1;

                    for (int i = arrayStart; i < content.length(); i++) {
                        char c = content.charAt(i);
                        if (c == '[') bracketCount++;
                        else if (c == ']') bracketCount--;
                        if (bracketCount == 0) {
                            arrayEnd = i;
                            break;
                        }
                    }

                    if (arrayStart != -1 && arrayEnd != -1) {
                        String roomArray = content.substring(arrayStart + 1, arrayEnd).trim();
                        List<String> roomBlocks = new ArrayList<>();
                        int braceCount = 0;
                        StringBuilder current = new StringBuilder();

                        for (int i = 0; i < roomArray.length(); i++) {
                            char c = roomArray.charAt(i);
                            if (c == '{') {
                                if (braceCount == 0) current = new StringBuilder();
                                braceCount++;
                            }

                            if (braceCount > 0) current.append(c);

                            if (c == '}') {
                                braceCount--;
                                if (braceCount == 0) {
                                    roomBlocks.add(current.toString());
                                }
                            }
                        }

                        for (String roomBlock : roomBlocks) {
                            String name = null;
                            boolean isAI = false;

                            int nameIndex = roomBlock.indexOf("\"name\"");
                            int isAIIndex = roomBlock.indexOf("\"isAI\"");

                            if (nameIndex != -1) {
                                int colonIndex = roomBlock.indexOf(":", nameIndex);
                                int startQuote = roomBlock.indexOf("\"", colonIndex);
                                int endQuote = roomBlock.indexOf("\"", startQuote + 1);
                                if (startQuote != -1 && endQuote != -1) {
                                    name = roomBlock.substring(startQuote + 1, endQuote);
                                }
                            }

                            if (isAIIndex != -1) {
                                int colonIndex = roomBlock.indexOf(":", isAIIndex);
                                String boolString = roomBlock.substring(colonIndex + 1).split(",|\\}")[0].trim();
                                isAI = boolString.equals("true");
                            }

                            List<String> messages = new ArrayList<>();
                            int messagesIndex = roomBlock.indexOf("\"messages\"");
                            if (messagesIndex != -1) {
                                int messageArrayStart = roomBlock.indexOf("[", messagesIndex);
                                int messageArrayEnd = roomBlock.indexOf("]", messageArrayStart);
                                if (messageArrayStart != -1 && messageArrayEnd != -1) {
                                    String raw = roomBlock.substring(messageArrayStart + 1, messageArrayEnd).trim();
                                    if (!raw.isEmpty()) {
                                        List<String> parsedMessages = new ArrayList<>();
                                        StringBuilder msgBuilder = new StringBuilder();
                                        boolean inQuotes = false;
                                        boolean escape = false;

                                        for (int i = 0; i < raw.length(); i++) {
                                            char c = raw.charAt(i);
                                            if (escape) {
                                                msgBuilder.append(c);
                                                escape = false;
                                            } else if (c == '\\') {
                                                escape = true;
                                            } else if (c == '"') {
                                                inQuotes = !inQuotes;
                                                if (!inQuotes) {
                                                    parsedMessages.add(msgBuilder.toString());
                                                    msgBuilder.setLength(0);
                                                }
                                            } else if (inQuotes) {
                                                msgBuilder.append(c);
                                            }
                                        }

                                        messages.addAll(parsedMessages);
                                    }
                                }
                            }

                            if (name != null) {
                                ChatRoom room = new ChatRoom(name, isAI, "");
                                data.getChatrooms().add(room);
                                room.setHistory(messages);
                            }
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
                writer.write("      \"passwordHash\": \"" + user.getPasswordHash() + "\"\n");
                writer.write("    }");
                if (i < users.size() - 1) writer.write(",");
                writer.write("\n");
            }

            writer.write("  ],\n  \"chatrooms\": [\n");

            List<ChatRoom> chatrooms = data.getChatrooms();
            for (int i = 0; i < chatrooms.size(); i++) {
                ChatRoom room = chatrooms.get(i);
                writer.write("    {\n");
                writer.write("      \"name\": \"" + room.getName() + "\",\n");
                writer.write("      \"participants\": [],\n");
                writer.write("      \"messages\": [");
                List<String> messages = room.getHistory();
                for (int j = 0; j < messages.size(); j++) {
                    writer.write("\"" + messages.get(j).replace("\"", "\\\"") + "\"");
                    if (j < messages.size() - 1) writer.write(", ");
                }
                writer.write("],\n");
                writer.write("      \"isAI\": " + room.isAI() + "\n");
                writer.write("    }");

                if (i < chatrooms.size() - 1) writer.write(",");
                writer.write("\n");
            }

            writer.write("  ]\n}");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void addChatroom(String roomName, boolean isAI) {
        DataParser data = loadData();

        // Verifica se a sala já existe
        for (ChatRoom room : data.getChatrooms()) {
            if (room.getName().equals(roomName)) {
                return;
            }
        }

        ChatRoom newRoom = new ChatRoom(roomName, isAI, "");
        data.getChatrooms().add(newRoom);

        saveData(data);
    }

    public static synchronized void addMessage(String roomName, String message) {
        DataParser data = loadData();

        // Atualiza apenas se a sala existir
        for (ChatRoom room : data.getChatrooms()) {
            if (room.getName().equals(roomName)) {
                room.getHistory().add(message);
                break;
            }
        }

        saveData(data);
    }
}
