package bgu.spl.net.impl.tftp;

import java.util.ArrayList;
import java.util.List;

public class WriteDataFile 
{
    private int blockNumber;
    private byte[] name;
    private List<byte[]> dataList;
    private int size = 0;

    public WriteDataFile(byte[] name) {
        this.name = name;
        this.blockNumber = 1;
        dataList = new ArrayList<>();
    }

    public void addPacket(byte[] dataPacket) {
        dataList.add(dataPacket);
        size += dataPacket.length;
        blockNumber ++;
    }

    public byte[] concatenate() {
        byte[] result = new byte[size];
        int currentPos = 0;
        for (byte[] array : dataList){
            for (byte b : array) {
                result[currentPos] = b;
                currentPos++;
            }
        }
        return result;
    }

    public byte[] getName() {
        return name;
    }

    public int getBlockNumber() {
        return blockNumber;
    }
}