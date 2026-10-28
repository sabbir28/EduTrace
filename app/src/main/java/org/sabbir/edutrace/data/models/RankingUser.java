package org.sabbir.edutrace.data.models;

import org.json.JSONObject;

/**
 * Model representing a ranked student on the EduTrace competitive leaderboard.
 */
public class RankingUser {
    public final int rank;
    public final int userId;
    public final String username;
    public final String displayName;
    public final String avatarUrl;
    public final String tier;
    public final long totalSeconds;
    public final double totalHours;
    public final String formattedTime;
    public final int streakDays;
    public final boolean isCurrentUser;

    public RankingUser(int rank, int userId, String username, String displayName, String avatarUrl,
                       String tier, long totalSeconds, double totalHours, String formattedTime,
                       int streakDays, boolean isCurrentUser) {
        this.rank = rank;
        this.userId = userId;
        this.username = username != null ? username : "";
        this.displayName = displayName != null && !displayName.isEmpty() ? displayName : this.username;
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
        this.tier = tier != null ? tier : "Bronze";
        this.totalSeconds = totalSeconds;
        this.totalHours = totalHours;
        this.formattedTime = formattedTime != null ? formattedTime : formatSeconds(totalSeconds);
        this.streakDays = streakDays;
        this.isCurrentUser = isCurrentUser;
    }

    public static RankingUser fromJson(JSONObject obj) {
        if (obj == null) return null;
        int rank = obj.optInt("rank", 0);
        int userId = obj.optInt("user_id", -1);
        String username = obj.optString("username", "Scholar");
        String displayName = obj.optString("display_name", username);
        String avatarUrl = obj.optString("avatar_url", "");
        String tier = obj.optString("tier", "Bronze");
        long totalSec = obj.optLong("total_seconds", 0);
        double totalHours = obj.optDouble("total_hours", totalSec / 3600.0);
        String formatted = obj.optString("study_time_formatted", formatSeconds(totalSec));
        int streak = obj.optInt("streak_days", 0);
        boolean isCurrent = obj.optBoolean("is_current_user", false);

        return new RankingUser(rank, userId, username, displayName, avatarUrl, tier,
                totalSec, totalHours, formatted, streak, isCurrent);
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) return "0m";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h > 0 && m > 0) return h + "h " + m + "m";
        if (h > 0) return h + "h";
        return m + "m";
    }
}
