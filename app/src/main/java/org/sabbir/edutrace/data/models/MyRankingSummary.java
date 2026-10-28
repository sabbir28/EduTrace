package org.sabbir.edutrace.data.models;

import org.json.JSONObject;

/**
 * Model representing the authenticated user's ranking summary across timeframes.
 */
public class MyRankingSummary {
    public final int allTimeRank;
    public final int dailyRank;
    public final int weeklyRank;
    public final int monthlyRank;
    public final int streakDays;
    public final String tier;
    public final long totalSeconds;
    public final double totalHours;
    public final String formattedTime;

    public MyRankingSummary(int allTimeRank, int dailyRank, int weeklyRank, int monthlyRank,
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
        this.formattedTime = formattedTime != null ? formattedTime : RankingUser.formatSeconds(totalSeconds);
    }

    public static MyRankingSummary fromJson(JSONObject obj) {
        if (obj == null) return new MyRankingSummary(0, 0, 0, 0, 0, "Bronze", 0, 0.0, "0m");
        int allTime = obj.optInt("all_time_rank", obj.optInt("rank", 0));
        int daily = obj.optInt("daily_rank", 0);
        int weekly = obj.optInt("weekly_rank", 0);
        int monthly = obj.optInt("monthly_rank", 0);
        int streak = obj.optInt("streak_days", 0);
        String tier = obj.optString("tier", obj.optString("current_tier", "Bronze"));
        long totalSec = obj.optLong("total_seconds", 0);
        double totalHours = obj.optDouble("total_hours", totalSec / 3600.0);
        String formatted = obj.optString("study_time_formatted", RankingUser.formatSeconds(totalSec));

        return new MyRankingSummary(allTime, daily, weekly, monthly, streak, tier, totalSec, totalHours, formatted);
    }
}
