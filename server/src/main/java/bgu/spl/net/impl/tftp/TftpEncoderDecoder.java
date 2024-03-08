package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.ArrayList;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    ArrayList<Byte> decodedBytes = new ArrayList<>();
    boolean opcodeCase = true;
    boolean endsWithZero = false;
    boolean data = false;
    boolean ack = false;
    boolean discOrDirq = false;
    boolean decoded = false;
    short dataSize = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        decodedBytes.add(nextByte);
        if(endsWithZero) endsWithZero(nextByte);
        else if(data) data(nextByte);
        else if(ack) ack(nextByte);
        else if(discOrDirq) discOrDirq(nextByte);
        else if(opcodeCase) opcode(nextByte);
        if(decoded) return decoded();
        return null;
    }

    public void endsWithZero(byte nextByte){
        if(nextByte == (byte) 0){
            decodedBytes.remove(decodedBytes.size() - 1);
            endsWithZero = false;
            decoded = true;
        }   
    }

    public void data(byte nextByte){
        if(decodedBytes.size() < 5)
            dataSize += ((short) nextByte) * (4 - decodedBytes.size()) * 256;
        else{
            if(dataSize == decodedBytes.size() - 2){
                data = false;
                dataSize = 0;
                decoded = true;
            }
        }
    }

    //size of packet is 4.
    public void ack(byte nextByte){
        if(decodedBytes.size() == 4){
            ack = false;
            decoded = true;
        }
    }

    //size of packet is 2.
    public void discOrDirq(byte nextByte){
        discOrDirq = false;
        decoded = true; 
    }

    public void opcode(byte nextByte){
        Byte[] opcode = new Byte[2];
        opcode[decodedBytes.size() - 1] = nextByte;
        if(decodedBytes.size() == 2){
            opcodeCase = false;
            short opcodeShort = ( short ) (( opcode [0]) << 8 | ( opcode [1]) );
            switch (opcodeShort) {
                case 1: case 5: case 7: case 8: case 9:
                    endsWithZero = true;
                    break;
                case 3:
                    data = true;
                    break;
                case 4:
                    ack = true;
                    break;
                case 6: case 2:
                    discOrDirq = true;
                    break;
                default:
                    decoded = true;
                    decodedBytes.clear();
                    break;
            }
        }
    }

    public byte[] decoded(){
        byte[] message = new byte[decodedBytes.size()];
        for (int i = 0; i < decodedBytes.size(); i++) 
            message[i] = decodedBytes.get(i);
        decodedBytes.clear();
        decoded = false;
        opcodeCase = true;
        return message;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }
}