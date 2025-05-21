package data;

import rooms.ChatRoom;
import java.util.ArrayList;
import java.util.List;

public class DataParser {

    private List<User> users;

    private List<ChatRoom> chatrooms;

    public DataParser() {
        this.users = new ArrayList<>();
        this.chatrooms = new ArrayList<>();
    }

    public List<User> getUsers() {
        return users;
    }

    public List<ChatRoom> getChatrooms() {
        return chatrooms;
    }

    public void addUser(User user) {
        this.users.add(user);
    }
}
