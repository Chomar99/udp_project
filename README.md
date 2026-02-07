# UDP Real-Time Messaging and File Sharing System

A complete Java application implementing a production-level real-time messaging and file sharing system using UDP sockets. The system supports multiple clients, message broadcasting, and reliable file transfer over UDP with packet segmentation and reassembly.

## 📋 Features

- **Real-time messaging** between multiple clients via central server
- **File sharing** with support for any file type (images, PDFs, documents, etc.)
- **UDP-based communication** using DatagramSocket and DatagramPacket
- **Client registration and heartbeat** mechanism (ping every 5 seconds)
- **File transfer with packet segmentation** (1024-byte chunks)
- **Loss-tolerant UDP logic** with retransmission and ACK
- **Progress tracking** for file transfers
- **Console-based UI** with intuitive commands
- **Multi-threaded architecture** for concurrent operations

## 🏗️ Architecture

The system consists of 4 main components:

### 1. **PacketProtocol.java**
Defines the packet structure and message types:
- `MSG` - Text messages
- `FILE_START` - Initiates file transfer with metadata
- `FILE_CHUNK` - File data chunks (max 1024 bytes)
- `FILE_END` - Completes file transfer
- `REGISTER` - Client registration
- `HEARTBEAT` - Keep-alive ping
- `ACK` / `FILE_ACK` - Acknowledgments
- `CLIENT_LIST` - Online users list

### 2. **FileManager.java**
Handles file operations:
- Splits files into chunks (max 1024 bytes each)
- Tracks file transfer state (sending)
- Reassembles received chunks
- Manages multiple concurrent file transfers
- Progress tracking and reporting

### 3. **Server.java**
Central UDP server:
- Maintains list of active clients (IP + port)
- Broadcasts messages to all connected clients
- Relays file transfers to all clients
- Monitors client heartbeats (15-second timeout)
- Handles client registration and disconnection
- Thread-safe concurrent client management

### 4. **Client.java**
UDP client application:
- Registers with server
- Sends heartbeat every 5 seconds
- Send/receive text messages
- Send/receive files
- Console-based UI with commands
- Multi-threaded send/receive operations
- Automatic file reassembly

## 📦 Project Structure

```
bisbis/
├── src/
│   ├── PacketProtocol.java    # Packet format and serialization
│   ├── FileManager.java         # File handling and chunking
│   ├── Server.java              # UDP server
│   └── Client.java              # UDP client
└── README.md                    # This file
```

## 🚀 Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Windows PowerShell, Command Prompt, or any terminal

### Compilation

Navigate to the project directory and compile all Java files:

```powershell
cd "c:\Users\basse\OneDrive\Bureau\bisbis"
javac src/*.java
```

This will compile all four Java files in the `src` directory.

## 🎮 Running the Application

### Step 1: Start the Server

Open a terminal and run:

```powershell
cd "c:\Users\basse\OneDrive\Bureau\bisbis"
java -cp src Server
```

You should see:
```
╔════════════════════════════════════════════╗
║    UDP Server Started Successfully         ║
║    Port: 9876                              ║
╚════════════════════════════════════════════╝

Type 'quit' to stop the server
```

### Step 2: Start Client 1

Open a **new terminal** and run:

```powershell
cd "c:\Users\basse\OneDrive\Bureau\bisbis"
java -cp src Client Alice
```

### Step 3: Start Client 2

Open **another new terminal** and run:

```powershell
cd "c:\Users\basse\OneDrive\Bureau\bisbis"
java -cp src Client Bob
```

### Step 4: Start Client 3

Open **yet another terminal** and run:

```powershell
cd "c:\Users\basse\OneDrive\Bureau\bisbis"
java -cp src Client Charlie
```

Each client will display:
```
╔════════════════════════════════════════════╗
║    UDP Client Started                      ║
║    Client ID: Alice                        ║
║    Local Port: 54321                       ║
╚════════════════════════════════════════════╝

╔════════════════════════════════════════════╗
║              Commands                      ║
╠════════════════════════════════════════════╣
║  Type message and press Enter to send      ║
║  /file <path>  - Send a file               ║
║  /users        - Show online users         ║
║  /help         - Show this help            ║
║  /quit         - Exit the application      ║
╚════════════════════════════════════════════╝

Alice > 
```

## 💬 Usage Examples

### Sending Messages

**Alice's terminal:**
```
Alice > Hello everyone!
[You]: Hello everyone!
```

