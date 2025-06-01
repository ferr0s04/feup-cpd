# CPD - 2nd Project
This document provides details regarding the implementation of some aspects of the chat application, such as:
- Security and Authentication
- Server Concurrency: Threads and Synchronization
- Fault Tolerance

## 1. Security and Authentication

### i) User Registration and Authentication Protocols
- **Registration Protocol:**
  - The client can send a `REGISTER <username> <password>` command.
  - The server checks if the user exists and, if not, registers the user and persists the data (`AuthenticationHandler.register()`).
  - Registration data is persisted in `data/data.json` via `DataUtils.saveData()`.

- **Authentication Protocol:**
  - The client sends `LOGIN <username> <password>`.
  - The server authenticates using `AuthenticationHandler.authenticate()`.
  - If successful, a session token is generated and sent to the client.
  - The client can later use `RESUME_SESSION <token>` to reconnect if the connection is lost.

---

### ii) Persistence of Registration Data
- User data is stored in `data/data.json` (see `DataUtils.java` and `User.java`).
- On registration, the new user is added to the file.
- On server start, users are loaded from this file.

---

### iii) Decoupling Authentication from Chatting
- After authentication, the user receives a session token.
- The user can move between rooms and chat without re-authenticating.
- The session is maintained in memory (`activeSessions` in `Server.java`).

---

### iv) Secure Channels
- All client-server communication uses `javax.net.ssl.SSLSocket` and `SSLServerSocket`.
- The server and client both use keystores/truststores for SSL/TLS.
- All data (including credentials) is sent over a secure channel.

---

### Code References

- **Server.java:**
  - Store new users: lines 157-170
  - Authenticate users: lines 77-110
  - Generate and send session tokens: lines 97-106
  - Handle reconnect: lines 111-156

- **Client.java:**
  - Send register command: lines 222-223
  - Send login command: lines 224-225
  - Reconnect with token: lines 220-221

---

## 2. Server Concurrency: Threads and Synchronization

### i) Connection Establishment

- **Thread Creation:**  
  When a new connection is accepted, the server creates a *virtual thread* for each client.
- **Thread Type:**  
  Virtual thread (`Thread.startVirtualThread`), see `Server.java`, line 52.
- **Role:**  
  This thread runs `handleClientConnection`, managing all communication with the client (authentication, commands, etc).
- **Termination:**  
  The thread ends when the client disconnects or an error occurs.

---

### ii) Reception of a "Join" Request (ENTER)

- **Responsible Thread:**  
  The request is processed by the same virtual thread created for the client (`handleClientConnection`).
- **Thread Type:**  
  Virtual thread.
- **Shared Data Structures Accessed:**  
  - `rooms` (global map of chat rooms) — access protected by `roomsLock` (Created in `Server.java` on line 16).
  - User session state (`Session`).
- **Synchronization:**  
  - Access to rooms is protected by `roomsLock` (`Server.java`, lines 201: `handleEnter(session, parts[1])` calls `getOrCreateRoom`, which locks roomsLock at line 262).
  - Each `ChatRoom` uses its own ReentrantLock for critical operations (`ChatRoom.java`, line 14).
- **Response Sending:**  
  The response is sent by the same client thread, using its own output stream (`session.out`, line 280).

---

### iii) Reception of a Message (MSG)

- **Responsible Thread:**  
  Again, the client's virtual thread processes the `MSG` command (`Server.java`, line 285).
- **Thread Type:**  
  Virtual thread.
- **Shared Data Structures Accessed:**  
  - The `ChatRoom` object for the current room (members list, history, context).
  - The output streams (`session.out`) of all room members.
- **Synchronization:**  
  - Each `ChatRoom` has its own `ReentrantLock` (`ChatRoom.java`, line 14) protecting access to members, history, and message sending (`broadcast` functions).
  - Sending to each output stream happens inside the room lock, ensuring no race conditions between threads writing to the same stream.
