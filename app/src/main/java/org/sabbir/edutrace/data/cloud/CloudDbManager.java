package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;
import org.sabbir.edutrace.data.models.CloudNotification;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.MyRankingSummary;
import org.sabbir.edutrace.data.models.RankingUser;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.data.models.TimelineDayData;
import org.sabbir.edutrace.data.models.TimelineSegment;
import org.sabbir.edutrace.data.models.TimerMatchStatus;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages modern RESTful cloud persistence and synchronization with the EduTrace Server.
 * All operations communicate seamlessly via REST JSON endpoints (JWT Authentication, Sync, Backup, Restore).
 */
public class CloudDbManager {
    private static final String TAG = "EduTraceCloud";
    private static final String PREFS_AUTH = "EduTrace_Cloud_Auth";
    private static final String KEY_USER_ID = "cloud_user_id";
    private static final String KEY_USERNAME = "cloud_username";
    private static final String KEY_EMAIL = "cloud_email";
    private static final String KEY_DISPLAY_NAME = "cloud_display_name";
    private static final String KEY_ACCESS_TOKEN = "cloud_access_token";
    private static final String KEY_IS_LOGGED_IN = "cloud_logged_in";
    private static final String KEY_LAST_BACKUP = "cloud_last_backup";
    public static final String KEY_AUTO_BACKUP = "cloud_auto_backup_enabled";
    public static final String KEY_SERVER_URL = "cloud_server_api_url";

    // Standard server endpoints (Vercel Production, Emulator, Host PC Wi-Fi, and Localhost)
    public static final String DEFAULT_PROD_URL = "https://edutrace-theta.vercel.app/api";
    public static final String DEFAULT_EMULATOR_URL = "http://10.0.2.2:5000/api";
    public static final String DEFAULT_WIFI_URL = "http://192.168.10.120:5000/api";
    public static final String DEFAULT_LOCAL_URL = "http://127.0.0.1:5000/api";

    private static final ExecutorService cloudExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AuthCallback {
        void onSuccess(CloudUser user);
        void onError(String message);
    }

    public interface AvailabilityCallback {
        void onAvailable();
        void onError(String message);
    }

    public interface EmailLookupCallback {
        void onFound(String username, String email);
        void onError(String message);
    }

    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    public interface SyncProgressCallback {
        void onProgress(String status);
        void onSuccess(String message);
        void onError(String message);
    }

    public interface HealthCallback {
        void onSuccess(String status);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    public interface NotificationsCallback {
        void onSuccess(List<CloudNotification> notifications, int unreadCount);
        void onError(String message);
    }

    public interface CountCallback {
        void onSuccess(int count);
        void onError(String message);
    }

    public interface TimerMatchCallback {
        void onSuccess(TimerMatchStatus status);
        void onError(String message);
    }

    public interface LeaderboardCallback {
        void onSuccess(List<RankingUser> users, RankingUser currentUserRank, int totalParticipants);
        void onError(String message);
    }

    public interface MyRankingCallback {
        void onSuccess(MyRankingSummary summary);
        void onError(String message);
    }

    public interface TimelineCallback {
        void onSuccess(TimelineDayData timelineData);
        void onError(String message);
    }

    public static String getServerUrl(Context context) {
        if (context == null) return DEFAULT_PROD_URL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, DEFAULT_PROD_URL);
    }

