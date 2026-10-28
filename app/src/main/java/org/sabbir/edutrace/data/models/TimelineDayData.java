package org.sabbir.edutrace.data.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a full 24-hour day routine analysis.
 */
public class TimelineDayData {
    public final String date;
    public final String wakeTime;
    public final String sleepTime;
    public final double totalSleepHours;
    public final double totalStudyHours;
    public final double totalIdleHours;
    public final List<TimelineSegment> segments;

    public TimelineDayData(String date, String wakeTime, String sleepTime, double totalSleepHours,
                           double totalStudyHours, double totalIdleHours, List<TimelineSegment> segments) {
        this.date = date != null ? date : "";
        this.wakeTime = wakeTime != null ? wakeTime : "--:--";
        this.sleepTime = sleepTime != null ? sleepTime : "--:--";
        this.totalSleepHours = totalSleepHours;
        this.totalStudyHours = totalStudyHours;
        this.totalIdleHours = totalIdleHours;
        this.segments = segments != null ? segments : new ArrayList<>();
    }

    public static TimelineDayData fromJson(JSONObject obj) {
        if (obj == null) return new TimelineDayData("", "--:--", "--:--", 0, 0, 0, new ArrayList<>());
        String date = obj.optString("date", "");
        String wakeTime = obj.optString("wake_time", "--:--");
        String sleepTime = obj.optString("sleep_time", "--:--");
        double sleepHours = obj.optDouble("total_sleep_hours", 0.0);
        double studyHours = obj.optDouble("total_study_hours", 0.0);
        double idleHours = obj.optDouble("total_idle_hours", 0.0);

        List<TimelineSegment> list = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("timeline");
        if (arr == null) arr = obj.optJSONArray("segments");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s != null) {
                    TimelineSegment seg = TimelineSegment.fromJson(s);
                    if (seg != null) list.add(seg);
                }
            }
        }

        return new TimelineDayData(date, wakeTime, sleepTime, sleepHours, studyHours, idleHours, list);
    }
}