**Bob and Charlie's terminals will show:**
```
[Alice]: Hello everyone!
Bob > 
```

### Viewing Online Users

**Bob's terminal:**
```
Bob > /users

╔════════════════════════════════════════════╗
║         Online Users                       ║
╠════════════════════════════════════════════╣
║  • Alice                                   ║
║  • Charlie                                 ║
╚════════════════════════════════════════════╝

Bob > 
```

### Sending a File

First, create a test file. In PowerShell:

```powershell
# Create a test text file
"Hello, this is a test file for UDP transfer!" | Out-File -FilePath "test.txt" -Encoding utf8

# Or create a larger file
1..1000 | ForEach-Object { "Line $_: Lorem ipsum dolor sit amet" } | Out-File -FilePath "large_test.txt" -Encoding utf8
```

**Alice sends a file:**
```
Alice > /file test.txt
[FILE] Preparing to send: test.txt
[FILE] Starting transfer: test.txt
[FILE] Sending test.txt: 100%
[FILE] Transfer completed: test.txt
Alice > 
```

**Bob and Charlie's terminals will show:**
```
[FILE] Alice is sending: test.txt (45 bytes)
[FILE] Receiving test.txt: 100%
[FILE] File received successfully: c:\Users\basse\OneDrive\Bureau\bisbis\received_files\test.txt

Bob > 
```

The file will be automatically saved in the `received_files` directory.

### Sending an Image File

If you have an image file:

```
Alice > /file C:\Users\basse\Pictures\photo.jpg
[FILE] Preparing to send: photo.jpg
[FILE] Starting transfer: photo.jpg
[FILE] Sending photo.jpg: 10%
[FILE] Sending photo.jpg: 20%
[FILE] Sending photo.jpg: 30%
...
[FILE] Transfer completed: photo.jpg
```

## 📊 Complete Example Session

### Server Console:
```
╔════════════════════════════════════════════╗
║    UDP Server Started Successfully         ║
║    Port: 9876                              ║
╚════════════════════════════════════════════╝

Type 'quit' to stop the server
[REGISTER] Client Alice connected from /127.0.0.1:54321
[REGISTER] Client Bob connected from /127.0.0.1:54322
[REGISTER] Client Charlie connected from /127.0.0.1:54323
[MESSAGE] From Alice: Hello everyone!
[MESSAGE] From Bob: Hi Alice!
[FILE_START] Client Alice sending file: test.txt (45 bytes)
[FILE_END] Client Alice completed file transfer (ID: 1)
```

### Alice's Console:
```
Alice > Hello everyone!
[You]: Hello everyone!

[Bob]: Hi Alice!

Alice > /users

╔════════════════════════════════════════════╗
║         Online Users                       ║
╠════════════════════════════════════════════╣
║  • Bob                                     ║
║  • Charlie                                 ║
╚════════════════════════════════════════════╝

Alice > /file test.txt
[FILE] Preparing to send: test.txt
[FILE] Starting transfer: test.txt
[FILE] Transfer completed: test.txt
Alice > 
```

### Bob's Console:
```
[Alice]: Hello everyone!

Bob > Hi Alice!
[You]: Hi Alice!

[FILE] Alice is sending: test.txt (45 bytes)
[FILE] File received successfully: received_files\test.txt

Bob > 
```

### Charlie's Console:
```
[Alice]: Hello everyone!

[Bob]: Hi Alice!

[FILE] Alice is sending: test.txt (45 bytes)
[FILE] File received successfully: received_files\test.txt

Charlie > 
```

## 🔧 Technical Details

### UDP Packet Structure

Each packet contains:
- **Type** (1 byte) - Message type identifier
- **Client ID length** (4 bytes) - Length of client identifier
- **Client ID** (variable) - Client identifier string
- **Sequence number** (4 bytes) - For file chunks
- **File ID** (4 bytes) - For file transfers
- **Data length** (4 bytes) - Length of payload
- **Data** (variable) - Actual payload

### File Transfer Protocol

1. **FILE_START**: Sender sends metadata (filename, file size)
2. **FILE_CHUNK**: Sender sends file in 1024-byte chunks with sequence numbers
3. **FILE_ACK**: Server acknowledges each chunk
4. **FILE_END**: Sender signals completion with total chunk count
5. **Reassembly**: Receiver assembles chunks in order and saves file

### Heartbeat Mechanism

