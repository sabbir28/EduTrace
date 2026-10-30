package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.utils.SessionManager;

/**
 * Manages live study session lifecycle on the EduTrace Server:
 * starting sessions, dedicated pause/resume endpoints, 60s heartbeats, stopping sessions,
 * 75s hard inactivity cutoff / 409 conflict auto-recovery, active session restoration,
 * subject stats, and daily session consolidation.
 */
public class CloudSessionManager {
    private static final String TAG = CloudApiClient.TAG;
    public static final String ACTION_SESSION_TIMED_OUT = "org.sabbir.edutrace.ACTION_SESSION_TIMED_OUT";
    public static final String KEY_ACTIVE_SERVER_SESSION_ID = "cloud_active_server_session_id";

    /**
     * Broadcast timeout/conflict notification to all active activities and services.
     */
    public static void handleSessionConflictOrTimeout(Context context, String reason) {
        if (context == null) return;
        try {
            context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_ACTIVE_SERVER_SESSION_ID)
                    .apply();

            new SessionManager(context).clearSession();

            Intent intent = new Intent(ACTION_SESSION_TIMED_OUT);
            intent.putExtra("reason", reason != null ? reason : "Session timed out on server (75s inactivity cutoff).");
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            Log.w(TAG, "Notice handling session timeout: " + t.getMessage());
        }
    }

    /**
     * Start live session tracking on server when a user opens and studies a subject.
     * Enforces single active session rule and handles 409 conflict auto-recovery.
     */
    public static void startLiveServerSession(Context context, int localSubjectId, String subjectName) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse subRes = CloudApiClient.sendApiRequest(context, "/subjects", "GET", null, token);
                int serverSubId = -1;
                if (subRes.isSuccess() && subRes.json != null) {
                    JSONArray items = subRes.json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject s = items.getJSONObject(i);
                            if (subjectName != null && subjectName.equalsIgnoreCase(s.optString("name", ""))) {
                                serverSubId = s.optInt("id", -1);
                                break;
                            }
                        }
                    }
                }

                if (serverSubId == -1 && subjectName != null && !subjectName.isEmpty()) {
                    // Auto-provision subject on server so session start never stalls
                    try {
                        JSONObject createSubPayload = new JSONObject();
                        createSubPayload.put("name", subjectName);
                        CloudApiClient.ApiResponse createRes = CloudApiClient.sendApiRequest(context, "/subjects", "POST", createSubPayload, token);
                        if (createRes.isSuccess() && createRes.json != null) {
                            JSONObject created = createRes.json.optJSONObject("subject");
                            if (created != null) {
                                serverSubId = created.optInt("id", -1);
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (serverSubId != -1) {
                    JSONObject payload = new JSONObject();
                    payload.put("subject_id", serverSubId);
                    payload.put("title", "Study: " + subjectName);
                    CloudApiClient.ApiResponse startRes = CloudApiClient.sendApiRequest(context, "/sessions/start", "POST", payload, token);
                    if (startRes.isSuccess() && startRes.json != null) {
                        JSONObject sess = startRes.json.optJSONObject("session");
                        if (sess != null) {
                            int serverSessId = sess.optInt("id", -1);
                            if (serverSessId != -1) {
                                context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                                        .edit()
                                        .putInt(KEY_ACTIVE_SERVER_SESSION_ID, serverSessId)
                                        .apply();
                                new SessionManager(context).setServerSessionId(serverSessId);
                                Log.d(TAG, "Live server study session started: #" + serverSessId);
                            }
                        }
                    } else if (startRes.statusCode == 409) {
                        // Previous session remained open on server. Fetch active session, complete it, and retry start
                        CloudApiClient.ApiResponse activeRes = CloudApiClient.sendApiRequest(context, "/sessions/active", "GET", null, token);
                        if (activeRes.isSuccess() && activeRes.json != null) {
                            JSONObject activeSess = activeRes.json.optJSONObject("session");
                            if (activeSess != null) {
                                int staleId = activeSess.optInt("id", -1);
                                if (staleId != -1) {
                                    JSONObject stopPayload = new JSONObject();
                                    stopPayload.put("status", "completed");
                                    stopPayload.put("notes", "Auto-closed previous session upon starting new session");
                                    CloudApiClient.sendApiRequest(context, "/sessions/" + staleId + "/stop", "POST", stopPayload, token);
                                }
                            }
                        }
                        // Retry starting the session
                        CloudApiClient.ApiResponse retryRes = CloudApiClient.sendApiRequest(context, "/sessions/start", "POST", payload, token);
                        if (retryRes.isSuccess() && retryRes.json != null) {
                            JSONObject sess = retryRes.json.optJSONObject("session");
                            if (sess != null) {
                                int serverSessId = sess.optInt("id", -1);
                                if (serverSessId != -1) {
                                    context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                                            .edit()
                                            .putInt(KEY_ACTIVE_SERVER_SESSION_ID, serverSessId)
                                            .apply();
                                    new SessionManager(context).setServerSessionId(serverSessId);
                                    Log.d(TAG, "Live server study session started on retry: #" + serverSessId);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start live server session: " + e.getMessage());
            }
        });
    }

    /**
     * Pause live study session on server (creates StudySessionPause record).
     */
    public static void pauseLiveServerSession(Context context, String notes, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        final int serverSessId = context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                .getInt(KEY_ACTIVE_SERVER_SESSION_ID, -1);
        if (serverSessId == -1) return;

        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (notes != null && !notes.isEmpty()) {
                    payload.put("notes", notes);
                }
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/sessions/" + serverSessId + "/pause", "POST", payload, token);
                if (res.isSuccess()) {
                    Log.d(TAG, "Live server study session paused: #" + serverSessId);
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Session paused on server"));
                } else {
                    if (res.statusCode == 409 || res.statusCode == 404 || res.statusCode == 410) {
                        handleSessionConflictOrTimeout(context, res.errorMessage);
                    }
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                Log.w(TAG, "Error pausing live server session: " + e.getMessage());
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void pauseLiveServerSession(Context context, String notes) {
        pauseLiveServerSession(context, notes, null);
    }

    /**
     * Resume live study session on server (closes open StudySessionPause record).
     */
    public static void resumeLiveServerSession(Context context, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        final int serverSessId = context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                .getInt(KEY_ACTIVE_SERVER_SESSION_ID, -1);
        if (serverSessId == -1) return;

        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/sessions/" + serverSessId + "/resume", "POST", new JSONObject(), token);
                if (res.isSuccess()) {
                    Log.d(TAG, "Live server study session resumed: #" + serverSessId);
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Session resumed on server"));
                } else {
                    if (res.statusCode == 409 || res.statusCode == 404 || res.statusCode == 410) {
                        handleSessionConflictOrTimeout(context, res.errorMessage);
                    }
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                Log.w(TAG, "Error resuming live server session: " + e.getMessage());
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void resumeLiveServerSession(Context context) {
        resumeLiveServerSession(context, null);
    }

    /**
     * Send live tick/heartbeat for the active session to EduTrace Server.
     * Prevents offline duration inflation (e.g. mobile data turned off) and maintains live sync.
     * Detects 75s inactivity timeout (HTTP 409) and safely triggers auto-cleanup.
     */
    public static void sendLiveSessionHeartbeat(Context context, long elapsedMillis, boolean isPaused, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        final int serverSessId = context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                .getInt(KEY_ACTIVE_SERVER_SESSION_ID, -1);

        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (serverSessId != -1) {
                    payload.put("session_id", serverSessId);
                }
                payload.put("elapsed_seconds", Math.max(0, elapsedMillis / 1000));
                payload.put("is_paused", isPaused);

                String endpoint = (serverSessId != -1) ? ("/sessions/" + serverSessId + "/heartbeat") : "/sessions/heartbeat";
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, endpoint, "POST", payload, token);
                if (res.isSuccess()) {
                    if (res.json != null) {
                        JSONObject sessObj = res.json.optJSONObject("session");
                        if (sessObj != null) {
                            int returnedId = sessObj.optInt("id", -1);
                            if (returnedId != -1) {
                                context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                                        .edit()
                                        .putInt(KEY_ACTIVE_SERVER_SESSION_ID, returnedId)
                                        .apply();
                                new SessionManager(context).setServerSessionId(returnedId);
                            }
                        }
                    }
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Heartbeat synced"));
                } else if (res.statusCode == 404 || res.statusCode == 409 || res.statusCode == 410 || res.statusCode == 400) {
                    handleSessionConflictOrTimeout(context, res.errorMessage);
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Session expired or completed on server: " + res.errorMessage));
                }
            } catch (Exception e) {
                // Heartbeat offline - mobile data may be off
                Log.d(TAG, "Live tick heartbeat offline: " + e.getMessage());
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void sendLiveSessionHeartbeat(Context context, long elapsedMillis, boolean isPaused) {
        sendLiveSessionHeartbeat(context, elapsedMillis, isPaused, null);
    }

    /**
     * Stop live study session on server and calculate total duration on completion.
     */
    public static void stopLiveServerSession(Context context, long elapsedMillis, String notes) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        final int serverSessId = context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                .getInt(KEY_ACTIVE_SERVER_SESSION_ID, -1);

        // Immediately remove active session ID so subsequent actions or offline failures never reuse it
        context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ACTIVE_SERVER_SESSION_ID)
                .apply();

        if (serverSessId == -1) return;

        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("status", "completed");
                payload.put("notes", notes != null ? notes : "");
                if (elapsedMillis > 0) {
                    payload.put("duration_seconds", Math.max(0, elapsedMillis / 1000));
                }
                CloudApiClient.sendApiRequest(context, "/sessions/" + serverSessId + "/stop", "POST", payload, token);
                Log.d(TAG, "Live server study session completed: #" + serverSessId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop live server session: " + e.getMessage());
            }
        });
    }

    public static void stopLiveServerSession(Context context, String notes) {
        stopLiveServerSession(context, 0, notes);
    }

    /**
     * Query GET /api/sessions/active to restore state or detect timed out sessions cleanly.
     */
    public static void fetchActiveSession(Context context, CloudDbManager.ActiveSessionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context) || callback == null) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/sessions/active", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    boolean active = res.json.optBoolean("active", false);
                    JSONObject sessionObj = res.json.optJSONObject("session");
                    long elapsedSeconds = 0;
                    if (sessionObj != null) {
                        elapsedSeconds = sessionObj.optLong("elapsed_seconds", 0);
                        int sessId = sessionObj.optInt("id", -1);
                        if (active && sessId != -1) {
                            context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                                    .edit()
                                    .putInt(KEY_ACTIVE_SERVER_SESSION_ID, sessId)
                                    .apply();
                            new SessionManager(context).setServerSessionId(sessId);
                        }
                    }
                    if (!active) {
                        context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                                .edit()
                                .remove(KEY_ACTIVE_SERVER_SESSION_ID)
                                .apply();
                    }
                    final boolean fActive = active;
                    final JSONObject fSession = sessionObj;
                    final long fElapsed = elapsedSeconds;
                    final String fServerTime = res.json.optString("server_time", "");
                    CloudApiClient.mainHandler.post(() -> callback.onActiveSession(fActive, fSession, fElapsed, fServerTime));
                } else {
                    CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Consolidate fragmented daily sessions via POST /api/sessions/consolidate.
     * Prunes redundant records and recalculates daily summary seamlessly.
     */
    public static void consolidateDailySessions(Context context, Integer subjectId, String dateIso, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (subjectId != null && subjectId > 0) {
                    payload.put("subject_id", subjectId);
                }
                if (dateIso != null && !dateIso.isEmpty()) {
                    payload.put("date", dateIso);
                }
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/sessions/consolidate", "POST", payload, token);
                if (res.isSuccess()) {
                    int pruned = res.json != null ? res.json.optInt("pruned_sessions_count", 0) : 0;
                    String msg = "Consolidated daily sessions. Pruned " + pruned + " redundant records.";
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(msg));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Query server-side calculated total values for a specific subject.
     */
    public static void fetchSubjectStats(Context context, String subjectName, CloudDbManager.SubjectStatsCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context) || callback == null) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse subRes = CloudApiClient.sendApiRequest(context, "/subjects", "GET", null, token);
                if (subRes.isSuccess() && subRes.json != null) {
                    JSONArray items = subRes.json.optJSONArray("items");
                    int serverSubId = -1;
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject s = items.getJSONObject(i);
                            if (subjectName != null && subjectName.equalsIgnoreCase(s.optString("name", ""))) {
                                serverSubId = s.optInt("id", -1);
                                break;
                            }
                        }
                    }
                    if (serverSubId != -1) {
                        CloudApiClient.ApiResponse statsRes = CloudApiClient.sendApiRequest(context, "/subjects/" + serverSubId + "/stats", "GET", null, token);
                        if (statsRes.isSuccess() && statsRes.json != null) {
                            JSONObject stats = statsRes.json.optJSONObject("stats");
                            if (stats != null) {
                                long secs = stats.optLong("total_seconds", 0);
                                double hours = stats.optDouble("total_hours", 0.0);
                                int count = stats.optInt("completed_sessions", 0);
                                CloudApiClient.mainHandler.post(() -> callback.onStats(secs, hours, count));
                                return;
                            }
                        }
                    }
                }
                CloudApiClient.mainHandler.post(() -> callback.onError("Subject stats not found"));
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
}
