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
    private static final String KEY_ACCUMULATED_ACTIVE_TIME = "accumulated_active_time";
    private static final String KEY_LAST_TICK_TIME = "last_tick_time";

    // Maximum expected interval between active timer ticks (1s regular cadence)
    // If the gap exceeds 3 minutes, app was closed/device slept/disconnected.
    private static final long MAX_TICK_GAP_MS = 3 * 60 * 1000;

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
                .putLong(KEY_PAUSE_START_TIME, 0)
                .putBoolean(KEY_IS_ACTIVE, true)
                .putLong(KEY_ACCUMULATED_ACTIVE_TIME, 0)
                .putLong(KEY_LAST_TICK_TIME, startTime)
                .apply();
    }

    public void updateSession(long totalBreakTime, int distractionCount) {
        prefs.edit()
                .putLong(KEY_TOTAL_BREAK_TIME, totalBreakTime)
                .putInt(KEY_DISTRACTION_COUNT, distractionCount)
                .apply();
    }

    /**
     * Records a live heartbeat tick from the timer loop (run every 1s).
     * Prevents wild duration jumps if the device slept or app was killed for hours.
     */
    public synchronized void recordTick(long now) {
        if (!isActive() || isPaused()) return;

        long lastTick = prefs.getLong(KEY_LAST_TICK_TIME, getStartTime());
        long delta = now - lastTick;
        if (delta <= 0) return;

        long currentAccum = prefs.getLong(KEY_ACCUMULATED_ACTIVE_TIME, 0);
        if (delta <= MAX_TICK_GAP_MS) {
            currentAccum += delta;
            prefs.edit()
                    .putLong(KEY_ACCUMULATED_ACTIVE_TIME, currentAccum)
                    .putLong(KEY_LAST_TICK_TIME, now)
                    .apply();
        } else {
            // Gap > 3 min detected! Device disconnected, screen slept, or app process was killed.
            // Credit at most 60s grace, then auto-pause to prevent 7-hour inflation.
            currentAccum += 60000;
            long autoPauseTime = lastTick + 60000;
            prefs.edit()
                    .putLong(KEY_ACCUMULATED_ACTIVE_TIME, currentAccum)
                    .putLong(KEY_LAST_TICK_TIME, now)
                    .putBoolean(KEY_IS_PAUSED, true)
                    .putLong(KEY_PAUSE_START_TIME, autoPauseTime)
                    .apply();
        }
    }

    public void setPaused(boolean paused, long pauseStartTime, long totalBreakTime) {
        long now = (pauseStartTime > 0) ? pauseStartTime : System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_IS_PAUSED, paused)
                .putLong(KEY_PAUSE_START_TIME, paused ? now : 0)
                .putLong(KEY_TOTAL_BREAK_TIME, totalBreakTime);

        if (paused) {
            // Accumulate active time up to the pause start
            long lastTick = prefs.getLong(KEY_LAST_TICK_TIME, getStartTime());
            long delta = Math.max(0, now - lastTick);
            long currentAccum = prefs.getLong(KEY_ACCUMULATED_ACTIVE_TIME, 0);
            if (delta > 0 && delta <= MAX_TICK_GAP_MS) {
                currentAccum += delta;
            }
            editor.putLong(KEY_ACCUMULATED_ACTIVE_TIME, currentAccum);
            editor.putLong(KEY_LAST_TICK_TIME, now);
        } else {
            // Resuming: reset lastTick to now so resume gap is never counted as study
            editor.putLong(KEY_LAST_TICK_TIME, System.currentTimeMillis());
        }
        editor.apply();
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

    public long getLastTickTime() {
        return prefs.getLong(KEY_LAST_TICK_TIME, getStartTime());
    }

    /**
     * Checks if the session was left running while app was killed or disconnected for > 5 minutes.
     */
    public boolean isStaleOrInterrupted() {
        if (!isActive()) return false;
        long now = System.currentTimeMillis();
        long lastTick = getLastTickTime();
        return !isPaused() && (now - lastTick) > (5 * 60 * 1000);
    }

    /**
     * Calculates the currently elapsed active study time in milliseconds.
     * Guaranteed never to jump to raw wall-clock time if user disconnected or app slept.
     */
    public long getActiveSessionElapsed() {
        if (!isActive()) return 0;

        long accumulated = prefs.getLong(KEY_ACCUMULATED_ACTIVE_TIME, 0);
        if (isPaused()) {
            return Math.max(0, accumulated);
        }

        long now = System.currentTimeMillis();
        long lastTick = getLastTickTime();
        long delta = Math.max(0, now - lastTick);

        if (delta > MAX_TICK_GAP_MS) {
            // Disconnection / idle gap: cap delta to 60s grace
            return Math.max(0, accumulated + 60000);
        } else {
            return Math.max(0, accumulated + delta);
        }
    }
}
