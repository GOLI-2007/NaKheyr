package shared;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

public class Frame {
    public static final byte TYPE_LOUNGE_MSG = 0x01;
    public static final byte TYPE_DIRECT_MSG = 0x02;
    public static final byte TYPE_ACK = 0x03;
    public static final byte TYPE_REGISTER = 0x04;
    public static final byte TYPE_ROOT_CMD = 0x05;
    public static final byte TYPE_SYSTEM_NOTICE = 0x06;
    public static final byte TYPE_LOGIN = 0x07;

    /**
     * این متد وظیفه دارد یک فریم کامل را بر اساس پروتکل باینری اختصاصی از استریم ورودی بخواند.
     * شما باید مشکل تکه‌تکه شدن پیام (Fragmentation) را در اینجا با خواندن دقیق بایت‌ها مدیریت کنید.
     */
    public static byte[] readNextFrame(DataInputStream in, Socket socket) throws IOException {
        if (socket != null) socket.setSoTimeout(0);

        int b1 = in.read();
        if (b1 == -1) throw new EOFException("Connection closed by peer");

        if (socket != null) socket.setSoTimeout(500);

        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();

        if (b2 == -1 || b3 == -1 || b4 == -1) throw new EOFException("Incomplete length prefix");

        int lengthPrefix = ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);

        byte[] frameData = new byte[lengthPrefix];
        in.readFully(frameData);
        return frameData;
    }

    /**
     * این متد یک فریم جدید با توجه به نوع و بدنه داده می‌سازد و آن را روی استریم خروجی به سمت شبکه ارسال می‌کند.
     */
    public static void sendFrame(DataOutputStream out, byte type, byte[] payload) throws IOException {
        synchronized (out) {
            int lengthPrefix = 1 + payload.length;
            out.writeInt(lengthPrefix);
            out.writeByte(type);
            out.write(payload);
            out.flush();
        }
    }
}