package client;

import shared.Frame;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class ConsoleReaderTask implements Runnable {
    private final DataOutputStream out;
    private final ClientState state;
    private final Socket socket;
    private final Random random = new Random();
    private volatile boolean running = true;

    public ConsoleReaderTask(DataOutputStream out, ClientState state, Socket socket) {
        this.out = out;
        this.state = state;
        this.socket = socket;
    }

    /**
     * این متد در یک ترد مجزا اجرا می‌شود و وظیفه دارد ورودی‌های متنی کاربر را
     * از کنسول به صورت مداوم خوانده، آن‌ها را پردازش کرده، به فریم مناسب تبدیل نموده
     * و در نهایت به سرور ارسال کند.
     */
    @Override
    public void run() {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            while (!state.isAuthenticated() && running) {
                printMenu();
                String choice = console.readLine();
                if (choice == null) break;

                if (choice.trim().equals("1")) {
                    // Register
                    System.out.println("[Register]");
                    System.out.print("Enter desired username: ");
                    String username = console.readLine();
                    System.out.print("Enter password: ");
                    String password = console.readLine();
                    String payloadStr = username + " " + password;
                    byte[] payload = payloadStr.getBytes(StandardCharsets.UTF_8);
                    state.prepareForResponse();
                    Frame.sendFrame(out, Frame.TYPE_REGISTER, payload);
                    if (state.waitForAuth()) {
                        break;
                    }
                } else if (choice.trim().equals("2")) {
                    // Login
                    System.out.println("[Login]");
                    System.out.print("Enter username: ");
                    String username = console.readLine();
                    System.out.print("Enter password: ");
                    String password = console.readLine();
                    String payloadStr = username + " " + password;
                    byte[] payload = payloadStr.getBytes(StandardCharsets.UTF_8);
                    state.prepareForResponse();
                    Frame.sendFrame(out, Frame.TYPE_LOGIN, payload);

                    if (state.waitForAuth()) {
                        break;
                    }

                    while (!state.isAuthenticated() && running && state.getLastNotice().contains("Incorrect password")) {
                        System.out.print("Enter password: ");
                        String retryPass = console.readLine();
                        if (retryPass == null) break;
                        String retryPayloadStr = username + " " + retryPass;
                        state.prepareForResponse();
                        Frame.sendFrame(out, Frame.TYPE_LOGIN, retryPayloadStr.getBytes(StandardCharsets.UTF_8));
                        if (state.waitForAuth()) {
                            break;
                        }
                    }
                } else {
                    System.out.println("Invalid option. Enter 1 or 2.");
                }
            }

            while (running) {
                String line = console.readLine();
                if (line == null) break;

                if (line.equalsIgnoreCase("exit")) {
                    running = false;
                    socket.close();
                    System.exit(0);
                    break;
                }

                if (line.startsWith("@")) {
                    int spaceIdx = line.indexOf(' ');
                    if (spaceIdx == -1) {
                        System.out.println("Invalid DM format. Use: @username message");
                        continue;
                    }
                    String target = line.substring(1, spaceIdx);
                    String msg = line.substring(spaceIdx + 1);
                    if (target.isEmpty() || msg.isEmpty()) {
                        System.out.println("Invalid DM format.");
                        continue;
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    int msgId = random.nextInt(Integer.MAX_VALUE);
                    dos.writeInt(msgId);
                    byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
                    dos.writeByte(targetBytes.length);
                    dos.write(targetBytes);
                    dos.write(msg.getBytes(StandardCharsets.UTF_8));
                    dos.flush();
                    Frame.sendFrame(out, Frame.TYPE_DIRECT_MSG, baos.toByteArray());
                } else {
                    byte[] payload = line.getBytes(StandardCharsets.UTF_8);
                    Frame.sendFrame(out, Frame.TYPE_LOUNGE_MSG, payload);
                }
            }
        } catch (Exception e) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void printMenu() {
        System.out.println("========================================");
        System.out.println("Welcome to \"Na-Kheyr\" Messenger!");
        System.out.println("1. Register");
        System.out.println("2. Login");
        System.out.print("Choose an option (1 or 2): ");
    }
}