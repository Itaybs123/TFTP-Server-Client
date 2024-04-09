package bgu.spl.net.srv;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionUsers<T> implements Connections<T> 
{
    private final ConcurrentHashMap<Integer,ConnectionHandler<T>> connections;

    ConnectionUsers() {
        connections = new ConcurrentHashMap<>();
    }

    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connections.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg) { 
        ConnectionHandler<T> handler = connections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    public void disconnect(int connectionId) {
        connections.remove(connectionId);
    }  

    public void closeAll() {
        connections.values().forEach(handler -> {
            try {
                handler.close();
            } catch (Exception e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        });
    }
}
