import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * GUI-based UDP Client for real-time messaging and file sharing
 * Features graphical interface with message display, file transfer, and user list
 */
public class ClientGUI extends JFrame {
    
    // UI Color Scheme
    private static final Color PRIMARY_COLOR = new Color(37, 99, 235); // Modern blue
    private static final Color SECONDARY_COLOR = new Color(59, 130, 246);
    private static final Color SENT_MSG_COLOR = new Color(59, 130, 246); // Light blue for sent
    private static final Color RECEIVED_MSG_COLOR = new Color(191, 219, 254); // Light blue for received
    private static final Color BACKGROUND_COLOR = new Color(239, 246, 255); // Very light blue
    private static final Color CHAT_BACKGROUND = new Color(255, 255, 255);
    private static final Color TEXT_PRIMARY = new Color(17, 24, 39);
    private static final Color TEXT_SECONDARY = new Color(107, 114, 128);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9876;
    private static final int HEARTBEAT_INTERVAL = 5000;
    private static final int CHUNK_SEND_DELAY = 10;
    private static final int MAX_RETRIES = 5;
    
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private String clientId;
    private FileManager fileManager;
    private volatile boolean running;
    private Set<String> onlineUsers;
    private Map<Integer, FileTransferTask> activeFileSends;
    private Map<Integer, File> receivedFiles; // Track received files by fileId
    
    // GUI Components
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JTextField messageField;
    private JComboBox<String> recipientComboBox;
    private JButton sendButton;
    private JButton sendFileButton;
    private JButton sendVoiceButton;
    private JButton viewUsersButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    
    // Audio recording
    private TargetDataLine targetLine;
    private AudioInputStream audioStream;
    private boolean isRecording = false;
    private File audioFile;
    
    public ClientGUI(String clientId) {
        this.clientId = clientId;
        this.fileManager = new FileManager();
        this.running = false;
        this.onlineUsers = ConcurrentHashMap.newKeySet();
        this.activeFileSends = new ConcurrentHashMap<>();
        this.receivedFiles = new ConcurrentHashMap<>();
        
        initializeGUI();
        start();
    }
    
