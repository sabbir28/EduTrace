package org.sabbir.edutrace.data.models;

import org.json.JSONObject;

/**
 * Model representing user's daily study match status compared to previous sessions.
 */
public class TimerMatchStatus {
    public final boolean matched;
    public final long todaySeconds;
    public final long targetSeconds;
    public final String message;
    public final double percentage;

    public TimerMatchStatus(boolean matched, long todaySeconds, long targetSeconds, String message, double percentage) {
        this.matched = matched;
        this.todaySeconds = todaySeconds;
        this.targetSeconds = targetSeconds;
        this.message = message != null ? message : "";
        this.percentage = percentage;
    }

    public static TimerMatchStatus fromJson(JSONObject obj) {
        if (obj == null) return new TimerMatchStatus(false, 0, 0, "", 0.0);
        boolean matched = obj.optBoolean("matched", false);
        long todaySec = obj.optLong("today_seconds", 0);
        long targetSec = obj.optLong("target_seconds", 0);
        String msg = obj.optString("message", "");
        double pct = obj.optDouble("percentage", 0.0);
        return new TimerMatchStatus(matched, todaySec, targetSec, msg, pct);
    }
}
