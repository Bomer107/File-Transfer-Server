package bgu.spl.net.impl.tftp;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    
    private Connections<byte[]> connections;
    private final Map<String, Integer> userNames;
    private int connectionId;

    private final Map<String, ReentrantReadWriteLock> files;
    private final String dirPath;
    private String userName = null;
    private int lastCommand = 0;
    private int ackNumber = 0;
    private List<byte[]> fileAsPackets = new LinkedList<>();


    public TftpProtocol(Map<String, Integer> userNames, Map<String, ReentrantReadWriteLock> files, String dirPath){
        this.userNames = userNames;
        this.files = files;
        this.dirPath = dirPath;
    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connections = connections;
        this.connectionId = connectionId;
    }

    @Override
    public void process(byte[] message) {
        if(message.length == 0){
            sendError(4);
            return;
        }
        short opcode = byteToShort(message);
        if(userName == null && opcode != 7){sendError(6); return;} 
        switch (opcode) {
            case 1:
                rrq(message);
                break;
            case 2:
                wrq(message);
                break;
            case 3:
                data(message);
                break;
            case 4:
                ack(message);
                break;
            case 5:
                error(message);
                break;
            case 6:
                dirq();
                break;
            case 7:
                logeq(message);
                break;
            case 8:
                delrq(message);
                break;
            case 10:
                disc();
                break;
        
            default:
                break;
            
        }

    }
    private void rrq(byte[] message){
        String fileName = decodeString(message);
        ReentrantReadWriteLock fileLock = files.get(fileName);

        if(fileLock != null){
            fileLock.readLock().lock();
            
            if(files.get(fileName) != null){
                try (FileInputStream in = new FileInputStream(dirPath + "/" + fileName);) {

                    short blockNumber = 0;
                    int dataSize = 512;

                    while(dataSize == 512){
                        ++blockNumber;
                        byte[] fileBytes = createDataPacket(512, blockNumber);
                        dataSize = in.read(fileBytes, 6, dataSize);
                        if(dataSize < 512){
                            byte[] lastFileBytes = createDataPacket(dataSize, blockNumber);
                            System.arraycopy(fileBytes, 6, lastFileBytes, 6, dataSize);
                            fileBytes = lastFileBytes;
                        }
                        fileAsPackets.add(fileBytes);
                    }

                    
                } catch (IOException e) {
                    System.err.println("An error occurred while reading the file: " + e.getMessage());
                    e.printStackTrace();
                }
                
            }
            else{ 
                sendError(1);
                return;
            }
            fileLock.readLock().unlock();
            sendData();
        }
        else{ 
            sendError(1);
            return;
        }
    }
    private void wrq(byte[] message){
        String msg = decodeString(message);
        //if is find
        sendError(5);
        //else
        sendAck(0);

    }
    private void data(byte[] message){

    }
    private void ack(byte[] message){

        byte[] numPacket = new byte[2];
        numPacket[0] = message[2];
        numPacket[1] = message[3];
        short ack = byteToShort(numPacket, 2);
        
        if(ack != ackNumber){
            sendError(0, "somehow the ack number doesn't match");
            return;
        }
        else if(ackNumber == 0){
            sendError(0, "isn't suppose to get ack");
            return;
        }

        sendData();
    }

    private void error(byte[] message){
        Byte[] numErrore=new Byte[2];
        numErrore[0]=message[2];
        numErrore[1]=message[3];
        short b_short = ( short ) ((( short ) numErrore [0]) << 8 | ( short ) ( numErrore [1]) );
   

    }

    private void dirq(){
        Set<String> fileNames = files.keySet();
        String dirq = "";
        for (String fileName : fileNames){
            dirq += fileName + '\n';
        }
        if(!dirq.equals(""))
            dirq = dirq.substring(0, dirq.length() - 1);
        else{
            dirq = "The folder is Empty";
        }
        connections.send(connectionId, stringToBytes(dirq));
    }

    private void logeq(byte[] message){
        if(userName != null){

        }
        String name = decodeString(message);
        Integer idOfUser = userNames.get(name);
        if(idOfUser != connectionId){
            sendError(7);
        }
  
        //else
        //add name to list
        sendAck(0);
    }

    private void delrq(byte[] message){
        String name=decodeString(message);
        sendAck(0);
        //if exsist
        //delete
        sendBcast(name,0);
    }
    
    private void disc(){
        //remove client
        sendAck(0);

    }

    private String decodeString(byte[] massage){
        return new String(massage, 2, massage.length - 2, StandardCharsets.UTF_8);
    }

    private void sendData(){
        if(!fileAsPackets.isEmpty()){
            byte[] msg = fileAsPackets.remove(0);
            connections.send(connectionId, msg);
            ++ackNumber;
        }
        else{
            ackNumber = 0;
        }
    } 

    private void sendError(int error){
        sendError(error, "");
    }

    private void sendError(int error, String additionToError){
        String msg = "";
        switch (error) {
            case 0:
                break;
            case 1:
                msg = "error 1 - File not found";
                break;

            case 2:
                msg = "error 2 - Access violation";
                break;

            case 3:
                msg = "error 3 - Disk full or allocation exceeded";
                break;

            case 4:
                msg = "error 4 - Illegal TFTP operation";
                break;

            case 5:
                msg = "File already exists";
                break;

            case 6:
                msg = "User not logged in";
                break;
            case 7:
                msg = "User already logged in";
                break;

            default:
                msg = "somehow the error msg you got is" + error;
                break;
        }
        msg += additionToError;

        //encoding the error message
        short a = 5;
        byte[] opcode = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        short b = (short)error;
        byte[] typeOfError = new byte[]{(byte) (b >> 8), (byte) (b & 0xff)};
        byte[] ans = stringToBytes(msg);
        byte[] msgToSend = new byte[opcode.length + typeOfError.length + ans.length + 1];      
        System.arraycopy(opcode, 0, msg, 0, opcode.length);
        System.arraycopy(typeOfError, 0, msg, opcode.length, typeOfError.length);
        System.arraycopy(ans, 0, msg, opcode.length + typeOfError.length, ans.length);
        msgToSend[msgToSend.length - 1] = (byte) 0;
        connections.send(connectionId, msgToSend);
    }

    private void sendAck(int numOfPacket){
        short a = 4;
        byte[] opcode = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        short b= (short)numOfPacket;
        byte[] numOfPacket_ = new byte[]{(byte) (b >> 8), (byte) (b & 0xff)};
        byte[] msg = new byte[4];      
        System.arraycopy(opcode, 0, msg, 0, 2);
        System.arraycopy(numOfPacket_, 0, msg, 2, 2);
        connections.send(connectionId, msg);
    }

    private void sendBcast(String name, int sign){
        short a = 9;
        byte[] opcode = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        byte[] sign_= new byte[]{(byte) sign};
        byte[] fileName= stringToBytes(name);
        byte[] msg = new byte[4 + fileName.length];      
        System.arraycopy(opcode, 0, msg, 0, 2);
        System.arraycopy(sign_, 0, msg, 2, 1);
        System.arraycopy(fileName, 0, msg, 3, fileName.length);
        msg[msg.length - 1] = (byte) 0;
        
        Collection<Integer> ids = userNames.values();
        for (Integer id : ids){
            connections.send(id, msg);
        }
    }
    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    } 


    private byte[] stringToBytes(String msg){
        return msg.getBytes(Charset.forName("UTF-8"));
    }

    public void terminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }

    private byte[] shortToByte(short number){
        byte[] fromShort = {(byte) (number >> 8), (byte) (number & 0xff)};
        return fromShort;
    }


    private short byteToShort(byte[] bytes){
        return byteToShort(bytes, 0);
    }

    private short byteToShort(byte[] bytes, int start){
        short fromBytes = ( short ) (bytes [start] << 8 |  bytes [start + 1] );
        return fromBytes;
    }

    private byte[] createDataPacket(int dataSize, short blockNumber){
        byte[] fileBytes = new byte[dataSize + 6];
        fileBytes[0] = (byte) 0; fileBytes[1] = (byte) 3;
        byte[] packetSize = shortToByte((short)dataSize);
        fileBytes[2] = packetSize[0]; fileBytes[3] = packetSize[1];
        byte[] blockBytes = shortToByte(blockNumber);
        fileBytes[4] = blockBytes[0]; fileBytes[5] = blockBytes[1];
        return fileBytes;
    }

    
}
