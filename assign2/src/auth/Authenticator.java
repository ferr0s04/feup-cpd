package auth;

import data.User;
import data.DataParser;
import data.DataUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Authenticator {

    private final Map<String, String> userPasswords = new HashMap<>();      // username -> passwordHash
    private final Map<String, List<String>> userChatrooms = new HashMap<>(); // username -> chatrooms
    private final String filePath; // ainda usado apenas como referência
    private DataParser data;

    public Authenticator(String filePath) throws IOException {
        this.filePath = filePath;
        loadUsers();
    }

    private void loadUsers() {
        data = DataUtils.loadData();

        for (User user : data.getUsers()) {
            userPasswords.put(user.getUsername(), user.getPasswordHash());
            userChatrooms.put(user.getUsername(), user.getChatrooms());
        }
    }

    public boolean authenticate(String username, String password) {
        if (!userPasswords.containsKey(username)) return false;

        String storedHash = userPasswords.get(username);
        String inputHash = hash(password);
        return storedHash.equals(inputHash);
    }

    public List<String> getUserChatrooms(String username) {
        return userChatrooms.getOrDefault(username, Collections.emptyList());
    }

    public boolean register(String username, String password, List<String> chatrooms) {
        if (userPasswords.containsKey(username)) {
            System.out.println("User already exists!");
            return false;
        }

        String passwordHash = hash(password);
        userPasswords.put(username, passwordHash);
        userChatrooms.put(username, chatrooms);

        User newUser = new User(username, passwordHash, chatrooms);
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
}
