package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import  java.util.Iterator;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

enum State{
    WAITING_COMMAND,
    READ,
    WRITE,
    DIRQ
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    
    private Connections<byte[]> connections;
    private int connectionId;

    private final Map<String, Integer> userNames;
    private String userName = null;
    
    private final Map<String, FileWithLock> files;

    private FileWithLock file = null;

    private final String dirPath;
    private LinkedList<Byte> lst = null;
    private ByteBuffer buffer = null;
    
    private State currState = State.WAITING_COMMAND;

    private short ackNumber = -1;
    private boolean shouldTerminate = false;


    public TftpProtocol(Map<String, Integer> userNames, Map<String, FileWithLock> files, String dirPath){
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
        if(message.length < 2){
            sendError(4);
            return;
        }
        switch (currState) {
            case WAITING_COMMAND:
                command(message);
                break;

            case WRITE:
                handleWrite(message);
                break;
        
            case READ:
                handleRead(message);
                break;

            case DIRQ:
                handleDirq(message);
                break;

            default:
                sendError(0, "error in the state the server was at");
                break;
        }
    }

    private void command(byte[] message){
        short opcode = byteToShort(message);
        if(userName == null && opcode != 7){sendError(6); return;} 
        switch (opcode) {
            case 1:
                rrq(message);
                break;
            case 2:
                wrq(message);
                break;
            case 4:
                ValidateAck(message, ackNumber);
                break;
            case 6:
                dirq();
                break;
            case 7:
                logrq(message);
                break;
            case 8:
                delrq(message);
                break;
            case 10:
                disc();
                break;
            default:
                sendError(4);
                break;
            
        }
    }


    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------RRQ-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */

    private void rrq(byte[] message){
        String fileName = decodeString(message, 2, message.length - 3);
        
        if(configReader(fileName)){
            sendData(buffer);
        }
    }

    private boolean configReader(String fileName){
        FileWithLock file = files.get(fileName);

        if(file == null){
            sendError(1);
            return false;
        }
        
        file.readLock();
        
        if(files.get(fileName) == null){ //means that the file was deleted while waited on the lock
            file.readUnlock();
            sendError(1);
            return false;
        }
        
        try(FileInputStream reader = new FileInputStream(file.getFile());
        FileChannel fileChan = reader.getChannel();) {
            
            long fileSize = fileChan.size();
            buffer = ByteBuffer.allocate((int) fileSize);

            fileChan.read(buffer);
            buffer.flip();

            file.readUnlock();
            file = null;
        } catch (IOException error) {
            sendError(0, "error in the file that the InputStreamGot");
            return false;
        }
        ackNumber = 0;
        currState = State.READ;
        sendAck(ackNumber);
        return true;
        

    }

    private void handleRead(byte[] message) {
        if(ValidateAck(message, ackNumber))
            sendData(buffer);
    }

    private void sendData(ByteBuffer buffer){
        int size = buffer.remaining() > 512 ? 512 : buffer.remaining(); 
        byte[] dataPacket = new byte[6 + size];
        prepareDataPacket(dataPacket, ++ackNumber);
        buffer.get(dataPacket, 6, size);
        connections.send(connectionId, dataPacket);

        if(size < 512){
            currState = State.WAITING_COMMAND;
            buffer.clear();
            buffer = null;
        }   
    }

