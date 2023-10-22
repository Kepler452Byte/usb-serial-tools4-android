package com.hoho.android.usbserial.util;

import android.text.TextUtils;

public class ProtocolFactory {
    public static String VA07="02010100";
    public static String CA07="02020100";
    public static String  P07="02030000";
    public static String  E07="00010000";
    public static String EP07="00010200";
    public static String VA97="B611"; //A电压
    public static String CA97="B621"; //A电流
    public static String  P97="B630"; //瞬时功率
    public static String  E97="9010";  //总电能
    public static String EP97="9012"; //峰电能

    private static byte[] dltmsg97_template={(byte)0xFE,(byte)0xFE,(byte)0xFE,(byte)0xFE,
            (byte)0x68,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00, (byte)0x68,(byte)0x01,
            (byte)0x02,(byte)0x00,(byte)0x00,(byte)0xD3,(byte)0x16};
    private static byte[] dltmsg07_template = {(byte)0xFE,(byte)0xFE,(byte)0xFE,(byte)0xFE,
            (byte)0x68,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x68,(byte)0x11,
            (byte)0x04,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xE5,(byte)0x16};

    public static byte[] BuildDLT645(String addr,String dataId)
    {
        boolean is07 = true;
        int msgLen = 0;
        if(TextUtils.isEmpty(addr)||TextUtils.isEmpty(dataId)){
            return null;
        }
        if(dataId.length() == 8){
            msgLen = 20;
        }else if(dataId.length() == 4){
            is07 = false;
            msgLen = 18;
        }else {
            return null;
        }
        byte[] addr_B = HexDump.hexStringToByteArray(addr);
        byte[] dataId_B = HexDump.hexStringToByteArray(dataId);
        if(addr_B == null || dataId_B == null){
            return null;
        }
        if(addr_B.length != 6){
            return null;
        }
        //checksum
        int i=0;
        byte checkSum = 0;
        for(i=0;i<dataId_B.length;i++){
            dataId_B[i] += (byte)0x33;
            checkSum = (byte)(checkSum + dataId_B[i]);
        }
        for(i=0;i<6;i++){
            checkSum = (byte)(checkSum + addr_B[i]);
        }

        byte[] bmsg = new byte[msgLen];
        if(is07) {
            System.arraycopy(dltmsg07_template,0,bmsg,0,msgLen);
            bmsg[14] = dataId_B[3];bmsg[15] = dataId_B[2];
            bmsg[16] = dataId_B[1];bmsg[17] = dataId_B[0];
            bmsg[18] = (byte)(checkSum+bmsg[18]); //checkSum
        }else {
            System.arraycopy(dltmsg97_template,0,bmsg,0,msgLen);
            bmsg[14] = dataId_B[1];bmsg[15] = dataId_B[0];
            bmsg[16] = (byte)(checkSum+bmsg[16]); //checkSum
        }
        //copy addr
        bmsg[5] = addr_B[5];bmsg[6] = addr_B[4];bmsg[7] = addr_B[3];bmsg[8] = addr_B[2];bmsg[9] = addr_B[1];bmsg[10] = addr_B[0];
        return bmsg;
    }
    public static int CalCRC16(byte[] data,int len)
    {
        int CRC = 0x0000ffff;
        int POLYNOMIAL = 0x0000a001;
        int i, j;
        for (i = 0; i < len; i++) {
            CRC ^= ((int) data[i] & 0x000000ff);
            for (j = 0; j < 8; j++) {
                if ((CRC & 0x00000001) != 0) {
                    CRC >>= 1;
                    CRC ^= POLYNOMIAL;
                } else {
                    CRC >>= 1;
                }
            }
        }
        return CRC;
    }
    public static byte[] BuildModbus(String addr,String code,String reg,String val,boolean isHex)
    {
        if(addr==null || code==null || reg==null||val ==null){
            return null;
        }
        int radix = isHex?16:10;
        byte addr_b = (byte)Integer.parseInt(addr,radix);
        byte code_b = (byte)Integer.parseInt(code,radix);
        int reg_i = Integer.parseInt(reg,radix);
        byte[] val_B = null;
        if(isHex) {
            val_B = HexDump.hexStringToByteArray(val);
            if (val_B == null) {
                return null;
            }
        }else{
            int val_i=Integer.parseInt(val);
            val_B = new byte[2];
            val_B[0] = (byte)((val_i>>8)&0xff);
            val_B[1] = (byte)(val_i&0xff);
        }

        int msgLen = val_B.length+6;
        byte[] bmsg = new byte[msgLen];
        bmsg[0] = addr_b;
        bmsg[1] = code_b;
        bmsg[2] = (byte)((reg_i>>8)&0xff);
        bmsg[3] = (byte)(reg_i&0xff);
        for(int i = 0; i < val_B.length;i++){
            bmsg[4+i] = val_B[i];
        }
        int crc = CalCRC16(bmsg,msgLen-2);
        bmsg[msgLen-2] = (byte)(crc&0xff);
        bmsg[msgLen-1] = (byte)((crc>>8)&0xff);
        return bmsg;
    }
}
