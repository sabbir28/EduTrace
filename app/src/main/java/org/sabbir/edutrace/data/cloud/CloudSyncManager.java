package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.utils.LocalVaultManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages bulk cloud synchronization: backing up local Room database records
 * to EduTrace Server, restoring remote cloud history, and debounced background
 * startup synchronization.
 */
public class CloudSyncManager {
    private static final String TAG = CloudApiClient.TAG;
    public static final String KEY_LAST_BACKUP = "cloud_last_backup";
    public static final String KEY_AUTO_BACKUP = "cloud_auto_backup_enabled";

    private static final long AUTO_SYNC_DEBOUNCE_MS = 15_000L;
    private static volatile long sLastAutoSyncTimestamp = 0;
    private static final AtomicBoolean sIsAutoSyncing = new AtomicBoolean(false);

    public static String getLastBackupTime(Context context) {
        if (context == null) return "Never";
        SharedPreferences prefs = context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE);
        long lastTime = prefs.getLong(KEY_LAST_BACKUP, 0);
        if (lastTime == 0) return "Never";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return sdf.format(new Date(lastTime));
    }

    /**
     * Completely automatic startup sync: pulls down user's data from cloud in background,
     * updates Room database seamlessly, and backs up any offline activity without disrupting
     * user experience or freezing the UI.
     */
    public static void autoSyncOnStartup(Context context) {
        if (context == null || !CloudAuthManager.isLoggedIn(context)) return;

        long now = System.currentTimeMillis();
        if (now - sLastAutoSyncTimestamp < AUTO_SYNC_DEBOUNCE_MS) {
            return;
        }
        if (!sIsAutoSyncing.compareAndSet(false, true)) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                Log.d(TAG, "Starting automatic background data load on app open...");

                // 1. Pull latest academic data from cloud (/sync/restore) & update Room DB
                doRestoreData(appContext, null);

                // 2. Refresh user profile session (/auth/me)
                CloudAuthManager.doSyncUserProfile(appContext);

                // 3. Prefetch notifications cache & unread count (/notifications)
                CloudRankingsManager.doPrefetchNotifications(appContext);

                // 4. Prefetch scholar rankings (/rankings/me & /rankings)
                CloudRankingsManager.doPrefetchRankings(appContext);

                // 5. Record app open telemetry
                CloudRankingsManager.recordDeviceActivity(appContext, "app_open");

                Log.d(TAG, "Automatic background data load on app open completed.");
            } catch (Throwable t) {
                Log.w(TAG, "Automatic background load completed with notice: " + t.getMessage());
            } finally {
                sLastAutoSyncTimestamp = System.currentTimeMillis();
                sIsAutoSyncing.set(false);
            }
        });
    }

    public static void backupData(Context context, CloudDbManager.SyncProgressCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Please sign in to backup data to EduTrace Server."));
            return;
        }
        CloudApiClient.cloudExecutor.execute(() -> doBackupData(context, callback));
    }

    public static void backupData(Context context, CloudDbManager.SyncCallback callback) {
        backupData(context, new CloudDbManager.SyncProgressCallback() {
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
        backupData(context, (CloudDbManager.SyncProgressCallback) null);
    }

    public static boolean doBackupData(Context context, CloudDbManager.SyncProgressCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Please sign in to view cloud status."));
            return false;
        }

        try {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Securing local study records into Local Safe Vault..."));

            // Safely archive local study history on device (Local Safe Vault)
            LocalVaultManager.autoSaveVault(context);

            long now = System.currentTimeMillis();
            if (context != null) {
                context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_LAST_BACKUP, now)
                        .apply();
            }

            // Sync is strictly pull-only (Server -> Client). Never send time or data updates to server.
            String successMsg = "Local data safely secured in Local Vault. Cloud sync is in download-only mode from server.";
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(successMsg));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Local vault save notice: " + e.getMessage());
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Backup error: " + e.getMessage()));
            return false;
        }
    }

    public static void restoreData(Context context, CloudDbManager.SyncProgressCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Please sign in to restore data from EduTrace Server."));
            return;
        }
        CloudApiClient.cloudExecutor.execute(() -> doRestoreData(context, callback));
    }

    public static void restoreData(Context context, CloudDbManager.SyncCallback callback) {
        restoreData(context, new CloudDbManager.SyncProgressCallback() {
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
        restoreData(context, (CloudDbManager.SyncProgressCallback) null);
    }

    public static boolean doRestoreData(Context context, CloudDbManager.SyncProgressCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Please sign in to restore data from EduTrace Server."));
            return false;
        }

        final String token = CloudAuthManager.getAccessToken(context);
        try {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Connecting to EduTrace REST API..."));

            CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/sync/restore", "GET", null, token);

            if (res.isSuccess() && res.json != null) {
                StudyDatabase localDb = StudyDatabase.getDatabase(context);
                StudyDao dao = localDb.studyDao();

                int restoredDegrees = 0;
                int restoredSubjects = 0;
                int restoredSessions = 0;

                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Restoring academic degrees & subjects..."));

                // 1. Degrees
                JSONArray degArr = res.json.optJSONArray("degrees");
                if (degArr != null) {
                    for (int i = 0; i < degArr.length(); i++) {
                        try {
                            JSONObject d = degArr.getJSONObject(i);
                            int localId = d.has("local_id") ? d.getInt("local_id") : d.optInt("id", 0);
                            String name = d.optString("name", "Degree " + localId);
                            String color = d.optString("color_hex", d.optString("color", "#3B82F6"));
                            Degree degree = new Degree(name, color);
                            degree.id = localId;
                            dao.insertOrReplaceDegree(degree);
                            restoredDegrees++;
                        } catch (Throwable t) {
                            Log.w(TAG, "Error restoring degree: " + t.getMessage());
                        }
                    }
                }

                // 2. Subjects
                JSONArray subArr = res.json.optJSONArray("subjects");
                if (subArr != null) {
                    for (int i = 0; i < subArr.length(); i++) {
                        try {
                            JSONObject s = subArr.getJSONObject(i);
                            int localId = s.has("local_id") ? s.getInt("local_id") : s.optInt("id", 0);
                            int degId = s.has("degree_local_id") ? s.getInt("degree_local_id") : s.optInt("degree_id", 1);
                            String name = s.optString("name", "Subject " + localId);
                            String color = s.optString("color_hex", s.optString("color", "#2563EB"));
                            Subject subject = new Subject(degId, name, color);
                            subject.id = localId;
                            dao.insertOrReplaceSubject(subject);
                            restoredSubjects++;
                        } catch (Throwable t) {
                            Log.w(TAG, "Error restoring subject: " + t.getMessage());
                        }
                    }
                }

                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Restoring study session history..."));

                // 3. Sessions
                JSONArray sessArr = res.json.optJSONArray("sessions");
                if (sessArr != null) {
                    for (int i = 0; i < sessArr.length(); i++) {
                        try {
                            JSONObject sess = sessArr.getJSONObject(i);
                            int localId = sess.has("local_id") ? sess.getInt("local_id") : sess.optInt("id", 0);
                            int subId = sess.has("subject_local_id") ? sess.getInt("subject_local_id") : sess.optInt("subject_id", 1);
                            long startMs = sess.optLong("start_timestamp", 0);
                            if (startMs <= 0) {
                                startMs = parseIsoOrEpoch(sess.optString("started_at", ""));
                            }
                            long endMs = sess.optLong("end_timestamp", 0);
                            if (endMs <= 0) {
                                endMs = parseIsoOrEpoch(sess.optString("ended_at", ""));
                            }
                            if (startMs <= 0) startMs = System.currentTimeMillis();
                            if (endMs <= 0) endMs = startMs;

                            StudySession session = new StudySession(
                                    subId,
                                    startMs,
                                    endMs,
                                    sess.optLong("break_duration_millis", 0),
                                    sess.optInt("distraction_count", 0),
                                    sess.optString("notes", "")
                            );
                            session.id = localId;
                            dao.insertOrReplaceSession(session);
                            restoredSessions++;
                        } catch (Throwable t) {
                            Log.w(TAG, "Error restoring session: " + t.getMessage());
                        }
                    }
                }

                LocalVaultManager.autoSaveVault(context);

                String msg = "Restored " + restoredSessions + " sessions, " + restoredSubjects + " subjects, and " + restoredDegrees + " degrees from cloud! 📥";
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(msg));
                return true;
            } else if (res.statusCode == 404) {
                return doFallbackRestore(context, token, callback);
            } else {
                String err = res.errorMessage != null ? res.errorMessage : "Failed to restore cloud data.";
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(err));
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Restore error: " + e.getMessage());
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Restore error: " + e.getMessage()));
            return false;
        }
    }

    private static boolean doFallbackRestore(Context context, String token, CloudDbManager.SyncProgressCallback callback) {
        try {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Restoring from EduTrace Server records..."));
            StudyDatabase localDb = StudyDatabase.getDatabase(context);
            StudyDao dao = localDb.studyDao();
            int restoredDegrees = 0;
            int restoredSubjects = 0;

            CloudApiClient.ApiResponse degListRes = CloudApiClient.sendApiRequest(context, "/degrees", "GET", null, token);
            if (degListRes.isSuccess() && degListRes.json != null) {
                JSONArray items = degListRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        try {
                            JSONObject d = items.getJSONObject(i);
                            Degree degree = new Degree(d.getString("name"), d.optString("color", "#3B82F6"));
                            degree.id = d.getInt("id");
                            dao.insertOrReplaceDegree(degree);
                            restoredDegrees++;
                        } catch (Throwable t) {
                            Log.w(TAG, "Error fallback restoring degree: " + t.getMessage());
                        }
                    }
                }
            }

            CloudApiClient.ApiResponse subListRes = CloudApiClient.sendApiRequest(context, "/subjects", "GET", null, token);
            if (subListRes.isSuccess() && subListRes.json != null) {
                JSONArray items = subListRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        try {
                            JSONObject s = items.getJSONObject(i);
                            int courseId = s.optInt("course_id", 1);
                            Subject subject = new Subject(courseId, s.getString("name"), s.optString("color", "#2563EB"));
                            subject.id = s.getInt("id");
                            dao.insertOrReplaceSubject(subject);
                            restoredSubjects++;
                        } catch (Throwable t) {
                            Log.w(TAG, "Error fallback restoring subject: " + t.getMessage());
                        }
                    }
                }
            }

            int restoredSessions = 0;
            CloudApiClient.ApiResponse sessListRes = CloudApiClient.sendApiRequest(context, "/sessions", "GET", null, token);
            if (sessListRes.isSuccess() && sessListRes.json != null) {
                JSONArray items = sessListRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        try {
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
                        } catch (Throwable t) {
                            Log.w(TAG, "Error fallback restoring session: " + t.getMessage());
                        }
                    }
                }
            }

            LocalVaultManager.autoSaveVault(context);
            String msg = "Restored " + restoredSessions + " sessions, " + restoredSubjects + " subjects & " + restoredDegrees + " degrees from EduTrace Server! 📥";
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(msg));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Fallback restore error: " + e.getMessage());
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Restore error: " + e.getMessage()));
            return false;
        }
    }

    public static long parseIsoOrEpoch(String dateStr) {
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
}
