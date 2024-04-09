package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import bgu.spl.net.api.MessagingProtocol;

public class TftpProtocolClient implements MessagingProtocol<byte[]> 
{
    public TftpProtocolClient(SharedState info) {
        this.info = info;
    }

    private volatile boolean shouldTerminate = false;
    private int dataSize = 512;
    private static final String FILES_DIRECTORY = "";

    private ReadDataFileClient readData = null;
    private WriteDataFileClient writeData = null;
    private SharedState info;

    @Override
    public byte[] process(byte[] msg) {
        ByteBuffer buffer = ByteBuffer.wrap(msg).order(ByteOrder.BIG_ENDIAN);
        short opcode = buffer.getShort();
        byte[] response = null;
        switch (opcode) {
            case 3: // DATA Packet
                response = handleDataPacket(msg); // RRQ DIRQ
                break;
            case 4: // ACK Packet
                response = handleAckPacket(msg);
                break;
            case 5: // ERROR Packet
                handleErrorPacket(msg);
                break;
            case 9: // BCAST Packet
                handleBcastPacket(msg);
                break;
            default:
                // For other opcodes, assume the server's handling is as per protocol specs
                break;
        }
        return response; // This assumes process method doesn't need to return a response immediately.
    }

    private byte[] handleDataPacket(byte[] msg) {
        ByteBuffer buffer = ByteBuffer.wrap(msg).order(ByteOrder.BIG_ENDIAN);
        short opcode = buffer.getShort();
        short packetSize = buffer.getShort(); // Now directly reads packet size
        short blockNumber = buffer.getShort(); // Now directly reads block number
        byte[] data = new byte[packetSize]; 
        buffer.get(data, 0, packetSize);
        if (blockNumber == 1) {
            readData = new ReadDataFileClient(info.getName()); // null if it is DIRQ.. is it problem?
        }
        if (readData != null) {
            readData.addPacket(data);
        }
        if (info.isRRQ()) {   
            if (data.length < dataSize) {
                
                byte[] newFile = readData.concatenate();
                try { 
                    Path filePath = Paths.get(FILES_DIRECTORY + new String(info.getName(), StandardCharsets.UTF_8));
                    Files.write(filePath, newFile);
                    System.out.println("RRQ " + new String(info.getName(), StandardCharsets.UTF_8) + " complete");
                    info.wake();
                    info.setRRQ(false);
                    info.setName(null);
                } catch (IOException e) {
                    info.wake();
                    info.setRRQ(false);
                    info.setName(null);
                    readData = null;
                } 
                readData = null;
            }
        } 
        if(info.isDIRQ())
            if (data.length < dataSize) { 
                byte[] listOfFiles = readData.concatenate();
                printDIRQ(listOfFiles);
                info.wake();
                info.setDIRQ(false);
                readData = null; 
            }
        return (constructAckPacket(blockNumber)); 
    }

    private void printDIRQ(byte[] listOfFiles) {
        String combinedFiles = new String(listOfFiles, StandardCharsets.UTF_8);
        String[] fileNames = combinedFiles.split("\0");
        for (String fileName : fileNames) {
            System.out.println(fileName);
        }
    }
    
    private byte[] constructAckPacket(short blockNumber) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 4);
        buffer.putShort(blockNumber);
        return buffer.array();
    }

    private byte[] handleAckPacket(byte[] msg) {
        short blockNumber = ByteBuffer.wrap(msg, 2, 2).getShort();
        System.out.println("ACK " + blockNumber);
        if(info.isSentLoginRequest()){
            info.wake();
            info.setLoggedIn(true);
            info.setSentLoginRequest(false);
        }
        if(info.isDELRQ()){ 
            info.wake();
            info.setDELRQ(false);
        }

        if(info.isSentDiscRequest()){
            shouldTerminate = true;
            info.wake();
            info.setDISC(true);
            info.setSentDiscRequest(false);
        }
        byte[] response = null;
        if(info.isWRQ()) {
            if(blockNumber == 0) {
                File file = new File(FILES_DIRECTORY + new String(info.getName(), StandardCharsets.UTF_8));
                writeData = new WriteDataFileClient(file);
            }
            response = communicator();            
        }
        return response;
    }

    private byte[] communicator() {
        if (writeData != null && !writeData.getIsOver()) 
        {
            byte[] dataToSend = writeData.readAndSend();
            byte[] dataPacket = constructDataPacket((short) writeData.getBlockNumber(), dataToSend);
            if (writeData.getIsOver()) 
            {
                System.out.println("WRQ " + new String(info.getName(), StandardCharsets.UTF_8) + " complete");
                info.wake();
                info.setWRQ(false);
                info.setName(null);
                writeData.close(); 
                writeData = null;
            }
            return dataPacket;
        }
        return null;
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

    private void handleErrorPacket(byte[] msg) {
        if(info.isSentLoginRequest()){
            info.wake();
            info.setSentLoginRequest(false);
        }
        if(info.isDELRQ()){ 
            info.wake();
            info.setDELRQ(false);
        }
        if (info.isRRQ()) {
            info.wake();
            info.setRRQ(false);
            info.setName(null);
            readData = null;
        }
        if (info.isWRQ()) {
            info.wake();
            info.setWRQ(false);
            info.setName(null);
            if(writeData != null) {
                writeData.close();
                writeData = null;
            }
        }
        if(info.isDIRQ()){
            info.wake();
            info.setDIRQ(false);
        }
        if(info.isSentDiscRequest()){
            info.wake();
            info.setSentDiscRequest(false);
        }
        short errorCode = ByteBuffer.wrap(msg, 2, 2).getShort();
        String errorMessage = new String(msg, 4, msg.length - 4, StandardCharsets.UTF_8);
        System.out.println("Error " + errorCode + ": " + errorMessage);        
    }

    private void handleBcastPacket(byte[] msg) {
        byte delAdd = msg[2];
        String filename = new String(msg, 3, msg.length - 4, StandardCharsets.UTF_8);
        String action = (delAdd == 1) ? "add" : "del";
        System.out.println("BCAST " + action + " " + filename);
    }

    @Override
    public synchronized boolean shouldTerminate() {
        return this.shouldTerminate;
    }

    public synchronized void terminate() {
        this.shouldTerminate = true;
    }
}

