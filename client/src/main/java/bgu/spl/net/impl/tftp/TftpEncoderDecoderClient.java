package bgu.spl.net.impl.tftp;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoderClient implements MessageEncoderDecoder<byte[]>
{
    private SharedState info;
    private byte[] bytes = new byte[1 << 10];
    private int len = 0;
    private boolean readOpcode = false;

    public TftpEncoderDecoderClient(SharedState info) {
        this.info = info;
    }

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (!readOpcode && len < 2) {
            pushByte(nextByte);
            if (len == 2) {
                readOpcode = true;
                if (isCompletePacket()) {
                    return popPacket();
                }
            }
            return null;
        } 
        else {
            pushByte(nextByte);
            if (isCompletePacket()) {
                return popPacket();
            }
        }
        return null;
    }

    private void pushByte(byte nextByte) 
    {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len] = nextByte;
        len++;
    }

    private byte[] popPacket() {
        byte[] packet = Arrays.copyOf(bytes, len);
        len = 0;
        readOpcode = false;
        return packet;
    }

    private boolean isCompletePacket() 
    {
        short opcode = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 0, 2)).order(ByteOrder.BIG_ENDIAN).getShort();
        switch (opcode) {
            case 3: // DATA
                if (len < 4) return false;
                short packetSize = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 2, 4)).order(ByteOrder.BIG_ENDIAN).getShort();
                return len == 4 + 2 + packetSize;
            case 4: // ACK packet
                return len == 4;
            case 5: // ERROR packet
                return len > 4 && bytes[len - 1] == 0;
            case 9: // BCAST
                return len > 3 && bytes[len - 1] == 0;
            default:
                return false;
        }
    }

    @Override
    public byte[] encode(byte[] message) {
        String command = new String(message);
        String[] parts = command.split(" ", 2);
        String commandType = parts[0];
        byte[] encodedMessage;
        switch (commandType) {
            case "LOGRQ":
                encodedMessage = encodeLogrq(parts[1]);
                break;
            case "DELRQ":
                encodedMessage = encodeDelrq(parts[1]);
                break;
            case "RRQ":
                encodedMessage = encodeRrq(parts[1]);
                break;
            case "WRQ":
                encodedMessage = encodeWrq(parts[1]);
                break;
            case "DIRQ":
                encodedMessage = encodeDirq();
                break;
            case "DISC":
                encodedMessage = encodeDisc();
                break;
            default:
                encodedMessage = message;
                break;
        }
        return encodedMessage;
    }

    private byte[] encodeLogrq(String username) {
        if (info.isLoggedIn()) {
            System.out.println("user is already logged in");
            return null;
        }
        info.setSentLoginRequest(true);
        byte[] usernameBytes = username.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(2 + usernameBytes.length + 1); // Opcode (2 bytes), username, zero byte
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 7); // Opcode for LOGRQ
        buffer.put(usernameBytes); // Username in UTF-8
        buffer.put((byte) 0); // Null terminator for the username
        return buffer.array();
    }

    private byte[] encodeDelrq(String filename) {
        info.setDELRQ(true);
        byte[] filenameBytes = filename.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(2 + filenameBytes.length + 1); // Opcode (2 bytes), filename, zero byte
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 8); // Opcode for DELRQ
        buffer.put(filenameBytes); // Filename in UTF-8
        buffer.put((byte) 0); // Null terminator for the filename
        return buffer.array();
    }

    private byte[] encodeRrq(String filename) {
        byte[] filenameBytes = filename.getBytes();
        String FILES_DIRECTORY = info.getFILES_DIRECTORY(); 
        File file = new File(FILES_DIRECTORY + filename);
        if (file.exists()) {
            System.out.println("file already exists");
            return null;
        }
        info.setRRQ(true);
        info.setName(filenameBytes);
        ByteBuffer buffer = ByteBuffer.allocate(2 + filenameBytes.length + 1); // Opcode (2 bytes), filename, zero byte
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 1); // Opcode for RRQ
        buffer.put(filenameBytes); // Filename in UTF-8
        buffer.put((byte) 0); // Null terminator for the filename
        return buffer.array();
    }

    private byte[] encodeWrq(String filename) {
        byte[] filenameBytes = filename.getBytes();
        String FILES_DIRECTORY = info.getFILES_DIRECTORY();
        File file = new File(FILES_DIRECTORY + filename);
        if (!file.exists()) {
            System.out.println("file does not exists");
            return null;
        }
        info.setWRQ(true);
        info.setName(filenameBytes);
        ByteBuffer buffer = ByteBuffer.allocate(2 + filenameBytes.length + 1); // Opcode (2 bytes), filename, zero byte
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 2); // Opcode for WRQ
        buffer.put(filenameBytes); // Filename in UTF-8
        buffer.put((byte) 0); // Null terminator for the filename
        return buffer.array();
    }

    private byte[] encodeDirq() {
        
        info.setDIRQ(true);
        ByteBuffer buffer = ByteBuffer.allocate(2); // Only Opcode
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 6); // Opcode for DIRQ
        return buffer.array();
    }

    private byte[] encodeDisc() {
        if (info.isDISC()) {
            System.out.println("user is already disconected in");
            return null;
        }
        info.setSentDiscRequest(true);
        ByteBuffer buffer = ByteBuffer.allocate(2); // Only Opcode
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 10); // Opcode for DISC
        return buffer.array();
    }
}
