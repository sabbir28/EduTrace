package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONObject;

/**
 * Model representing user's daily study match status compared to server scholars.
 * Direct port of Android TimerMatchStatus.java.
 */
public class DesktopTimerMatchStatus {
    public final boolean matched;
    public final long todaySeconds;
    public final long targetSeconds;
    public final String message;
    public final double percentage;
    public final int activeScholarsCount;

    public DesktopTimerMatchStatus(boolean matched, long todaySeconds, long targetSeconds, String message, double percentage, int activeScholarsCount) {
        this.matched = matched;
        this.todaySeconds = todaySeconds;
        this.targetSeconds = targetSeconds;
        this.message = message != null ? message : "";
        this.percentage = percentage;
        this.activeScholarsCount = activeScholarsCount;
    }

    public static DesktopTimerMatchStatus fromJson(JSONObject obj) {
        if (obj == null) return new DesktopTimerMatchStatus(false, 0, 0, "", 0.0, 0);
        boolean matched = obj.optBoolean("matched", false);
        long todaySec = obj.optLong("today_seconds", 0);
        long targetSec = obj.optLong("target_seconds", 0);
        String msg = obj.optString("message", "");
        double pct = obj.optDouble("percentage", 0.0);
        int scholars = obj.optInt("active_scholars", obj.optInt("count", 1));
        return new DesktopTimerMatchStatus(matched, todaySec, targetSec, msg, pct, scholars);
    }
}

