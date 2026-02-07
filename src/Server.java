import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Central UDP Server for real-time messaging and file sharing
 * Handles client registration, heartbeat monitoring, message broadcasting, and file relay
 */
public class Server {
    
    private static final int SERVER_PORT = 9876;
    private static final int HEARTBEAT_TIMEOUT = 15000; // 15 seconds
    private static final int CLEANUP_INTERVAL = 5000; // 5 seconds
    
    private DatagramSocket socket;
    private Map<String, ClientInfo> clients;
    private FileManager fileManager;
    private volatile boolean running;
    
    public Server() {
        this.clients = new ConcurrentHashMap<>();
        this.fileManager = new FileManager();
        this.running = false;
    }
    
    /**
     * Starts the UDP server
     */
    public void start() {
        try {
            socket = new DatagramSocket(SERVER_PORT);
            running = true;
            
            System.out.println("╔════════════════════════════════════════════╗");
            System.out.println("║    UDP Server Started Successfully         ║");
            System.out.println("║    Port: " + SERVER_PORT + "                             ║");
            System.out.println("╚════════════════════════════════════════════╝");
            System.out.println();
            
            // Start heartbeat monitor thread
            startHeartbeatMonitor();
            
            // Start packet receiver thread
            startPacketReceiver();
            
        } catch (SocketException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Monitors client heartbeats and removes inactive clients
     */
    private void startHeartbeatMonitor() {
        Thread monitor = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL);
                    long currentTime = System.currentTimeMillis();
                    
                    // Check for inactive clients
                    List<String> inactiveClients = new ArrayList<>();
                    for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
                        if (currentTime - entry.getValue().lastHeartbeat > HEARTBEAT_TIMEOUT) {
                            inactiveClients.add(entry.getKey());
                        }
                    }
                    
                    // Remove inactive clients
                    for (String clientId : inactiveClients) {
                        ClientInfo info = clients.remove(clientId);
                        if (info != null) {
                            System.out.println("[DISCONNECT] Client " + clientId + " timed out");
                            broadcastClientList();
                        }
                    }
                    
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }
    
