package server;

import shared.Frame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RelayServer {
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static ServerSocket serverSocket;
    private static volatile boolean running = true;

    /**
     * نقطه ورود برنامه سرور.
     * وظیفه این متد خواندن پورت از آرگومان‌ها، ایجاد ServerSocket،
     * مدیریت Thread Pool و واگذاری سوکتِ کلاینت‌های متصل شده به ConnectionThread است.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java server.RelayServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        serverSocket = new ServerSocket(port);
        System.out.println("Relay Server started on port " + port);

        new Thread(RelayServer::adminConsole).start();

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new ConnectionThread(clientSocket));
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }
        threadPool.shutdown();
    }

    private static void adminConsole() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                String line = reader.readLine();
                if (line == null) break;
                processAdminCommand(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processAdminCommand(String cmd) {
        if (cmd.equals("/list")) {
            System.out.println("Online users: " + SessionManager.getOnlineClients().keySet());
        } else if (cmd.startsWith("/kick ")) {
            String username = cmd.substring(6).trim();
            ConnectionThread client = SessionManager.getClient(username);
            if (client != null) {
                try {
                    byte[] payload = {(byte) 0xDF};
                    Frame.sendFrame(client.getOut(), Frame.TYPE_ROOT_CMD, payload);
                    client.socket.close();
                    SessionManager.removeClient(username);
                    String announcement = "[LOUNGE] \"ANNOUNCEMENT: " + username + " has been kicked by the administrator.\"";
                    byte[] annPayload = announcement.getBytes(StandardCharsets.UTF_8);
                    for (ConnectionThread c : SessionManager.getOnlineClients().values()) {
                        try {
                            if (!c.getUsername().equals(username)) {
                                Frame.sendFrame(c.getOut(), Frame.TYPE_LOUNGE_MSG, annPayload);
                            }
                        } catch (Exception ignored) {}
                    }
                    System.out.println("Kicked " + username);
                } catch (Exception e) {
                    System.err.println("Failed to kick " + username);
                }
            } else {
                System.out.println("User " + username + " is not online.");
            }
        } else if (cmd.startsWith("/mute ")) {
            String[] parts = cmd.split(" ");
            if (parts.length == 3) {
                String username = parts[1];
                try {
                    int seconds = Integer.parseInt(parts[2]);
                    SessionManager.muteUser(username, seconds);
                    System.out.println("Muted " + username + " for " + seconds + " seconds.");
                } catch (NumberFormatException e) {
                    System.out.println("Invalid duration.");
                }
            } else {
                System.out.println("Usage: /mute <username> <seconds>");
            }
        } else if (cmd.equals("/shutdown")) {
            String notice = "Server is shutting down. Disconnecting...";
            byte[] payload = notice.getBytes(StandardCharsets.UTF_8);
            for (ConnectionThread client : SessionManager.getOnlineClients().values()) {
                try {
                    Frame.sendFrame(client.getOut(), Frame.TYPE_SYSTEM_NOTICE, payload);
                    client.socket.close();
                } catch (Exception ignored) {}
            }
            OfflineQueueManager.flushAllToDisk();
            SessionManager.shutdown();
            threadPool.shutdown();

            running = false;
            try { serverSocket.close(); } catch (Exception ignored) {}
            System.out.println("Shutting down...");
            System.exit(0);
        } else {
            System.out.println("Unknown admin command.");
        }
    }
}