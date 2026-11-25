package server;

import shared.Frame;
import shared.OfflineMessage;
import shared.UserProfile;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ConnectionThread implements Runnable {
    final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private boolean authenticated = false;
    private int loginAttempts = 0;

    public ConnectionThread(Socket socket) {
        this.socket = socket;
    }

    /**
     * حلقه اصلی پردازش هر کلاینت (The Event Loop).
     * این متد باید شامل چرخه تعاملی احراز هویت (Login/Register)، خواندن مداوم فریم‌ها،
     * مسیریابی پیام‌ها بر اساس Frame Type، مدیریت قطعی‌های ناگهانی و هندل کردن Timeouts باشد.
     */
    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            while (!authenticated) {
                byte[] frameData;
                try {
                    frameData = Frame.readNextFrame(in, socket);
                } catch (SocketTimeoutException e) {
                    System.err.println("Request Timed out.");
                    break;
                }

                if (frameData == null || frameData.length == 0) {
                    continue;
                }

                byte type = frameData[0];
                byte[] payload = Arrays.copyOfRange(frameData, 1, frameData.length);

                if (type == Frame.TYPE_REGISTER) {
                    handleRegister(payload);
                } else if (type == Frame.TYPE_LOGIN) {
                    handleLogin(payload);
                } else {
                }
            }

            while (authenticated && !socket.isClosed()) {
                byte[] frameData;
                try {
                    frameData = Frame.readNextFrame(in, socket);
                } catch (SocketTimeoutException e) {
                    System.err.println("Request Timed out.");
                    break;
                }

                if (frameData == null || frameData.length == 0) {
                    continue;
                }

                byte type = frameData[0];
                byte[] payload = Arrays.copyOfRange(frameData, 1, frameData.length);

                switch (type) {
                    case Frame.TYPE_LOUNGE_MSG:
                        String text = new String(payload, StandardCharsets.UTF_8);
                        if (text.startsWith("/")) {
                            handleCommand(text);
                        } else {
                            broadcastLounge(text);
                        }
                        break;

                    case Frame.TYPE_DIRECT_MSG:
                        handleDirectMessage(payload);
                        break;

                    case Frame.TYPE_ACK:
                        if (payload.length == 4) {
                            int msgId = ((payload[0] & 0xFF) << 24) |
                                    ((payload[1] & 0xFF) << 16) |
                                    ((payload[2] & 0xFF) << 8) |
                                    (payload[3] & 0xFF);
                            SessionManager.handleAck(msgId);
                        }
                        break;

                    default:
                        break;
                }
            }

        } catch (IOException e) {
        } finally {
            cleanup();
        }
    }

    private void handleRegister(byte[] payload) throws IOException {
        String str = new String(payload, StandardCharsets.UTF_8);
        int space = str.indexOf(' ');
        if (space == -1) {
            sendSystemNotice("Invalid registration format.");
            return;
        }
        String username = str.substring(0, space);
        String password = str.substring(space + 1);

        if (!ProfileManager.createProfileIfAbsent(username, password)) {
            sendSystemNotice("Username is already taken. Please choose another username.");
            return;
        }

//        ProfileManager.createProfile(username, password);
        this.username = username;
        authenticated = true;
        SessionManager.addClient(username, this);
        sendSystemNotice("Registration successful! Welcome to \"Na-Kheyr\" Messenger!");
    }

    private void handleLogin(byte[] payload) throws IOException {
        String str = new String(payload, StandardCharsets.UTF_8);
        int space = str.indexOf(' ');
        if (space == -1) {
            sendSystemNotice("Invalid login format.");
            return;
        }
        String username = str.substring(0, space);
        String password = str.substring(space + 1);

        UserProfile profile = ProfileManager.getProfile(username);
        if (profile == null) {
            sendSystemNotice("Account not found. Please register first or check your spelling.");
            return;
        }

        if (!profile.password.equals(password)) {
            loginAttempts++;
            if (loginAttempts >= 3) {
                sendSystemNotice("Disconnected from server: Maximum password attempts exceeded.");
                socket.close();
            } else {
                int remaining = 3 - loginAttempts;
                sendSystemNotice("Incorrect password. Attempts remaining: " + remaining);
            }
            return;
        }

        this.username = username;
        authenticated = true;
        SessionManager.addClient(username, this);
        sendSystemNotice("Login successful! Welcome back, " + username + ".");

        List<OfflineMessage> offlineMsgs = OfflineQueueManager.loadAndClearOfflineMessages(username);
        for (OfflineMessage msg : offlineMsgs) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(msg.messageId);
            byte[] senderBytes = msg.sender.getBytes(StandardCharsets.UTF_8);
            dos.writeByte(senderBytes.length);
            dos.write(senderBytes);
            String formatted = "[DM] " + msg.sender + ": " + msg.text;
            dos.write(formatted.getBytes(StandardCharsets.UTF_8));
            dos.flush();
            Frame.sendFrame(out, Frame.TYPE_DIRECT_MSG, baos.toByteArray());
            ProfileManager.incrementReceived(username);
        }
    }

    private void handleCommand(String text) throws IOException {
        if (text.equals("/profile")) {
            UserProfile profile = ProfileManager.getProfile(username);
            if (profile == null) {
                sendSystemNotice("Profile not found.");
                return;
            }
            String stars = "*".repeat(profile.password.length());
            String msg = "=== User Profile ===\n" +
                    "Username: " + profile.username + "\n" +
                    "Password: [HIDDEN] " + stars + "\n" +
                    "Messages Sent: " + profile.messagesSent + "\n" +
                    "Messages Received: " + profile.messagesReceived + "\n" +
                    "====================";
            sendSystemNotice(msg);
        } else if (text.startsWith("/change_password")) {
            String[] parts = text.trim().split("\\s+", 3);

            if (parts.length < 2) {
                sendSystemNotice("Invalid format. Use: /change_password <old> <new>");
                return;
            }

            String oldPass = parts[1];
            String newPass = (parts.length == 3) ? parts[2] : "";

            String result = ProfileManager.changePassword(username, oldPass, newPass);
            if (result.equals("SUCCESS")) {
                sendSystemNotice("Password updated successfully.");
            } else {
                sendSystemNotice(result);
            }
        }
    }

    private void broadcastLounge(String text) throws IOException {
        if (SessionManager.isMuted(username)) {
            sendSystemNotice("You are currently muted and cannot send messages.");
            return;
        }

        String broadcastMsg = "[LOUNGE] " + username + ": " + text;
        byte[] payload = broadcastMsg.getBytes(StandardCharsets.UTF_8);
        for (ConnectionThread client : SessionManager.getOnlineClients().values()) {
            try {
                Frame.sendFrame(client.out, Frame.TYPE_LOUNGE_MSG, payload);
            } catch (Exception ignored) {}
        }
    }

    private void handleDirectMessage(byte[] payload) throws IOException {
        if (SessionManager.isMuted(username)) {
            sendSystemNotice("You are currently muted and cannot send messages.");
            return;
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        DataInputStream dis = new DataInputStream(bais);
        int msgId = dis.readInt();
        int nameLen = dis.readUnsignedByte();
        byte[] nameBytes = new byte[nameLen];
        dis.readFully(nameBytes);
        String target = new String(nameBytes, StandardCharsets.UTF_8);
        byte[] remaining = new byte[dis.available()];
        dis.readFully(remaining);
        String text = new String(remaining, StandardCharsets.UTF_8);

        if (!ProfileManager.userExists(target)) {
            sendSystemNotice("Target user " + target + " is offline or does not exist.");
            return;
        }

        ProfileManager.incrementSent(username);

        ConnectionThread targetClient = SessionManager.getClient(target);
        if (targetClient == null) {
            OfflineQueueManager.enqueueMessage(target, new OfflineMessage(msgId, username, text));
            sendSystemNotice("User " + target + " is offline. Message enqueued for offline delivery.");
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(msgId);
        byte[] senderBytes = username.getBytes(StandardCharsets.UTF_8);
        dos.writeByte(senderBytes.length);
        dos.write(senderBytes);
        String formatted = "[DM] " + username + ": " + text;
        dos.write(formatted.getBytes(StandardCharsets.UTF_8));
        dos.flush();
        byte[] dmPayload = baos.toByteArray();

        SessionManager.registerPendingMessage(msgId, username, target, text);
        SessionManager.startAckTimer(msgId, target, this);

        try {
            Frame.sendFrame(targetClient.out, Frame.TYPE_DIRECT_MSG, dmPayload);
            sendSystemNotice("Sent DM to " + target + " with ID " + msgId);
        } catch (Exception e) {
            SessionManager.cancelPendingDelivery(msgId);
            try {
                targetClient.getSocket().close();
            } catch (Exception ignored) {}
            SessionManager.removeClient(target);
            OfflineQueueManager.enqueueMessage(target, new OfflineMessage(msgId, username, text));
            sendSystemNotice("User " + target + " is offline. Message enqueued for offline delivery.");
        }
    }

    private void sendSystemNotice(String msg) throws IOException {
        byte[] payload = msg.getBytes(StandardCharsets.UTF_8);
        Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, payload);
    }

    private void cleanup() {
        if (username != null) {
            SessionManager.removeClient(username);
        }
        try { socket.close(); } catch (Exception ignored) {}
    }

    public DataOutputStream getOut() {
        return out;
    }

    public String getUsername() {
        return username;
    }

    public Socket getSocket() {
        return socket;
    }
}