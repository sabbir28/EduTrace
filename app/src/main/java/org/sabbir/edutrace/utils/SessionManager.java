package org.sabbir.edutrace.utils;
import org.sabbir.edutrace.R;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the state of an active study session using SharedPreferences.
 * Allows the timer to persist across activity restarts and app kills.
 */
public class SessionManager {
    private static final String PREF_NAME = "EduTrace_ActiveSession";
    private static final String KEY_SUBJECT_ID = "subject_id";
    private static final String KEY_SUBJECT_NAME = "subject_name";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_TOTAL_BREAK_TIME = "total_break_time";
    private static final String KEY_DISTRACTION_COUNT = "distraction_count";
    private static final String KEY_IS_PAUSED = "is_paused";
    private static final String KEY_PAUSE_START_TIME = "pause_start_time";
    private static final String KEY_IS_ACTIVE = "is_active";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void startSession(int subjectId, String subjectName, long startTime) {
        prefs.edit()
                .putInt(KEY_SUBJECT_ID, subjectId)
                .putString(KEY_SUBJECT_NAME, subjectName)
                .putLong(KEY_START_TIME, startTime)
                .putLong(KEY_TOTAL_BREAK_TIME, 0)
                .putInt(KEY_DISTRACTION_COUNT, 0)
                .putBoolean(KEY_IS_PAUSED, false)
                .putBoolean(KEY_IS_ACTIVE, true)
                .apply();
    }

    public void updateSession(long totalBreakTime, int distractionCount) {
        prefs.edit()
                .putLong(KEY_TOTAL_BREAK_TIME, totalBreakTime)
                .putInt(KEY_DISTRACTION_COUNT, distractionCount)
                .apply();
    }

    public void setPaused(boolean paused, long pauseStartTime, long totalBreakTime) {
        prefs.edit()
                .putBoolean(KEY_IS_PAUSED, paused)
                .putLong(KEY_PAUSE_START_TIME, pauseStartTime)
                .putLong(KEY_TOTAL_BREAK_TIME, totalBreakTime)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isActive() {
        return prefs.getBoolean(KEY_IS_ACTIVE, false);
    }

    public int getSubjectId() {
        return prefs.getInt(KEY_SUBJECT_ID, -1);
    }

    public String getSubjectName() {
        return prefs.getString(KEY_SUBJECT_NAME, "");
    }

    public long getStartTime() {
        return prefs.getLong(KEY_START_TIME, 0);
    }

    public long getTotalBreakTime() {
        return prefs.getLong(KEY_TOTAL_BREAK_TIME, 0);
    }

    public int getDistractionCount() {
        return prefs.getInt(KEY_DISTRACTION_COUNT, 0);
    }

    public boolean isPaused() {
        return prefs.getBoolean(KEY_IS_PAUSED, false);
    }

    public long getPauseStartTime() {
        return prefs.getLong(KEY_PAUSE_START_TIME, 0);
    }

    /**
     * Calculates the currently elapsed study time in milliseconds.
     */
    public long getActiveSessionElapsed() {
        if (!isActive()) return 0;
        
        long now = System.currentTimeMillis();
        long startTime = getStartTime();
        long totalBreak = getTotalBreakTime();
        
        if (isPaused()) {
            long pauseStart = getPauseStartTime();
            // If paused, we don't count the time since pauseStart as study time
            return pauseStart - startTime - totalBreak;
        } else {
            return now - startTime - totalBreak;
        }
    }
}