    private void prepareDataPacket(byte[] dataPacket, int blockNumber){
        dataPacket[0] = 0; dataPacket[1] = 3;
        STB(dataPacket, 2, dataPacket.length - 6);
        STB(dataPacket, 4, blockNumber);
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------WRQ-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */

    private void wrq(byte[] message){
        String fileName = decodeString(message, 2, message.length - 3);
        boolean configWriter = configWriter(fileName);

        if(configWriter){
            currState = State.WRITE;
            ackNumber = 1;
        }
    }

    private boolean configWriter(String fileName){
        File currFile = new File(dirPath + "/" + fileName);
        if(currFile.exists()){
            sendError(5);
            return false;
        }
        sendAck(0);
        file = new FileWithLock(new ReentrantReadWriteLock(true), currFile);
        lst = new LinkedList<Byte>();
        return true;
    }

    private void handleWrite(byte[] message){

        if(!validateDataPacket(message))
            return;

        
        for(int i = 6; i < message.length; ++i){
            lst.add(message[i]);
        }
        sendAck(ackNumber++);

        int dataSize = message.length - 6;
        if(dataSize < 512){
            createFile();
            file = null;
            currState = State.WAITING_COMMAND;
        }
    }

    private void createFile(){
        file.writeLock();
        File createFile = file.getFile();
        String fileName = createFile.getName();
        try {

            synchronized(files){
                if(!createFile.exists()){
                    if(createFile.createNewFile())
                        files.put(fileName, file);
                        sendBcast(fileName, 1);       
                }
                else{
                    file.writeUnlock();
                    file = files.get(fileName);
                    file.writeLock();
                }    
            }
            
            FileOutputStream writer = new FileOutputStream(createFile);
            FileChannel fileChan = writer.getChannel();


            fileChan.write(ByteBuffer.wrap(convertToByteArray(lst)));
            lst = null;

            file.writeUnlock();

            fileChan.close();
            writer.close();

        } catch (IOException e) {
            sendError(0, "there was an error in creating the file");
            file.writeUnlock();
        }
    }

    private byte[] convertToByteArray(List<Byte> lst){
        byte[] byteArr = new byte[lst.size()];
        Iterator<Byte> iter = lst.iterator();
        int i = 0;
        while(iter.hasNext()){
            byteArr[i++] = (iter.next()).byteValue();
        }
        return byteArr;
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------ValidateAck-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */


    private boolean ValidateAck(byte[] message, short ackNumber){

        short ack = byteToShort(message, 2);
        
        if(ack != ackNumber){
            sendError(0, "somehow the ack number doesn't match");
            return false;
        }
        else if(ackNumber == -1){
            sendError(0, "isn't suppose to get ack");
            return false;
        }
        if(currState == State.WAITING_COMMAND)
            ackNumber = -1;
        
        return true;
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------dirq-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */

    private void dirq(){

        sendAck(0);
        ackNumber = 0;

        Set<String> fileNames = files.keySet();
        LinkedList<Byte> dirq = new LinkedList<Byte>();
        Iterator<String> iter = fileNames.iterator();

        while(iter.hasNext()){
            String fileName = iter.next();
            byte[] fileBytes = stringToBytes(fileName);
            for(byte byt : fileBytes){
                dirq.add(byt);
            }
            if(iter.hasNext()){
                dirq.add(new Byte((byte)0));
            }
        }

        buffer = ByteBuffer.allocate(dirq.size());
        for(Byte byt : dirq){
            buffer.put(byt.byteValue());
        }
        buffer.flip();
        sendData(buffer);
    }

    private void handleDirq(byte[] message){
        sendData(buffer);
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------logrq-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */

    private void logrq(byte[] message){
        if(userName != null){
            sendError(7);
        }
        String name = decodeString(message, 2, message.length - 3);
        if(userNames.get(name) == null){
            userName = name;
            userNames.put(name, connectionId);
            sendAck(0);
        }
        else{
            sendError(7);
        }
        
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------delrq-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */

    private void delrq(byte[] message){
        String name = decodeString(message, 2, message.length - 3);
        file = files.get(name);
        if (file != null){
            file.writeLock();
            if (files.get(name) != null){
                files.remove(name);
                file.getFile().delete();
                sendAck(0);
                sendBcast(name,0);
            }
            else{
                sendError(1);
            }
            file.writeUnlock();
            file = null;
        }
        else{
            sendError(1);
        }
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------disc-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */
    
    private void disc(){
        if(userName != null){
            userNames.remove(userName);
            sendAck(0);
            connections.disconnect(connectionId);
            shouldTerminate = true;
        }
        else{
            sendError(6);
        }
    }

    /*
     * --------------------------------------------------------------------------------------------------------
     * ------------------------------------------other_function-----------------------------------------------------------
     * --------------------------------------------------------------------------------------------------------
     */

    private String decodeString(byte[] massage, int index, int length){
        return new String(massage, index, length, StandardCharsets.UTF_8);
    }

    private void sendError(int error){
        sendError(error, "");
    }

    private void sendError(int error, String additionToError){
        String msg = "";
        switch (error) {
            case 0:
                msg = "somehow the error msg you got is: ";
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
                break;
        }
        msg += additionToError;

        byte[] msgToSend = createError(error, msg);
        connections.send(connectionId, msgToSend);
    }

    private static byte[] createError(int error, String msg){
        byte[] msgBytes = stringToBytes(msg);
        byte[] errPack = new byte[5 + msgBytes.length];
        errPack[0] = 0; errPack[1] = 5; errPack[errPack.length - 1] = 0;
        STB(errPack, 2, (short)error);
        for(int i = 0; i < msgBytes.length; ++i){
            errPack[i + 4] = msgBytes[i];
        }
        return errPack;
    }

    private void sendAck(int numOfPacket){
        connections.send(connectionId, createAck(numOfPacket));
    }

    private byte[] createAck(int numOfPacket){
        byte[] message = {0, 4, 0, 0};
        STB(message, 2, (short)numOfPacket);
        return message;
    }

    private static byte[] createBcast(String name, int sign){
        byte[] opcode = {0, 9, (byte)sign};
        byte[] fileName = stringToBytes(name);
        byte[] msg = new byte[4 + fileName.length];      
        System.arraycopy(opcode, 0, msg, 0, 3);
        System.arraycopy(fileName, 0, msg, 3, fileName.length);
        msg[msg.length - 1] = (byte) 0;
        return msg;
    }

    private void sendBcast(String name, int sign){
        byte[] msg = createBcast(name, sign);
        Collection<Integer> ids = userNames.values();
        for (Integer id : ids){
            connections.send(id, msg);
        }
    }
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    private static byte[] stringToBytes(String msg){
        return msg.getBytes(Charset.forName("UTF-8"));
    }

    private static void STB(byte[] arr, int index, int number){
        isValid(arr, index);
        short shortNum = (short) number;
        arr[index] = (byte)(shortNum >> 8);
        arr[index + 1] = (byte)(shortNum & 0xff);
    }
/* 
    public static void main(String[] args){
        byte[] arr = createError(6, "User not logged in");
        printArray(arr);
    }


    public static void printArray(byte[] arr){
        if(arr == null){
            System.out.println("null");
            return;
        }
        System.out.print("[");
        for(int i = 0; i < arr.length - 1; i++){
            System.out.print(arr[i] + ", ");
        }
        if(arr.length > 0)
            System.out.print(arr[arr.length - 1]);
        System.out.println("]");
    }

*/
    private short byteToShort(byte[] bytes){
        return byteToShort(bytes, 0);
    }

    private short byteToShort(byte[] bytes, int index){
        isValid(bytes, index);
        short fromBytes = ( short ) (bytes[index] << 8 |  (bytes[index + 1] & 0xff));
        return fromBytes;
    }

    private static void isValid(byte[] arr, int index){
        if(arr == null)
            throw new NullPointerException("arr is null");
        
        if(index > arr.length - 2 || index < 0)
            throw new IndexOutOfBoundsException("index out of bounds");
    }

    private boolean validateDataPacket(byte[] dataPack){
        short opcode = byteToShort(dataPack, 0);
        if(opcode != (short)3){
            sendError(0, "got different packet instead of DataPacket");
            return false;
        }
        short length = byteToShort(dataPack, 2);
        if(length != (short)(dataPack.length - 6)){
            sendError(0, "the packet size written on the packet, and the real packet size are not the same");
            return false;
        }

        short blockNumber = byteToShort(dataPack, 4);
        if(blockNumber != (short)ackNumber){
            sendError(0, "the block number isn't the same as the block number that was supposed to arrive");
            return false;
        }

        return true;
    }
}
