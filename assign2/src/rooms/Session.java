package rooms;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class Session {
    public final Socket socket;
    public final BufferedReader in;
    public final PrintWriter out;
    private volatile String username;
    private final ReentrantLock lock = new ReentrantLock();
    private ChatRoom currentRoom = null;
    private String lastRoomName = null;

    public Session(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true); // autoflush
    }

    public void flush() {
        out.flush();
    }

    public ChatRoom getCurrentRoom() {
        lock.lock();
        try {
            return currentRoom;
        } finally {
            lock.unlock();
        }
    }

    public void setCurrentRoom(ChatRoom room) {
        lock.lock();
        try {
            this.currentRoom = room;
        } finally {
            lock.unlock();
        }
    }

    public void clearCurrentRoom() {
        lock.lock();
        try {
            this.lastRoomName = (currentRoom != null) ? currentRoom.getName() : null;
            this.currentRoom = null;
        } finally {
            lock.unlock();
        }
    }

    public String getLastRoomName() {
        lock.lock();
        try {
            return lastRoomName;
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            if (currentRoom != null) {
                currentRoom.leave(this);
                lastRoomName = currentRoom.getName();
                currentRoom = null;
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Error closing session: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }


    public String getUsername() { return username; }

    public void setUsername(String username) {
        this.username = username;
    }

    private volatile long lastPongTime = System.currentTimeMillis();

    public void updatePongTime() {
        lastPongTime = System.currentTimeMillis();
    }

    public long getLastPongTime() {
        return lastPongTime;
    }

    public boolean isClosed() {
        return socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown();
    }
}
