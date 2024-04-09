package bgu.spl.net.srv;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.impl.tftp.TftpProtocol;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

public abstract class BaseServer<T> implements Server<T> {

    private final int port;
    private final Supplier<BidiMessagingProtocol<T>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;
    private int countId;
    private ConnectionUsers<byte[]> connectionUsers;

    public BaseServer(
            int port,
            Supplier<BidiMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
        this.countId = 0;
        this.connectionUsers = new ConnectionUsers<byte[]>();
    }

    @Override
    public void serve() 
    {
        try (ServerSocket serverSock = new ServerSocket(port)) 
        {
			System.out.println("Server started");
            this.sock = serverSock; //just to be able to close
            
            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();
                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(clientSock,encdecFactory.get(),protocolFactory.get());
                this.connectionUsers.connect(countId, (ConnectionHandler) handler);
                ((TftpProtocol) handler.getProtocol()).start(countId, connectionUsers);
                countId++;
                execute(handler);
            }
        } catch (IOException ex) {}
        finally {
            try {
                close();
            } catch (IOException e) {System.err.println("Exception occurred while closing the server: " + e.getMessage());}
        }
        System.out.println("server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null) {
			sock.close();
        }
        connectionUsers.closeAll();
    }
    protected abstract void execute(BlockingConnectionHandler<T>  handler);
}