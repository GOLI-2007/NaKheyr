package server;

import shared.UserProfile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProfileManager {
    private static final Path PROFILES_DIR = Paths.get("profiles");

    static {
        try {
            Files.createDirectories(PROFILES_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getProfilePath(String username) {
        return PROFILES_DIR.resolve(username + ".ser");
    }

    private static UserProfile loadFromDisk(String username) {
        Path path = getProfilePath(username);
        if (!Files.exists(path)) return null;
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {
            return (UserProfile) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveToDisk(UserProfile profile) {
        Path path = getProfilePath(profile.username);
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(path))) {
            oos.writeObject(profile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean userExists(String username) {
        return Files.exists(getProfilePath(username));
    }

    public static synchronized UserProfile getProfile(String username) {
        return loadFromDisk(username);
    }

    public static synchronized void createProfile(String username, String password) {
        UserProfile profile = new UserProfile(username, password);
        saveToDisk(profile);
    }

    public static synchronized String changePassword(String username, String oldPass, String newPass) {
        UserProfile profile = getProfile(username);
        if (profile == null) return "Profile not found.";
        if (!profile.password.equals(oldPass)) {
            return "Failed to update password. Current password does not match.";
        }
        if (newPass == null || newPass.isEmpty() || newPass.equals(oldPass)) {
            return "Failed to update password. New password cannot be empty or identical to the old password.";
        }
        profile.password = newPass;
        saveToDisk(profile);
        return "SUCCESS";
    }

    public static synchronized void incrementSent(String username) {
        UserProfile profile = getProfile(username);
        if (profile != null) {
            profile.messagesSent++;
            saveToDisk(profile);
        }
    }

    public static synchronized void incrementReceived(String username) {
        UserProfile profile = getProfile(username);
        if (profile != null) {
            profile.messagesReceived++;
            saveToDisk(profile);
        }
    }

    public static synchronized boolean createProfileIfAbsent(String username, String password) {
        if (Files.exists(getProfilePath(username))) {
            return false;
        }
        UserProfile profile = new UserProfile(username, password);
        saveToDisk(profile);
        return true;
    }
}