package org.sabbir.edutrace.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the state of an active study session using SharedPreferences.
 * Strictly aligned with EduTraceServer study session specifications:
 * - 60-second live heartbeat intervals
 * - 15-second grace window
 * - 75-second hard server inactivity cutoff
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
    private static final String KEY_SERVER_SESSION_ID = "server_session_id";

    // Server-enforced hard timeout cutoff: 60s ping interval + 15s grace window = 75 seconds.
    public static final long SERVER_HEARTBEAT_INTERVAL_MS = 60 * 1000L;
    public static final long SERVER_GRACE_MS = 15 * 1000L;
    public static final long MAX_TICK_GAP_MS = 75 * 1000L;

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
                .putInt(KEY_SERVER_SESSION_ID, -1)
                .apply();
    }

    public void updateSession(long totalBreakTime, int distractionCount) {
        prefs.edit()
                .putLong(KEY_TOTAL_BREAK_TIME, totalBreakTime)
                .putInt(KEY_DISTRACTION_COUNT, distractionCount)
                .apply();
    }

    /**
     * Records a live tick from the timer loop.
     * Prevents clock manipulation and aligns strictly with the server's 75s inactivity rule.
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
            // Gap > 75s detected! Server hard inactivity cutoff exceeded (60s heartbeat + 15s grace).
            // Credit at most 15s grace (server rule), then auto-pause to prevent fake duration inflation.
            currentAccum += SERVER_GRACE_MS;
            long autoPauseTime = lastTick + SERVER_GRACE_MS;
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
            } else if (delta > MAX_TICK_GAP_MS) {
                currentAccum += SERVER_GRACE_MS;
            }
            editor.putLong(KEY_ACCUMULATED_ACTIVE_TIME, currentAccum);
            editor.putLong(KEY_LAST_TICK_TIME, now);
        } else {
            // Resuming: reset lastTick to now so resume gap is never counted as study
            editor.putLong(KEY_LAST_TICK_TIME, System.currentTimeMillis());
        }
        editor.apply();
    }

    public void setServerSessionId(int serverSessionId) {
        prefs.edit().putInt(KEY_SERVER_SESSION_ID, serverSessionId).apply();
    }

    public int getServerSessionId() {
        return prefs.getInt(KEY_SERVER_SESSION_ID, -1);
    }

    /**
     * Synchronize session state from server GET /api/sessions/active.
     */
    public void syncFromServer(int serverSessionId, int subjectId, String subjectName, long startTime, long elapsedSeconds, boolean isPaused) {
        long elapsedMillis = Math.max(0, elapsedSeconds * 1000L);
        long now = System.currentTimeMillis();
        prefs.edit()
                .putBoolean(KEY_IS_ACTIVE, true)
                .putInt(KEY_SERVER_SESSION_ID, serverSessionId)
                .putInt(KEY_SUBJECT_ID, subjectId)
                .putString(KEY_SUBJECT_NAME, subjectName != null ? subjectName : "")
                .putLong(KEY_START_TIME, startTime > 0 ? startTime : (now - elapsedMillis))
                .putLong(KEY_ACCUMULATED_ACTIVE_TIME, elapsedMillis)
                .putBoolean(KEY_IS_PAUSED, isPaused)
                .putLong(KEY_LAST_TICK_TIME, now)
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

    public long getLastTickTime() {
        return prefs.getLong(KEY_LAST_TICK_TIME, getStartTime());
    }

    /**
     * Checks if the session was left running while app was killed or disconnected for > 75 seconds.
     */
    public boolean isStaleOrInterrupted() {
        if (!isActive()) return false;
        long now = System.currentTimeMillis();
        long lastTick = getLastTickTime();
        return !isPaused() && (now - lastTick) > MAX_TICK_GAP_MS;
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
            // Disconnection / idle gap: cap delta to 15s grace (server policy)
            return Math.max(0, accumulated + SERVER_GRACE_MS);
        } else {
            return Math.max(0, accumulated + delta);
        }
    }
}
