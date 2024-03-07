package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T>{

    ConcurrentHashMap<Integer, ConnectionHandler<T>> idConnections = new ConcurrentHashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        idConnections.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> personToSend = idConnections.get(connectionId);
        personToSend.send(msg);
        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'disconnect'");
    }
    
}
