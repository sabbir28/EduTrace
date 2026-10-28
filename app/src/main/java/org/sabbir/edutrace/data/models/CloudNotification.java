package org.sabbir.edutrace.data.models;

import org.json.JSONObject;

/**
 * Model representing an in-app cloud notification from EduTrace Server.
 */
public class CloudNotification {
    public final int id;
    public final String title;
    public final String message;
    public final String type;
    public boolean isRead;
    public final long createdAtEpoch;
    public final String actionUrl;
    public final String metadataJson;

    public CloudNotification(int id, String title, String message, String type, boolean isRead,
                             long createdAtEpoch, String actionUrl, String metadataJson) {
        this.id = id;
        this.title = title != null ? title : "";
        this.message = message != null ? message : "";
        this.type = type != null ? type : "info";
        this.isRead = isRead;
        this.createdAtEpoch = createdAtEpoch;
        this.actionUrl = actionUrl != null ? actionUrl : "";
        this.metadataJson = metadataJson != null ? metadataJson : "";
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("title", title);
            obj.put("message", message);
            obj.put("type", type);
            obj.put("is_read", isRead);
            obj.put("created_at_epoch", createdAtEpoch);
            obj.put("action_url", actionUrl);
            obj.put("metadata", metadataJson);
        } catch (Exception ignored) {}
        return obj;
    }

    public static CloudNotification fromJson(JSONObject obj) {
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
        String meta = obj.optString("metadata", "");
        return new CloudNotification(id, title, message, type, isRead, createdAt, actionUrl, meta);
    }

    private static long parseIsoEpoch(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return System.currentTimeMillis();
        try {
            if (dateStr.matches("^\\d+$")) return Long.parseLong(dateStr);
            String clean = dateStr.replace("Z", "");
            if (clean.contains(".")) clean = clean.substring(0, clean.indexOf('.'));
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            java.text.SimpleDateFormat sdfAlt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            java.util.Date d = null;
            try {
                d = sdf.parse(clean);
            } catch (Exception e) {
                d = sdfAlt.parse(clean);
            }
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
