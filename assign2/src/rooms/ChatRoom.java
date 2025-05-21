package rooms;

import data.DataUtils;
import org.json.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ChatRoom {
    public String name;
    public boolean isAI;
    public String prompt;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Session> members = new ArrayList<>();
    private final List<String> history = new ArrayList<>();

    public ChatRoom(String name, boolean isAI, String prompt) {
        this.name = name;
        this.isAI = isAI;
        this.prompt = prompt;
    }

    public void join(Session s) {
        lock.lock();
        try {
            members.add(s);
            s.out.println(buildJoinBanner(name));
            for (String msg : history) {
                s.out.println(msg);
            }
            broadcastServer("[" + s.getUsername() + " entered the room]");
        } finally {
            lock.unlock();
        }
    }

    public void leave(Session s) {
        lock.lock();
        try {
            members.remove(s);
            broadcastServer("[" + s.getUsername() + " left the room]");
        } finally {
            lock.unlock();
        }
    }

    public void broadcast(String msg) {
        lock.lock();
        try {
            // Add the message to the history
            history.add(msg);

            // Saves the message to the data file
            DataUtils.addMessage(this.name, msg);

            // Send to all members
            for (Session s : members) {
                s.out.println(msg);
                s.out.flush();
            }
        } finally {
            lock.unlock();
        }
    }

    public void broadcastServer(String msg) {
        lock.lock();
        try {
            history.add(msg);
            for (Session s : members) {
                s.out.println(msg);
            }
        } finally {
            lock.unlock();
        }
    }

    public String getName() {
        return name;
    }

    public boolean isAI() {
        return isAI;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(history);
        } finally {
            lock.unlock();
        }
    }

    public void setHistory(List<String> msgs) {
        lock.lock();
        try {
            history.clear();
            history.addAll(msgs);
        } finally {
            lock.unlock();
        }
    }

    private JSONArray aiContext; // Para armazenar o contexto da IA

    public JSONArray getAIContext() {
        lock.lock();
        try {
            return aiContext;
        } finally {
            lock.unlock();
        }
    }

    public void setAIContext(JSONArray context) {
        lock.lock();
        try {
            this.aiContext = context;
        } finally {
            lock.unlock();
        }
    }

    private String buildJoinBanner(String roomName) {
        String base = " JOINED ROOM: " + roomName + " ";
        int totalLength = 92;
        int dashes = (totalLength - base.length()) / 2;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dashes; i++) sb.append("-");
        sb.append(base);
        while (sb.length() < totalLength) sb.append("-");
        return sb.toString();
    }
}
