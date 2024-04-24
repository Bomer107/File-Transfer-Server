package bgu.spl.net.impl.tftp;

import java.io.File;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class fileWithLock {

    private final ReentrantReadWriteLock RWLock;
    private final File file;
    private volatile boolean finished = false;

    fileWithLock(ReentrantReadWriteLock RWLock, File file){
        this.RWLock = RWLock;
        this.file = file;
    }

    public ReentrantReadWriteLock getLock(){
        return RWLock;
    }

    public File getFile(){
        return file;
    }

    public void readLock(){
        RWLock.readLock().lock();
    }

    public void readUnlock(){
        RWLock.readLock().unlock();
    }

    public void writeLock(){
        RWLock.writeLock().lock();
    }

    public void writeUnlock(){
        RWLock.writeLock().unlock();
    }

    public void finished(){
        finished = true;
    }

    public boolean isFinished(){
        return finished;
    }
}
