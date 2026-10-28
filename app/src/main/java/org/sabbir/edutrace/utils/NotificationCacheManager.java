package org.sabbir.edutrace.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.models.CloudNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance local cache manager for notifications.
 * Provides instant 0ms retrieval, stale-while-revalidate caching,
 * and optimistic UI updates for marking read and deleting alerts.
 */
public class NotificationCacheManager {
    private static final String PREF_NAME = "EduTrace_Notification_Cache";
    private static final String KEY_ITEMS = "cached_notification_items";
    private static final String KEY_UNREAD_COUNT = "cached_unread_count";
    private static final String KEY_HAS_CACHED = "cached_has_ever_cached";

    public static synchronized List<CloudNotification> getCachedNotifications(Context context) {
        if (context == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ITEMS, null);
        List<CloudNotification> list = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    CloudNotification n = CloudNotification.fromJson(obj);
                    if (n != null) list.add(n);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public static synchronized int getCachedUnreadCount(Context context) {
        if (context == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_UNREAD_COUNT, 0);
    }

    public static synchronized boolean hasCachedData(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_HAS_CACHED, false);
    }

    public static synchronized void saveNotifications(Context context, List<CloudNotification> list, int unreadCount) {
        if (context == null) return;
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (CloudNotification n : list) {
                if (n != null) {
                    arr.put(n.toJson());
                }
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, arr.toString())
                .putInt(KEY_UNREAD_COUNT, Math.max(0, unreadCount))
                .putBoolean(KEY_HAS_CACHED, true)
                .apply();
    }

    public static synchronized void markItemRead(Context context, int notificationId) {
        if (context == null) return;
        List<CloudNotification> list = getCachedNotifications(context);
        int unread = getCachedUnreadCount(context);
        for (CloudNotification n : list) {
            if (n.id == notificationId && !n.isRead) {
                n.setRead(true);
                unread = Math.max(0, unread - 1);
            }
        }
        saveNotifications(context, list, unread);
    }

    public static synchronized void deleteItem(Context context, int notificationId) {
        if (context == null) return;
        List<CloudNotification> list = getCachedNotifications(context);
        int unread = getCachedUnreadCount(context);
        CloudNotification toRemove = null;
        for (CloudNotification n : list) {
            if (n.id == notificationId) {
                toRemove = n;
                if (!n.isRead) {
                    unread = Math.max(0, unread - 1);
                }
                break;
            }
        }
        if (toRemove != null) {
            list.remove(toRemove);
        }
        saveNotifications(context, list, unread);
    }

    public static synchronized void markAllRead(Context context) {
        if (context == null) return;
        List<CloudNotification> list = getCachedNotifications(context);
        for (CloudNotification n : list) {
            n.setRead(true);
        }
        saveNotifications(context, list, 0);
    }

    public static List<CloudNotification> getFallbackGuestNotifications(Context context) {
        List<CloudNotification> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        list.add(new CloudNotification(
                1001,
                "Welcome to EduTrace 🎓",
                "Start a study session to track your learning journey, maintain streaks, and climb the scholar ranks!",
                "welcome",
                false,
                now,
                "",
                ""
        ));
        list.add(new CloudNotification(
                1002,
                "Distraction Warden Active 🛡️",
                "EduTrace monitors study hours and helps you stay distraction-free with Automatic Library Mode.",
                "system",
                true,
                now - (3600 * 1000),
                "",
                ""
        ));
        return list;
    }
}
