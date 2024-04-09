package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class TftpClient {


    public static void main(String[] args) throws IOException {
        SharedState info = new SharedState();
        try (Socket socket = new Socket(args[0], 7777); // the try-with-resources resource must either be a variable declaration or an expression denoting a reference to a final or effectively final variable(compiler.err.try.with.resources.expr.needs.var)
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            
            TftpEncoderDecoderClient encoderDecoder = new TftpEncoderDecoderClient(info);
            TftpProtocolClient protocol = new TftpProtocolClient(info);

            ListeningThread listeningThread = new ListeningThread(in,out,protocol,encoderDecoder);
            listeningThread.start();
            
            KeyboardThread keyboardThread = new KeyboardThread(out, encoderDecoder,protocol,info);
            keyboardThread.start();

            listeningThread.join();
            keyboardThread.join();
        } catch (IOException | InterruptedException e) {}
        System.out.println("client closed!!!");
    }
}

class KeyboardThread extends Thread {
    private DataOutputStream out;
    private TftpEncoderDecoderClient encoderDecoder;
    private TftpProtocolClient protocol;
    private SharedState info;

    public KeyboardThread(DataOutputStream out, TftpEncoderDecoderClient encoderDecoder, TftpProtocolClient protocol, SharedState info) {
        this.out = out;
        this.encoderDecoder = encoderDecoder;
        this.protocol = protocol;
        this.info = info;
    }

    @Override
    public void run() {
        try (BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (!protocol.shouldTerminate() && (line = userInput.readLine()) != null) { 
                byte[] encodedMessage = encoderDecoder.encode(line.getBytes());
                if (encodedMessage != null) {
                    out.write(encodedMessage);
                    out.flush();
                    try {
                        info.waitToBeWaken(); 
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ListeningThread extends Thread {
    private DataInputStream in;
    private DataOutputStream out;
    private TftpProtocolClient protocol;
    private TftpEncoderDecoderClient encoderDecoder;

    public ListeningThread(DataInputStream in, DataOutputStream out, TftpProtocolClient protocol, TftpEncoderDecoderClient encoderDecoder) {
        this.in = in;
        this.out = out;
        this.protocol = protocol;
        this.encoderDecoder = encoderDecoder;
    }

    @Override
    public void run() {
        try {
            while (!protocol.shouldTerminate()) {
                byte nextByte = in.readByte(); 
                byte[] message = encoderDecoder.decodeNextByte(nextByte);
                if (message != null) { 
                    byte[] response = protocol.process(message);
                    if (response != null) {
                        out.write(response); 
                        out.flush();
                    }
                }
            }
        } catch (IOException ex) {}
    }
}