package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class WriteDataFileClient 
{
    private int blockNumber;
    private FileInputStream fileInputStream; 
    private int dataSize = 512;
    private boolean over;

    public WriteDataFileClient(File file) {
        try {
            this.fileInputStream = new FileInputStream(file);
        } catch (IOException e) {}
        blockNumber = 0;
        over = false;
    }

    public byte[] readAndSend() {
        byte[] packet = new byte[dataSize];
        try {
            int bytesRead = fileInputStream.read(packet);
            if (bytesRead == -1) { 
                over = true;
                return new byte[0];
            }
            else if(bytesRead < dataSize){
                over = true;
                byte[] finalPacket = new byte[bytesRead];
                System.arraycopy(packet, 0, finalPacket, 0, bytesRead);
                packet = finalPacket;
            }
        } catch (IOException e) {
            over = true;
            return new byte[0];
        }
        blockNumber++;
        return packet;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public boolean getIsOver() {
        return over;
    }

    public void close() {
        try {
            fileInputStream.close();
        } catch (IOException e) {}
    }
}