package shared;

import java.io.Serializable;

public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;
    public String username;
    public String password;
    public int messagesSent;
    public int messagesReceived;

    public UserProfile(String username, String password) {
        this.username = username;
        this.password = password;
        this.messagesSent = 0;
        this.messagesReceived = 0;
    }
}