import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatServer {
    private static final int PORT = 8888;
    private static final int MAX_CLIENTS = 50;
    
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, ClientHandler> clients;
    private boolean running;
    
    public ChatServer() {
        this.threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
        this.clients = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            
            System.out.println("Chat server started on port " + PORT);
            System.out.println("Waiting for connections...");
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from " + clientSocket.getRemoteSocketAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    threadPool.execute(clientHandler);
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            stop();
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdown();
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
        System.out.println("Server stopped");
    }
    
    public synchronized boolean addClient(String username, ClientHandler clientHandler) {
        if (clients.containsKey(username)) {
            return false;
        }
        clients.put(username, clientHandler);
        broadcastMessage(username + " joined the chat!", clientHandler);
        System.out.println("User " + username + " registered");
        return true;
    }
    
    public synchronized void removeClient(String username) {
        ClientHandler removed = clients.remove(username);
        if (removed != null) {
            broadcastMessage(username + " left the chat!", removed);
            System.out.println("User " + username + " disconnected");
        }
    }
    
    public synchronized void broadcastMessage(String message, ClientHandler excludeClient) {
        for (ClientHandler client : clients.values()) {
            if (client != excludeClient) {
                client.sendMessage(message);
            }
        }
    }
    
    public synchronized void sendPrivateMessage(String senderUsername, String targetUsername, String message) {
        ClientHandler sender = clients.get(senderUsername);
        ClientHandler target = clients.get(targetUsername);
        
        if (target != null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String privateMsg = String.format("[%s] %s (private): %s", timestamp, senderUsername, message);
            target.sendMessage(privateMsg);
            sender.sendMessage("Private message sent to " + targetUsername);
            System.out.println("Private message: " + senderUsername + " -> " + targetUsername);
        } else {
            sender.sendMessage("User " + targetUsername + " not found or offline");
        }
    }
    
    public synchronized String getOnlineUsers() {
        return "Online users (" + clients.size() + "): " + String.join(", ", clients.keySet());
    }
    
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        
        // Handle shutdown gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            server.stop();
        }));
        
        server.start();
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    
    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }
    
    @Override
    public void run() {
        try {
            setupStreams();
            handleUsernameRegistration();
            handleMessages();
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    private void setupStreams() throws IOException {
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
    }
    
    private void handleUsernameRegistration() throws IOException {
        while (username == null) {
            writer.println("Enter username: ");
            String inputUsername = reader.readLine();
            
            if (inputUsername == null || inputUsername.trim().isEmpty()) {
                writer.println("Invalid username. Please try again.");
                continue;
            }
            
            inputUsername = inputUsername.trim();
            
            if (server.addClient(inputUsername, this)) {
                username = inputUsername;
                sendWelcomeMessage();
            } else {
                writer.println("Username already taken. Please try again.");
            }
        }
    }
    
    private void sendWelcomeMessage() {
        StringBuilder welcome = new StringBuilder();
        welcome.append("Welcome ").append(username).append("! You are now connected to the chat server.\n");
        welcome.append("Commands:\n");
        welcome.append("  /list - Show online users\n");
        welcome.append("  /msg <username> <message> - Send private message\n");
        welcome.append("  /broadcast <message> - Send message to all users\n");
        welcome.append("  /quit - Disconnect from server\n");
        welcome.append("You can also just type a message to broadcast to everyone.");
        
        writer.println(welcome.toString());
    }
    
    private void handleMessages() throws IOException {
        String message;
        while ((message = reader.readLine()) != null) {
            if (message.trim().isEmpty()) {
                continue;
            }
            
            processMessage(message.trim());
        }
    }
    
    private void processMessage(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        if (message.startsWith("/")) {
            handleCommand(message, timestamp);
        } else {
            // Regular message - broadcast to all
            String formattedMessage = String.format("[%s] %s: %s", timestamp, username, message);
            server.broadcastMessage(formattedMessage, this);
        }
    }
    
    private void handleCommand(String message, String timestamp) {
        String[] parts = message.split("\\s+", 3);
        String command = parts[0].toLowerCase();
        
        switch (command) {
            case "/quit":
                writer.println("Goodbye!");
                cleanup();
                break;
                
            case "/list":
                writer.println(server.getOnlineUsers());
                break;
                
            case "/msg":
                if (parts.length >= 3) {
                    String targetUsername = parts[1];
                    String privateMessage = parts[2];
                    server.sendPrivateMessage(username, targetUsername, privateMessage);
                } else {
                    writer.println("Usage: /msg <username> <message>");
                }
                break;
                
            case "/broadcast":
                if (parts.length >= 2) {
                    String broadcastMessage = message.substring(message.indexOf(' ') + 1);
                    String formattedMessage = String.format("[%s] %s (broadcast): %s", 
                                                           timestamp, username, broadcastMessage);
                    server.broadcastMessage(formattedMessage, this);
                    writer.println("Message broadcasted to all users.");
                } else {
                    writer.println("Usage: /broadcast <message>");
                }
                break;
                
            default:
                writer.println("Invalid command. Available commands: /list, /msg, /broadcast, /quit");
        }
    }
    
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }
    
    private void cleanup() {
        try {
            if (username != null) {
                server.removeClient(username);
            }
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}