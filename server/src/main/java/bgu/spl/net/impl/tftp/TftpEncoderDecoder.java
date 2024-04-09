package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> 
{
    private byte[] bytes = new byte[1 << 10]; // check if 512
    private int len = 0;
    private boolean readOpcode = false;

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

    private void pushByte(byte nextByte) {
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

    private boolean isCompletePacket() {
        short opcode = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 0, 2)).order(ByteOrder.BIG_ENDIAN).getShort();
        switch (opcode) {
            case 1: // RRQ
            case 2: // WRQ
            case 7: // LOGRQ
            case 8: // DELRQ
                return len > 2 && bytes[len - 1] == 0;
            case 3: // DATA
                if (len < 4) return false;
                short packetSize = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 2, 4)).order(ByteOrder.BIG_ENDIAN).getShort();
                return len == 4 + 2 + packetSize;
            case 4: // ACK packet
                return len == 4;
            case 5: // ERROR packet
                return len > 4 && bytes[len - 1] == 0;
            case 6: // DIRQ
            case 10: // DISC
                return len == 2;
            case 9: // BCAST
                return len > 3 && bytes[len - 1] == 0;
            default:
                return false;
        }
    }
    
    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

}