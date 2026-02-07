import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * UDP Client for real-time messaging and file sharing
 * Features: registration, heartbeat, send/receive messages, file transfer
 */
public class Client {
    
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9876;
    private static final int CLIENT_PORT = 0; // Use random available port
    private static final int HEARTBEAT_INTERVAL = 5000; // 5 seconds
    private static final int CHUNK_SEND_DELAY = 10; // 10ms delay between chunks
    private static final int MAX_RETRIES = 5;
    private static final int ACK_TIMEOUT = 2000; // 2 seconds
    
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private String clientId;
    private FileManager fileManager;
    private volatile boolean running;
    private Set<String> onlineUsers;
    
    // File transfer tracking
    private Map<Integer, FileTransferTask> activeFileSends;
    
    public Client(String clientId) {
        this.clientId = clientId;
        this.fileManager = new FileManager();
        this.running = false;
        this.onlineUsers = ConcurrentHashMap.newKeySet();
        this.activeFileSends = new ConcurrentHashMap<>();
    }
    
    /**
     * Starts the client and connects to server
     */
    public void start() {
        try {
            socket = new DatagramSocket(CLIENT_PORT);
            serverAddress = InetAddress.getByName(SERVER_HOST);
            running = true;
            
            System.out.println("╔════════════════════════════════════════════╗");
            System.out.println("║    UDP Client Started                      ║");
            System.out.println("║    Client ID: " + String.format("%-28s", clientId) + "║");
            System.out.println("║    Local Port: " + String.format("%-27d", socket.getLocalPort()) + "║");
            System.out.println("╚════════════════════════════════════════════╝");
            System.out.println();
            
            // Register with server
            register();
            
            // Start heartbeat thread
            startHeartbeat();
            
            // Start receiver thread
            startReceiver();
            
            // Start UI thread
            startUI();
            
        } catch (Exception e) {
            System.err.println("Failed to start client: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Registers client with server
     */
    private void register() {
        try {
            PacketProtocol.Packet registerPacket = PacketProtocol.createRegisterPacket(clientId);
            sendPacket(registerPacket);
            System.out.println("[INFO] Registration request sent to server");
        } catch (IOException e) {
            System.err.println("Failed to register: " + e.getMessage());
        }
    }
    
    /**
     * Starts heartbeat thread
     */
    private void startHeartbeat() {
        Thread heartbeat = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    PacketProtocol.Packet heartbeatPacket = PacketProtocol.createHeartbeatPacket(clientId);
                    sendPacket(heartbeatPacket);
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    System.err.println("Error sending heartbeat: " + e.getMessage());
                }
            }
        });
        heartbeat.setDaemon(true);
        heartbeat.start();
    }
    
    /**
     * Starts packet receiver thread
     */
    private void startReceiver() {
        Thread receiver = new Thread(() -> {
            byte[] buffer = new byte[PacketProtocol.MAX_PACKET_SIZE];
            
            while (running) {
                try {
                    DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(datagramPacket);
                    
                    byte[] data = Arrays.copyOf(datagramPacket.getData(), datagramPacket.getLength());
                    processReceivedPacket(data);
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error receiving packet: " + e.getMessage());
                    }
                }
            }
        });
        receiver.setDaemon(true);
        receiver.start();
    }
    
    /**
     * Processes received packets
     */
    private void processReceivedPacket(byte[] data) {
        try {
            PacketProtocol.Packet packet = PacketProtocol.deserialize(data);
            
            switch (packet.type) {
                case PacketProtocol.ACK:
                    // Registration acknowledged
                    break;
                    
                case PacketProtocol.MSG:
                    handleReceivedMessage(packet);
                    break;
                    
                case PacketProtocol.FILE_START:
                    handleFileStart(packet);
                    break;
                    
                case PacketProtocol.FILE_CHUNK:
                    handleFileChunk(packet);
                    break;
                    
                case PacketProtocol.FILE_END:
                    handleFileEnd(packet);
                    break;
                    
                case PacketProtocol.CLIENT_LIST:
                    handleClientList(packet);
                    break;
                    
                case PacketProtocol.FILE_ACK:
                    handleFileAck(packet);
                    break;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing received packet: " + e.getMessage());
        }
    }
    
    /**
     * Handles received text message
     */
    private void handleReceivedMessage(PacketProtocol.Packet packet) {
        String message = new String(packet.data);
        System.out.println("\n[" + packet.clientId + "]: " + message);
        showPrompt();
    }
    
    /**
     * Handles file transfer start
     */
    private void handleFileStart(PacketProtocol.Packet packet) {
        PacketProtocol.FileMetadata metadata = PacketProtocol.extractFileMetadata(packet);
        fileManager.startFileReception(packet.fileId, metadata.filename, metadata.fileSize);
        System.out.println("\n[FILE] " + packet.clientId + " is sending: " + metadata.filename + 
                         " (" + metadata.fileSize + " bytes)");
        showPrompt();
    }
    
    /**
     * Handles file chunk
     */
    private void handleFileChunk(PacketProtocol.Packet packet) {
        fileManager.receiveChunk(packet.fileId, packet.sequenceNumber, packet.data);
        
        FileManager.FileReceptionState state = fileManager.getReceptionState(packet.fileId);
        if (state != null && packet.sequenceNumber % 10 == 0) {
            System.out.println("\n[FILE] Receiving " + state.getFilename() + ": " + 
                             state.getProgress() + "%");
            showPrompt();
        }
    }
    
    /**
     * Handles file transfer end
     */
    private void handleFileEnd(PacketProtocol.Packet packet) {
        try {
            int totalChunks = java.nio.ByteBuffer.wrap(packet.data).getInt();
            File savedFile = fileManager.completeFileReception(packet.fileId, totalChunks);
            
            if (savedFile != null) {
                System.out.println("\n[FILE] File received successfully: " + savedFile.getAbsolutePath());
            } else {
                System.out.println("\n[FILE] Error receiving file");
            }
            showPrompt();
        } catch (IOException e) {
            System.err.println("\n[FILE] Error saving file: " + e.getMessage());
            showPrompt();
        }
    }
    
    /**
     * Handles client list update
     */
    private void handleClientList(PacketProtocol.Packet packet) {
        String data = new String(packet.data);
        if (data.startsWith("ONLINE_USERS:")) {
            String[] users = data.substring(13).split(",");
            onlineUsers.clear();
            for (String user : users) {
                if (!user.isEmpty() && !user.equals(clientId)) {
                    onlineUsers.add(user);
                }
            }
        }
    }
    
    /**
     * Handles file ACK
     */
    private void handleFileAck(PacketProtocol.Packet packet) {
        FileTransferTask task = activeFileSends.get(packet.fileId);
        if (task != null) {
            task.markChunkAcknowledged(packet.sequenceNumber);
        }
    }
    
    /**
     * Sends a text message
     */
    public void sendMessage(String message) {
        try {
            PacketProtocol.Packet packet = PacketProtocol.createMessagePacket(clientId, message);
            sendPacket(packet);
            System.out.println("[You]: " + message);
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }
    
    /**
     * Sends a file
     */
    public void sendFile(String filepath) {
        File file = new File(filepath);
        if (!file.exists()) {
            System.out.println("[ERROR] File not found: " + filepath);
            return;
        }
        
        try {
            System.out.println("[FILE] Preparing to send: " + file.getName());
            FileManager.FileTransferState state = fileManager.prepareFileTransfer(file);
            
            // Create and start file transfer task
            FileTransferTask task = new FileTransferTask(state);
            activeFileSends.put(state.getFileId(), task);
            
            Thread transferThread = new Thread(task);
            transferThread.start();
            
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to prepare file: " + e.getMessage());
        }
    }
    
    /**
     * Displays online users
     */
    public void showOnlineUsers() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║         Online Users                       ║");
        System.out.println("╠════════════════════════════════════════════╣");
        if (onlineUsers.isEmpty()) {
            System.out.println("║  No other users online                     ║");
        } else {
            for (String user : onlineUsers) {
                System.out.println("║  • " + String.format("%-40s", user) + "║");
            }
        }
        System.out.println("╚════════════════════════════════════════════╝\n");
    }
    
    /**
     * Sends a packet to the server
     */
    private void sendPacket(PacketProtocol.Packet packet) throws IOException {
        byte[] data = PacketProtocol.serialize(packet);
        DatagramPacket datagramPacket = new DatagramPacket(
            data, data.length, serverAddress, SERVER_PORT
        );
        socket.send(datagramPacket);
    }
    
    /**
     * File transfer task that runs in separate thread
     */
    private class FileTransferTask implements Runnable {
        private FileManager.FileTransferState state;
        private Set<Integer> acknowledgedChunks;
        
        public FileTransferTask(FileManager.FileTransferState state) {
            this.state = state;
            this.acknowledgedChunks = ConcurrentHashMap.newKeySet();
        }
        
        public void markChunkAcknowledged(int sequenceNumber) {
            acknowledgedChunks.add(sequenceNumber);
        }
        
        @Override
        public void run() {
            try {
                // Send FILE_START
                PacketProtocol.Packet startPacket = PacketProtocol.createFileStartPacket(
                    clientId, state.getFileId(), state.getFilename(), state.getFileSize()
                );
                sendPacket(startPacket);
                System.out.println("[FILE] Starting transfer: " + state.getFilename());
                
                // Send all chunks
                int totalChunks = state.getTotalChunks();
                int lastProgress = 0;
                
                for (int i = 0; i < totalChunks; i++) {
                    byte[] chunk = state.getChunk(i);
                    if (chunk != null) {
                        // Send chunk with retry logic
                        sendChunkWithRetry(i, chunk);
                        
                        // Display progress
                        int progress = ((i + 1) * 100) / totalChunks;
                        if (progress != lastProgress && progress % 10 == 0) {
                            System.out.println("[FILE] Sending " + state.getFilename() + ": " + progress + "%");
                            lastProgress = progress;
                        }
                        
                        // Small delay between chunks to avoid overwhelming network
                        Thread.sleep(CHUNK_SEND_DELAY);
                    }
                }
                
                // Send FILE_END
                PacketProtocol.Packet endPacket = PacketProtocol.createFileEndPacket(
                    clientId, state.getFileId(), totalChunks
                );
                sendPacket(endPacket);
                
                System.out.println("[FILE] Transfer completed: " + state.getFilename());
                activeFileSends.remove(state.getFileId());
                showPrompt();
                
            } catch (Exception e) {
                System.err.println("[FILE] Transfer error: " + e.getMessage());
                showPrompt();
            }
        }
        
        private void sendChunkWithRetry(int sequenceNumber, byte[] chunk) throws IOException, InterruptedException {
            PacketProtocol.Packet chunkPacket = PacketProtocol.createFileChunkPacket(
                clientId, state.getFileId(), sequenceNumber, chunk
            );
            
            int retries = 0;
            while (retries < MAX_RETRIES) {
                sendPacket(chunkPacket);
                
                // Wait for ACK (simplified - in production would use proper timeout)
                Thread.sleep(50); // Small delay for ACK
                
                if (acknowledgedChunks.contains(sequenceNumber)) {
                    return; // ACK received
                }
                
                retries++;
            }
            
            // Consider it sent even without ACK (UDP is lossy)
            acknowledgedChunks.add(sequenceNumber);
        }
    }
    
    /**
     * Starts console UI
     */
    private void startUI() {
        Thread uiThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            
            showHelp();
            showPrompt();
            
            while (running) {
                try {
                    String input = scanner.nextLine().trim();
                    
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    if (input.equalsIgnoreCase("/quit")) {
                        System.out.println("Disconnecting...");
                        stop();
                        break;
                    } else if (input.equalsIgnoreCase("/help")) {
                        showHelp();
                    } else if (input.equalsIgnoreCase("/users")) {
                        showOnlineUsers();
                    } else if (input.startsWith("/file ")) {
                        String filepath = input.substring(6).trim();
                        sendFile(filepath);
                    } else {
                        sendMessage(input);
                    }
                    
                    showPrompt();
                    
                } catch (Exception e) {
                    if (running) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            }
            
            scanner.close();
        });
        uiThread.start();
    }
    
    /**
     * Shows help menu
     */
    private void showHelp() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║              Commands                      ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║  Type message and press Enter to send      ║");
        System.out.println("║  /file <path>  - Send a file               ║");
        System.out.println("║  /users        - Show online users         ║");
        System.out.println("║  /help         - Show this help            ║");
        System.out.println("║  /quit         - Exit the application      ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
    }
    
    /**
     * Shows input prompt
     */
    private void showPrompt() {
        System.out.print(clientId + " > ");
    }
    
    /**
     * Stops the client
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
    
    /**
     * Main method to start a client
     */
    public static void main(String[] args) {
        String clientId = "Client";
        
        if (args.length > 0) {
            clientId = args[0];
        } else {
            // Generate random client ID
            clientId = "Client_" + (int)(Math.random() * 1000);
        }
        
        Client client = new Client(clientId);
        client.start();
    }
}
