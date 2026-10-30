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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages atomic cloud synchronization for academic degrees and subjects:
 * pulls curriculum and history from server, deduplicates degrees/subjects by name
 * to prevent double/duplicate entries, syncs only genuinely missing items,
 * and strictly guarantees that NO local study time or session durations are uploaded via sync.
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
     * deduplicates any double degrees/subjects, syncs only missing curriculum (zero local time sent),
     * and refreshes notifications, rankings, and user session.
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

                // 1. Pull latest academic data from cloud (/sync/restore), de-duplicate & update Room DB
                doRestoreData(appContext, null);

                // 2. Sync only missing degrees/subjects to server (strictly ZERO session time sent)
                doSyncCurriculum(appContext, null);

                // 3. Refresh user profile session (/auth/me)
                CloudAuthManager.doSyncUserProfile(appContext);

                // 4. Prefetch notifications cache & unread count (/notifications)
                CloudRankingsManager.doPrefetchNotifications(appContext);

                // 5. Prefetch scholar rankings (/rankings/me & /rankings)
                CloudRankingsManager.doPrefetchRankings(appContext);

                // 6. Record app open telemetry
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

    /**
     * Sync curriculum structure (Degrees & Subjects) only:
     * Checks what degrees/subjects already exist on the server.
     * Only sends items that are truly MISSING on the server to prevent duplicate/double names.
     * NEVER sends any local study time or session durations.
     */
    public static void syncCurriculum(Context context, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Sign in required."));
            return;
        }
        CloudApiClient.cloudExecutor.execute(() -> doSyncCurriculum(context, callback));
    }

    public static void syncCurriculum(Context context) {
        syncCurriculum(context, null);
    }

    public static boolean doSyncCurriculum(Context context, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return false;
        final String token = CloudAuthManager.getAccessToken(context);

        try {
            StudyDatabase localDb = StudyDatabase.getDatabase(context);
            StudyDao dao = localDb.studyDao();

            List<Degree> localDegrees = dao.getDegreesListSync();
            List<Subject> localSubjects = dao.getSubjectsListSync();

            // 1. Query server to inspect existing degrees (case-insensitive name check)
            CloudApiClient.ApiResponse serverDegRes = CloudApiClient.sendApiRequest(context, "/degrees", "GET", null, token);
            Set<String> serverDegreeNames = new HashSet<>();
            if (serverDegRes.isSuccess() && serverDegRes.json != null) {
                JSONArray items = serverDegRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String n = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
                        if (!n.isEmpty()) serverDegreeNames.add(n);
                    }
                }
            }

            // 2. Query server to inspect existing subjects (case-insensitive name check)
            CloudApiClient.ApiResponse serverSubRes = CloudApiClient.sendApiRequest(context, "/subjects", "GET", null, token);
            Set<String> serverSubjectNames = new HashSet<>();
            if (serverSubRes.isSuccess() && serverSubRes.json != null) {
                JSONArray items = serverSubRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String n = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
                        if (!n.isEmpty()) serverSubjectNames.add(n);
                    }
                }
            }

            // 3. Collect only degrees that are TRULY MISSING on the server (prevents double degree creation)
            JSONArray missingDegreesArr = new JSONArray();
            Set<String> addedDegKeys = new HashSet<>();
            for (Degree d : localDegrees) {
                if (d.name == null || d.name.trim().isEmpty()) continue;
                String key = d.name.trim().toLowerCase(Locale.ROOT);
                if (!serverDegreeNames.contains(key) && !addedDegKeys.contains(key)) {
                    JSONObject dObj = new JSONObject();
                    dObj.put("local_id", d.id);
                    dObj.put("name", d.name.trim());
                    dObj.put("color_hex", d.colorHex != null ? d.colorHex : "#3B82F6");
                    missingDegreesArr.put(dObj);
                    addedDegKeys.add(key);
                }
            }

            // 4. Collect only subjects that are TRULY MISSING on the server
            JSONArray missingSubjectsArr = new JSONArray();
            Set<String> addedSubKeys = new HashSet<>();
            for (Subject s : localSubjects) {
                if (s.name == null || s.name.trim().isEmpty()) continue;
                String key = s.name.trim().toLowerCase(Locale.ROOT);
                if (!serverSubjectNames.contains(key) && !addedSubKeys.contains(key)) {
                    JSONObject sObj = new JSONObject();
                    sObj.put("local_id", s.id);
                    sObj.put("degree_local_id", s.degreeId);
                    sObj.put("name", s.name.trim());
                    sObj.put("color_hex", s.colorHex != null ? s.colorHex : "#2563EB");
                    missingSubjectsArr.put(sObj);
                    addedSubKeys.add(key);
                }
            }

            // 5. If missing items exist, send only the missing curriculum with ZERO study sessions/time
            if (missingDegreesArr.length() > 0 || missingSubjectsArr.length() > 0) {
                JSONObject payload = new JSONObject();
                payload.put("degrees", missingDegreesArr);
                payload.put("subjects", missingSubjectsArr);
                payload.put("sessions", new JSONArray()); // Strictly NO local study time sent!

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/sync/backup", "POST", payload, token);
                if (res.isSuccess()) {
                    Log.d(TAG, "Synced missing curriculum: " + missingDegreesArr.length() + " degrees & " + missingSubjectsArr.length() + " subjects (zero study time).");
                }
            } else {
                Log.d(TAG, "All degrees and subjects already exist on server. Zero duplicate requests sent.");
            }

            LocalVaultManager.autoSaveVault(context);
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Curriculum synced."));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Curriculum sync notice: " + e.getMessage());
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            return false;
        }
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

            // 1. Safely archive local study history on device (Local Safe Vault)
            LocalVaultManager.autoSaveVault(context);

            // 2. Sync missing degrees and subjects if any (with ZERO study time)
            doSyncCurriculum(context, null);

            long now = System.currentTimeMillis();
            if (context != null) {
                context.getSharedPreferences(CloudApiClient.PREFS_AUTH, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_LAST_BACKUP, now)
                        .apply();
            }

            String successMsg = "Local data safely secured in Local Vault. Curriculum synced (no local time sent).";
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

    /**
     * Pulls degrees, subjects, and study sessions from the server.
     * De-duplicates degrees and subjects by name so degree names are never doubled/duplicated.
     */
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

                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Restoring & deduplicating degrees and subjects..."));

                // Step A: Inspect existing local degrees and merge any pre-existing duplicate names
                List<Degree> localDegrees = dao.getDegreesListSync();
                Map<String, Degree> localDegreeByName = new HashMap<>();
                List<Subject> allLocalSubjects = dao.getSubjectsListSync();

                for (Degree ld : localDegrees) {
                    if (ld.name == null) continue;
                    String key = ld.name.trim().toLowerCase(Locale.ROOT);
                    if (!localDegreeByName.containsKey(key)) {
                        localDegreeByName.put(key, ld);
                    } else {
                        // Pre-existing local duplicate degree found! Merge its subjects into primary degree and remove duplicate
                        Degree primary = localDegreeByName.get(key);
                        for (Subject s : allLocalSubjects) {
                            if (s.degreeId == ld.id) {
                                s.degreeId = primary.id;
                                dao.updateSubject(s);
                            }
                        }
                        dao.deleteDegree(ld);
                    }
                }

                // Step B: Process server degrees with strict name-based de-duplication
                Map<Integer, Integer> serverToLocalDegreeId = new HashMap<>();
                JSONArray degArr = res.json.optJSONArray("degrees");
                if (degArr != null) {
                    for (int i = 0; i < degArr.length(); i++) {
                        try {
                            JSONObject d = degArr.getJSONObject(i);
                            int serverDegId = d.has("id") ? d.getInt("id") : d.optInt("local_id", 0);
                            String name = d.optString("name", "Degree " + serverDegId).trim();
                            String color = d.optString("color_hex", d.optString("color", "#3B82F6"));
                            String key = name.toLowerCase(Locale.ROOT);

                            if (localDegreeByName.containsKey(key)) {
                                // Match found! Reuse existing local degree ID, NEVER create a double entry!
                                Degree existing = localDegreeByName.get(key);
                                serverToLocalDegreeId.put(serverDegId, existing.id);
                                if (color != null && !color.equals(existing.colorHex)) {
                                    existing.colorHex = color;
                                    dao.updateDegree(existing);
                                }
                                restoredDegrees++;
                            } else {
                                // Truly missing locally: insert cleanly
                                Degree newDeg = new Degree(name, color);
                                dao.insertDegree(newDeg);
                                List<Degree> refreshed = dao.getDegreesListSync();
                                Degree created = null;
                                for (Degree r : refreshed) {
                                    if (r.name != null && r.name.trim().equalsIgnoreCase(name)) {
                                        created = r;
                                        break;
                                    }
                                }
                                int localId = (created != null) ? created.id : serverDegId;
                                serverToLocalDegreeId.put(serverDegId, localId);
                                localDegreeByName.put(key, created != null ? created : newDeg);
                                restoredDegrees++;
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Error restoring degree: " + t.getMessage());
                        }
                    }
                }

                // Step C: Process server subjects with strict name and degree de-duplication
                List<Subject> refreshedLocalSubjects = dao.getSubjectsListSync();
                JSONArray subArr = res.json.optJSONArray("subjects");
                if (subArr != null) {
                    for (int i = 0; i < subArr.length(); i++) {
                        try {
                            JSONObject s = subArr.getJSONObject(i);
                            int serverSubId = s.has("id") ? s.getInt("id") : s.optInt("local_id", 0);
                            int serverDegId = s.has("degree_local_id") ? s.getInt("degree_local_id") : s.optInt("degree_id", 1);
                            int targetDegId = serverToLocalDegreeId.containsKey(serverDegId)
                                    ? serverToLocalDegreeId.get(serverDegId)
                                    : (localDegreeByName.values().isEmpty() ? 1 : localDegreeByName.values().iterator().next().id);

                            String name = s.optString("name", "Subject " + serverSubId).trim();
                            String color = s.optString("color_hex", s.optString("color", "#2563EB"));

                            Subject existingSub = null;
                            for (Subject sub : refreshedLocalSubjects) {
                                if (sub.degreeId == targetDegId && sub.name != null && sub.name.trim().equalsIgnoreCase(name)) {
                                    existingSub = sub;
                                    break;
                                }
                            }

                            if (existingSub != null) {
                                // Already exists locally! Reuse existing, do NOT duplicate
                                if (color != null && !color.equals(existingSub.colorHex)) {
                                    existingSub.colorHex = color;
                                    dao.updateSubject(existingSub);
                                }
                                restoredSubjects++;
                            } else {
                                // Truly missing locally: insert
                                Subject newSub = new Subject(targetDegId, name, color);
                                dao.insertSubject(newSub);
                                refreshedLocalSubjects.add(newSub);
                                restoredSubjects++;
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Error restoring subject: " + t.getMessage());
                        }
                    }
                }

                // Step D: Pull study sessions without duplicate records
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onProgress("Restoring verified study sessions..."));

                JSONArray sessArr = res.json.optJSONArray("sessions");
                if (sessArr != null) {
                    List<StudySession> existingSessions = dao.getSessionsListSync();
                    for (int i = 0; i < sessArr.length(); i++) {
                        try {
                            JSONObject sess = sessArr.getJSONObject(i);
                            int subId = sess.has("subject_local_id") ? sess.getInt("subject_local_id") : sess.optInt("subject_id", 1);
                            long startMs = sess.optLong("start_timestamp", 0);
                            if (startMs <= 0) startMs = parseIsoOrEpoch(sess.optString("started_at", ""));
                            long endMs = sess.optLong("end_timestamp", 0);
                            if (endMs <= 0) endMs = parseIsoOrEpoch(sess.optString("ended_at", ""));
                            if (startMs <= 0) startMs = System.currentTimeMillis();
                            if (endMs <= 0) endMs = startMs;

                            boolean exists = false;
                            for (StudySession es : existingSessions) {
                                if (es.subjectId == subId && Math.abs(es.startTimestamp - startMs) < 1500) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                StudySession session = new StudySession(
                                        subId,
                                        startMs,
                                        endMs,
                                        sess.optLong("break_duration_millis", 0),
                                        sess.optInt("distraction_count", 0),
                                        sess.optString("notes", "")
                                );
                                dao.insertSession(session);
                                existingSessions.add(session);
                                restoredSessions++;
                            }
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

            List<Degree> localDegrees = dao.getDegreesListSync();
            Map<String, Degree> localDegreeByName = new HashMap<>();
            for (Degree ld : localDegrees) {
                if (ld.name != null) localDegreeByName.put(ld.name.trim().toLowerCase(Locale.ROOT), ld);
            }
            Map<Integer, Integer> serverToLocalDegreeId = new HashMap<>();

            CloudApiClient.ApiResponse degListRes = CloudApiClient.sendApiRequest(context, "/degrees", "GET", null, token);
            if (degListRes.isSuccess() && degListRes.json != null) {
                JSONArray items = degListRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        try {
                            JSONObject d = items.getJSONObject(i);
                            int serverId = d.getInt("id");
                            String name = d.getString("name").trim();
                            String color = d.optString("color", "#3B82F6");
                            String key = name.toLowerCase(Locale.ROOT);

                            if (localDegreeByName.containsKey(key)) {
                                serverToLocalDegreeId.put(serverId, localDegreeByName.get(key).id);
                            } else {
                                Degree degree = new Degree(name, color);
                                dao.insertDegree(degree);
                                List<Degree> refreshed = dao.getDegreesListSync();
                                for (Degree r : refreshed) {
                                    if (r.name != null && r.name.trim().equalsIgnoreCase(name)) {
                                        serverToLocalDegreeId.put(serverId, r.id);
                                        localDegreeByName.put(key, r);
                                        break;
                                    }
                                }
                                restoredDegrees++;
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Error fallback restoring degree: " + t.getMessage());
                        }
                    }
                }
            }

            List<Subject> localSubjects = dao.getSubjectsListSync();
            CloudApiClient.ApiResponse subListRes = CloudApiClient.sendApiRequest(context, "/subjects", "GET", null, token);
            if (subListRes.isSuccess() && subListRes.json != null) {
                JSONArray items = subListRes.json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        try {
                            JSONObject s = items.getJSONObject(i);
                            int courseId = s.optInt("course_id", 1);
                            int targetDegId = serverToLocalDegreeId.containsKey(courseId)
                                    ? serverToLocalDegreeId.get(courseId)
                                    : (localDegreeByName.values().isEmpty() ? 1 : localDegreeByName.values().iterator().next().id);

                            String name = s.getString("name").trim();
                            String color = s.optString("color", "#2563EB");

                            boolean exists = false;
                            for (Subject sub : localSubjects) {
                                if (sub.degreeId == targetDegId && sub.name != null && sub.name.trim().equalsIgnoreCase(name)) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                Subject subject = new Subject(targetDegId, name, color);
                                dao.insertSubject(subject);
                                localSubjects.add(subject);
                                restoredSubjects++;
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Error fallback restoring subject: " + t.getMessage());
                        }
                    }
                }
            }

            LocalVaultManager.autoSaveVault(context);
            String msg = "Restored " + restoredSubjects + " subjects & " + restoredDegrees + " degrees from EduTrace Server! 📥";
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
