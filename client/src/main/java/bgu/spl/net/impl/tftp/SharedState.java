package bgu.spl.net.impl.tftp;

import java.io.File;

public class SharedState {
    private boolean wake = false;
    private boolean isRRQ = false;
    private boolean isWRQ = false;
    private boolean isDISC = false;
    private boolean isDELRQ = false;
    private boolean isDIRQ = false;
    private boolean isLoggedIn = false;
    private boolean isSentLoginRequest = false;
    private boolean isSentDiscRequest = false;
    private byte[] name = null;
    private static final String FILES_DIRECTORY = "";

    public synchronized void waitToBeWaken() throws InterruptedException {
        while (!wake) {
            wait();
        }
        wake = false;
    }

    public synchronized void wake() {
        wake = true;
        notify();
    }


    public synchronized void setRRQ(boolean isRRQ) {
        this.isRRQ = isRRQ;
    }

    public synchronized void setWRQ(boolean isWRQ) {
        this.isWRQ = isWRQ;
    }

    public synchronized void setDISC(boolean isDISC) {
        this.isDISC = isDISC;
    }

    public synchronized void setDIRQ(boolean isDIRQ) {
        this.isDIRQ = isDIRQ;
    }

    public synchronized void setLoggedIn(boolean isLoggedIn) {
        this.isLoggedIn = isLoggedIn;
    }

    public synchronized void setSentLoginRequest(boolean isSentLoginRequest) {
        this.isSentLoginRequest = isSentLoginRequest;
    }

    public synchronized void setSentDiscRequest(boolean isSentDiscRequest) {
        this.isSentDiscRequest = isSentDiscRequest;
    }

    public synchronized boolean isRRQ() {
        return isRRQ;
    }

    public synchronized boolean isWRQ() {
        return isWRQ;
    }

    public synchronized boolean isDISC() {
        return isDISC;
    }

    public synchronized boolean isDIRQ() {
        return isDIRQ;
    }

    public synchronized boolean isLoggedIn() {
        return isLoggedIn;
    }

    public synchronized boolean isSentLoginRequest() {
        return isSentLoginRequest;
    }

    public synchronized boolean isSentDiscRequest() {
        return isSentDiscRequest;
    }

    public synchronized void setName(byte[] name) {
        this.name = name;
    }

    public synchronized byte[] getName() {
        return name;
    }

    public static String getFILES_DIRECTORY() {
        return FILES_DIRECTORY;
    }

    public synchronized void setDELRQ(boolean isDELRQ) {
        this.isDELRQ = isDELRQ;
    }

    public synchronized boolean isDELRQ() {
        return isDELRQ;
    }
}
