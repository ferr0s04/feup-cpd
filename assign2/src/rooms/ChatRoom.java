package rooms;

import org.json.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import ai.Prompter;

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
            s.out.println("---------------------------------- " + "JOINED ROOM: " + name + " ----------------------------------");
            for (String msg : history) {
                s.out.println(msg);
            }
            broadcast("[" + s.getUsername() + " entered the room]");
        } finally {
            lock.unlock();
        }
    }

    public void leave(Session s) {
        lock.lock();
        try {
            members.remove(s);
            broadcast("[" + s.getUsername() + " left the room]");
        } finally {
            lock.unlock();
        }
    }

    public void broadcast(String msg) {
        lock.lock();
        try {
            history.add(msg);
            for (Session s : members) {
                s.out.println(msg);
            }

            if (isAI) {
                // TODO: Call LLM process and broadcast response as "Bot: ..."
                Prompter prompter = new Prompter();
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

}
