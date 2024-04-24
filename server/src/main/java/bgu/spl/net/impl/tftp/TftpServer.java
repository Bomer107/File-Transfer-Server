package bgu.spl.net.impl.tftp;
import java.io.File;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import bgu.spl.net.srv.Server;

public class TftpServer {

    public static void main(String[] args){

        ConcurrentHashMap<String, Integer> userNames = new ConcurrentHashMap<>();

        String dirPath;
        int port = 7777;

        String currDir = (Paths.get("").toAbsolutePath()).toString();
        int lastIndexOfDirPath = currDir.lastIndexOf("server");
        if(lastIndexOfDirPath != -1)
            dirPath = ((currDir).substring(0, lastIndexOfDirPath)) + "/Files";
        else{
            dirPath = currDir + "/server/Files";
        }
       
        

        File filesDir = new File(dirPath);
        ConcurrentHashMap<String, fileWithLock> filesWithLocks = new ConcurrentHashMap<>();
    
        File[] files = filesDir.listFiles();
        for(File file: files)
            if(file.isFile()){
                fileWithLock fwl = new fileWithLock(new ReentrantReadWriteLock(true), file);
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