    /**
     * Main packet receiver thread
     */
    private void startPacketReceiver() {
        Thread receiver = new Thread(() -> {
            byte[] buffer = new byte[PacketProtocol.MAX_PACKET_SIZE];
            
            while (running) {
                try {
                    DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(datagramPacket);
                    
                    // Process packet in separate thread to avoid blocking
                    byte[] data = Arrays.copyOf(datagramPacket.getData(), datagramPacket.getLength());
                    InetAddress clientAddress = datagramPacket.getAddress();
                    int clientPort = datagramPacket.getPort();
                    
                    processPacket(data, clientAddress, clientPort);
                    
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
     * Processes received packets based on type
     */
    private void processPacket(byte[] data, InetAddress address, int port) {
        try {
            PacketProtocol.Packet packet = PacketProtocol.deserialize(data);
            
            switch (packet.type) {
                case PacketProtocol.REGISTER:
                    handleRegistration(packet, address, port);
                    break;
                    
                case PacketProtocol.HEARTBEAT:
                    handleHeartbeat(packet, address, port);
                    break;
                    
                case PacketProtocol.MSG:
                    handleMessage(packet);
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
                    
                case PacketProtocol.FILE_ACK:
                    handleFileAck(packet);
                    break;
                    
                default:
                    System.out.println("[UNKNOWN] Received unknown packet type: " + packet.type);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing packet: " + e.getMessage());
        }
    }
    
    /**
     * Handles client registration
     */
    private void handleRegistration(PacketProtocol.Packet packet, InetAddress address, int port) {
        String clientId = packet.clientId;
        ClientInfo info = new ClientInfo(clientId, address, port);
        clients.put(clientId, info);
        
        System.out.println("[REGISTER] Client " + clientId + " connected from " + address + ":" + port);
        
        // Send ACK back to client
        sendAck(clientId, address, port);
        
        // Broadcast updated client list to all clients
        broadcastClientList();
    }
    
    /**
     * Handles heartbeat from client
     */
    private void handleHeartbeat(PacketProtocol.Packet packet, InetAddress address, int port) {
        String clientId = packet.clientId;
        ClientInfo info = clients.get(clientId);
        
        if (info != null) {
            info.updateHeartbeat();
        } else {
            // Client not registered, treat as registration
            handleRegistration(packet, address, port);
        }
    }
    
    /**
     * Handles text message and broadcasts to all clients
     */
    private void handleMessage(PacketProtocol.Packet packet) {
        String message = new String(packet.data);
        System.out.println("[MESSAGE] From " + packet.clientId + ": " + message);
        
        // Broadcast to all clients except sender
        broadcastPacket(packet, packet.clientId);
    }
    
    /**
     * Handles file transfer start
     */
    private void handleFileStart(PacketProtocol.Packet packet) {
        PacketProtocol.FileMetadata metadata = PacketProtocol.extractFileMetadata(packet);
        System.out.println("[FILE_START] Client " + packet.clientId + " sending file: " + 
                         metadata.filename + " (" + metadata.fileSize + " bytes)");
        
        // Relay to all clients except sender
        broadcastPacket(packet, packet.clientId);
    }
    
    /**
     * Handles file chunk
     */
    private void handleFileChunk(PacketProtocol.Packet packet) {
        // Relay chunk to all clients except sender
        broadcastPacket(packet, packet.clientId);
        
        // Send ACK back to sender
        ClientInfo sender = clients.get(packet.clientId);
        if (sender != null) {
            sendFileAck(packet.clientId, packet.sequenceNumber, packet.fileId, 
                       sender.address, sender.port);
        }
    }
    
    /**
     * Handles file transfer end
     */
    private void handleFileEnd(PacketProtocol.Packet packet) {
        System.out.println("[FILE_END] Client " + packet.clientId + " completed file transfer (ID: " + packet.fileId + ")");
        
        // Relay to all clients except sender
        broadcastPacket(packet, packet.clientId);
    }
    
    /**
     * Handles file ACK from clients
     */
    private void handleFileAck(PacketProtocol.Packet packet) {
        // This is acknowledgment from clients that they received chunks
        // Server doesn't need to process this in current design
    }
    
    /**
     * Broadcasts a packet to all clients except the sender, or sends to specific recipient
     */
    private void broadcastPacket(PacketProtocol.Packet packet, String excludeClientId) {
        try {
            byte[] data = PacketProtocol.serialize(packet);
            
            // Check if this is a targeted message (one-to-one)
            if (packet.recipient != null && !packet.recipient.equals("ALL")) {
                // Send only to specific recipient
                ClientInfo recipient = clients.get(packet.recipient);
                if (recipient != null) {
                    DatagramPacket datagramPacket = new DatagramPacket(
                        data, data.length, recipient.address, recipient.port
                    );
                    socket.send(datagramPacket);
                    System.out.println("[PRIVATE] " + packet.clientId + " → " + packet.recipient);
                } else {
                    System.out.println("[ERROR] Recipient not found: " + packet.recipient);
                }
            } else {
                // Broadcast to all clients except sender
                for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
                    if (!entry.getKey().equals(excludeClientId)) {
                        ClientInfo client = entry.getValue();
                        DatagramPacket datagramPacket = new DatagramPacket(
                            data, data.length, client.address, client.port
                        );
                        socket.send(datagramPacket);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error broadcasting packet: " + e.getMessage());
        }
    }
    
    /**
     * Broadcasts current client list to all connected clients
     */
    private void broadcastClientList() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("ONLINE_USERS:");
            for (String clientId : clients.keySet()) {
                sb.append(clientId).append(",");
            }
            
            PacketProtocol.Packet packet = new PacketProtocol.Packet(
                PacketProtocol.CLIENT_LIST, "SERVER", sb.toString().getBytes()
            );
            
            byte[] data = PacketProtocol.serialize(packet);
            
            for (ClientInfo client : clients.values()) {
                DatagramPacket datagramPacket = new DatagramPacket(
                    data, data.length, client.address, client.port
                );
                socket.send(datagramPacket);
            }
        } catch (IOException e) {
            System.err.println("Error broadcasting client list: " + e.getMessage());
        }
    }
    
    /**
     * Sends ACK to a client
     */
    private void sendAck(String clientId, InetAddress address, int port) {
        try {
            PacketProtocol.Packet ackPacket = PacketProtocol.createAckPacket("SERVER", 0, 0);
            byte[] data = PacketProtocol.serialize(ackPacket);
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        } catch (IOException e) {
            System.err.println("Error sending ACK: " + e.getMessage());
        }
    }
    
    /**
     * Sends file ACK to a client
     */
    private void sendFileAck(String clientId, int sequenceNumber, int fileId, 
                            InetAddress address, int port) {
        try {
            PacketProtocol.Packet ackPacket = PacketProtocol.createFileAckPacket(
                "SERVER", sequenceNumber, fileId
            );
            byte[] data = PacketProtocol.serialize(ackPacket);
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        } catch (IOException e) {
            System.err.println("Error sending file ACK: " + e.getMessage());
        }
    }
    
    /**
     * Stops the server
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("Server stopped");
    }
    
    /**
     * Client information container
     */
    private static class ClientInfo {
        String clientId;
        InetAddress address;
        int port;
        long lastHeartbeat;
        
        public ClientInfo(String clientId, InetAddress address, int port) {
            this.clientId = clientId;
            this.address = address;
            this.port = port;
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public void updateHeartbeat() {
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }
    
    /**
     * Main method to start the server
     */
    public static void main(String[] args) {
        Server server = new Server();
        server.start();
        
        // Keep server running
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type 'quit' to stop the server");
        while (true) {
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("quit")) {
                server.stop();
                break;
            }
        }
        scanner.close();
    }
}
