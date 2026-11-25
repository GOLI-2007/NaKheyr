package client;

import shared.Frame;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerListenerTask implements Runnable {
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ClientState state;
    private final Socket socket;
    private volatile boolean running = true;

    public ServerListenerTask(DataInputStream in, DataOutputStream out, ClientState state, Socket socket) {
        this.in = in;
        this.out = out;
        this.state = state;
        this.socket = socket;
    }

    /**
     * این متد در یک ترد مجزا اجرا می‌شود و وظیفه دارد به صورت مداوم به استریم ورودی سوکت
     * گوش داده، فریم‌های ارسالی از سمت سرور را تجزیه کند[cite: 74].
     * همچنین چاپ پیام‌ها در کنسول و ارسال فریم تأییدیه (ACK) پس از دریافت پیام خصوصی، بر عهده این متد است[cite: 70, 74].
     */
    @Override
    public void run() {
        try {
            while (running) {
                byte[] frameData = Frame.readNextFrame(in, socket);
                byte type = frameData[0];
                byte[] payload = new byte[frameData.length - 1];
                System.arraycopy(frameData, 1, payload, 0, payload.length);

                switch (type) {
                    case Frame.TYPE_LOUNGE_MSG:
                        String loungeMsg = new String(payload, StandardCharsets.UTF_8);
                        System.out.println(loungeMsg);
                        break;

                    case Frame.TYPE_DIRECT_MSG:
                        int msgId = ((payload[0] & 0xFF) << 24) |
                                ((payload[1] & 0xFF) << 16) |
                                ((payload[2] & 0xFF) << 8) |
                                (payload[3] & 0xFF);
                        int nameLen = payload[4] & 0xFF;
                        String sender = new String(payload, 5, nameLen, StandardCharsets.UTF_8);
                        String formattedText = new String(payload, 5 + nameLen, payload.length - (5 + nameLen), StandardCharsets.UTF_8);
                        System.out.println(formattedText);

                        byte[] ackPayload = new byte[4];
                        ackPayload[0] = (byte) (msgId >> 24);
                        ackPayload[1] = (byte) (msgId >> 16);
                        ackPayload[2] = (byte) (msgId >> 8);
                        ackPayload[3] = (byte) msgId;

                        synchronized (out) {
                            Frame.sendFrame(out, Frame.TYPE_ACK, ackPayload);
                        }
                        break;

                    case Frame.TYPE_SYSTEM_NOTICE:
                        String sysMsg = new String(payload, StandardCharsets.UTF_8);
                        if (sysMsg.startsWith("Sent DM to ")) {
                            System.out.println("[" + sysMsg + "]");
                        } else if (sysMsg.startsWith("=== User Profile ===")) {
                            System.out.println(sysMsg);
                        } else {
                            System.out.println(">> [SYSTEM NOTICE]: " + sysMsg);
                        }

                        if (sysMsg.contains("Disconnected from server")) {
                            running = false;
                            try { socket.close(); } catch (Exception ignored) {}
                            System.exit(0);
                        }

                        if (!state.isAuthenticated()) {
                            if (sysMsg.contains("successful") || sysMsg.contains("Welcome")) {
                                state.setAuthenticated(true, sysMsg);
                            } else if (sysMsg.contains("already taken") ||
                                    sysMsg.contains("not found") ||
                                    sysMsg.contains("Incorrect password")) {
                                state.setAuthenticated(false, sysMsg);
                            }
                        }
                        break;

                    case Frame.TYPE_ROOT_CMD:
                        if (payload.length == 1 && payload[0] == (byte) 0xDF) {
                            System.out.println(">> [SYSTEM NOTICE]: You have been kicked by the administrator.");
                            running = false;
                            socket.close();
                            System.exit(0);
                        }
                        break;

                    default:
                        break;
                }
            }
        } catch (Exception e) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
            running = false;
            System.exit(0);
        }
    }
}