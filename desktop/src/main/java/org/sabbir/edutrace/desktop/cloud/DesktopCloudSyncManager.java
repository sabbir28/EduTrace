package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.desktop.data.DataManager;
import org.sabbir.edutrace.desktop.data.DesktopDegree;
import org.sabbir.edutrace.desktop.data.DesktopStudySession;
import org.sabbir.edutrace.desktop.data.DesktopSubject;

import javax.swing.*;
import java.util.*;

/**
 * Manages two-way curriculum synchronization, cloud backups, and data restore
 * between EduTrace Desktop and the EduTrace Server REST API.
 * Direct port of Android CloudSyncManager.java.
 */
public class DesktopCloudSyncManager {

    private static DesktopCloudSyncManager instance;

    public static synchronized DesktopCloudSyncManager getInstance() {
        if (instance == null) {
            instance = new DesktopCloudSyncManager();
        }
        return instance;
    }

    public interface SyncCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String message);
    }

    private DesktopCloudSyncManager() {}

    /**
     * Sync curriculum structure (Degrees & Subjects) only.
     * Prevents duplicate/double degrees and subjects by checking server state first.
     */
    public void syncCurriculum(SyncCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sign in required to synchronize curriculum."));
            return;
        }

        final String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onProgress("Connecting to EduTrace Server..."));

                DataManager dm = DataManager.getInstance();
                List<DesktopDegree> localDegrees = dm.getDegrees();
                List<DesktopSubject> localSubjects = dm.getSubjects();

                // 1. Inspect existing degrees on server
                DesktopCloudApiClient.ApiResponse degRes = DesktopCloudApiClient.sendApiRequest("/degrees", "GET", null, token);
                Set<String> serverDegreeNames = new HashSet<>();
                Map<String, Integer> serverDegreeIdByName = new HashMap<>();

                if (degRes.isSuccess() && degRes.json != null) {
                    JSONArray items = degRes.json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            String n = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
                            if (!n.isEmpty()) {
                                serverDegreeNames.add(n);
                                serverDegreeIdByName.put(n, item.optInt("id", -1));
                            }
                        }
                    }
                }

                // 2. Inspect existing subjects on server
                DesktopCloudApiClient.ApiResponse subRes = DesktopCloudApiClient.sendApiRequest("/subjects", "GET", null, token);
                Set<String> serverSubjectNames = new HashSet<>();
                if (subRes.isSuccess() && subRes.json != null) {
                    JSONArray items = subRes.json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            String n = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
                            if (!n.isEmpty()) serverSubjectNames.add(n);
                        }
                    }
                }

                // 3. Collect missing degrees
                JSONArray missingDegreesArr = new JSONArray();
                Set<String> addedDeg = new HashSet<>();
                for (DesktopDegree d : localDegrees) {
                    if (d.getName() == null || d.getName().trim().isEmpty()) continue;
                    String key = d.getName().trim().toLowerCase(Locale.ROOT);
                    if (!serverDegreeNames.contains(key) && !addedDeg.contains(key)) {
                        JSONObject dObj = new JSONObject();
                        dObj.put("local_id", d.getId());
                        dObj.put("name", d.getName().trim());
                        dObj.put("color_hex", d.getColorHex() != null ? d.getColorHex() : "#3B82F6");
                        missingDegreesArr.put(dObj);
                        addedDeg.add(key);
                    }
                }

                // 4. Collect missing subjects
                JSONArray missingSubjectsArr = new JSONArray();
                Set<String> addedSub = new HashSet<>();
                for (DesktopSubject s : localSubjects) {
                    if (s.getName() == null || s.getName().trim().isEmpty()) continue;
                    String key = s.getName().trim().toLowerCase(Locale.ROOT);
                    if (!serverSubjectNames.contains(key) && !addedSub.contains(key)) {
                        JSONObject sObj = new JSONObject();
                        sObj.put("local_id", s.getId());
                        sObj.put("degree_local_id", s.getDegreeId());
                        sObj.put("name", s.getName().trim());
                        sObj.put("color_hex", s.getColorHex() != null ? s.getColorHex() : "#2563EB");
                        missingSubjectsArr.put(sObj);
                        addedSub.add(key);
                    }
                }

                // 5. Send missing items via /sync/backup
                if (missingDegreesArr.length() > 0 || missingSubjectsArr.length() > 0) {
                    JSONObject payload = new JSONObject();
                    payload.put("degrees", missingDegreesArr);
                    payload.put("subjects", missingSubjectsArr);
                    payload.put("sessions", new JSONArray()); // zero study time sent

                    DesktopCloudApiClient.ApiResponse backupRes = DesktopCloudApiClient.sendApiRequest("/sync/backup", "POST", payload, token);
                    if (backupRes.isSuccess()) {
                        String msg = "Curriculum synced (" + missingDegreesArr.length() + " degrees & " + missingSubjectsArr.length() + " subjects added).";
                        if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess(msg));
                    } else {
                        String err = backupRes.errorMessage != null ? backupRes.errorMessage : "Failed to sync curriculum.";
                        if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                } else {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess("Curriculum is already up to date with server!"));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sync notice: " + e.getMessage()));
            }
        });
    }

    /**
     * Full Cloud Backup: backs up degrees, subjects, and study sessions to EduTrace Cloud.
     */
    public void backupData(SyncCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sign in required to backup data."));
            return;
        }

        final String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onProgress("Securing local curriculum and sessions..."));

                DataManager dm = DataManager.getInstance();
                List<DesktopDegree> localDegrees = dm.getDegrees();
                List<DesktopSubject> localSubjects = dm.getSubjects();
                List<DesktopStudySession> localSessions = dm.getSessions();

                JSONArray degArr = new JSONArray();
                for (DesktopDegree d : localDegrees) {
                    JSONObject obj = new JSONObject();
                    obj.put("local_id", d.getId());
                    obj.put("name", d.getName());
                    obj.put("color_hex", d.getColorHex());
                    degArr.put(obj);
                }

                JSONArray subArr = new JSONArray();
                for (DesktopSubject s : localSubjects) {
                    JSONObject obj = new JSONObject();
                    obj.put("local_id", s.getId());
                    obj.put("degree_local_id", s.getDegreeId());
                    obj.put("name", s.getName());
                    obj.put("color_hex", s.getColorHex());
                    subArr.put(obj);
                }

                JSONArray sessArr = new JSONArray();
                for (DesktopStudySession ss : localSessions) {
                    JSONObject obj = new JSONObject();
                    obj.put("uuid", ss.getId());
                    obj.put("subject_name", ss.getSubjectName());
                    obj.put("start_timestamp", ss.getStartTimestamp());
                    obj.put("end_timestamp", ss.getEndTimestamp());
                    obj.put("duration_seconds", ss.getDurationSeconds());
                    obj.put("mode", ss.getMode());
                    obj.put("notes", ss.getNotes());
                    sessArr.put(obj);
                }

                JSONObject payload = new JSONObject();
                payload.put("degrees", degArr);
                payload.put("subjects", subArr);
                payload.put("sessions", sessArr);

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/sync/backup", "POST", payload, token);
                if (res.isSuccess()) {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess("Cloud Backup Completed! " +
                            degArr.length() + " degrees, " + subArr.length() + " subjects, " + sessArr.length() + " sessions secured."));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Backup failed.";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Backup error: " + e.getMessage()));
            }
        });
    }

    /**
     * Full Cloud Restore: pulls degrees, subjects, and study sessions from EduTrace Cloud
     * and merges them into local storage with name-based de-duplication.
     */
    public void restoreData(SyncCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sign in required to restore cloud backup."));
            return;
        }

        final String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onProgress("Connecting to EduTrace REST API..."));

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/sync/restore", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onProgress("Restoring & de-duplicating degrees, subjects, and sessions..."));

                    DataManager dm = DataManager.getInstance();
                    List<DesktopDegree> localDegrees = dm.getDegrees();
                    Map<String, DesktopDegree> degreeByName = new HashMap<>();
                    for (DesktopDegree d : localDegrees) {
                        degreeByName.put(d.getName().trim().toLowerCase(Locale.ROOT), d);
                    }

                    int restoredDegrees = 0;
                    int restoredSubjects = 0;
                    int restoredSessions = 0;

                    // 1. Process Degrees
                    Map<Integer, Integer> serverToLocalDegreeId = new HashMap<>();
                    JSONArray degArr = res.json.optJSONArray("degrees");
                    if (degArr != null) {
                        for (int i = 0; i < degArr.length(); i++) {
                            JSONObject obj = degArr.getJSONObject(i);
                            int sDegId = obj.optInt("id", obj.optInt("local_id", -1));
                            String name = obj.optString("name", "").trim();
                            String color = obj.optString("color_hex", "#3B82F6");
                            if (name.isEmpty()) continue;

                            String key = name.toLowerCase(Locale.ROOT);
                            if (degreeByName.containsKey(key)) {
                                serverToLocalDegreeId.put(sDegId, degreeByName.get(key).getId());
                            } else {
                                DesktopDegree created = dm.addDegree(name, color);
                                if (created != null) {
                                    degreeByName.put(key, created);
                                    serverToLocalDegreeId.put(sDegId, created.getId());
                                    restoredDegrees++;
                                }
                            }
                        }
                    }

                    // 2. Process Subjects
                    List<DesktopSubject> localSubjects = dm.getSubjects();
                    Set<String> localSubjectKeys = new HashSet<>();
                    for (DesktopSubject s : localSubjects) {
                        localSubjectKeys.add(s.getName().trim().toLowerCase(Locale.ROOT));
                    }

                    JSONArray subArr = res.json.optJSONArray("subjects");
                    if (subArr != null) {
                        for (int i = 0; i < subArr.length(); i++) {
                            JSONObject obj = subArr.getJSONObject(i);
                            String name = obj.optString("name", "").trim();
                            String color = obj.optString("color_hex", "#FACC15");
                            int sDegId = obj.optInt("degree_id", obj.optInt("degree_local_id", 1));
                            int targetDegreeId = serverToLocalDegreeId.getOrDefault(sDegId, 1);
                            if (name.isEmpty()) continue;

                            String key = name.toLowerCase(Locale.ROOT);
                            if (!localSubjectKeys.contains(key)) {
                                dm.addSubject(targetDegreeId, name, color);
                                localSubjectKeys.add(key);
                                restoredSubjects++;
                            }
                        }
                    }

                    // 3. Process Sessions
                    List<DesktopStudySession> currentSessions = dm.getSessions();
                    Set<String> sessionIds = new HashSet<>();
                    for (DesktopStudySession s : currentSessions) {
                        if (s.getId() != null) sessionIds.add(s.getId());
                    }

                    JSONArray sessArr = res.json.optJSONArray("sessions");
                    if (sessArr != null) {
                        for (int i = 0; i < sessArr.length(); i++) {
                            JSONObject obj = sessArr.getJSONObject(i);
                            String uuid = obj.optString("uuid", UUID.randomUUID().toString());
                            if (!sessionIds.contains(uuid)) {
                                String subName = obj.optString("subject_name", obj.optString("title", "General Study"));
                                long start = obj.optLong("start_timestamp", System.currentTimeMillis());
                                long end = obj.optLong("end_timestamp", start + 1800000L);
                                long dur = obj.optLong("duration_seconds", 1800L);
                                String mode = obj.optString("mode", "STOPWATCH");
                                String notes = obj.optString("notes", "");

                                DesktopStudySession newSess = new DesktopStudySession(uuid, subName, start, end, dur, mode, notes);
                                dm.addSession(newSess);
                                sessionIds.add(uuid);
                                restoredSessions++;
                            }
                        }
                    }

                    String msg = "Restore Complete: " + restoredDegrees + " new degrees, " +
                            restoredSubjects + " new subjects, " + restoredSessions + " sessions merged from cloud!";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess(msg));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to restore data.";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Restore error: " + e.getMessage()));
            }
        });
    }
}
