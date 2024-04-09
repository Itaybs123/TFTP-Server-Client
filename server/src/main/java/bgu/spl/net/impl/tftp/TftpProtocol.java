package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

// import java.util.Iterator;
// import java.util.concurrent.ConcurrentMap;
// import java.io.FileInputStream;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  
{
    private boolean shouldTerminate = false;
    private int connectionId;
    private byte[] username = null;
    private Connections<byte[]> connections;
    private ReadDataFile readData;
    private WriteDataFile writeData;
    private boolean read = false;
    private int dataSize = 512;
    private boolean isLoggedIn = false;
    private static final String FILES_DIRECTORY = "Flies/";
    private final FileLockManager fileLockManager = new FileLockManager();
    private String filePathForRRQ;
    private String filePathForDELRQ;

    static class Holder 
    {
        static ConcurrentHashMap<byte[], Integer> login_username = new ConcurrentHashMap<>();
        static ConcurrentHashMap<Integer, byte[]> login_id = new ConcurrentHashMap<>();
    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message){
        short opcode = ByteBuffer.wrap(message, 0, 2).order(ByteOrder.BIG_ENDIAN).getShort();
        if (!isLoggedIn && opcode != 7) {
            String errorMessage = "User not logged in - operation requires login";
            byte[] errorPacket = constructErrorPacket((short) 6, errorMessage);
            connections.send(connectionId, errorPacket);
            return; 
        }
        switch (opcode) {
            case 1: // RRQ
                processRRQ(message);
                break;
            case 2: // WRQ
                processWRQ(message);
                break;
            case 3: // DATA
                processDATA(message);
                break;
            case 4: // ACK
                processACK(message);
                break;
            case 5: // ERROR
                processERROR(message);
                break;
            case 6: // DIRQ
                processDIRQ();
                break;
            case 7: // LOGRQ
                processLOGRQ(message);
                break;
            case 8: // DELRQ
                processDELRQ(message);
                break;
            case 9: // BCAST
                processBCAST(message[2],extractName(message));
                break;
            case 10: // DISC
                processDISC();
                break;
            default: // Unknown or illegal opcode
                String errorMessage = "Illegal TFTP operation - Unknown Opcode: " + opcode;
                byte[] errorPacket = constructErrorPacket((short) 4, errorMessage);
                connections.send(connectionId, errorPacket);
                break;
        }
    }

    private void processLOGRQ(byte[] message) {
        byte[] response;
        if (isLoggedIn) {
            response = constructErrorPacket((short) 7, "User already logged in"); // check if 7?
        } 
        else {
            username = extractName(message);
            boolean userExists = Holder.login_username.keySet().stream().anyMatch(key -> Arrays.equals(key, username));
            if (userExists) {
                response = constructErrorPacket((short) 7, "Username already connected");
            } 
            else {
                Holder.login_username.put(username, connectionId);
                Holder.login_id.put(connectionId, username);
                isLoggedIn = true;
                response = constructAckPacket((short) 0);
            }
        }
        connections.send(connectionId, response);
    }

    private void processDELRQ(byte[] message) {
        byte[] filenameBytes = extractName(message); 
        byte[] response;
        filePathForDELRQ = FILES_DIRECTORY + new String(filenameBytes, StandardCharsets.UTF_8);
        fileLockManager.lockWrite(filePathForDELRQ);
        try {
            File file = new File(filePathForDELRQ);
            if (file.exists()) {
                Files.delete(Paths.get(file.getAbsolutePath()));
                response = constructAckPacket((short) 0);
                connections.send(connectionId, response);
                processBCAST((byte)0, filenameBytes);
            } else {
                String errorMessage = "File not found";
                response = constructErrorPacket((short) 1, errorMessage);
                connections.send(connectionId, response);
            }
        } catch (IOException e) {
            String errorMessage = "Access violation - File cannot be written, read or deleted.";
            response = constructErrorPacket((short) 2, errorMessage);
            connections.send(connectionId, response);
        } finally {
            fileLockManager.unlockWrite(filePathForDELRQ);
        }     
    } 

    private void processRRQ(byte[] message) {
        byte[] filenameBytes = extractName(message);
        filePathForRRQ = FILES_DIRECTORY + new String(filenameBytes, StandardCharsets.UTF_8);
        fileLockManager.lockRead(filePathForRRQ);
        File file = new File(filePathForRRQ);
        if (file.exists()) {
            read = true;
            readData = new ReadDataFile(file);
            communicator(); 
        } 
        else {
            String errorMessage = "File not found";
            byte[] response = constructErrorPacket((short) 1, errorMessage);
            connections.send(connectionId, response);
            fileLockManager.unlockRead(filePathForRRQ);
            filePathForRRQ = null;
        }
    }

    private void processWRQ(byte[] message) {
        byte[] filenameBytes = extractName(message); 
        byte[] response;
        File file = new File(FILES_DIRECTORY + new String(filenameBytes, StandardCharsets.UTF_8));
        if (file.exists()) {
            response = constructErrorPacket((short) 5, "File already exists");
        } 
        else {
            // לתפוס מפתח עם השם הזה
            response = constructAckPacket((short) 0);
            writeData = new WriteDataFile(filenameBytes);
        }
        connections.send(connectionId, response);
    }

    private void processDIRQ() {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            File folder = new File(FILES_DIRECTORY);
            File[] listOfFiles = folder.listFiles();
            if (listOfFiles != null) {
                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        byteStream.write(listOfFiles[i].getName().getBytes(StandardCharsets.UTF_8));
                        if (i < listOfFiles.length - 1) {
                            byteStream.write(0);
                        }
                    }
                }
            }
            byte[] allFilesBytes = byteStream.toByteArray();
            int totalLength = allFilesBytes.length;
            int start = 0;
            short blockNumber = 1;
            while (start < totalLength) {
                int end = Math.min(start + dataSize, totalLength);
                byte[] dataChunk = Arrays.copyOfRange(allFilesBytes, start, end);
                byte[] dataPacket = constructDataPacket(blockNumber, dataChunk);
                connections.send(connectionId, dataPacket);
                start = end;
                blockNumber++;
            }
        } catch (Exception e) {
            byte[] errorPacket = constructErrorPacket((short) 2, "Access violation or directory issue.");
            connections.send(connectionId, errorPacket); }
    }

    private void processDATA(byte[] message) {
        int blockNumber = ((message[4] & 0xff) << 8) | (message[5] & 0xff);
        byte[] response;
        if (writeData == null || blockNumber != writeData.getBlockNumber()) {        
            response = constructErrorPacket((short) 2," File cannot be written, read or deleted");
            connections.send(connectionId, response);
        }
        else {
            response = constructAckPacket((short) writeData.getBlockNumber());
            byte[] data = extractData(message);
            writeData.addPacket(data); 
            connections.send(connectionId, response);
            if(data.length < dataSize) {
                byte[] newFile = writeData.concatenate();
                byte[] nameOfFile = writeData.getName();
                writeData = null;
                try { 
                    Path filePath = Paths.get(FILES_DIRECTORY + new String(nameOfFile, StandardCharsets.UTF_8));
                    Files.write(filePath, newFile);
                    processBCAST((byte)1, nameOfFile);
                } catch (IOException e) {
                }
            }
        }
    }
    
    private void processBCAST(byte deletedAdded, byte[] filenameBytes) {
        byte[] bcastPacket = constructBCASTPacket(deletedAdded, filenameBytes);
        Holder.login_id.forEach((id, user) -> {
            connections.send(id, bcastPacket);
        });
    }

    private void processDISC() {
        if (username != null) {
            terminate();
        }
        byte[] ackPacket = constructAckPacket((short) 0);
        connections.send(connectionId, ackPacket);
        connections.disconnect(connectionId);
    }

    private void processERROR(byte[] message) {
        short errorCode = ByteBuffer.wrap(message, 2, 2).order(ByteOrder.BIG_ENDIAN).getShort();
        String errorMessage = new String(message, 4, message.length - 5, StandardCharsets.UTF_8);
        System.out.println("Received ERROR packet: Code=" + errorCode + ", Message=" + errorMessage);
    }

    private void processACK(byte[] message) {
        if (read)
        {
            short blockNumber = ByteBuffer.wrap(message, 2, 2).order(ByteOrder.BIG_ENDIAN).getShort(); // ACK's block number starts at 2nd byte
            if (readData != null && readData.getBlockNumber() == blockNumber) 
            {
                communicator();
            }
        }
    }
    
    //----------------------------------------------------------------------------------------------------------

    private void communicator() {
        if (readData != null && !readData.getIsOver()) {
            byte[] dataToSend = readData.readAndSend();
            byte[] dataPacket = constructDataPacket((short) readData.getBlockNumber(), dataToSend);
            connections.send(connectionId, dataPacket);
            if (readData.getIsOver()) {
                read = false;
                readData.close();
                readData = null;
                fileLockManager.unlockRead(filePathForRRQ);
                filePathForRRQ = null;
            }
        }
    }
            
    private byte[] constructDataPacket(short blockNumber, byte[] dataBlock) {
        ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + 2 + dataBlock.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 3); // Opcode for DATA is 3
        buffer.putShort((short) dataBlock.length); // Packet size: the length of the data block
        buffer.putShort(blockNumber); // Block number
        buffer.put(dataBlock); // Data block
        return buffer.array();
    }
    
    private byte[] constructBCASTPacket(byte deletedAdded, byte[] filenameBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(2 + 1 + filenameBytes.length + 1);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 9); // Opcode for BCAST is 9
        buffer.put(deletedAdded); // Deleted/Added indicator
        buffer.put(filenameBytes); // Filename
        buffer.put((byte) 0); // Null terminator for the filename
        return buffer.array();
    }
    
    private byte[] extractData(byte[] message) {
        byte[] data = new byte[message.length - 6]; 
        System.arraycopy(message, 6, data, 0, data.length);
        return data;
    }

    private byte[] extractName(byte[] message) {
        byte[] username = new byte[message.length - 3]; 
        System.arraycopy(message, 2, username, 0, username.length);
        return username;
    }

    private byte[] constructErrorPacket(short errorCode, String errorMessage) {
        byte[] errorMsgBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + errorMsgBytes.length + 1); // Opcode (2 bytes), ErrorCode (2 bytes), ErrMsg (dynamic), 0 byte
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 5); // Opcode for ERROR is 5
        buffer.putShort(errorCode); // Error Code
        buffer.put(errorMsgBytes); // Error Message
        buffer.put((byte) 0); // Null terminator for the error message
        return buffer.array();
    }

    private byte[] constructAckPacket(short blockNumber) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 4); // Opcode for ACK is 4
        buffer.putShort(blockNumber);
        return buffer.array();
    }
    
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public void terminate() {
        shouldTerminate = true;
        Holder.login_username.remove(username);
        Holder.login_id.remove(connectionId);
    }
}
