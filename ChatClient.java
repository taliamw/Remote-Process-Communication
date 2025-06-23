import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Scanner scanner;
    private String username;
    private boolean connected;
    private Thread messageReceiver;
    
    public ChatClient(String host, int port) {
        this.scanner = new Scanner(System.in);
        this.connected = false;
        
        try {
            connect(host, port);
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
        }
    }
    
    private void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        connected = true;
        
        System.out.println("Connected to chat server at " + host + ":" + port);
        
        // Start message receiver thread
        startMessageReceiver();
        
        // Handle username registration
        handleUsernameRegistration();
        
        // Start sending messages
        handleUserInput();
    }
    
    private void startMessageReceiver() {
        messageReceiver = new Thread(() -> {
            try {
                String message;
                while (connected && (message = reader.readLine()) != null) {
                    // Clear current input line and print message
                    System.out.print("\r" + " ".repeat(50) + "\r");
                    System.out.println(message);
                    
                    // Re-display input prompt if we have a username
                    if (username != null) {
                        System.out.print("[" + username + "] ");
                        System.out.flush();
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    System.out.println("\nConnection lost with server");
                }
            }
        });
        messageReceiver.setDaemon(true);
        messageReceiver.start();
    }
    
    private void handleUsernameRegistration() {
        try {
            String prompt;
            while (username == null && (prompt = reader.readLine()) != null) {
                if (prompt.contains("Enter username:")) {
                    System.out.print("Enter your username: ");
                    String inputUsername = scanner.nextLine().trim();
                    writer.println(inputUsername);
                    username = inputUsername;
                } else if (prompt.contains("Username already taken")) {
                    System.out.println(prompt);
                    username = null; // Reset to try again
                } else if (prompt.contains("Welcome")) {
                    System.out.println(prompt);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error during username registration: " + e.getMessage());
        }
    }
    
    private void handleUserInput() {
        System.out.println("\n=== You can start chatting now ===");
        System.out.println("Available commands:");
        System.out.println("  /list - Show online users");
        System.out.println("  /msg <username> <message> - Send private message");
        System.out.println("  /broadcast <message> - Send message to all users");
        System.out.println("  /quit - Disconnect from server");
        System.out.println("  Or just type a message to broadcast to everyone");
        System.out.println("=" + "=".repeat(40));
        
        try {
            String input;
            while (connected) {
                if (username != null) {
                    System.out.print("[" + username + "] ");
                }
                
                input = scanner.nextLine().trim();
                
                if (!input.isEmpty()) {
                    if (input.equalsIgnoreCase("/quit")) {
                        disconnect();
                        break;
                    } else {
                        writer.println(input);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling user input: " + e.getMessage());
        }
    }
    
    public void disconnect() {
        connected = false;
        try {
            if (writer != null) {
                writer.println("/quit");
            }
            if (messageReceiver != null) {
                messageReceiver.interrupt();
            }
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            
            System.out.println("Disconnected from server");
        } catch (IOException e) {
            System.err.println("Error during disconnection: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        // Parse command line arguments
        if (args.length >= 2) {
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port " + DEFAULT_PORT);
            }
        } else if (args.length == 1) {
            host = args[0];
        }
        
        System.out.println("=== Chat Client ===");
        
        ChatClient client = new ChatClient(host, port);
        
        // Handle shutdown gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nExiting...");
            client.disconnect();
        }));
    }
}