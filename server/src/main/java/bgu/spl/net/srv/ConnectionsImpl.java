package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T>{

    ConcurrentHashMap<Integer, ConnectionHandler<T>> iDconnections = new ConcurrentHashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
    }

    @Override
    public boolean send(int connectionId, T msg) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'send'");
    }

    @Override
    public void disconnect(int connectionId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'disconnect'");
    }
    
}