    /**
     * Initialize the graphical user interface
     */
    private void initializeGUI() {
        setTitle("UDP Chat Client - " + clientId);
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 245));
        
        // Top panel - Status
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(PRIMARY_COLOR);
        topPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JLabel titleLabel = new JLabel("💬 UDP Messaging - " + clientId, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        
        statusLabel = new JLabel("Connecting...", SwingConstants.LEFT);
        statusLabel.setForeground(new Color(224, 242, 254));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        topPanel.add(statusLabel, BorderLayout.SOUTH);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Chat area and user list
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBackground(new Color(240, 240, 245));
        
        // Chat area - using JPanel with BoxLayout to support images and rich content
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(BACKGROUND_COLOR);
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        chatScrollPane = new JScrollPane(chatPanel);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)), 
            "Chat Messages",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 12)
        ));
        
        centerPanel.add(chatScrollPane, BorderLayout.CENTER);
        
        // User list panel
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setPreferredSize(new Dimension(200, 0));
        userPanel.setBackground(new Color(240, 240, 245));
        
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Arial", Font.PLAIN, 12));
        userList.setBackground(Color.WHITE);
        userList.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)), 
            "Online Users",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 12)
        ));
        
        userPanel.add(userScrollPane, BorderLayout.CENTER);
        centerPanel.add(userPanel, BorderLayout.EAST);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - Input and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(new Color(240, 240, 245));
        
        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setPreferredSize(new Dimension(0, 20));
        progressBar.setVisible(false);
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        
        // Recipient selector panel
        JPanel recipientPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        recipientPanel.setBackground(new Color(240, 240, 245));
        
        JLabel recipientLabel = new JLabel("Send to:");
        recipientLabel.setFont(new Font("Arial", Font.BOLD, 12));
        recipientPanel.add(recipientLabel);
        
        recipientComboBox = new JComboBox<>();
        recipientComboBox.setFont(new Font("Arial", Font.PLAIN, 12));
        recipientComboBox.addItem("ALL (Broadcast)");
        recipientComboBox.setPreferredSize(new Dimension(200, 25));
        recipientPanel.add(recipientComboBox);
        
        bottomPanel.add(recipientPanel, BorderLayout.CENTER);
        
        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBackground(new Color(240, 240, 245));
        
        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 13));
        messageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(150, 150, 150)),
            new EmptyBorder(5, 5, 5, 5)
        ));
        messageField.addActionListener(e -> sendMessage());
        
        inputPanel.add(messageField, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        buttonPanel.setBackground(new Color(240, 240, 245));
        
        sendButton = new JButton("Send Message");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setBackground(new Color(59, 130, 246)); // Blue
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setOpaque(true);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendMessage());
        
        sendFileButton = new JButton("Send File");
        sendFileButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendFileButton.setBackground(new Color(59, 130, 246)); // Blue
        sendFileButton.setForeground(Color.WHITE);
        sendFileButton.setFocusPainted(false);
        sendFileButton.setBorderPainted(false);
        sendFileButton.setOpaque(true);
        sendFileButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendFileButton.addActionListener(e -> selectAndSendFile());
        
        sendVoiceButton = new JButton("🎤 Voice");
        sendVoiceButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendVoiceButton.setBackground(new Color(59, 130, 246)); // Blue
        sendVoiceButton.setForeground(Color.WHITE);
        sendVoiceButton.setFocusPainted(false);
        sendVoiceButton.setBorderPainted(false);
        sendVoiceButton.setOpaque(true);
        sendVoiceButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendVoiceButton.addActionListener(e -> toggleVoiceRecording());
        
        viewUsersButton = new JButton("Refresh Users");
        viewUsersButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        viewUsersButton.setBackground(new Color(59, 130, 246)); // Blue
        viewUsersButton.setForeground(Color.WHITE);
        viewUsersButton.setFocusPainted(false);
        viewUsersButton.setBorderPainted(false);
        viewUsersButton.setOpaque(true);
        viewUsersButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        viewUsersButton.addActionListener(e -> refreshUserDisplay());
        
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(sendVoiceButton);
        buttonPanel.add(viewUsersButton);
        
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Window close handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
                System.exit(0);
            }
        });
    }
    
    /**
     * Starts the client and connects to server
     */
    public void start() {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_HOST);
            running = true;
            
            appendToChat("═══════════════════════════════════════════════\n");
            appendToChat("UDP Client Started\n");
            appendToChat("Client ID: " + clientId + "\n");
            appendToChat("Local Port: " + socket.getLocalPort() + "\n");
            appendToChat("═══════════════════════════════════════════════\n\n");
            
            updateStatus("Connected to server", true);
            
            register();
            startHeartbeat();
            startReceiver();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to start client: " + e.getMessage(), 
                "Connection Error", 
                JOptionPane.ERROR_MESSAGE);
            updateStatus("Connection failed", false);
        }
    }
    
    /**
     * Registers client with server
     */
    private void register() {
        try {
            PacketProtocol.Packet registerPacket = PacketProtocol.createRegisterPacket(clientId);
            sendPacket(registerPacket);
            appendToChat("[INFO] Registration request sent to server\n");
        } catch (IOException e) {
            appendToChat("[ERROR] Failed to register: " + e.getMessage() + "\n");
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
                    appendToChat("[ERROR] Heartbeat failed: " + e.getMessage() + "\n");
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
                        appendToChat("[ERROR] Receive error: " + e.getMessage() + "\n");
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
            appendToChat("[ERROR] Processing error: " + e.getMessage() + "\n");
        }
    }
    
    private void handleReceivedMessage(PacketProtocol.Packet packet) {
        String message = new String(packet.data);
        appendReceivedMessage("[" + packet.clientId + "]", message);
    }
    
    private void handleFileStart(PacketProtocol.Packet packet) {
        PacketProtocol.FileMetadata metadata = PacketProtocol.extractFileMetadata(packet);
        fileManager.startFileReception(packet.fileId, metadata.filename, metadata.fileSize);
        appendToChat("[FILE] " + packet.clientId + " is sending: " + metadata.filename + 
                   " (" + metadata.fileSize + " bytes)\n");
        showProgress(true);
    }
    
    private void handleFileChunk(PacketProtocol.Packet packet) {
        fileManager.receiveChunk(packet.fileId, packet.sequenceNumber, packet.data);
        
        FileManager.FileReceptionState state = fileManager.getReceptionState(packet.fileId);
        if (state != null) {
            updateProgress(state.getProgress(), "Receiving " + state.getFilename());
        }
    }
    
    private void handleFileEnd(PacketProtocol.Packet packet) {
        try {
            int totalChunks = java.nio.ByteBuffer.wrap(packet.data).getInt();
            File savedFile = fileManager.completeFileReception(packet.fileId, totalChunks);
            
            if (savedFile != null) {
                // Store the file
                receivedFiles.put(packet.fileId, savedFile);
                
                // Display based on file type
                if (isImageFile(savedFile)) {
                    // Display image inline
                    appendImageToChat("[" + packet.clientId + "]", savedFile);
                } else if (isAudioFile(savedFile)) {
                    // Display audio file with play button
                    appendAudioToChat("[" + packet.clientId + "]", savedFile);
                } else {
                    // Display file attachment with open/save buttons
                    appendFileToChat("[" + packet.clientId + "]", savedFile);
                }
            }
            showProgress(false);
        } catch (IOException e) {
            appendToChat("[ERROR] File save error: " + e.getMessage() + "\n");
            showProgress(false);
        }
    }
    
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
            refreshUserDisplay();
        }
    }
    
    private void handleFileAck(PacketProtocol.Packet packet) {
        FileTransferTask task = activeFileSends.get(packet.fileId);
        if (task != null) {
            task.markChunkAcknowledged(packet.sequenceNumber);
        }
    }
    
    /**
     * Sends a text message
     */
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        
        try {
            // Get selected recipient
            String selectedRecipient = getSelectedRecipient();
            
            PacketProtocol.Packet packet = PacketProtocol.createMessagePacket(clientId, message);
            packet.setRecipient(selectedRecipient);
            
            sendPacket(packet);
            
            // Show sent message with proper styling
            String recipientInfo = selectedRecipient.equals("ALL") ? "Everyone" : selectedRecipient;
            appendSentMessage(message, recipientInfo);
            
            messageField.setText("");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to send message: " + e.getMessage(), 
                "Send Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Opens file chooser and sends selected file
     */
    private void selectAndSendFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select File to Send");
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            sendFile(file);
        }
    }
    
    /**
     * Gets the selected recipient from combo box
     */
    private String getSelectedRecipient() {
        String selected = (String) recipientComboBox.getSelectedItem();
        if (selected == null || selected.equals("ALL (Broadcast)")) {
            return "ALL";
        }
        // Remove the bullet point if present
        return selected.replace("● ", "").trim();
    }
    
    /**
     * Sends a file
     */
    private void sendFile(File file) {
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, 
                "File not found: " + file.getName(), 
                "File Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            String selectedRecipient = getSelectedRecipient();
            String recipientInfo = selectedRecipient.equals("ALL") ? "Everyone" : selectedRecipient;
            
            // Show sent file/image/audio inline for sender
            if (isImageFile(file)) {
                appendSentImage(file, recipientInfo);
            } else if (isAudioFile(file)) {
                appendSentAudio(file, recipientInfo);
            } else {
                appendSentFile(file, recipientInfo);
            }
            
            FileManager.FileTransferState state = fileManager.prepareFileTransfer(file);
            
            FileTransferTask task = new FileTransferTask(state, selectedRecipient);
            activeFileSends.put(state.getFileId(), task);
            
            Thread transferThread = new Thread(task);
            transferThread.start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to prepare file: " + e.getMessage(), 
                "File Error", 
                JOptionPane.ERROR_MESSAGE);
        }
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
     * File transfer task
     */
    private class FileTransferTask implements Runnable {
        private FileManager.FileTransferState state;
        private Set<Integer> acknowledgedChunks;
        private String recipient;
        
        public FileTransferTask(FileManager.FileTransferState state, String recipient) {
            this.state = state;
            this.recipient = recipient;
            this.acknowledgedChunks = ConcurrentHashMap.newKeySet();
        }
        
        public void markChunkAcknowledged(int sequenceNumber) {
            acknowledgedChunks.add(sequenceNumber);
        }
        
        @Override
        public void run() {
            try {
                showProgress(true);
                
                PacketProtocol.Packet startPacket = PacketProtocol.createFileStartPacket(
                    clientId, state.getFileId(), state.getFilename(), state.getFileSize()
                );
                startPacket.setRecipient(recipient);
                sendPacket(startPacket);
                
                if (recipient.equals("ALL")) {
                    appendToChat("[FILE] Starting transfer to ALL: " + state.getFilename() + "\n");
                } else {
                    appendToChat("[FILE] Starting transfer to " + recipient + ": " + state.getFilename() + "\n");
                }
                
                int totalChunks = state.getTotalChunks();
                
                for (int i = 0; i < totalChunks; i++) {
                    byte[] chunk = state.getChunk(i);
                    if (chunk != null) {
                        sendChunkWithRetry(i, chunk);
                        
                        int progress = ((i + 1) * 100) / totalChunks;
                        updateProgress(progress, "Sending " + state.getFilename());
                        
                        Thread.sleep(CHUNK_SEND_DELAY);
                    }
                }
                
                PacketProtocol.Packet endPacket = PacketProtocol.createFileEndPacket(
                    clientId, state.getFileId(), totalChunks
                );
                endPacket.setRecipient(recipient);
                sendPacket(endPacket);
                
                appendToChat("[FILE] Transfer completed: " + state.getFilename() + "\n");
                activeFileSends.remove(state.getFileId());
                showProgress(false);
                
            } catch (Exception e) {
                appendToChat("[ERROR] Transfer error: " + e.getMessage() + "\n");
                showProgress(false);
            }
        }
        
        private void sendChunkWithRetry(int sequenceNumber, byte[] chunk) throws IOException, InterruptedException {
            PacketProtocol.Packet chunkPacket = PacketProtocol.createFileChunkPacket(
                clientId, state.getFileId(), sequenceNumber, chunk
            );
            chunkPacket.setRecipient(recipient);
            
            int retries = 0;
            while (retries < MAX_RETRIES) {
                sendPacket(chunkPacket);
                Thread.sleep(50);
                
                if (acknowledgedChunks.contains(sequenceNumber)) {
                    return;
                }
                retries++;
            }
            acknowledgedChunks.add(sequenceNumber);
        }
    }
    
    /**
     * Appends text to chat area (for system messages)
     */
    private void appendToChat(String text) {
        SwingUtilities.invokeLater(() -> {
            JLabel messageLabel = new JLabel("<html>" + text.replace("\n", "<br>") + "</html>");
            messageLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            messageLabel.setForeground(TEXT_SECONDARY);
            messageLabel.setBorder(new EmptyBorder(3, 10, 3, 10));
            messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            chatPanel.add(messageLabel);
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Appends sent message to chat
     */
    private void appendSentMessage(String message, String recipientInfo) {
        SwingUtilities.invokeLater(() -> {
            JPanel messageContainer = new JPanel();
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
            messageContainer.setBackground(BACKGROUND_COLOR);
            messageContainer.setAlignmentX(Component.RIGHT_ALIGNMENT);
            messageContainer.setBorder(new EmptyBorder(3, 50, 3, 10));
            
            // Add glue to push message to right
            messageContainer.add(Box.createHorizontalGlue());
            
            // Message bubble
            JPanel bubble = new JPanel();
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setBackground(SENT_MSG_COLOR);
            bubble.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(2, 2, 2, 2),
                new EmptyBorder(8, 12, 8, 12)
            ));
            
            // Message text
            JLabel msgLabel = new JLabel("<html>" + message.replace("\n", "<br>") + "</html>");
            msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            msgLabel.setForeground(Color.WHITE);
            msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(msgLabel);
            
            // Time and recipient
            String timeStr = TIME_FORMAT.format(new Date());
            JLabel timeLabel = new JLabel(timeStr + " • to " + recipientInfo);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(new Color(224, 242, 254));
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(Box.createVerticalStrut(3));
            bubble.add(timeLabel);
            
            messageContainer.add(bubble);
            chatPanel.add(messageContainer);
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Appends received message to chat
     */
    private void appendReceivedMessage(String senderName, String message) {
        SwingUtilities.invokeLater(() -> {
            JPanel messageContainer = new JPanel();
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
            messageContainer.setBackground(BACKGROUND_COLOR);
            messageContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageContainer.setBorder(new EmptyBorder(3, 10, 3, 50));
            
            // Message bubble
            JPanel bubble = new JPanel();
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setBackground(RECEIVED_MSG_COLOR);
            bubble.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(2, 2, 2, 2),
                new EmptyBorder(8, 12, 8, 12)
            ));
            
            // Sender name
            JLabel nameLabel = new JLabel(senderName);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            nameLabel.setForeground(PRIMARY_COLOR);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(nameLabel);
            bubble.add(Box.createVerticalStrut(2));
            
            // Message text
            JLabel msgLabel = new JLabel("<html>" + message.replace("\n", "<br>") + "</html>");
            msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            msgLabel.setForeground(TEXT_PRIMARY);
            msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(msgLabel);
            
            // Time
            String timeStr = TIME_FORMAT.format(new Date());
            JLabel timeLabel = new JLabel(timeStr);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(TEXT_SECONDARY);
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(Box.createVerticalStrut(3));
            bubble.add(timeLabel);
            
            messageContainer.add(bubble);
            messageContainer.add(Box.createHorizontalGlue());
            
            chatPanel.add(messageContainer);
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Scrolls chat to bottom
     */
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    
    /**
     * Appends a received image to chat area
     */
    private void appendImageToChat(String senderName, File imageFile) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Container aligned left for received
                JPanel messageContainer = new JPanel();
                messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
                messageContainer.setBackground(BACKGROUND_COLOR);
                messageContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
                messageContainer.setBorder(new EmptyBorder(3, 10, 3, 50));
                
                // Create panel for image message
                JPanel imageMessagePanel = new JPanel();
                imageMessagePanel.setLayout(new BoxLayout(imageMessagePanel, BoxLayout.Y_AXIS));
                imageMessagePanel.setBackground(RECEIVED_MSG_COLOR);
                imageMessagePanel.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(2, 2, 2, 2),
                    new EmptyBorder(8, 8, 8, 8)
                ));
                imageMessagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                // Sender label
                JLabel senderLabel = new JLabel(senderName);
                senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                senderLabel.setForeground(PRIMARY_COLOR);
                senderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                imageMessagePanel.add(senderLabel);
                imageMessagePanel.add(Box.createVerticalStrut(5));
                
                // Load and scale image
                ImageIcon originalIcon = new ImageIcon(imageFile.getAbsolutePath());
                Image originalImage = originalIcon.getImage();
                
                // Scale image to fit (max 300x300)
                int maxWidth = 300;
                int maxHeight = 300;
                int width = originalIcon.getIconWidth();
                int height = originalIcon.getIconHeight();
                
                if (width > maxWidth || height > maxHeight) {
                    double scale = Math.min((double)maxWidth / width, (double)maxHeight / height);
                    width = (int)(width * scale);
                    height = (int)(height * scale);
                }
                
                Image scaledImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaledImage);
                
                // Image label (clickable to view full size)
                JLabel imageLabel = new JLabel(scaledIcon);
                imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                imageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                imageLabel.setToolTipText("Click to view full size");
                imageLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
                imageLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        openFile(imageFile);
                    }
                });
                imageMessagePanel.add(imageLabel);
                
                // Buttons panel
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                buttonPanel.setBackground(RECEIVED_MSG_COLOR);
                buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                JButton viewButton = new JButton("👁 View");
                viewButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                viewButton.setBackground(new Color(37, 99, 235)); // Blue
                viewButton.setForeground(Color.WHITE);
                viewButton.setBorderPainted(false);
                viewButton.setFocusPainted(false);
                viewButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                viewButton.setOpaque(true);
                viewButton.addActionListener(e -> openFile(imageFile));
                
                JButton saveButton = new JButton("💾 Save");
                saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                saveButton.setBackground(new Color(59, 130, 246)); // Light blue
                saveButton.setForeground(Color.WHITE);
                saveButton.setBorderPainted(false);
                saveButton.setFocusPainted(false);
                saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                saveButton.setOpaque(true);
                saveButton.addActionListener(e -> saveFileAs(imageFile));
                
                buttonPanel.add(viewButton);
                buttonPanel.add(saveButton);
                imageMessagePanel.add(buttonPanel);
                
                // Time
                String timeStr = TIME_FORMAT.format(new Date());
                JLabel timeLabel = new JLabel(timeStr);
                timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                timeLabel.setForeground(TEXT_SECONDARY);
                timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                imageMessagePanel.add(Box.createVerticalStrut(5));
                imageMessagePanel.add(timeLabel);
                
                messageContainer.add(imageMessagePanel);
                messageContainer.add(Box.createHorizontalGlue());
                
                chatPanel.add(messageContainer);
                chatPanel.add(Box.createVerticalStrut(5));
                chatPanel.revalidate();
                scrollToBottom();
                
            } catch (Exception e) {
                appendToChat("[ERROR] Failed to display image: " + e.getMessage() + "\n");
            }
        });
    }
    
    /**
     * Appends sent image to chat (for sender)
     */
    private void appendSentImage(File imageFile, String recipientInfo) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Container aligned right for sent
                JPanel messageContainer = new JPanel();
                messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
                messageContainer.setBackground(BACKGROUND_COLOR);
                messageContainer.setAlignmentX(Component.RIGHT_ALIGNMENT);
                messageContainer.setBorder(new EmptyBorder(3, 50, 3, 10));
                
                // Add glue to push to right
                messageContainer.add(Box.createHorizontalGlue());
                
                // Create panel for image message
                JPanel imageMessagePanel = new JPanel();
                imageMessagePanel.setLayout(new BoxLayout(imageMessagePanel, BoxLayout.Y_AXIS));
                imageMessagePanel.setBackground(SENT_MSG_COLOR);
                imageMessagePanel.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(2, 2, 2, 2),
                    new EmptyBorder(8, 8, 8, 8)
                ));
                imageMessagePanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                
                // Load and scale image
                ImageIcon originalIcon = new ImageIcon(imageFile.getAbsolutePath());
                Image originalImage = originalIcon.getImage();
                
                // Scale image to fit (max 300x300)
                int maxWidth = 300;
                int maxHeight = 300;
                int width = originalIcon.getIconWidth();
                int height = originalIcon.getIconHeight();
                
                if (width > maxWidth || height > maxHeight) {
                    double scale = Math.min((double)maxWidth / width, (double)maxHeight / height);
                    width = (int)(width * scale);
                    height = (int)(height * scale);
                }
                
                Image scaledImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaledImage);
                
                // Image label (clickable to view full size)
                JLabel imageLabel = new JLabel(scaledIcon);
                imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                imageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                imageLabel.setToolTipText("Click to view full size");
                imageLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
                imageLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        openFile(imageFile);
                    }
                });
                imageMessagePanel.add(imageLabel);
                
                // Buttons panel
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                buttonPanel.setBackground(SENT_MSG_COLOR);
                buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                JButton viewButton = new JButton("👁 View");
                viewButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                viewButton.setBackground(new Color(37, 99, 235)); // Blue
                viewButton.setForeground(Color.WHITE);
                viewButton.setBorderPainted(false);
                viewButton.setFocusPainted(false);
                viewButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                viewButton.setOpaque(true);
                viewButton.addActionListener(e -> openFile(imageFile));
                
                JButton saveButton = new JButton("💾 Save");
                saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                saveButton.setBackground(new Color(59, 130, 246)); // Light blue
                saveButton.setForeground(Color.WHITE);
                saveButton.setBorderPainted(false);
                saveButton.setFocusPainted(false);
                saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                saveButton.setOpaque(true);
                saveButton.addActionListener(e -> saveFileAs(imageFile));
                
                buttonPanel.add(viewButton);
                buttonPanel.add(saveButton);
                imageMessagePanel.add(buttonPanel);
                
                // Time and recipient
                String timeStr = TIME_FORMAT.format(new Date());
                JLabel timeLabel = new JLabel(timeStr + " • to " + recipientInfo);
                timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                timeLabel.setForeground(new Color(224, 242, 254));
                timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                imageMessagePanel.add(Box.createVerticalStrut(5));
                imageMessagePanel.add(timeLabel);
                
                messageContainer.add(imageMessagePanel);
                
                chatPanel.add(messageContainer);
                chatPanel.add(Box.createVerticalStrut(5));
                chatPanel.revalidate();
                scrollToBottom();
                
            } catch (Exception e) {
                appendToChat("[ERROR] Failed to display sent image: " + e.getMessage() + "\n");
            }
        });
    }
    
    /**
     * Appends sent file to chat (for sender)
     */
    private void appendSentFile(File file, String recipientInfo) {
        SwingUtilities.invokeLater(() -> {
            // Container aligned right for sent
            JPanel messageContainer = new JPanel();
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
            messageContainer.setBackground(BACKGROUND_COLOR);
            messageContainer.setAlignmentX(Component.RIGHT_ALIGNMENT);
            messageContainer.setBorder(new EmptyBorder(3, 50, 3, 10));
            
            // Add glue to push to right
            messageContainer.add(Box.createHorizontalGlue());
            
            JPanel filePanel = new JPanel();
            filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
            filePanel.setBackground(SENT_MSG_COLOR);
            filePanel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(2, 2, 2, 2),
                new EmptyBorder(10, 10, 10, 10)
            ));
            filePanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            // File info panel
            JPanel fileInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            fileInfoPanel.setBackground(SENT_MSG_COLOR);
            fileInfoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // File icon
            JLabel fileIcon = new JLabel("📎");
            fileIcon.setFont(new Font("Segoe UI", Font.PLAIN, 32));
            fileInfoPanel.add(fileIcon);
            
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(SENT_MSG_COLOR);
            
            JLabel fileLabel = new JLabel(file.getName());
            fileLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            fileLabel.setForeground(Color.WHITE);
            
            JLabel sizeLabel = new JLabel(formatFileSize(file.length()));
            sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            sizeLabel.setForeground(new Color(224, 242, 254));
            
            infoPanel.add(fileLabel);
            infoPanel.add(sizeLabel);
            fileInfoPanel.add(infoPanel);
            
            filePanel.add(fileInfoPanel);
            filePanel.add(Box.createVerticalStrut(8));
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            buttonPanel.setBackground(SENT_MSG_COLOR);
            buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JButton openButton = new JButton("📂 Open");
            openButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            openButton.setBackground(new Color(37, 99, 235)); // Blue
            openButton.setForeground(Color.WHITE);
            openButton.setBorderPainted(false);
            openButton.setFocusPainted(false);
            openButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openButton.setOpaque(true);
            openButton.addActionListener(e -> openFile(file));
            buttonPanel.add(openButton);
            
            JButton saveButton = new JButton("💾 Save As");
            saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            saveButton.setBackground(new Color(59, 130, 246)); // Light blue
            saveButton.setForeground(Color.WHITE);
            saveButton.setBorderPainted(false);
            saveButton.setFocusPainted(false);
            saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            saveButton.setOpaque(true);
            saveButton.addActionListener(e -> saveFileAs(file));
            buttonPanel.add(saveButton);
            
            filePanel.add(buttonPanel);
            
            // Time and recipient
            String timeStr = TIME_FORMAT.format(new Date());
            JLabel timeLabel = new JLabel(timeStr + " • to " + recipientInfo);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(new Color(224, 242, 254));
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            filePanel.add(Box.createVerticalStrut(5));
            filePanel.add(timeLabel);
            
            messageContainer.add(filePanel);
            
            chatPanel.add(messageContainer);
            chatPanel.add(Box.createVerticalStrut(5));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Appends a received file attachment to chat area
     */
    private void appendFileToChat(String senderName, File file) {
        SwingUtilities.invokeLater(() -> {
            // Container aligned left for received
            JPanel messageContainer = new JPanel();
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
            messageContainer.setBackground(BACKGROUND_COLOR);
            messageContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageContainer.setBorder(new EmptyBorder(3, 10, 3, 50));
            
            JPanel filePanel = new JPanel();
            filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
            filePanel.setBackground(RECEIVED_MSG_COLOR);
            filePanel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(2, 2, 2, 2),
                new EmptyBorder(10, 10, 10, 10)
            ));
            filePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Sender name
            JLabel senderLabel = new JLabel(senderName);
            senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            senderLabel.setForeground(PRIMARY_COLOR);
            senderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            filePanel.add(senderLabel);
            filePanel.add(Box.createVerticalStrut(8));
            
            // File info panel
            JPanel fileInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            fileInfoPanel.setBackground(RECEIVED_MSG_COLOR);
            fileInfoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // File icon
            JLabel fileIcon = new JLabel("📎");
            fileIcon.setFont(new Font("Segoe UI", Font.PLAIN, 32));
            fileInfoPanel.add(fileIcon);
            
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(RECEIVED_MSG_COLOR);
            
            JLabel fileLabel = new JLabel(file.getName());
            fileLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            fileLabel.setForeground(TEXT_PRIMARY);
            
            JLabel sizeLabel = new JLabel(formatFileSize(file.length()));
            sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            sizeLabel.setForeground(TEXT_SECONDARY);
            
            infoPanel.add(fileLabel);
            infoPanel.add(sizeLabel);
            fileInfoPanel.add(infoPanel);
            
            filePanel.add(fileInfoPanel);
            filePanel.add(Box.createVerticalStrut(8));
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            buttonPanel.setBackground(RECEIVED_MSG_COLOR);
            buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JButton openButton = new JButton("📂 Open");
            openButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            openButton.setBackground(new Color(37, 99, 235)); // Blue
            openButton.setForeground(Color.WHITE);
            openButton.setBorderPainted(false);
            openButton.setFocusPainted(false);
            openButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openButton.setOpaque(true);
            openButton.addActionListener(e -> openFile(file));
            buttonPanel.add(openButton);
            
            JButton saveButton = new JButton("💾 Save As");
            saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            saveButton.setBackground(new Color(59, 130, 246)); // Light blue
            saveButton.setForeground(Color.WHITE);
            saveButton.setBorderPainted(false);
            saveButton.setFocusPainted(false);
            saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            saveButton.setOpaque(true);
            saveButton.addActionListener(e -> saveFileAs(file));
            buttonPanel.add(saveButton);
            
            filePanel.add(buttonPanel);
            
            // Time
            String timeStr = TIME_FORMAT.format(new Date());
            JLabel timeLabel = new JLabel(timeStr);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(TEXT_SECONDARY);
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            filePanel.add(Box.createVerticalStrut(5));
            filePanel.add(timeLabel);
            
            messageContainer.add(filePanel);
            messageContainer.add(Box.createHorizontalGlue());
            
            chatPanel.add(messageContainer);
            chatPanel.add(Box.createVerticalStrut(5));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Appends a received audio file to chat area
     */
    private void appendAudioToChat(String senderName, File audioFile) {
        SwingUtilities.invokeLater(() -> {
            // Container aligned left for received
            JPanel messageContainer = new JPanel();
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
            messageContainer.setBackground(BACKGROUND_COLOR);
            messageContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageContainer.setBorder(new EmptyBorder(3, 10, 3, 50));
            
            JPanel audioPanel = new JPanel();
            audioPanel.setLayout(new BoxLayout(audioPanel, BoxLayout.Y_AXIS));
            audioPanel.setBackground(RECEIVED_MSG_COLOR);
            audioPanel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(2, 2, 2, 2),
                new EmptyBorder(10, 10, 10, 10)
            ));
            audioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Sender name
            JLabel senderLabel = new JLabel(senderName);
            senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            senderLabel.setForeground(PRIMARY_COLOR);
            senderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            audioPanel.add(senderLabel);
            audioPanel.add(Box.createVerticalStrut(8));
            
            // Audio info panel
            JPanel audioInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            audioInfoPanel.setBackground(RECEIVED_MSG_COLOR);
            audioInfoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Audio icon
            JLabel audioIcon = new JLabel("🎤");
            audioIcon.setFont(new Font("Segoe UI", Font.PLAIN, 32));
            audioInfoPanel.add(audioIcon);
            
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(RECEIVED_MSG_COLOR);
            
            JLabel audioLabel = new JLabel("Voice Message");
            audioLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            audioLabel.setForeground(TEXT_PRIMARY);
            
            JLabel sizeLabel = new JLabel(formatFileSize(audioFile.length()));
            sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            sizeLabel.setForeground(TEXT_SECONDARY);
            
            infoPanel.add(audioLabel);
            infoPanel.add(sizeLabel);
            audioInfoPanel.add(infoPanel);
            
            audioPanel.add(audioInfoPanel);
            audioPanel.add(Box.createVerticalStrut(8));
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            buttonPanel.setBackground(RECEIVED_MSG_COLOR);
            buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JButton playButton = new JButton("▶ Play");
            playButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            playButton.setBackground(new Color(34, 197, 94)); // Green
            playButton.setForeground(Color.WHITE);
            playButton.setBorderPainted(false);
            playButton.setFocusPainted(false);
            playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            playButton.setOpaque(true);
            playButton.addActionListener(e -> playAudio(audioFile));
            buttonPanel.add(playButton);
            
            JButton saveButton = new JButton("💾 Save As");
            saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            saveButton.setBackground(new Color(59, 130, 246)); // Blue
            saveButton.setForeground(Color.WHITE);
            saveButton.setBorderPainted(false);
            saveButton.setFocusPainted(false);
            saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            saveButton.setOpaque(true);
            saveButton.addActionListener(e -> saveFileAs(audioFile));
            buttonPanel.add(saveButton);
            
            audioPanel.add(buttonPanel);
            
            // Time
            String timeStr = TIME_FORMAT.format(new Date());
            JLabel timeLabel = new JLabel(timeStr);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(TEXT_SECONDARY);
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            audioPanel.add(Box.createVerticalStrut(5));
            audioPanel.add(timeLabel);
            
            messageContainer.add(audioPanel);
            messageContainer.add(Box.createHorizontalGlue());
            
            chatPanel.add(messageContainer);
            chatPanel.add(Box.createVerticalStrut(5));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Appends sent audio to chat (for sender)
     */
    private void appendSentAudio(File audioFile, String recipientInfo) {
        SwingUtilities.invokeLater(() -> {
            // Container aligned right for sent
            JPanel messageContainer = new JPanel();
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.X_AXIS));
            messageContainer.setBackground(BACKGROUND_COLOR);
            messageContainer.setAlignmentX(Component.RIGHT_ALIGNMENT);
            messageContainer.setBorder(new EmptyBorder(3, 50, 3, 10));
            
            // Add glue to push to right
            messageContainer.add(Box.createHorizontalGlue());
            
            JPanel audioPanel = new JPanel();
            audioPanel.setLayout(new BoxLayout(audioPanel, BoxLayout.Y_AXIS));
            audioPanel.setBackground(SENT_MSG_COLOR);
            audioPanel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(2, 2, 2, 2),
                new EmptyBorder(10, 10, 10, 10)
            ));
            audioPanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            // Audio info panel
            JPanel audioInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            audioInfoPanel.setBackground(SENT_MSG_COLOR);
            audioInfoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Audio icon
            JLabel audioIcon = new JLabel("🎤");
            audioIcon.setFont(new Font("Segoe UI", Font.PLAIN, 32));
            audioInfoPanel.add(audioIcon);
            
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(SENT_MSG_COLOR);
            
            JLabel audioLabel = new JLabel("Voice Message");
            audioLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            audioLabel.setForeground(Color.WHITE);
            
            JLabel sizeLabel = new JLabel(formatFileSize(audioFile.length()));
            sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            sizeLabel.setForeground(new Color(224, 242, 254));
            
            infoPanel.add(audioLabel);
            infoPanel.add(sizeLabel);
            audioInfoPanel.add(infoPanel);
            
            audioPanel.add(audioInfoPanel);
            audioPanel.add(Box.createVerticalStrut(8));
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            buttonPanel.setBackground(SENT_MSG_COLOR);
            buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JButton playButton = new JButton("▶ Play");
            playButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            playButton.setBackground(new Color(16, 185, 129)); // Teal
            playButton.setForeground(Color.WHITE);
            playButton.setBorderPainted(false);
            playButton.setFocusPainted(false);
            playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            playButton.setOpaque(true);
            playButton.addActionListener(e -> playAudio(audioFile));
            buttonPanel.add(playButton);
            
            JButton saveButton = new JButton("💾 Save As");
            saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            saveButton.setBackground(new Color(99, 102, 241)); // Indigo
            saveButton.setForeground(Color.WHITE);
            saveButton.setBorderPainted(false);
            saveButton.setFocusPainted(false);
            saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            saveButton.setOpaque(true);
            saveButton.addActionListener(e -> saveFileAs(audioFile));
            buttonPanel.add(saveButton);
            
            audioPanel.add(buttonPanel);
            
            // Time and recipient
            String timeStr = TIME_FORMAT.format(new Date());
            JLabel timeLabel = new JLabel(timeStr + " • to " + recipientInfo);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(new Color(224, 242, 254));
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            audioPanel.add(Box.createVerticalStrut(5));
            audioPanel.add(timeLabel);
            
            messageContainer.add(audioPanel);
            
            chatPanel.add(messageContainer);
            chatPanel.add(Box.createVerticalStrut(5));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }
    
    /**
     * Opens a file with the default system application
     */
    private void openFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Cannot open file: Desktop operations not supported",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to open file: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Saves a file to a user-selected location
     */
    private void saveFileAs(File sourceFile) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(sourceFile.getName()));
        fileChooser.setDialogTitle("Save File As");
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File destFile = fileChooser.getSelectedFile();
            try {
                java.nio.file.Files.copy(
                    sourceFile.toPath(),
                    destFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                JOptionPane.showMessageDialog(this,
                    "File saved successfully to:\n" + destFile.getAbsolutePath(),
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save file: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Formats file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Checks if file is an image
     */
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
               name.endsWith(".png") || name.endsWith(".gif") ||
               name.endsWith(".bmp");
    }
    
    /**
     * Checks if file is an audio file
     */
    private boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".wav") || name.endsWith(".mp3") ||
               name.endsWith(".m4a") || name.endsWith(".aac") ||
               name.endsWith(".ogg");
    }
    
    /**
     * Toggles voice recording on/off
     */
    private void toggleVoiceRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }
    
    /**
     * Starts audio recording
     */
    private void startRecording() {
        try {
            AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100, // Sample rate
                16,    // Sample size in bits
                2,     // Channels (stereo)
                4,     // Frame size
                44100, // Frame rate
                false  // Big endian
            );
            
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            
            if (!AudioSystem.isLineSupported(info)) {
                JOptionPane.showMessageDialog(this,
                    "Microphone not supported on this system",
                    "Recording Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(format);
            targetLine.start();
            
            isRecording = true;
            sendVoiceButton.setText("⏹ Stop");
            sendVoiceButton.setBackground(new Color(239, 68, 68)); // Red
            
            // Create audio file
            audioFile = new File("temp_audio_" + System.currentTimeMillis() + ".wav");
            
            // Record in background thread
            Thread recordThread = new Thread(() -> {
                try {
                    AudioInputStream audioInputStream = new AudioInputStream(targetLine);
                    AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, audioFile);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> 
                        appendToChat("[ERROR] Recording failed: " + e.getMessage() + "\n")
                    );
                }
            });
            recordThread.start();
            
            appendToChat("[VOICE] Recording started... Click Stop when done\n");
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to start recording: " + e.getMessage(),
                "Recording Error",
                JOptionPane.ERROR_MESSAGE);
            isRecording = false;
            sendVoiceButton.setText("🎤 Voice");
            sendVoiceButton.setBackground(new Color(59, 130, 246));
        }
    }
    
    /**
     * Stops audio recording and sends the file
     */
    private void stopRecording() {
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }
        
        isRecording = false;
        sendVoiceButton.setText("🎤 Voice");
        sendVoiceButton.setBackground(new Color(59, 130, 246));
        
        if (audioFile != null && audioFile.exists()) {
            appendToChat("[VOICE] Recording stopped. Sending...\n");
            sendFile(audioFile);
            
            // Delete temp file after sending
            Thread deleteThread = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    audioFile.delete();
                } catch (Exception e) {
                    // Ignore
                }
            });
            deleteThread.start();
        }
    }
    
    /**
     * Plays an audio file
     */
    private void playAudio(File audioFile) {
        Thread playThread = new Thread(() -> {
            try {
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
                AudioFormat format = audioInputStream.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine sourceLine = (SourceDataLine) AudioSystem.getLine(info);
                
                sourceLine.open(format);
                sourceLine.start();
                
                SwingUtilities.invokeLater(() -> 
                    appendToChat("[AUDIO] Playing...\n")
                );
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = audioInputStream.read(buffer, 0, buffer.length)) != -1) {
                    sourceLine.write(buffer, 0, bytesRead);
                }
                
                sourceLine.drain();
                sourceLine.close();
                audioInputStream.close();
                
                SwingUtilities.invokeLater(() -> 
                    appendToChat("[AUDIO] Playback finished\n")
                );
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    JOptionPane.showMessageDialog(this,
                        "Failed to play audio: " + e.getMessage(),
                        "Playback Error",
                        JOptionPane.ERROR_MESSAGE)
                );
            }
        });
        playThread.start();
    }
    
    /**
     * Updates status label
     */
    private void updateStatus(String status, boolean connected) {
        SwingUtilities.invokeLater(() -> {
            String statusText = connected ? "● " + status : "○ " + status;
            statusLabel.setText(statusText);
        });
    }
    
    /**
     * Updates progress bar
     */
    private void updateProgress(int percent, String text) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            progressBar.setString(text + " - " + percent + "%");
        });
    }
    
    /**
     * Shows/hides progress bar
     */
    private void showProgress(boolean show) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(show);
            if (!show) {
                progressBar.setValue(0);
                progressBar.setString("Ready");
            }
        });
    }
    
    /**
     * Refreshes user list display and recipient combo box
     */
    private void refreshUserDisplay() {
        SwingUtilities.invokeLater(() -> {
            // Update user list panel
            userListModel.clear();
            for (String user : onlineUsers) {
                userListModel.addElement("● " + user);
            }
            if (onlineUsers.isEmpty()) {
                userListModel.addElement("(No other users online)");
            }
            
            // Update recipient combo box
            String currentSelection = (String) recipientComboBox.getSelectedItem();
            recipientComboBox.removeAllItems();
            recipientComboBox.addItem("ALL (Broadcast)");
            
            for (String user : onlineUsers) {
                recipientComboBox.addItem(user);
            }
            
            // Try to restore previous selection
            if (currentSelection != null) {
                for (int i = 0; i < recipientComboBox.getItemCount(); i++) {
                    if (recipientComboBox.getItemAt(i).equals(currentSelection)) {
                        recipientComboBox.setSelectedIndex(i);
                        return;
                    }
                }
            }
            // Default to ALL if previous selection not found
            recipientComboBox.setSelectedIndex(0);
        });
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
     * Main method to start GUI client
     */
    public static void main(String[] args) {
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel
        }
        
        String clientId = "User";
        
        if (args.length > 0) {
            clientId = args[0];
        } else {
            // Prompt for username
            clientId = JOptionPane.showInputDialog(
                null,
                "Enter your username:",
                "UDP Chat Client",
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (clientId == null || clientId.trim().isEmpty()) {
                clientId = "User_" + (int)(Math.random() * 1000);
            }
        }
        
        final String finalClientId = clientId.trim();
        
        SwingUtilities.invokeLater(() -> {
            ClientGUI client = new ClientGUI(finalClientId);
            client.setVisible(true);
        });
    }
}
