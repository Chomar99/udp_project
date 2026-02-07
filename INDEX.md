# UDP MESSAGING & FILE SHARING SYSTEM
## Complete Project Index

---

## 🎯 START HERE

**First Time Users:** Double-click → [quickstart.bat](quickstart.bat)

**Read Documentation:** [QUICKSTART.txt](QUICKSTART.txt) or [README.md](README.md)

---

## 📁 PROJECT FILES

### 🚀 Quick Start Scripts (Windows)
- **quickstart.bat** - One-click start (server + 3 clients in separate windows)
- **compile.bat** - Compile all Java files
- **run-server.bat** - Start the UDP server
- **run-client.bat** - Start a client (prompts for name)

### ☕ Java Source Code (src/ directory)
1. **PacketProtocol.java** (258 lines)
   - Message types and packet structure
   - Serialization/deserialization
   - Packet creation helpers

2. **FileManager.java** (237 lines)
   - File chunking (1024-byte segments)
   - File reassembly
   - Progress tracking
   - Transfer state management

3. **Server.java** (350 lines)
   - Central UDP server (port 9876)
   - Client management
   - Message broadcasting
   - File relay
   - Heartbeat monitoring

4. **Client.java** (464 lines)
   - UDP client with console UI
   - Message send/receive
   - File transfer
   - Heartbeat mechanism
   - Multi-threaded operations

### 📚 Documentation
- **README.md** (685 lines) - Complete user and developer guide
- **QUICKSTART.txt** (324 lines) - Quick reference and command guide
- **PROJECT_SUMMARY.md** (500+ lines) - Implementation summary and statistics
- **ARCHITECTURE_DIAGRAM.txt** (450+ lines) - Visual architecture diagrams
- **INDEX.md** (this file) - Project navigation

### 🧪 Test Files
- **test-file.txt** - Sample file for testing file transfer

---

## 🎮 HOW TO USE

### Option 1: Fastest (Recommended)
```
Double-click: quickstart.bat
```
This automatically starts server + 3 clients (Alice, Bob, Charlie)

### Option 2: Step by Step
```
1. Double-click: compile.bat
2. Double-click: run-server.bat
3. Double-click: run-client.bat (open 3 times for 3 clients)
```

### Option 3: Command Line
```powershell
# Compile
javac src\*.java

# Terminal 1
java -cp src Server

# Terminal 2, 3, 4
java -cp src Client Alice
java -cp src Client Bob
java -cp src Client Charlie
```

---

## 💬 CLIENT COMMANDS

| Command | Description |
|---------|-------------|
| `<message>` | Send text message (just type and Enter) |
| `/file <path>` | Send a file to all clients |
| `/users` | Show online users |
| `/help` | Show help menu |
| `/quit` | Exit client |

### Examples:
```
Alice > Hello everyone!              # Send message
Alice > /file test-file.txt          # Send file
Alice > /file C:\photos\image.jpg    # Send file with full path
Alice > /users                       # See who's online
```

---

## 📖 DOCUMENTATION GUIDE

### For First-Time Users:
1. Start with **[QUICKSTART.txt](QUICKSTART.txt)**
2. Try the quickstart.bat script
3. Experiment with commands

### For Detailed Understanding:
1. Read **[README.md](README.md)** for complete documentation
2. Review **[ARCHITECTURE_DIAGRAM.txt](ARCHITECTURE_DIAGRAM.txt)** for system design
3. Check **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)** for implementation details

