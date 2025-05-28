# Server Concurrency: Threads and Synchronization

This document describes the concurrency implementation in the server, focusing on the types of threads used and synchronization of access to shared structures, including output streams.

---

## i) Connection Establishment

- **Thread Creation:**  
  When a new connection is accepted, the server creates a *virtual thread* for each client.
- **Thread Type:**  
  Virtual thread (`Thread.startVirtualThread`), see `Server.java`, line 52.
- **Role:**  
  This thread runs `handleClientConnection`, managing all communication with the client (authentication, commands, etc).
- **Termination:**  
  The thread ends when the client disconnects or an error occurs.

---

## ii) Reception of a "Join" Request (ENTER)

- **Responsible Thread:**  
  The request is processed by the same virtual thread created for the client (`handleClientConnection`).
- **Type:**  
  Virtual thread.
- **Shared Data Structures Accessed:**  
  - `rooms` (global map of chat rooms) — access protected by `roomsLock` (`Server.java`, lines 67, 273).
  - User session state (`Session`).
- **Synchronization:**  
  Uses `ReentrantLock` to protect access to `rooms` (lines 273-277).
- **Response Sending:**  
  The response is sent by the same client thread, using its own output stream (`session.out`).

---

## iii) Reception of a Message (MSG)

- **Responsible Thread:**  
  The client's virtual thread processes the `MSG` command (`Server.java`, line 285).
- **Shared Data Structures Accessed:**  
  - `ChatRoom` (members list, history, etc).
  - Output streams of room members.
- **Synchronization:**  
  - Each `ChatRoom` has its own `ReentrantLock` (`ChatRoom.java`, line 13) protecting access to members, history, and message sending (`broadcast`, lines 27-39).
  - Sending to each output stream happens inside the room lock, ensuring no race conditions between threads writing to the same stream.
- **Message Forwarding:**  
  The same client thread calls `room.broadcast`, which sends the message to all room members.

---

## iv) Processing of Message to the LLM (AI)

- **Responsible Thread:**  
  When a message is sent to an AI room, the client thread creates a new *virtual thread* to process the LLM response (`Server.java`, line 307).
- **Type:**  
  Virtual thread (`Thread.ofVirtual().start`).
- **Shared Data Structures Accessed:**  
  - AI room context (`room.context`), protected by the room lock.
  - Room history and members.
- **Synchronization:**  
  - Access to context and history is protected by the room lock (`ChatRoom.java`, methods `getAIContext`, `setAIContext`, `broadcast`).
- **Simultaneous LLM Messages:**  
  Each message to the LLM is processed in an independent virtual thread. The context is updated inside the room lock, ensuring only one thread at a time modifies the context, avoiding race conditions.
- **Response Sending:**  
  The virtual thread created for the LLM sends the response to all room members using the room's `broadcast` method (with lock).

---

## Synchronization Summary

- **Global Structures:**  
  - `rooms`, `activeSessions`, `userTokens` — protected by `ReentrantLock` in `Server.java`.
- **Per Room:**  
  - Each `ChatRoom` has its own lock to protect members, history, context, and output streams.
- **Output Streams:**  
  - Each session has its own output stream, but sending to all members is done inside the room lock, preventing concurrency issues between threads.

---

## Code References

- **Server.java:**  
  - Thread creation: lines 38, 54  
  - Rooms lock: lines 67, 273  
  - Command processing: lines 273-277, 285-312  
  - LLM threads: line 307

- **ChatRoom.java:**  
  - Room lock: line 13  
  - Critical methods: `join` (17), `leave` (25), `broadcast` (27), `broadcastServer` (39), `setAIContext` (74)

- **Session.java:**  
  - Session lock: line 13  
  - Session state access methods

---