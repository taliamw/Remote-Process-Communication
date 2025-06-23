# Java Multi-Client Chat Server

A console-based multi-client chat application in Java using sockets. Clients connect to a server, send messages, and the server routes them to the appropriate recipients—either private or broadcast.

---

## Features

- Multiple clients chat concurrently
- Unique username registration
- Private messaging with `/msg`
- Broadcast messaging with `/broadcast`
- List online users with `/list`
- Graceful disconnection with `/quit`

---

## Files

- `ChatServer.java` — Server handling connections and message routing
- `ChatClient.java` — Client for sending and receiving messages

---

## How to Run

1. **Compile the code:**

```bash
javac ChatServer.java ChatClient.java
```

2. **Start the server:**

```bash
java ChatServer
```

3. **Start each client (in a new terminal):**

```bash
java ChatClient
```

---

## Commands

- Just type a message — broadcast to all users  
- `/msg <user> <message>` — send private message  
- `/broadcast <message>` — broadcast message to all  
- `/list` — show online users  
- `/quit` — disconnect from server  

---

## Notes

- Each client must use a unique username  
- Start the server before any clients  
- Max supported clients: 50 (configurable in `ChatServer.java`)  
- To exit: use `/quit` or press `Ctrl+C`