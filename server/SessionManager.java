package server;

import shared.OfflineMessage;

import java.util.concurrent.*;

public class SessionManager {

    private static final ConcurrentHashMap<String, ConnectionThread> onlineClients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> muteExpiry = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ScheduledFuture<?>> pendingAcks = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService timerExecutor = new ScheduledThreadPoolExecutor(4);

    /**
     * یک کلاینت جدید را پس از احراز هویت موفقیت‌آمیز، به لیست کاربران آنلاین (ConcurrentHashMap) اضافه می‌کند.
     */
    public static void addClient(String username, ConnectionThread clientThread) {
        onlineClients.put(username, clientThread);
    }

    /**
     * کلاینت را از لیست کاربران آنلاین سیستم حذف می‌کند (مثلاً هنگام قطع ارتباط یا اخراج).
     */
    public static void removeClient(String username) {
        onlineClients.remove(username);
//        muteExpiry.remove(username);
    }

    /**
     * کاربر مشخص شده را برای مدت زمان تعیین شده (به ثانیه) ساکت (Mute) می‌کند.
     * زمان انقضا باید به صورت پویا (Time-based) محاسبه و ذخیره شود.
     */
    public static void muteUser(String username, int durationInSeconds) {
        long expiry = System.currentTimeMillis() + durationInSeconds * 1000L;
        muteExpiry.put(username, expiry);
    }

    /**
     * بررسی می‌کند که آیا کاربر در حال حاضر میوت است یا خیر.
     * در این متد باید زمان فعلی سیستم با زمان انقضای محرومیت کاربر مقایسه شود.
     */
    public static boolean isMuted(String username) {
        Long expiry = muteExpiry.get(username);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            muteExpiry.remove(username);
            return false;
        }
        return true;
    }

    public static void startAckTimer(int messageId, String targetUsername, ConnectionThread senderThread) {
        ScheduledFuture<?> future = timerExecutor.schedule(() -> {
            ConnectionThread targetClient = onlineClients.get(targetUsername);
            if (targetClient != null) {
                try {
                    targetClient.getSocket().close();
                } catch (Exception ignored) {}
                SessionManager.removeClient(targetUsername);
            }

            PendingMessage pending = pendingMessageMap.remove(messageId);
            if (pending != null) {
                OfflineQueueManager.enqueueMessage(targetUsername,
                        new OfflineMessage(messageId, pending.sender, pending.text));
            }
            pendingAcks.remove(messageId);
        }, 5, TimeUnit.SECONDS);

        pendingAcks.put(messageId, future);
    }

    private static final ConcurrentHashMap<Integer, PendingMessage> pendingMessageMap = new ConcurrentHashMap<>();

    public static void registerPendingMessage(int messageId, String sender, String target, String text) {
        pendingMessageMap.put(messageId, new PendingMessage(sender, target, text));
    }

    public static void handleAck(int messageId) {
        ScheduledFuture<?> future = pendingAcks.remove(messageId);
        if (future != null) {
            future.cancel(false);
            PendingMessage pm = pendingMessageMap.remove(messageId);
            if (pm != null) {
                ProfileManager.incrementReceived(pm.target);
            }
        }
    }

    public static void shutdown() {
        timerExecutor.shutdownNow();
    }

    static class PendingMessage {
        String sender, target, text;
        PendingMessage(String sender, String target, String text) {
            this.sender = sender;
            this.target = target;
            this.text = text;
        }
    }

    public static void cancelPendingDelivery(int messageId) {
        ScheduledFuture<?> future = pendingAcks.remove(messageId);
        if (future != null) {
            future.cancel(false);
        }
        pendingMessageMap.remove(messageId);
    }

    public static ConnectionThread getClient(String username) {return onlineClients.get(username);}

    public static ConcurrentHashMap<String, ConnectionThread> getOnlineClients() {return onlineClients;}
}