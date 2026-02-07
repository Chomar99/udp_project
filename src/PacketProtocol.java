import java.io.*;
import java.nio.ByteBuffer;

/**
 * PacketProtocol defines the structure and message types for UDP communication
 * This class handles serialization/deserialization of different packet types
 */
public class PacketProtocol {
    
    // Message Types
    public static final byte MSG = 1;
    public static final byte FILE_START = 2;
    public static final byte FILE_CHUNK = 3;
    public static final byte FILE_END = 4;
    public static final byte REGISTER = 5;
    public static final byte HEARTBEAT = 6;
    public static final byte ACK = 7;
    public static final byte CLIENT_LIST = 8;
    public static final byte FILE_ACK = 9;
    
    // Constants
    public static final int MAX_PACKET_SIZE = 65507; // Max UDP packet size
    public static final int MAX_CHUNK_SIZE = 1024; // Max file chunk size
    public static final int HEADER_SIZE = 50; // Reserved space for header
    
    /**
     * Packet structure:
     * - Type (1 byte)
     * - ClientID length (4 bytes)
     * - ClientID (variable)
     * - Sequence number (4 bytes) - for file chunks
     * - File ID (4 bytes) - for file transfers
     * - Data length (4 bytes)
     * - Data (variable)
     */
    
    public static class Packet {
        public byte type;
        public String clientId;
        public String recipient; // "ALL" for broadcast, or specific client ID
        public int sequenceNumber;
        public int fileId;
        public byte[] data;
        
        public Packet(byte type, String clientId, byte[] data) {
            this.type = type;
            this.clientId = clientId;
            this.recipient = "ALL"; // Default to broadcast
            this.sequenceNumber = 0;
            this.fileId = 0;
            this.data = data;
        }
        
        public Packet(byte type, String clientId, int sequenceNumber, int fileId, byte[] data) {
            this.type = type;
            this.clientId = clientId;
            this.recipient = "ALL"; // Default to broadcast
            this.sequenceNumber = sequenceNumber;
            this.fileId = fileId;
            this.data = data;
        }
        
        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }
    }
    
    /**
     * Serializes a packet into a byte array for UDP transmission
     */
    public static byte[] serialize(Packet packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Write type
        dos.writeByte(packet.type);
        
        // Write clientId
        byte[] clientIdBytes = packet.clientId.getBytes("UTF-8");
        dos.writeInt(clientIdBytes.length);
        dos.write(clientIdBytes);
        
        // Write recipient
        String recipient = packet.recipient != null ? packet.recipient : "ALL";
        byte[] recipientBytes = recipient.getBytes("UTF-8");
        dos.writeInt(recipientBytes.length);
        dos.write(recipientBytes);
        
        // Write sequence number and file ID
        dos.writeInt(packet.sequenceNumber);
        dos.writeInt(packet.fileId);
        
        // Write data
        if (packet.data != null) {
            dos.writeInt(packet.data.length);
            dos.write(packet.data);
        } else {
            dos.writeInt(0);
        }
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializes a byte array into a Packet object
     */
    public static Packet deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        
        // Read type
        byte type = dis.readByte();
        
        // Read clientId
        int clientIdLength = dis.readInt();
        byte[] clientIdBytes = new byte[clientIdLength];
        dis.readFully(clientIdBytes);
        String clientId = new String(clientIdBytes, "UTF-8");
        
        // Read recipient
        int recipientLength = dis.readInt();
        byte[] recipientBytes = new byte[recipientLength];
        dis.readFully(recipientBytes);
        String recipient = new String(recipientBytes, "UTF-8");
        
        // Read sequence number and file ID
        int sequenceNumber = dis.readInt();
        int fileId = dis.readInt();
        
        // Read data
        int dataLength = dis.readInt();
        byte[] packetData = null;
        if (dataLength > 0) {
            packetData = new byte[dataLength];
            dis.readFully(packetData);
        }
        
        Packet packet = new Packet(type, clientId, sequenceNumber, fileId, packetData);
        packet.recipient = recipient;
        return packet;
    }
    
    /**
     * Creates a text message packet
     */
    public static Packet createMessagePacket(String clientId, String message) {
        return new Packet(MSG, clientId, message.getBytes());
    }
    
    /**
     * Creates a registration packet
     */
    public static Packet createRegisterPacket(String clientId) {
        return new Packet(REGISTER, clientId, new byte[0]);
    }
    
    /**
     * Creates a heartbeat packet
     */
    public static Packet createHeartbeatPacket(String clientId) {
        return new Packet(HEARTBEAT, clientId, new byte[0]);
    }
    
    /**
     * Creates a file start packet with metadata
     */
    public static Packet createFileStartPacket(String clientId, int fileId, String filename, long fileSize) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(filename);
            dos.writeLong(fileSize);
            dos.flush();
            return new Packet(FILE_START, clientId, 0, fileId, baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Creates a file chunk packet
     */
    public static Packet createFileChunkPacket(String clientId, int fileId, int sequenceNumber, byte[] chunkData) {
        return new Packet(FILE_CHUNK, clientId, sequenceNumber, fileId, chunkData);
    }
    
    /**
     * Creates a file end packet
     */
    public static Packet createFileEndPacket(String clientId, int fileId, int totalChunks) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(totalChunks);
        return new Packet(FILE_END, clientId, 0, fileId, buffer.array());
    }
    
    /**
     * Creates an ACK packet
     */
    public static Packet createAckPacket(String clientId, int sequenceNumber, int fileId) {
        return new Packet(ACK, clientId, sequenceNumber, fileId, new byte[0]);
    }
    
    /**
     * Creates a file ACK packet
     */
    public static Packet createFileAckPacket(String clientId, int sequenceNumber, int fileId) {
        return new Packet(FILE_ACK, clientId, sequenceNumber, fileId, new byte[0]);
    }
    
    /**
     * Extracts filename and file size from FILE_START packet
     */
    public static FileMetadata extractFileMetadata(Packet packet) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(packet.data);
            DataInputStream dis = new DataInputStream(bais);
            String filename = dis.readUTF();
            long fileSize = dis.readLong();
            return new FileMetadata(filename, fileSize);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static class FileMetadata {
        public String filename;
        public long fileSize;
        
        public FileMetadata(String filename, long fileSize) {
            this.filename = filename;
            this.fileSize = fileSize;
        }
    }
}
