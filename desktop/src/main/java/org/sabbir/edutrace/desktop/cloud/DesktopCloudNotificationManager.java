package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages in-app notifications and real-time live timer matches.
 * Direct port of Android CloudRankingsManager notification and timer-match logic.
 */
public class DesktopCloudNotificationManager {

    private static DesktopCloudNotificationManager instance;

    public static synchronized DesktopCloudNotificationManager getInstance() {
        if (instance == null) {
            instance = new DesktopCloudNotificationManager();
        }
        return instance;
    }

    @FunctionalInterface
    public interface NotificationsCallback {
        void onSuccess(List<DesktopCloudNotification> notifications, int unreadCount);
        default void onError(String message) {}
    }

    @FunctionalInterface
    public interface UnreadCountCallback {
        void onSuccess(int unreadCount);
        default void onError(String message) {}
    }

    @FunctionalInterface
    public interface TimerMatchCallback {
        void onSuccess(DesktopTimerMatchStatus status);
        default void onError(String message) {}
    }

    @FunctionalInterface
    public interface ActionCallback {
        void onSuccess(String message);
        default void onError(String message) {}
    }

    private DesktopCloudNotificationManager() {}

    public void fetchNotifications(boolean unreadOnly, NotificationsCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sign in required to view notifications."));
            return;
        }

        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                String endpoint = "/notifications?unread_only=" + unreadOnly + "&limit=50";
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest(endpoint, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    List<DesktopCloudNotification> list = new ArrayList<>();
                    JSONArray arr = res.json.optJSONArray("notifications");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            DesktopCloudNotification n = DesktopCloudNotification.fromJson(item);
                            if (n != null) list.add(n);
                        }
                    }
                    int unreadCount = res.json.optInt("unread_count", 0);
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(list, unreadCount));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load notifications.";
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onError("Notification error: " + e.getMessage()));
                }
            }
        });
    }

    public void getUnreadNotificationCount(UnreadCountCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess(0));
            return;
        }

        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/notifications/unread-count", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    int count = res.json.optInt("unread_count", 0);
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(count));
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public void markNotificationAsRead(int notificationId, ActionCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) return;
        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/notifications/" + notificationId + "/read", "POST", null, token);
                if (res.isSuccess()) {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess("Notification marked as read"));
                }
            } catch (Exception ignored) {}
        });
    }

    public void markAllNotificationsAsRead(ActionCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) return;
        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/notifications/read-all", "POST", null, token);
                if (res.isSuccess()) {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess("All notifications marked as read"));
                }
            } catch (Exception ignored) {}
        });
    }

    public void checkTimerMatch(TimerMatchCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) return;
        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/notifications/timer-match", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    DesktopTimerMatchStatus status = DesktopTimerMatchStatus.fromJson(res.json);
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(status));
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}
