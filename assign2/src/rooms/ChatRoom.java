package rooms;

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
            s.out.println("ROOM_JOINED " + name);
            for (String msg : history) {
                s.out.println("ROOM_MSG " + name + " " + msg);
            }
            broadcast("[" + s.username + " enters the room]");
        } finally {
            lock.unlock();
        }
    }

    public void leave(Session s) {
        lock.lock();
        try {
            members.remove(s);
            broadcast("[" + s.username + " leaves the room]");
        } finally {
            lock.unlock();
        }
    }

    public void broadcast(String msg) {
        lock.lock();
        try {
            history.add(msg);
            for (Session s : members) {
                s.out.println("ROOM_MSG " + name + " " + msg);
            }

            if (isAI) {
                // TODO: Call LLM process and broadcast response as "Bot: ..."
            }

        } finally {
            lock.unlock();
        }
    }

    // Getters e Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAI() {
        return isAI;
    }

    public void setAI(boolean AI) {
        isAI = AI;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }


}
