package server;

import shared.OfflineMessage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class OfflineQueueManager {
    private static final Path OFFLINE_DIR = Paths.get("offline_data");
    private static final int MAX_IN_MEMORY_LIMIT = 5;
    private static final ConcurrentHashMap<String, List<OfflineMessage>> memoryQueues = new ConcurrentHashMap<>();

    static {
        try {
            Files.createDirectories(OFFLINE_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getOfflinePath(String username) {
        return OFFLINE_DIR.resolve(username + ".dat");
    }

    private static List<OfflineMessage> loadFromDisk(String username) {
        Path path = getOfflinePath(username);
        if (!Files.exists(path)) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {
            return (List<OfflineMessage>) ois.readObject();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void writeToDisk(String username, List<OfflineMessage> list) {
        Path path = getOfflinePath(username);
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(path))) {
            oos.writeObject(list);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void deleteDiskFile(String username) {
        Path path = getOfflinePath(username);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * این متد پیام‌ها را به صف آفلاین کاربر اضافه می‌کند.
     * در صورتی که تعداد پیام‌ها از حد مجاز (MAX_IN_MEMORY_LIMIT) فراتر رفت،
     * کل صف باید روی دیسک (در پوشه offline_data) با استفاده از ObjectOutputStream سریالایز شود.
     */

    public static void enqueueMessage(String username, OfflineMessage message) {
        List<OfflineMessage> list = memoryQueues.computeIfAbsent(username,
                k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            Path diskPath = getOfflinePath(username);
            if (Files.exists(diskPath)) {
                List<OfflineMessage> diskList = loadFromDisk(username);
                diskList.add(message);
                writeToDisk(username, diskList);
                list.clear();
                return;
            }

            list.add(message);
            if (list.size() > MAX_IN_MEMORY_LIMIT) {
                List<OfflineMessage> merged = new ArrayList<>(list);
                writeToDisk(username, merged);
                list.clear();
            }
        }
    }

    public static synchronized List<OfflineMessage> loadAndClearOfflineMessages(String username) {
        List<OfflineMessage> result = new ArrayList<>();

        List<OfflineMessage> diskList = loadFromDisk(username);
        result.addAll(diskList);
        deleteDiskFile(username);

        List<OfflineMessage> memList = memoryQueues.remove(username);
        if (memList != null) {
            synchronized (memList) {
                result.addAll(memList);
            }
        }
        return result;
    }

    public static synchronized void flushAllToDisk() {
        for (String user : memoryQueues.keySet()) {
            List<OfflineMessage> list = memoryQueues.get(user);
            if (list != null && !list.isEmpty()) {
                synchronized (list) {
                    Path diskPath = getOfflinePath(user);
                    if (Files.exists(diskPath)) {
                        List<OfflineMessage> diskList = loadFromDisk(user);
                        diskList.addAll(list);
                        writeToDisk(user, diskList);
                    } else {
                        writeToDisk(user, new ArrayList<>(list));
                    }
                    list.clear();
                }
            }
        }
    }
}