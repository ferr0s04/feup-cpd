package rooms;

import java.io.*;
import java.net.Socket;

public class Session {
    public final Socket socket;
    public final BufferedReader in;
    public final PrintWriter out;
    public String username;          // set after AUTH
    public ChatRoom currentRoom = null;     // null until ENTER

    public Session(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true); // autoflush
    }

    public void close() {
        try {
            if (currentRoom != null) {
                currentRoom.leave(this);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Error closing session: " + e.getMessage());
        }
    }
}
