package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONObject;

/**
 * Model representing the authenticated user's ranking summary across timeframes.
 * Direct port of Android MyRankingSummary.java.
 */
public class DesktopMyRankingSummary {
    public final int allTimeRank;
    public final int dailyRank;
    public final int weeklyRank;
    public final int monthlyRank;
    public final int streakDays;
    public final String tier;
    public final long totalSeconds;
    public final double totalHours;
    public final String formattedTime;

    public DesktopMyRankingSummary(int allTimeRank, int dailyRank, int weeklyRank, int monthlyRank,
                                   int streakDays, String tier, long totalSeconds, double totalHours,
                                   String formattedTime) {
        this.allTimeRank = allTimeRank;
        this.dailyRank = dailyRank;
        this.weeklyRank = weeklyRank;
        this.monthlyRank = monthlyRank;
        this.streakDays = streakDays;
        this.tier = tier != null ? tier : "Bronze";
        this.totalSeconds = totalSeconds;
        this.totalHours = totalHours;
        this.formattedTime = formattedTime != null ? formattedTime : formatSeconds(totalSeconds);
    }

    public static DesktopMyRankingSummary fromJson(JSONObject obj) {
        if (obj == null) return new DesktopMyRankingSummary(0, 0, 0, 0, 0, "Bronze", 0, 0.0, "0m");
        int allTime = obj.optInt("all_time_rank", obj.optInt("rank", 0));
        int daily = obj.optInt("daily_rank", 0);
        int weekly = obj.optInt("weekly_rank", 0);
        int monthly = obj.optInt("monthly_rank", 0);
        int streak = obj.optInt("streak_days", 0);
        String tier = obj.optString("tier", obj.optString("current_tier", "Bronze"));
        long totalSec = obj.optLong("total_seconds", 0);
        double totalHours = obj.optDouble("total_hours", totalSec / 3600.0);
        String formatted = obj.optString("study_time_formatted", formatSeconds(totalSec));

        return new DesktopMyRankingSummary(allTime, daily, weekly, monthly, streak, tier, totalSec, totalHours, formatted);
    }

    private static String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
}
