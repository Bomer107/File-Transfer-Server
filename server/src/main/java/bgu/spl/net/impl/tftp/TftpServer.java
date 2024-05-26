package bgu.spl.net.impl.tftp;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import bgu.spl.net.srv.Server;

public class TftpServer {

    public static void main(String[] args){

        ConcurrentHashMap<String, Integer> userNames = new ConcurrentHashMap<>();

        String dirPath;
        int port = Integer.parseInt(args[0]);

        String currDir = (Paths.get("").toAbsolutePath()).toString();
        dirPath = currDir + "/Files";
       
        File filesDir = new File(dirPath);
        ConcurrentHashMap<String, FileWithLock> filesWithLocks = new ConcurrentHashMap<>();
    
        File[] files = filesDir.listFiles();
        for(File file: files)
            if(file.isFile()){
                FileWithLock fwl = new FileWithLock(new ReentrantReadWriteLock(true), file);
                fwl.finished();
                filesWithLocks.put(file.getName(), fwl);
            }
            
        Server.threadPerClient(
                port, //7777
                () -> new TftpProtocol(userNames, filesWithLocks, filesDir.getAbsolutePath()), //protocol factory
                TftpEncoderDecoder::new //message encoder decoder factory
                ).serve();
    }
}
