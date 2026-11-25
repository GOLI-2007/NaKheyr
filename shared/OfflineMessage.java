package shared;

import java.io.Serializable;

public class OfflineMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    public int messageId;
    public String sender;
    public String text;

    public OfflineMessage(int messageId, String sender, String text) {
        this.messageId = messageId;
        this.sender = sender;
        this.text = text;
    }
}