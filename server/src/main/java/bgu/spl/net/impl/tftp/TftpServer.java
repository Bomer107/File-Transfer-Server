package bgu.spl.net.impl.tftp;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Server;

public class TftpServer {

    public static void main(String[] args){

        ConcurrentHashMap<String, Integer> userNames = new ConcurrentHashMap<>();

        String filesPath;

        if (args.length > 0){ //wants the server to work with a different directory
            try {
                if(!Files.isDirectory(Paths.get(args[0]))){
                    throw new InvalidPathException(null, null);
                }
                filesPath = args[0];
            } catch (InvalidPathException | SecurityException exception) {
                System.out.println("the argument you entered is not a valid path to a directory");
                return;
            }
        }
        else { //works with the defult directory- Flies
            Path currDirPath = Paths.get("").toAbsolutePath();
            String currDirString = currDirPath.toString();
            int lastIndexOfDirPath = currDirString.lastIndexOf("server");
            filesPath = ((currDirString).substring(0, lastIndexOfDirPath)) + "/Flies";
        }

        File filesDir = new File(filesPath);

        ConcurrentHashMap<String, ReentrantReadWriteLock> fileWithLocks = new ConcurrentHashMap<>();
        File[] files = filesDir.listFiles();
        for(File file: files){
            if(file.isFile())
                fileWithLocks.put(file.getName(), new ReentrantReadWriteLock(true));
        }

        Server.threadPerClient(
                7777, //port
                () -> new TftpProtocol(userNames, fileWithLocks), //protocol factory
                TftpEncoderDecoder::new //message encoder decoder factory
                ).serve();
    }
}
