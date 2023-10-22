package com.hoho.android.usbserial.examples;

public class CustomSerialCMD {
    private String cmd;
    private String alias;
    private boolean isHex;
    private int period;
    private boolean addCR;

    public CustomSerialCMD(String cmd, String alias, boolean isHex, boolean addCR, int period) {
        this.cmd = cmd;
        if(alias==null){
            this.alias = cmd;
        }else {
            this.alias = alias;
        }
        this.isHex = isHex;
        this.period = period;
        this.addCR = addCR;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isHex() {
        return isHex;
    }

    public void setHex(boolean hex) {
        isHex = hex;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public boolean isAddCR() {
        return addCR;
    }

    public void setAddCR(boolean addCR) {
        this.addCR = addCR;
    }
}
