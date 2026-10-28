package org.sabbir.edutrace.data.models;

import org.json.JSONObject;

/**
 * Model representing a contiguous slice of a 24-hour day (sleep, study, idle/lost time).
 */
public class TimelineSegment {
    public final String type; // "sleep", "study", "idle", "active"
    public final String start; // "HH:MM"
    public final String end;   // "HH:MM"
    public final int durationMinutes;
    public final String label;
    public final String colorHex;

    public TimelineSegment(String type, String start, String end, int durationMinutes, String label, String colorHex) {
        this.type = type != null ? type : "idle";
        this.start = start != null ? start : "00:00";
        this.end = end != null ? end : "00:00";
        this.durationMinutes = durationMinutes;
        this.label = label != null ? label : "";
        this.colorHex = colorHex != null ? colorHex : "#FFFFFF";
    }

    public static TimelineSegment fromJson(JSONObject obj) {
        if (obj == null) return null;
        String type = obj.optString("type", "idle");
        String start = obj.optString("start", "00:00");
        String end = obj.optString("end", "00:00");
        int duration = obj.optInt("duration_minutes", 0);
        String label = obj.optString("label", type);
        String color = obj.optString("color", defaultColorForType(type));

        return new TimelineSegment(type, start, end, duration, label, color);
    }

    public static String defaultColorForType(String type) {
        if (type == null) return "#FFFFFF";
        switch (type.toLowerCase()) {
            case "sleep":
                return "#1E1B4B";
            case "study":
                return "#2563EB";
            case "idle":
            default:
                return "#FFFFFF";
        }
    }
}
