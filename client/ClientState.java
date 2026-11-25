package client;

public class ClientState {
    private boolean authenticated = false;
    private boolean authResult = false;
    private boolean responseReceived = false;
    private String lastNotice = "";

    public synchronized void setAuthenticated(boolean result, String notice) {
        this.authResult = result;
        if (result) {
            this.authenticated = true;
        }
        this.lastNotice = notice != null ? notice : "";
        this.responseReceived = true;
        this.notifyAll();
    }

    public synchronized void prepareForResponse() {
        responseReceived = false;
    }

    public synchronized boolean waitForAuth() throws InterruptedException {
        while (!responseReceived) {
            this.wait();
        }
        return authResult;
    }

    public synchronized boolean isAuthenticated() {
        return authenticated;
    }

    public synchronized String getLastNotice() {
        return lastNotice;
    }
}