- Clients send `HEARTBEAT` every 5 seconds
- Server expects heartbeat within 15 seconds
- Clients not sending heartbeat are considered disconnected
- Server broadcasts updated client list on changes

### Threading Model

**Server:**
- Main thread: Packet reception
- Heartbeat monitor thread: Checks client timeouts
- Packet processing: Inline (could be threaded for production)

**Client:**
- Main thread: UI input
- Heartbeat thread: Sends periodic pings
- Receiver thread: Processes incoming packets
- File transfer threads: One per active file send

## 🛠️ Features in Detail

### Loss-Tolerant UDP

- **Retransmission**: Chunks resent up to 5 times if not acknowledged
- **ACK mechanism**: Server acknowledges each chunk
- **Timeout handling**: 2-second timeout for acknowledgments
- **Sequence numbers**: Ensures correct chunk ordering

### Progress Tracking

- Real-time percentage display during transfer
- Updates every 10% for messages
- Tracks both sending and receiving progress

### Error Handling

- File not found detection
- Network error recovery
- Missing chunk detection
- Graceful disconnection handling

## 🔍 Troubleshooting

### Port Already in Use
If you see "Address already in use":
```powershell
# Find process using port 9876
netstat -ano | findstr :9876

# Kill the process (replace PID with actual process ID)
taskkill /PID <PID> /F
```

### Firewall Issues
If clients can't connect:
- Allow Java through Windows Firewall
- Check antivirus software settings

### Files Not Received
- Check `received_files` directory in the project folder
- Ensure sufficient disk space
- Check file permissions

## 📝 Key Implementation Notes

### Why UDP for File Transfer?

This implementation demonstrates:
- **Custom reliability** on top of UDP
- **Broadcast capability** of UDP
- **Low-latency communication**
- **Manual flow control** and congestion management

### Production Considerations

For production use, consider adding:
- **Encryption** (TLS over UDP / DTLS)
- **Authentication** (client verification)
- **Better flow control** (sliding window protocol)
- **Adaptive timeout** based on network conditions
- **Compression** for file transfers
- **GUI interface** instead of console
- **Database** for message persistence
- **File deduplication**
- **Bandwidth limiting**

## 📄 Commands Reference

| Command | Description | Example |
|---------|-------------|---------|
| `<message>` | Send text message | `Hello everyone!` |
| `/file <path>` | Send a file | `/file test.txt` |
| `/users` | Show online users | `/users` |
| `/help` | Show help menu | `/help` |
| `/quit` | Exit application | `/quit` |

## 🎯 Testing Scenarios

### Test 1: Multiple Clients Chatting
1. Start server
2. Start 3 clients (Alice, Bob, Charlie)
3. Have them exchange messages
4. Verify all receive all messages

### Test 2: File Transfer
1. All clients online
2. Alice sends a file
3. Verify Bob and Charlie receive it
4. Check `received_files` directory

### Test 3: Client Disconnection
1. Start server and 3 clients
2. Close one client (Ctrl+C or /quit)
3. Type `/users` in another client
4. Verify disconnected client removed after 15 seconds

### Test 4: Large File Transfer
1. Create a 5MB file
2. Send it using `/file` command
3. Monitor progress indicators
4. Verify successful receipt

## 🏆 System Capabilities

✅ Supports unlimited number of clients  
✅ Handles concurrent file transfers  
✅ Broadcasts to all connected clients  
✅ Automatic client discovery  
✅ Heartbeat-based connection monitoring  
✅ Reliable file transfer over unreliable UDP  
✅ Progress tracking and reporting  
✅ Clean error handling and recovery  
✅ Thread-safe operations  
✅ Object-oriented design  

## 📞 Support

For issues or questions:
1. Check the Troubleshooting section
2. Verify Java version: `java -version`
3. Ensure all files compiled without errors
4. Check server is running before starting clients

## 🎓 Learning Outcomes

This project demonstrates:
- UDP socket programming in Java
- Multi-threaded network applications
- Custom protocol design
- File I/O and serialization
- Reliable communication over unreliable transport
- Client-server architecture
- Real-time systems design

## 📌 Important Notes

- **Server must run before clients**
- **Default server: localhost:9876**
- **Files saved to: received_files/**
- **Heartbeat interval: 5 seconds**
- **Client timeout: 15 seconds**
- **Max chunk size: 1024 bytes**
- **Max UDP packet: 65507 bytes**

---

**Built with production-level Java practices and clean OOP design.**
