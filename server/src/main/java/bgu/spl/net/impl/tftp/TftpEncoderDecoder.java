package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.ArrayList;


enum Case{
    OPCODE,
    ENDS_WITH_ZERO,
    DATA,
    ACK
}


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    ArrayList<Byte> decodedBytes = new ArrayList<>();
    Case cas = Case.OPCODE;
    boolean decoded = false;
    short dataSize = -7;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        decodedBytes.add(nextByte);
        switch (cas) {
            case ENDS_WITH_ZERO:
                endsWithZero(nextByte);
                break;
            case DATA:
                data(nextByte);
                break;
            case ACK:
                ack(nextByte);
                break;
            case OPCODE:
                opcode(nextByte);
                break;
            default:
                break;
        }
        if(decoded) return decoded();
        return null;
    }

    public void endsWithZero(byte nextByte){
        if(nextByte == (byte) 0){
            decoded = true;
        }   
    }

    public void data(byte nextByte){
        if(decodedBytes.size() == 4)
            dataSize = BTS(decodedBytes, 2);
        
        if(dataSize == decodedBytes.size() - 6){
            dataSize = -7;
            decoded = true;
        }
    }

    //size of packet is 4.
    public void ack(byte nextByte){
        if(decodedBytes.size() == 4){
            decoded = true;
        }
    }

    public void opcode(byte nextByte){
        if(decodedBytes.size() == 0 && nextByte != 0){
            return;
        }
        if(decodedBytes.size() == 2){
            short opcodeShort = BTS(decodedBytes);
            switch (opcodeShort) {
                case 1: case 2: case 5: case 7: case 8: case 9:
                    cas = Case.ENDS_WITH_ZERO;
                    break;
                case 3:
                    cas = Case.DATA;
                    break;
                case 4:
                    cas = Case.ACK;
                    break;
                case 6: case 10:
                    decoded = true;
                    break;
                default:
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
        cas = Case.OPCODE;
        return message;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    //BTS = Byte To Short
    private short BTS(ArrayList<Byte> arr){
        return BTS(arr, 0);
    }

    private short BTS(ArrayList<Byte> arr, int index){
        if(arr == null)
            throw new NullPointerException("arr is null");
        
        if(index > arr.size() - 2 || index < 0)
            throw new IndexOutOfBoundsException("index out of bounds");
        
        return (short) (((arr.get(index)) << 8) | (arr.get(index + 1) & 0xff));
    }

    /*
     * -------------------------------------------------------------------------------------------------
     * ------------------------------------------- FOR TESTING -----------------------------------------
     * -------------------------------------------------------------------------------------------------
     

    public static void main(String[] args){
        byte[] commandData = {0x00, 0x02, 0x00, 0x1a, 0x00, 0x01, (byte) 0xd7, (byte) 0xa6 ,
                            (byte) 0xd7, (byte) 0x95, (byte) 0xd7, (byte) 0xaa, 0x20, (byte) 0xd7, 
                            (byte) 0xa9, (byte) 0xd7, (byte) 0x90, (byte) 0xd7, (byte) 0xa0, 
                            (byte) 0xd7, (byte) 0x99, 0x20, (byte) 0xd7, (byte) 0x91, 
                            (byte) 0xd7, (byte) 0x95, (byte) 0xd7, (byte) 0xa0, (byte) 0xd7, (byte) 0x94, 10};
        byte[] command2 = {0, 1, 3, 5, 8};
        byte[] test = commandData;
        TftpEncoderDecoder encdec = new TftpEncoderDecoder();
        for(int i = 0; i < test.length; i++){
            printArray(encdec.decodeNextByte(test[i]));
        }
    }
    */

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

    public static byte[] unite(byte[] arr1, byte[] arr2){
        byte[] result = new byte[arr1.length + arr2.length];
        for(int i = 0; i < arr1.length; i++){
            result[i] = arr1[i];
        }
        for(int i = 0; i < arr2.length; i++){
            result[i + arr1.length] = arr2[i];
        }
        return result;
    }
}