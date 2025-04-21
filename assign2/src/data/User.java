package data;

import java.util.List;

public class User {

    private String username;
    private String passwordHash;
    private List<String> chatrooms;

    // Construtor
    public User(String username, String passwordHash, List<String> chatrooms) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.chatrooms = chatrooms;
    }

    // Getters e Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public List<String> getChatrooms() {
        return chatrooms;
    }

    public void setChatrooms(List<String> chatrooms) {
        this.chatrooms = chatrooms;
    }
}
