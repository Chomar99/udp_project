import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * FileManager handles file chunking, reassembly, and progress tracking
 * Supports concurrent file transfers with loss-tolerant UDP logic
 */
public class FileManager {
    
    private static final int MAX_CHUNK_SIZE = PacketProtocol.MAX_CHUNK_SIZE;
    private static final int MAX_RETRIES = 5;
    private static final int ACK_TIMEOUT = 2000; // 2 seconds
    
    // Active file transfers (for sending)
    private Map<Integer, FileTransferState> activeTransfers;
    
    // Active file receptions (for receiving)
    private Map<Integer, FileReceptionState> activeReceptions;
    
    private int nextFileId;
    
    public FileManager() {
        this.activeTransfers = new ConcurrentHashMap<>();
        this.activeReceptions = new ConcurrentHashMap<>();
        this.nextFileId = 1;
    }
    
    /**
     * Prepares a file for transfer by splitting it into chunks
     */
    public FileTransferState prepareFileTransfer(File file) throws IOException {
        int fileId = getNextFileId();
        FileTransferState state = new FileTransferState(fileId, file);
        activeTransfers.put(fileId, state);
        return state;
    }
    
    /**
     * Gets the next available file ID
     */
    private synchronized int getNextFileId() {
        return nextFileId++;
    }
    
    /**
     * Marks a chunk as acknowledged
     */
    public void markChunkAcknowledged(int fileId, int sequenceNumber) {
        FileTransferState state = activeTransfers.get(fileId);
        if (state != null) {
            state.markChunkAcknowledged(sequenceNumber);
        }
    }
    
    /**
     * Gets a file transfer state
     */
    public FileTransferState getTransferState(int fileId) {
        return activeTransfers.get(fileId);
    }
    
    /**
     * Removes a completed file transfer
     */
    public void removeTransfer(int fileId) {
        activeTransfers.remove(fileId);
    }
    
    /**
     * Starts receiving a file
     */
    public void startFileReception(int fileId, String filename, long fileSize) {
        FileReceptionState state = new FileReceptionState(fileId, filename, fileSize);
        activeReceptions.put(fileId, state);
    }
    
    /**
     * Receives a file chunk
     */
    public void receiveChunk(int fileId, int sequenceNumber, byte[] chunkData) {
        FileReceptionState state = activeReceptions.get(fileId);
        if (state != null) {
            state.receiveChunk(sequenceNumber, chunkData);
        }
    }
    
    /**
     * Completes file reception and saves to disk
     */
    public File completeFileReception(int fileId, int totalChunks) throws IOException {
        FileReceptionState state = activeReceptions.get(fileId);
        if (state != null) {
            File savedFile = state.assembleAndSave(totalChunks);
            activeReceptions.remove(fileId);
            return savedFile;
        }
        return null;
    }
    
    /**
     * Gets a file reception state
     */
    public FileReceptionState getReceptionState(int fileId) {
        return activeReceptions.get(fileId);
    }
    
    /**
     * State for file being sent
     */
    public static class FileTransferState {
        private int fileId;
        private File file;
        private String filename;
        private long fileSize;
        private List<byte[]> chunks;
        private Set<Integer> acknowledgedChunks;
        private int totalChunks;
        
        public FileTransferState(int fileId, File file) throws IOException {
            this.fileId = fileId;
            this.file = file;
            this.filename = file.getName();
            this.fileSize = file.length();
            this.chunks = new ArrayList<>();
            this.acknowledgedChunks = ConcurrentHashMap.newKeySet();
            splitFileIntoChunks();
        }
        
        /**
         * Splits the file into chunks
         */
        private void splitFileIntoChunks() throws IOException {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[MAX_CHUNK_SIZE];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    chunks.add(chunk);
                }
                
                totalChunks = chunks.size();
            }
        }
        
        public int getFileId() {
            return fileId;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public int getTotalChunks() {
            return totalChunks;
        }
        
        public byte[] getChunk(int sequenceNumber) {
            if (sequenceNumber >= 0 && sequenceNumber < chunks.size()) {
                return chunks.get(sequenceNumber);
            }
            return null;
        }
        
        public void markChunkAcknowledged(int sequenceNumber) {
            acknowledgedChunks.add(sequenceNumber);
        }
        
        public boolean isChunkAcknowledged(int sequenceNumber) {
            return acknowledgedChunks.contains(sequenceNumber);
        }
        
        public boolean isComplete() {
            return acknowledgedChunks.size() == totalChunks;
        }
        
        public int getProgress() {
            return (acknowledgedChunks.size() * 100) / totalChunks;
        }
        
        /**
         * Gets list of unacknowledged chunks for retransmission
         */
        public List<Integer> getUnacknowledgedChunks() {
            List<Integer> unacked = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                if (!acknowledgedChunks.contains(i)) {
                    unacked.add(i);
                }
            }
            return unacked;
        }
    }
    
    /**
     * State for file being received
     */
    public static class FileReceptionState {
        private int fileId;
        private String filename;
        private long fileSize;
        private Map<Integer, byte[]> receivedChunks;
        private long bytesReceived;
        
        public FileReceptionState(int fileId, String filename, long fileSize) {
            this.fileId = fileId;
            this.filename = filename;
            this.fileSize = fileSize;
            this.receivedChunks = new ConcurrentHashMap<>();
            this.bytesReceived = 0;
        }
        
        public void receiveChunk(int sequenceNumber, byte[] chunkData) {
            if (!receivedChunks.containsKey(sequenceNumber)) {
                receivedChunks.put(sequenceNumber, chunkData);
                bytesReceived += chunkData.length;
            }
        }
        
        public boolean hasChunk(int sequenceNumber) {
            return receivedChunks.containsKey(sequenceNumber);
        }
        
        public int getProgress() {
            if (fileSize == 0) return 100;
            return (int) ((bytesReceived * 100) / fileSize);
        }
        
        public String getFilename() {
            return filename;
        }
        
        public int getFileId() {
            return fileId;
        }
        
        /**
         * Assembles all chunks and saves to disk
         */
        public File assembleAndSave(int totalChunks) throws IOException {
            // Create received_files directory if it doesn't exist
            File receivedDir = new File("received_files");
            if (!receivedDir.exists()) {
                receivedDir.mkdirs();
            }
            
            // Create output file
            File outputFile = new File(receivedDir, filename);
            
            // Write chunks in order
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                for (int i = 0; i < totalChunks; i++) {
                    byte[] chunk = receivedChunks.get(i);
                    if (chunk != null) {
                        fos.write(chunk);
                    } else {
                        throw new IOException("Missing chunk " + i + " for file " + filename);
                    }
                }
            }
            
            return outputFile;
        }
        
        /**
         * Gets list of missing chunks
         */
        public List<Integer> getMissingChunks(int totalChunks) {
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                if (!receivedChunks.containsKey(i)) {
                    missing.add(i);
                }
            }
            return missing;
        }
    }
}
