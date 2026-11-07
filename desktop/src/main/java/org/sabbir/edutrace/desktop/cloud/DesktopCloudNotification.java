package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Model representing an in-app cloud notification from EduTrace Server.
 * Direct port of Android CloudNotification.java.
 */
public class DesktopCloudNotification {
    public final int id;
    public final String title;
    public final String message;
    public final String type;
    public boolean isRead;
    public final long createdAtEpoch;
    public final String actionUrl;

    public DesktopCloudNotification(int id, String title, String message, String type, boolean isRead,
                                    long createdAtEpoch, String actionUrl) {
        this.id = id;
        this.title = title != null ? title : "";
        this.message = message != null ? message : "";
        this.type = type != null ? type : "info";
        this.isRead = isRead;
        this.createdAtEpoch = createdAtEpoch;
        this.actionUrl = actionUrl != null ? actionUrl : "";
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    public static DesktopCloudNotification fromJson(JSONObject obj) {
        if (obj == null) return null;
        int id = obj.optInt("id", -1);
        String title = obj.optString("title", "Notification");
        String message = obj.optString("message", "");
        String type = obj.optString("type", "info");
        boolean isRead = obj.optBoolean("is_read", false);
        long createdAt = obj.optLong("created_at_epoch", 0);
        if (createdAt == 0) {
            String createdStr = obj.optString("created_at", "");
            createdAt = parseIsoEpoch(createdStr);
        }
        String actionUrl = obj.optString("action_url", "");
        return new DesktopCloudNotification(id, title, message, type, isRead, createdAt, actionUrl);
    }

    private static long parseIsoEpoch(String isoStr) {
        if (isoStr == null || isoStr.trim().isEmpty()) return System.currentTimeMillis();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(isoStr);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}