### For Developers:
1. Read source code comments in src/*.java files
2. Study **[ARCHITECTURE_DIAGRAM.txt](ARCHITECTURE_DIAGRAM.txt)**
3. Review packet flow diagrams
4. Check **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)** for technical specs

---

## 🏗️ ARCHITECTURE OVERVIEW

```
Server (Port 9876)
    ├── Client Management
    ├── Message Broadcasting
    ├── File Relay
    └── Heartbeat Monitoring

Client (Random Port)
    ├── UI Thread (user input)
    ├── Receiver Thread (incoming packets)
    ├── Heartbeat Thread (keep-alive)
    └── File Transfer Threads (file sending)

Communication: 100% UDP
Reliability: ACK + Retransmission
File Chunking: 1024 bytes max per chunk
```

---

## ✨ KEY FEATURES

✅ Real-time messaging  
✅ File sharing (any type)  
✅ Multiple concurrent clients  
✅ Heartbeat monitoring  
✅ Auto-disconnect detection  
✅ Progress tracking  
✅ Loss-tolerant UDP  
✅ Thread-safe operations  
✅ Production-level code  

---

## 🔧 TECHNICAL SPECS

- **Language:** Java 8+
- **Protocol:** UDP only (DatagramSocket/DatagramPacket)
- **Server Port:** 9876
- **Client Ports:** Random available
- **Heartbeat:** Every 5 seconds
- **Timeout:** 15 seconds
- **Max Chunk:** 1024 bytes
- **Max Packet:** 65,507 bytes

---

## 📊 FILE LOCATIONS

### Source Files:
- `src/*.java` - Java source code
- `src/*.class` - Compiled class files (after compilation)

### Received Files:
- `received_files/` - Auto-created directory for received files
- Files sent by other clients are saved here

### Test Files:
- `test-file.txt` - Sample file for testing

---

## 🎯 TESTING CHECKLIST

- [ ] Compile all files: `compile.bat`
- [ ] Start server: `run-server.bat`
- [ ] Start 3 clients: `run-client.bat` (3 times)
- [ ] Send messages between clients
- [ ] Send test-file.txt from one client
- [ ] Verify file received on other clients
- [ ] Check `/users` command
- [ ] Test client disconnect (close one client)
- [ ] Verify timeout detection after 15 seconds

---

## 🐛 TROUBLESHOOTING

### "javac is not recognized"
**Solution:** Install Java JDK and add to PATH

### "Port already in use"
**Solution:** Close previous server or restart

### "File not found"
**Solution:** Use full path or place file in project directory

### Clients can't connect
**Solution:**
1. Ensure server runs first
2. Check firewall settings
3. Allow Java through Windows Firewall

---

## 📞 QUICK HELP

| Need | Go To |
|------|-------|
| Quick start | [QUICKSTART.txt](QUICKSTART.txt) |
| Commands | [QUICKSTART.txt](QUICKSTART.txt) section "Commands" |
| Full guide | [README.md](README.md) |
| Architecture | [ARCHITECTURE_DIAGRAM.txt](ARCHITECTURE_DIAGRAM.txt) |
| Implementation | [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) |
| Troubleshooting | [README.md](README.md) section "Troubleshooting" |

---

## 📈 PROJECT STATISTICS

- **Total Files:** 13
- **Java Source:** 4 files, ~1,309 lines
- **Documentation:** 4 files, ~1,959 lines
- **Scripts:** 4 batch files
- **Total Project:** ~3,268 lines

---

## 🎓 LEARNING OUTCOMES

This project demonstrates:
- ✅ UDP socket programming
- ✅ Multi-threading
- ✅ Network protocol design
- ✅ File I/O and serialization
- ✅ Reliability over UDP
- ✅ Client-server architecture
- ✅ Real-time systems
- ✅ Clean OOP design

---

## 🏆 PROJECT STATUS

**✅ COMPLETE AND FULLY FUNCTIONAL**

All requirements implemented:
- ✅ 1 central UDP server
- ✅ 3+ UDP clients (unlimited supported)
- ✅ Message broadcasting
- ✅ File sharing with segmentation
- ✅ Heartbeat mechanism
- ✅ Loss-tolerant UDP
- ✅ Progress tracking
- ✅ Console UI
- ✅ Production-level quality
- ✅ Complete documentation

---

## 🚀 NEXT STEPS

1. **First Time:** Run `quickstart.bat`
2. **Explore:** Try all commands (/file, /users, /help)
3. **Test:** Send messages and files
4. **Learn:** Read the documentation
5. **Extend:** Add your own features!

---

## 📝 FILE MANIFEST

```
bisbis/
├── src/
│   ├── PacketProtocol.java          ✅ Protocol definitions
│   ├── FileManager.java              ✅ File operations
│   ├── Server.java                   ✅ UDP server
│   ├── Client.java                   ✅ UDP client
│   └── *.class                       ✅ Compiled classes
├── quickstart.bat                    ✅ Auto-start all
├── compile.bat                       ✅ Compile script
├── run-server.bat                    ✅ Server launcher
├── run-client.bat                    ✅ Client launcher
├── test-file.txt                     ✅ Test file
├── README.md                         ✅ Full documentation
├── QUICKSTART.txt                    ✅ Quick guide
├── PROJECT_SUMMARY.md                ✅ Implementation summary
├── ARCHITECTURE_DIAGRAM.txt          ✅ Visual diagrams
└── INDEX.md                          ✅ This file
```

---

**Ready to start? Double-click `quickstart.bat`**

---

*Built with Java, UDP, and clean object-oriented design*  
*Production-level quality • Fully documented • Ready to use*