    public static void setServerUrl(Context context, String url) {
        if (context == null || url == null) return;
        String cleanUrl = url.trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        if (!cleanUrl.endsWith("/api")) {
            cleanUrl = cleanUrl + "/api";
        }
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_URL, cleanUrl)
                .apply();
    }

    public static boolean isLoggedIn(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
                && prefs.getInt(KEY_USER_ID, -1) != -1
                && !prefs.getString(KEY_ACCESS_TOKEN, "").isEmpty();
    }

    public static CloudUser getCurrentUser(Context context) {
        if (context == null || !isLoggedIn(context)) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        int id = prefs.getInt(KEY_USER_ID, -1);
        String username = prefs.getString(KEY_USERNAME, "");
        String email = prefs.getString(KEY_EMAIL, "");
        String displayName = prefs.getString(KEY_DISPLAY_NAME, username);
        String token = prefs.getString(KEY_ACCESS_TOKEN, "");
        return new CloudUser(id, username, email, displayName, token);
    }

    public static String getAccessToken(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }

    public static String getLastBackupTime(Context context) {
        if (context == null) return "Never";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        long lastTime = prefs.getLong(KEY_LAST_BACKUP, 0);
        if (lastTime == 0) return "Never";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return sdf.format(new Date(lastTime));
    }

    public static void logout(Context context) {
        if (context == null) return;
        final String token = getAccessToken(context);
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_EMAIL)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_ACCESS_TOKEN)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply();

        // Notify server asynchronously
        if (!token.isEmpty()) {
            cloudExecutor.execute(() -> {
                try {
                    sendApiRequest(context, "/auth/logout", "POST", null, token);
                } catch (Throwable ignored) {}
            });
        }
    }

    private static void saveUserSession(Context context, CloudUser user) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_USER_ID, user.getId())
                .putString(KEY_USERNAME, user.getUsername())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_DISPLAY_NAME, user.getDisplayName())
                .putString(KEY_ACCESS_TOKEN, user.getToken() != null ? user.getToken() : "")
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    /**
     * Completely automatic startup sync: pulls down user's data if local DB is empty,
     * or backs up any new offline study activity without requiring manual button clicks.
     */
    public static void autoSyncOnStartup(Context context) {
        if (!isLoggedIn(context)) return;
        cloudExecutor.execute(() -> {
            try {
                StudyDatabase localDb = StudyDatabase.getDatabase(context);
                List<Degree> degrees = localDb.studyDao().getDegreesListSync();
                if (degrees == null || degrees.isEmpty()) {
                    Log.d(TAG, "Local database empty on start -> Auto-restoring from EduTrace Cloud...");
                    restoreData(context);
                } else {
                    Log.d(TAG, "Local database active on start -> Auto-syncing to EduTrace Cloud...");
                    backupData(context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Auto-sync on startup error: " + e.getMessage());
            }
        });
    }

    /**
     * Start live session tracking on server when a user opens and studies a subject.
     */
    public static void startLiveServerSession(Context context, int localSubjectId, String subjectName) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse subRes = sendApiRequest(context, "/subjects", "GET", null, token);
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

                if (serverSubId != -1) {
                    JSONObject payload = new JSONObject();
                    payload.put("subject_id", serverSubId);
                    payload.put("title", "Study: " + subjectName);
                    ApiResponse startRes = sendApiRequest(context, "/sessions/start", "POST", payload, token);
                    if (startRes.isSuccess() && startRes.json != null) {
                        JSONObject sess = startRes.json.optJSONObject("session");
                        if (sess != null) {
                            int serverSessId = sess.optInt("id", -1);
                            if (serverSessId != -1) {
                                context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                                        .edit()
                                        .putInt("cloud_active_server_session_id", serverSessId)
                                        .apply();
                                Log.d(TAG, "Live server study session started: #" + serverSessId);
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
     * Send live tick/heartbeat for the active session to EduTrace Server.
     * Prevents offline duration inflation (e.g. mobile data turned off) and maintains live sync.
     */
    public static void sendLiveSessionHeartbeat(Context context, long elapsedMillis, boolean isPaused, ActionCallback callback) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        final int serverSessId = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .getInt("cloud_active_server_session_id", -1);

        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (serverSessId != -1) {
                    payload.put("session_id", serverSessId);
                }
                payload.put("elapsed_seconds", Math.max(0, elapsedMillis / 1000));
                payload.put("is_paused", isPaused);

                String endpoint = (serverSessId != -1) ? ("/sessions/" + serverSessId + "/heartbeat") : "/sessions/heartbeat";
                ApiResponse res = sendApiRequest(context, endpoint, "POST", payload, token);
                if (res.isSuccess()) {
                    if (res.json != null) {
                        JSONObject sessObj = res.json.optJSONObject("session");
                        if (sessObj != null) {
                            int returnedId = sessObj.optInt("id", -1);
                            if (returnedId != -1) {
                                context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                                        .edit()
                                        .putInt("cloud_active_server_session_id", returnedId)
                                        .apply();
                            }
                        }
                    }
                    if (callback != null) mainHandler.post(() -> callback.onSuccess("Heartbeat synced"));
                } else if (res.statusCode == 404 || res.statusCode == 409) {
                    context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                            .edit()
                            .remove("cloud_active_server_session_id")
                            .apply();
                    if (callback != null) mainHandler.post(() -> callback.onError("Session expired on server"));
                }
            } catch (Exception e) {
                // Heartbeat offline - mobile data may be off
                Log.d(TAG, "Live tick heartbeat offline: " + e.getMessage());
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
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
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        final int serverSessId = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .getInt("cloud_active_server_session_id", -1);
        if (serverSessId == -1) return;

        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("status", "completed");
                payload.put("notes", notes != null ? notes : "");
                if (elapsedMillis > 0) {
                    payload.put("duration_seconds", Math.max(0, elapsedMillis / 1000));
                }
                sendApiRequest(context, "/sessions/" + serverSessId + "/stop", "POST", payload, token);
                context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                        .edit()
                        .remove("cloud_active_server_session_id")
                        .apply();
                Log.d(TAG, "Live server study session completed: #" + serverSessId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop live server session: " + e.getMessage());
            }
        });
    }

    public static void stopLiveServerSession(Context context, String notes) {
        stopLiveServerSession(context, 0, notes);
    }

    public interface SubjectStatsCallback {
        void onStats(long totalSeconds, double totalHours, int sessionCount);
        void onError(String message);
    }

    /**
     * Query server-side calculated total values for a specific subject.
     */
    public static void fetchSubjectStats(Context context, String subjectName, SubjectStatsCallback callback) {
        if (!isLoggedIn(context) || callback == null) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse subRes = sendApiRequest(context, "/subjects", "GET", null, token);
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
                        ApiResponse statsRes = sendApiRequest(context, "/subjects/" + serverSubId + "/stats", "GET", null, token);
                        if (statsRes.isSuccess() && statsRes.json != null) {
                            JSONObject stats = statsRes.json.optJSONObject("stats");
                            if (stats != null) {
                                long secs = stats.optLong("total_seconds", 0);
                                double hours = stats.optDouble("total_hours", 0.0);
                                int count = stats.optInt("completed_sessions", 0);
                                mainHandler.post(() -> callback.onStats(secs, hours, count));
                                return;
                            }
                        }
                    }
                }
                mainHandler.post(() -> callback.onError("Subject stats not found"));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ==========================================
    // HTTP API Transport Implementation
    // ==========================================

    private static class ApiResponse {
        int statusCode;
        JSONObject json;
        String rawBody;
        String errorMessage;

        boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    private static ApiResponse sendApiRequest(Context context, String endpoint, String method, JSONObject payload, String token) {
        String primaryBase = getServerUrl(context);
        ApiResponse resp = executeSingleRequest(primaryBase, endpoint, method, payload, token);

        // Fallback: if default emulator URL cannot connect, try Wi-Fi host URL automatically
        if (!resp.isSuccess() && resp.errorMessage != null && resp.errorMessage.contains("Connect")
                && DEFAULT_EMULATOR_URL.equals(primaryBase)) {
            Log.d(TAG, "Primary emulator URL failed, attempting Wi-Fi fallback: " + DEFAULT_WIFI_URL);
            ApiResponse fallback = executeSingleRequest(DEFAULT_WIFI_URL, endpoint, method, payload, token);
            if (fallback.isSuccess() || fallback.statusCode > 0) {
                setServerUrl(context, DEFAULT_WIFI_URL);
                return fallback;
            }
        }
        return resp;
    }

    private static ApiResponse executeSingleRequest(String baseUrl, String endpoint, String method, JSONObject payload, String token) {
        ApiResponse result = new ApiResponse();
        HttpURLConnection conn = null;
        try {
            String fullUrl = baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            if (payload != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                conn.setDoOutput(true);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }
            }

            int code = conn.getResponseCode();
            result.statusCode = code;

            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                result.rawBody = sb.toString();

                try {
                    result.json = new JSONObject(result.rawBody);
                } catch (Exception ignored) {
                    // Raw string body
                }
            }

            if (!result.isSuccess()) {
                String errMsg = "Request failed (" + code + ")";
                if (result.json != null) {
                    if (result.json.has("message")) errMsg = result.json.getString("message");
                    else if (result.json.has("error")) errMsg = result.json.getString("error");
                }
                result.errorMessage = errMsg;
            }

        } catch (ConnectException ce) {
            result.statusCode = 0;
            result.errorMessage = "Unable to connect to EduTrace Server. Please check network/host URL.";
        } catch (Exception e) {
            result.statusCode = 0;
            result.errorMessage = e.getMessage() != null ? e.getMessage() : "Network error";
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    // ==========================================
    // Public Online API Operations
    // ==========================================

    public static void checkServerHealth(Context context, HealthCallback callback) {
        cloudExecutor.execute(() -> {
            ApiResponse res = sendApiRequest(context, "/health", "GET", null, null);
            if (res.isSuccess()) {
                mainHandler.post(() -> callback.onSuccess("EduTrace Server is Online 🟢"));
            } else {
                mainHandler.post(() -> callback.onError(res.errorMessage != null ? res.errorMessage : "Server unreachable"));
            }
        });
    }

    public static void checkUserAvailability(Context context, String username, String email, AvailabilityCallback callback) {
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (username != null && !username.trim().isEmpty()) payload.put("username", username.trim());
                if (email != null && !email.trim().isEmpty()) payload.put("email", email.trim());

                ApiResponse res = sendApiRequest(context, "/auth/check-availability", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    boolean available = res.json.optBoolean("available", true);
                    if (available) {
                        mainHandler.post(callback::onAvailable);
                    } else {
                        String msg = res.json.optString("message", "User identifier already in use.");
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } else {
                    String msg = res.errorMessage != null ? res.errorMessage : "Server validation error";
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public static void checkUserAvailability(String username, String email, AvailabilityCallback callback) {
        checkUserAvailability(null, username, email, callback);
    }

    public static void register(Context context, String username, String email, String password, String displayName, AuthCallback callback) {
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username.trim());
                payload.put("display_name", (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim());
                payload.put("first_name", (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim());
                payload.put("email", email.trim().toLowerCase(Locale.ROOT));
                payload.put("password", password);

                ApiResponse res = sendApiRequest(context, "/auth/register", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String token = res.json.optString("access_token", "");
                    JSONObject userObj = res.json.optJSONObject("user");
                    int userId = (userObj != null) ? userObj.optInt("id", 1) : 1;
                    String retEmail = (userObj != null) ? userObj.optString("email", email) : email;
                    String retName = (userObj != null) ? userObj.optString("first_name", displayName) : displayName;

                    CloudUser user = new CloudUser(userId, username, retEmail, retName, token);
                    saveUserSession(context, user);
                    mainHandler.post(() -> callback.onSuccess(user));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Registration failed.";
                    mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Registration error: " + e.getMessage()));
            }
        });
    }

    public static void login(Context context, String usernameOrEmail, String password, AuthCallback callback) {
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail.trim());
                payload.put("email", usernameOrEmail.trim());
                payload.put("password", password);

                ApiResponse res = sendApiRequest(context, "/auth/login", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String token = res.json.optString("access_token", "");
                    JSONObject userObj = res.json.optJSONObject("user");
                    int userId = (userObj != null) ? userObj.optInt("id", 1) : 1;
                    String retEmail = (userObj != null) ? userObj.optString("email", "") : "";
                    String retName = (userObj != null) ? userObj.optString("first_name", usernameOrEmail) : usernameOrEmail;

                    CloudUser user = new CloudUser(userId, usernameOrEmail, retEmail, retName, token);
                    saveUserSession(context, user);
                    mainHandler.post(() -> callback.onSuccess(user));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Login failed. Please check credentials.";
                    mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Login error: " + e.getMessage()));
            }
        });
    }

    public static void findUserEmail(Context context, String usernameOrEmail, EmailLookupCallback callback) {
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username_or_email", usernameOrEmail.trim());

                ApiResponse res = sendApiRequest(context, "/auth/find-email", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String user = res.json.optString("username", usernameOrEmail);
                    String email = res.json.optString("email", "");
                    if (email.isEmpty()) {
                        mainHandler.post(() -> callback.onError("No email found for this user."));
                    } else {
                        mainHandler.post(() -> callback.onFound(user, email));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "User account not found.";
                    mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Lookup error: " + e.getMessage()));
            }
        });
    }

    public static void findUserEmail(String usernameOrEmail, EmailLookupCallback callback) {
        findUserEmail(null, usernameOrEmail, callback);
    }

    public static void resetPassword(Context context, String usernameOrEmail, String newPassword, AuthCallback callback) {
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username_or_email", usernameOrEmail.trim());
                payload.put("new_password", newPassword);

                ApiResponse res = sendApiRequest(context, "/auth/reset-password", "POST", payload, null);
                if (res.isSuccess()) {
                    CloudUser dummyUser = new CloudUser(-1, usernameOrEmail, "", usernameOrEmail, "");
                    mainHandler.post(() -> callback.onSuccess(dummyUser));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Password reset failed.";
                    mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Password reset error: " + e.getMessage()));
            }
        });
    }

    public static void resetPassword(String usernameOrEmail, String newPassword, AuthCallback callback) {
        resetPassword(null, usernameOrEmail, newPassword, callback);
    }

    // ==========================================
    // Bulk Cloud Sync (Backup & Restore)
    // ==========================================

    public static void backupData(Context context, SyncProgressCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Please sign in to backup data to EduTrace Server."));
            return;
        }

        final String token = getAccessToken(context);

        cloudExecutor.execute(() -> {
            try {
                if (callback != null) mainHandler.post(() -> callback.onProgress("Collecting local study data..."));
                StudyDatabase localDb = StudyDatabase.getDatabase(context);
                StudyDao dao = localDb.studyDao();

                List<Degree> degrees = dao.getDegreesListSync();
                List<Subject> subjects = dao.getSubjectsListSync();
                List<StudySession> sessions = dao.getSessionsListSync();

                JSONObject rootPayload = new JSONObject();

                // 1. Degrees
                JSONArray degArr = new JSONArray();
                for (Degree d : degrees) {
                    JSONObject dObj = new JSONObject();
                    dObj.put("local_id", d.id);
                    dObj.put("name", d.name);
                    dObj.put("color_hex", d.colorHex != null ? d.colorHex : "#3B82F6");
                    degArr.put(dObj);
                }
                rootPayload.put("degrees", degArr);

                // 2. Subjects
                JSONArray subArr = new JSONArray();
                for (Subject s : subjects) {
                    JSONObject sObj = new JSONObject();
                    sObj.put("local_id", s.id);
                    sObj.put("degree_local_id", s.degreeId);
                    sObj.put("name", s.name);
                    sObj.put("color_hex", s.colorHex != null ? s.colorHex : "#2563EB");
                    subArr.put(sObj);
                }
                rootPayload.put("subjects", subArr);

                // 3. Sessions
                JSONArray sessArr = new JSONArray();
                for (StudySession s : sessions) {
                    JSONObject sessObj = new JSONObject();
                    sessObj.put("local_id", s.id);
                    sessObj.put("subject_local_id", s.subjectId);
                    sessObj.put("start_timestamp", s.startTimestamp);
                    sessObj.put("end_timestamp", s.endTimestamp);
                    sessObj.put("break_duration_millis", s.breakDurationMillis);
                    sessObj.put("distraction_count", s.distractionCount);
                    sessObj.put("notes", s.notes != null ? s.notes : "");
                    sessArr.put(sessObj);
                }
                rootPayload.put("sessions", sessArr);

                if (callback != null) mainHandler.post(() -> callback.onProgress("Uploading to EduTrace Cloud Server..."));

                ApiResponse res = sendApiRequest(context, "/sync/backup", "POST", rootPayload, token);

                if (res.isSuccess()) {
                    long now = System.currentTimeMillis();
                    context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_BACKUP, now)
                            .apply();

                    org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(context);

                    String successMsg = "Successfully backed up " + sessions.size() + " sessions & " + subjects.size() + " subjects to EduTrace Server! ☁️";
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(successMsg));
                } else if (res.statusCode == 404) {
                    // Fallback: sync directly using individual REST endpoints
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Syncing records to EduTrace Server..."));
                    int syncedDegrees = 0;
                    int syncedSubjects = 0;

                    for (Degree d : degrees) {
                        JSONObject dPayload = new JSONObject();
                        dPayload.put("name", d.name);
                        dPayload.put("color", d.colorHex != null ? d.colorHex : "#3B82F6");
                        ApiResponse dRes = sendApiRequest(context, "/degrees", "POST", dPayload, token);
                        int serverDegreeId = -1;
                        if (dRes.isSuccess() && dRes.json != null) {
                            JSONObject createdDegree = dRes.json.optJSONObject("degree");
                            if (createdDegree != null) serverDegreeId = createdDegree.optInt("id", -1);
                        }
                        if (serverDegreeId != -1) {
                            syncedDegrees++;
                            JSONObject cPayload = new JSONObject();
                            cPayload.put("name", "Core Studies");
                            cPayload.put("degree_id", serverDegreeId);
                            ApiResponse cRes = sendApiRequest(context, "/courses", "POST", cPayload, token);
                            int serverCourseId = -1;
                            if (cRes.isSuccess() && cRes.json != null) {
                                JSONObject createdCourse = cRes.json.optJSONObject("course");
                                if (createdCourse != null) serverCourseId = createdCourse.optInt("id", -1);
                            }
                            if (serverCourseId != -1) {
                                for (Subject s : subjects) {
                                    if (s.degreeId == d.id) {
                                        JSONObject sPayload = new JSONObject();
                                        sPayload.put("name", s.name);
                                        sPayload.put("color", s.colorHex != null ? s.colorHex : "#2563EB");
                                        sPayload.put("course_id", serverCourseId);
                                        ApiResponse sRes = sendApiRequest(context, "/subjects", "POST", sPayload, token);
                                        if (sRes.isSuccess()) syncedSubjects++;
                                    }
                                }
                            }
                        }
                    }

                    long now = System.currentTimeMillis();
                    context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_BACKUP, now)
                            .apply();

                    org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(context);
                    String successMsg = "Successfully synced " + syncedDegrees + " degrees & " + syncedSubjects + " subjects to EduTrace Server! ☁️";
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(successMsg));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Backup failed.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) mainHandler.post(() -> callback.onError("Backup error: " + e.getMessage()));
            }
        });
    }

    public static void backupData(Context context, SyncCallback callback) {
        backupData(context, new SyncProgressCallback() {
            @Override
            public void onProgress(String status) {}

            @Override
            public void onSuccess(String message) {
                if (callback != null) callback.onSuccess(message);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    public static void backupData(Context context) {
        backupData(context, (SyncProgressCallback) null);
    }

    public static void restoreData(Context context, SyncProgressCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Please sign in to restore data from EduTrace Server."));
            return;
        }

        final String token = getAccessToken(context);

        cloudExecutor.execute(() -> {
            try {
                if (callback != null) mainHandler.post(() -> callback.onProgress("Connecting to EduTrace REST API..."));

                ApiResponse res = sendApiRequest(context, "/sync/restore", "GET", null, token);

                if (res.isSuccess() && res.json != null) {
                    StudyDatabase localDb = StudyDatabase.getDatabase(context);
                    StudyDao dao = localDb.studyDao();

                    int restoredDegrees = 0;
                    int restoredSubjects = 0;
                    int restoredSessions = 0;

                    if (callback != null) mainHandler.post(() -> callback.onProgress("Restoring academic degrees & subjects..."));

                    // 1. Degrees
                    JSONArray degArr = res.json.optJSONArray("degrees");
                    if (degArr != null) {
                        for (int i = 0; i < degArr.length(); i++) {
                            JSONObject d = degArr.getJSONObject(i);
                            Degree degree = new Degree(d.getString("name"), d.optString("color_hex", "#3B82F6"));
                            degree.id = d.getInt("local_id");
                            dao.insertOrReplaceDegree(degree);
                            restoredDegrees++;
                        }
                    }

                    // 2. Subjects
                    JSONArray subArr = res.json.optJSONArray("subjects");
                    if (subArr != null) {
                        for (int i = 0; i < subArr.length(); i++) {
                            JSONObject s = subArr.getJSONObject(i);
                            Subject subject = new Subject(
                                    s.getInt("degree_local_id"),
                                    s.getString("name"),
                                    s.optString("color_hex", "#2563EB")
                            );
                            subject.id = s.getInt("local_id");
                            dao.insertOrReplaceSubject(subject);
                            restoredSubjects++;
                        }
                    }

                    if (callback != null) mainHandler.post(() -> callback.onProgress("Restoring study session history..."));

                    // 3. Sessions
                    JSONArray sessArr = res.json.optJSONArray("sessions");
                    if (sessArr != null) {
                        for (int i = 0; i < sessArr.length(); i++) {
                            JSONObject sess = sessArr.getJSONObject(i);
                            StudySession session = new StudySession(
                                    sess.getInt("subject_local_id"),
                                    sess.getLong("start_timestamp"),
                                    sess.getLong("end_timestamp"),
                                    sess.optLong("break_duration_millis", 0),
                                    sess.optInt("distraction_count", 0),
                                    sess.optString("notes", "")
                            );
                            session.id = sess.getInt("local_id");
                            dao.insertOrReplaceSession(session);
                            restoredSessions++;
                        }
                    }

                    org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(context);

                    String msg = "Restored " + restoredSessions + " sessions, " + restoredSubjects + " subjects, and " + restoredDegrees + " degrees from cloud! 📥";
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(msg));
                } else if (res.statusCode == 404) {
                    // Fallback: restore from individual endpoints /degrees and /subjects
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Restoring from EduTrace Server records..."));
                    StudyDatabase localDb = StudyDatabase.getDatabase(context);
                    StudyDao dao = localDb.studyDao();
                    int restoredDegrees = 0;
                    int restoredSubjects = 0;

                    ApiResponse degListRes = sendApiRequest(context, "/degrees", "GET", null, token);
                    if (degListRes.isSuccess() && degListRes.json != null) {
                        JSONArray items = degListRes.json.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject d = items.getJSONObject(i);
                                Degree degree = new Degree(d.getString("name"), d.optString("color", "#3B82F6"));
                                degree.id = d.getInt("id");
                                dao.insertOrReplaceDegree(degree);
                                restoredDegrees++;
                            }
                        }
                    }

                    ApiResponse subListRes = sendApiRequest(context, "/subjects", "GET", null, token);
                    if (subListRes.isSuccess() && subListRes.json != null) {
                        JSONArray items = subListRes.json.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject s = items.getJSONObject(i);
                                int courseId = s.optInt("course_id", 1);
                                Subject subject = new Subject(courseId, s.getString("name"), s.optString("color", "#2563EB"));
                                subject.id = s.getInt("id");
                                dao.insertOrReplaceSubject(subject);
                                restoredSubjects++;
                            }
                        }
                    }

                    int restoredSessions = 0;
                    ApiResponse sessListRes = sendApiRequest(context, "/sessions", "GET", null, token);
                    if (sessListRes.isSuccess() && sessListRes.json != null) {
                        JSONArray items = sessListRes.json.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject sess = items.getJSONObject(i);
                                int subId = sess.optInt("subject_id", 1);
                                String startedStr = sess.optString("started_at", "");
                                String endedStr = sess.optString("ended_at", "");
                                int durationSec = sess.optInt("duration_seconds", 0);
                                long startMs = parseIsoOrEpoch(startedStr);
                                long endMs = !endedStr.isEmpty() ? parseIsoOrEpoch(endedStr) : (startMs + (durationSec * 1000L));
                                StudySession session = new StudySession(
                                        subId,
                                        startMs > 0 ? startMs : System.currentTimeMillis() - (durationSec * 1000L),
                                        endMs > 0 ? endMs : System.currentTimeMillis(),
                                        0,
                                        0,
                                        sess.optString("notes", "")
                                );
                                session.id = sess.optInt("id", 0);
                                dao.insertOrReplaceSession(session);
                                restoredSessions++;
                            }
                        }
                    }

                    org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(context);
                    String msg = "Restored " + restoredSessions + " sessions, " + restoredSubjects + " subjects & " + restoredDegrees + " degrees from EduTrace Server! 📥";
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(msg));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to restore cloud data.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) mainHandler.post(() -> callback.onError("Restore error: " + e.getMessage()));
            }
        });
    }

    private static long parseIsoOrEpoch(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || "null".equals(dateStr)) return 0;
        try {
            if (dateStr.matches("^\\d+$")) return Long.parseLong(dateStr);
            String clean = dateStr.replace("Z", "");
            if (clean.contains(".")) clean = clean.substring(0, clean.indexOf('.'));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date d = sdf.parse(clean);
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void restoreData(Context context, SyncCallback callback) {
        restoreData(context, new SyncProgressCallback() {
            @Override
            public void onProgress(String status) {}

            @Override
            public void onSuccess(String message) {
                if (callback != null) callback.onSuccess(message);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    public static void restoreData(Context context) {
        restoreData(context, (SyncProgressCallback) null);
    }

    // ==========================================
    // Account Security: Change Password
    // ==========================================

    public static void changePassword(Context context, String currentPassword, String newPassword, ActionCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Please sign in to change your password."));
            return;
        }
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("current_password", currentPassword);
                payload.put("new_password", newPassword);

                ApiResponse res = sendApiRequest(context, "/auth/change-password", "PUT", payload, token);
                if (res.isSuccess()) {
                    String msg = "Password updated successfully! 🔒";
                    if (res.json != null && res.json.has("message")) {
                        msg = res.json.getString("message");
                    }
                    final String successMsg = msg;
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(successMsg));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to update password.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    // ==========================================
    // In-App Notification Center & Timer-Match
    // ==========================================

    public static void fetchNotifications(Context context, boolean unreadOnly, NotificationsCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Sign in required to view notifications."));
            return;
        }
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                String endpoint = "/notifications?unread_only=" + (unreadOnly ? "true" : "false") + "&limit=50";
                ApiResponse res = sendApiRequest(context, endpoint, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    List<CloudNotification> list = new ArrayList<>();
                    JSONArray arr = res.json.optJSONArray("notifications");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            CloudNotification n = CloudNotification.fromJson(item);
                            if (n != null) list.add(n);
                        }
                    }
                    int unreadCount = res.json.optInt("unread_count", 0);
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(list, unreadCount));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load notifications.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError("Error loading notifications: " + e.getMessage()));
            }
        });
    }

    public static void fetchUnreadNotificationCount(Context context, CountCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onSuccess(0));
            return;
        }
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse res = sendApiRequest(context, "/notifications/unread-count", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    int count = res.json.optInt("count", res.json.optInt("unread_count", 0));
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(count));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(0));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onSuccess(0));
            }
        });
    }

    public static void markNotificationRead(Context context, int notificationId, ActionCallback callback) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse res = sendApiRequest(context, "/notifications/" + notificationId + "/read", "PUT", null, token);
                if (res.isSuccess()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess("Marked as read"));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void markAllNotificationsRead(Context context, ActionCallback callback) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse res = sendApiRequest(context, "/notifications/read-all", "PUT", null, token);
                if (res.isSuccess()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess("All notifications marked as read"));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void deleteNotification(Context context, int notificationId, ActionCallback callback) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse res = sendApiRequest(context, "/notifications/" + notificationId, "DELETE", null, token);
                if (res.isSuccess()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess("Notification removed"));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void checkTimerMatch(Context context, TimerMatchCallback callback) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse res = sendApiRequest(context, "/notifications/timer-match", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    TimerMatchStatus status = TimerMatchStatus.fromJson(res.json);
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(status));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ==========================================
    // Leaderboard & Student Rankings
    // ==========================================

    public static void fetchLeaderboard(Context context, String timeframe, LeaderboardCallback callback) {
        final String token = isLoggedIn(context) ? getAccessToken(context) : null;
        cloudExecutor.execute(() -> {
            try {
                String tf = (timeframe != null && !timeframe.isEmpty()) ? timeframe.toLowerCase() : "all_time";
                ApiResponse res = sendApiRequest(context, "/rankings?timeframe=" + tf, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    List<RankingUser> users = new ArrayList<>();
                    JSONArray arr = res.json.optJSONArray("rankings");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            RankingUser u = RankingUser.fromJson(item);
                            if (u != null) users.add(u);
                        }
                    }
                    RankingUser currentRank = null;
                    JSONObject curObj = res.json.optJSONObject("current_user");
                    if (curObj == null) curObj = res.json.optJSONObject("current_user_rank");
                    if (curObj == null) curObj = res.json.optJSONObject("current_user_stats");
                    if (curObj != null) {
                        currentRank = RankingUser.fromJson(curObj);
                    }
                    if (currentRank == null) {
                        for (RankingUser u : users) {
                            if (u.isCurrentUser) {
                                currentRank = u;
                                break;
                            }
                        }
                    }
                    int total = res.json.optInt("total_participants", users.size());
                    final RankingUser fCurrentRank = currentRank;
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(users, fCurrentRank, total));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load leaderboard rankings.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError("Leaderboard error: " + e.getMessage()));
            }
        });
    }

    public static void fetchMyRanking(Context context, MyRankingCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Sign in to view your personal ranking."));
            return;
        }
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                ApiResponse res = sendApiRequest(context, "/rankings/me", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    MyRankingSummary summary = MyRankingSummary.fromJson(res.json);
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(summary));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load user ranking.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError("Ranking error: " + e.getMessage()));
            }
        });
    }

    // ==========================================
    // 24-Hour Timeline & Sleep/Lost Time Analysis
    // ==========================================

    public static void fetch24hTimeline(Context context, String dateYmd, TimelineCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Sign in to analyze 24h daily routine."));
            return;
        }
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                String endpoint = "/analytics/timeline/24h" + (dateYmd != null && !dateYmd.isEmpty() ? "?date=" + dateYmd : "");
                ApiResponse res = sendApiRequest(context, endpoint, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    TimelineDayData data = TimelineDayData.fromJson(res.json);
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(data));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load 24h timeline data.";
                    if (callback != null) mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError("Timeline error: " + e.getMessage()));
            }
        });
    }

    public static void recordDeviceActivity(Context context, String eventType, ActionCallback callback) {
        if (!isLoggedIn(context) || eventType == null || eventType.isEmpty()) return;
        final String token = getAccessToken(context);
        cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event_type", eventType);
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                payload.put("timestamp", iso.format(new Date()));

                ApiResponse res = sendApiRequest(context, "/analytics/timeline/activity", "POST", payload, token);
                if (res.isSuccess()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess("Activity recorded"));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void recordDeviceActivity(Context context, String eventType) {
        recordDeviceActivity(context, eventType, null);
    }
}
