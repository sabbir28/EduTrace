package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONObject;

public class DesktopRankingUser {
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

    public DesktopRankingUser(int rank, int userId, String username, String displayName, String avatarUrl,
                              String tier, long totalSeconds, double totalHours, String formattedTime,
                              int streakDays, boolean isCurrentUser) {
        this.rank = rank;
        this.userId = userId;
        this.username = username != null ? username : "";
        this.displayName = (displayName != null && !displayName.trim().isEmpty()) ? displayName : this.username;
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
        this.tier = tier != null ? tier : "Bronze";
        this.totalSeconds = totalSeconds;
        this.totalHours = totalHours;
        this.formattedTime = formattedTime != null ? formattedTime : formatSeconds(totalSeconds);
        this.streakDays = streakDays;
        this.isCurrentUser = isCurrentUser;
    }

    public static DesktopRankingUser fromJson(JSONObject obj) {
        if (obj == null) return null;
        int rank = obj.optInt("rank", 0);
        int userId = obj.optInt("user_id", -1);

        String rawDisplayName = obj.optString("display_name", "").trim();
        String rawName = obj.optString("name", "").trim();
        String rawFirstName = obj.optString("first_name", "").trim();
        String rawUsername = obj.optString("username", "").trim();
        String rawUserLabel = obj.optString("user_label", "").trim();

        String displayName;
        if (!rawDisplayName.isEmpty()) displayName = rawDisplayName;
        else if (!rawName.isEmpty()) displayName = rawName;
        else if (!rawFirstName.isEmpty()) displayName = rawFirstName;
        else if (!rawUsername.isEmpty()) displayName = rawUsername;
        else if (!rawUserLabel.isEmpty()) displayName = rawUserLabel;
        else displayName = "Scholar #" + (userId > 0 ? userId : "");

        String username = !rawUsername.isEmpty() ? rawUsername : displayName;
        String avatarUrl = obj.optString("avatar_url", "");
        String tier = obj.optString("tier", "Bronze");
        long totalSec = obj.optLong("total_seconds", 0);
        double totalHours = obj.optDouble("total_hours", totalSec / 3600.0);

        String formatted = obj.optString("formatted_time", "").trim();
        if (formatted.isEmpty()) {
            formatted = obj.optString("study_time_formatted", formatSeconds(totalSec));
        }

        int streak = obj.optInt("streak_days", 0);
        boolean isCurrent = obj.optBoolean("is_current_user", false);

        return new DesktopRankingUser(rank, userId, username, displayName, avatarUrl, tier,
                totalSec, totalHours, formatted, streak, isCurrent);
    }

    public static String formatSeconds(long totalSec) {
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