- **Message Forwarding:**  
  The same client thread calls `room.broadcast`, which sends the message to all room members inside the room lock.
- **Race Condition Prevention:**
  - Like said before, all writes to the output streams of room members are performed inside the `ChatRoom`'s lock (see `broadcast` method).
  - This guarantees that no two threads can write to the same output stream at the same time, even if multiple clients send messages concurrently to the same room.

---

### iv) Processing of Message to the LLM (AI)

- **Responsible Thread:**  
  When a message is sent to an AI room, the client thread creates a new *virtual thread* to process the LLM response (`Server.java`, line 307).
- **Thread Type:**  
  Virtual thread.
- **Shared Data Structures Accessed:**  
  - AI room context (`room.context`), protected by the room lock.
  - Room history and members.
- **Synchronization:**  
  - Access to context and history is protected by the room lock (`ChatRoom.java`, methods `getAIContext`, `setAIContext`, `broadcast`).
- **Simultaneous LLM Messages:**  
  Each message to the LLM is processed in an independent virtual thread. The context is updated inside the room lock, ensuring only one thread at a time modifies the context, avoiding race conditions.
- **Response Sending:**  
  The virtual thread created for the LLM sends the response to all room members using the room's `broadcast` method (with lock).
- **API Call Formating**
  The LLM call is made utilizing the message sent by the user (which is put into the `prompt` field in the API call) and the previous context (a JSONArray which is put into the 'context' field also in the API call). 
- **Context in AI Room Creation**
  In order to create an AI chatroom with a predefined context, the room is created and then a prompt is sent out with the requested context. The `context` received is then saved, but not the AI response.

---

### Synchronization Summary

- **Global Structures:**  
  - `rooms`, `activeSessions`, `userTokens` — all accesses are protected by a `ReentrantLock` in `Server.java` to prevent concurrent modifications.
- **Per Room:**  
  - Each `ChatRoom` instance has its own `ReentrantLock` to protect its members list, message history, context, and all operations that modify or read these fields.
- **Output Streams:**  
  - Each session has its own output stream, but all writes to the output streams of room members are performed inside the room's lock (in `ChatRoom.broadcast` and `broadcastServer`).
  - This ensures that no two threads can write to the same output stream at the same time, preventing race conditions and message interleaving.
- **Critical Operations:**  
  - All operations that modify shared state (`join`, `leave`, `broadcast`, `context update`) are performed inside the appropriate lock (global or per-room), ensuring thread safety throughout the server.

---

### Code References

- **Server.java:**  
  - Thread creation: lines 52, 378  
  - Rooms lock: lines 67, 262  
  - Command processing: lines 273-277, 285-312  
  - LLM threads: line 303

- **ChatRoom.java:**  
  - Room lock: line 14  
  - Critical methods: `join` (25), `leave` (39), `broadcast` (49), `broadcastServer` (68), `setAIContext` (120)

- **Session.java:**  
  - Session lock: line 12  
  - Session state access methods

---

## 3. Fault Tolerance

### i) Client Side
- The client receives a token from the server after successful authentication (`TOKEN:`).
- On reconnect, the client uses this token to resume the session (`RESUME_SESSION <token>`), not the password.
- The client does **not** cache the password for reconnection; it only uses the token.
- The client automatically attempts to reconnect and resume the session if the connection is lost.
- The client does not require the user to re-authenticate or rejoin the room after a reconnect.

---

### ii) Server Side
- The server issues a unique token per user on login and stores it in `userTokens`.
- On `RESUME_SESSION <token>`, the server finds the user by token and restores the session.
- The server attempts to rejoin the user to their last room using `lastRoomMap`.
- The server maintains user state (current room, session) and relays messages to the reconnected session.
- The server does **not** implement token expiration (room for improvement).

---

### iii) User State
- The user's last room is tracked and restored on reconnect.
- The session is re-bound to the user and room after reconnection.
- The server relays messages to the new session.
