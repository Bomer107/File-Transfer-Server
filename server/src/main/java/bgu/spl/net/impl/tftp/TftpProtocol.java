package bgu.spl.net.impl.tftp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    static LinkedList <String> list;
    

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void process(byte[] message) {
        Byte[] opcode=new Byte[2];
        opcode[0]=message[0];
        opcode[1]=message[1];
        short b_short = ( short ) ((( short ) opcode [0]) << 8 | ( short ) ( opcode [1]) );

    }
    private void rrq(byte[] message){
        String msg= decodeString(message);
        //if find
        sendData(msg);

    }
    private void wrq(byte[] message){
        String msg= decodeString(message);
        //if is find
        sendError("file already exists",5);
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
    private void dirq(byte[] message){

       

    }
    private void logeq(byte[] message){
        String name=decodeString(message);
        //if is find
        sendError("user already logged in", 7);
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
    
    private void disq(byte[] message){
        //remove client
        sendAck();

    }
    private String decodeString(byte[] massage){
        String msg=new String(massage, 2, massage.length - 2, StandardCharsets.UTF_8);
        return msg;
    }
    private void sendData(String fileName){

    }
    private void sendError(String msg,int error){

    }
    private void sendAck(){

    }
    private void sendBcast(String name,int sign){

    }
    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    } 


    
}
