package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    
    private Connections<byte[]> connections;
    private final Map<String, Integer> userNames;
    private int connectionId;

    private final Map<String, ReentrantReadWriteLock> files;
    private String userName = null;


    public TftpProtocol(Map<String, Integer> userNames, Map<String, ReentrantReadWriteLock> files){
        this.userNames = userNames;
        this.files = files;
    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connections = connections;
        this.connectionId = connectionId;
    }

    @Override
    public void process(byte[] message) {
        short b_short = ( short ) (message [0] << 8 |  message [1] );
        if(userName == null && b_short != 7){sendError(6);} 
        else{
            switch (b_short) {
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

    }
    private void rrq(byte[] message){
        String msg= decodeString(message);
        sendData(msg);

    }
    private void wrq(byte[] message){
        String msg = decodeString(message);
        //if is find
        sendError(5);
        //else
        sendAck();

    }
    private void data(byte[] message){

    }
    private void ack(byte[] message){
        Byte[] numPacket=new Byte[2];
        numPacket[0]=message[2];
        numPacket[1]=message[3];
        short b_short = ( short ) ((( short ) numPacket [0]) << 8 | ( short ) ( numPacket [1]) );

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
        sendAck();
    }

    private void delrq(byte[] message){
        String name=decodeString(message);
        sendAck();
        //if exsist
        //delete
        sendBcast(name,0);
    }
    
    private void disc(){
        //remove client
        sendAck();

    }

    private String decodeString(byte[] massage){
        return new String(massage, 2, massage.length - 2, StandardCharsets.UTF_8);
    }

    private void sendData(String fileName){
        

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
        byte[] concatenatedArray = new byte[opcode.length + typeOfError.length + ans.length + 1];      
        System.arraycopy(opcode, 0, concatenatedArray, 0, opcode.length);
        System.arraycopy(typeOfError, 0, concatenatedArray, opcode.length, typeOfError.length);
        System.arraycopy(ans, 0, concatenatedArray, opcode.length + typeOfError.length, ans.length);
        concatenatedArray[concatenatedArray.length - 1] = (byte) 0;
        connections.send(connectionId, concatenatedArray);
    }

    private void sendAck(int numOfPacket){
        short a = 4;
        byte[] opcode = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        short b= (short)numOfPacket;
        byte[] numOfPacket_ = new byte[]{(byte) (b >> 8), (byte) (b & 0xff)};
        byte[] concatenatedArray = new byte[4];      
        System.arraycopy(opcode, 0, concatenatedArray, 0, 2);
        System.arraycopy(numOfPacket_, 0, concatenatedArray, 2, 2);
        connections.send(connectionId, concatenatedArray);
    }
    private void sendBcast(String name, int sign){
        short a = 9;
        byte[] opcode = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        byte[] sign_= new byte[]{(byte) sign};
        byte[] fileName= stringToBytes(name);
        byte[] concatenatedArray = new byte[4 + fileName.length];      
        System.arraycopy(opcode, 0, concatenatedArray, 0, 2);
        System.arraycopy(sign_, 0, concatenatedArray, 2, 1);
        System.arraycopy(fileName, 0, concatenatedArray, 3, fileName.length);
        concatenatedArray[concatenatedArray.length - 1] = (byte) 0;
        
        Collection<Integer> ids = userNames.values();
        for (Integer id : ids){
            connections.send(id, concatenatedArray);
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

    
}
