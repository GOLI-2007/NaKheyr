package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class RelayClient {

    /**
     * نقطه ورود برنامه کلاینت.
     * وظیفه این متد برقراری اتصال سوکت به آدرس و پورت سرور، و سپس راه‌اندازی و اجرای
     * تردهای ServerListenerTask و ConsoleReaderTask برای ارتباط دوطرفه است.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java client.RelayClient <host> <port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        System.out.println("Connecting to Relay Server...");
        Socket socket = new Socket(host, port);
        System.out.println("Connected!");

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        ClientState state = new ClientState();

        Thread listenerThread = new Thread(new ServerListenerTask(in, out, state, socket));
        Thread consoleThread = new Thread(new ConsoleReaderTask(out, state, socket));

        listenerThread.start();
        consoleThread.start();

        listenerThread.join();
        consoleThread.join();
    }
}