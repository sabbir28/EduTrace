package org.sabbir.edutrace.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.data.models.Subject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages an isolated, redundant local "Safe Vault" folder to securely preserve
 * user study data (degrees, subjects, study sessions) automatically in the background
 * without prompting or requiring user intervention.
 */
public class LocalVaultManager {
    private static final String TAG = "LocalVaultManager";
    private static final String VAULT_FILE_NAME = "edutrace_safe_vault.json";
    private static final String VAULT_BACKUP_NAME = "edutrace_safe_vault.bak";

    private static final ExecutorService vaultExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface VaultCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * Resolves the primary local safe vault directory (dual internal and external paths).
     */
    public static File getVaultDir(Context context) {
        File dir = null;
        try {
            File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                dir = new File(ext, "EduTrace_SafeVault");
            }
        } catch (Exception ignored) {}

        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            dir = new File(context.getFilesDir(), "safe_vault");
            if (!dir.exists()) dir.mkdirs();
        }
        return dir;
    }

    private static File getInternalVaultDir(Context context) {
        File dir = new File(context.getFilesDir(), "safe_vault");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Automatically saves all application data to the safe vault directory in the background.
     * Silent and non-intrusive.
     */
    public static void autoSaveVault(Context context) {
        if (context == null) return;
        saveVaultAsync(context.getApplicationContext(), null);
    }

    /**
     * Saves a snapshot of all user application data to the safe vault.
     */
    public static void saveVaultAsync(Context context, VaultCallback callback) {
        vaultExecutor.execute(() -> {
            try {
                StudyDatabase db = StudyDatabase.getDatabase(context);
                StudyDao dao = db.studyDao();

                List<Degree> degrees = dao.getDegreesListSync();
                List<Subject> subjects = dao.getSubjectsListSync();
                List<StudySession> sessions = dao.getSessionsListSync();

                JSONObject root = new JSONObject();
                root.put("schema_version", 1);
                root.put("timestamp", System.currentTimeMillis());
                root.put("saved_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

                // 1. Serialize Degrees
                JSONArray degreesArray = new JSONArray();
                for (Degree d : degrees) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", d.id);
                    obj.put("name", d.name);
                    obj.put("colorHex", d.colorHex);
                    degreesArray.put(obj);
                }
                root.put("degrees", degreesArray);

                // 2. Serialize Subjects
                JSONArray subjectsArray = new JSONArray();
                for (Subject s : subjects) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", s.id);
                    obj.put("degreeId", s.degreeId);
                    obj.put("name", s.name);
                    obj.put("colorHex", s.colorHex);
                    subjectsArray.put(obj);
                }
                root.put("subjects", subjectsArray);

                // 3. Serialize Study Sessions
                JSONArray sessionsArray = new JSONArray();
                for (StudySession s : sessions) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", s.id);
                    obj.put("subjectId", s.subjectId);
                    obj.put("startTimestamp", s.startTimestamp);
                    obj.put("endTimestamp", s.endTimestamp);
                    obj.put("breakDurationMillis", s.breakDurationMillis);
                    obj.put("distractionCount", s.distractionCount);
                    obj.put("notes", s.notes != null ? s.notes : "");
                    sessionsArray.put(obj);
                }
                root.put("sessions", sessionsArray);

                String jsonContent = root.toString(2);

                // Write to primary vault location
                File primaryDir = getVaultDir(context);
                writeFileWithBackup(primaryDir, jsonContent);

                // Also write to internal redundant vault location
                File internalDir = getInternalVaultDir(context);
                if (!internalDir.equals(primaryDir)) {
                    writeFileWithBackup(internalDir, jsonContent);
                }

                String successMsg = "Safe Vault updated: " + sessions.size() + " sessions & " + subjects.size() + " subjects secured 🛡️";
                Log.d(TAG, successMsg);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(successMsg));
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to save safe vault", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Vault error: " + e.getMessage()));
                }
            }
        });
    }

    private static void writeFileWithBackup(File dir, String content) throws Exception {
        if (!dir.exists()) dir.mkdirs();
        File targetFile = new File(dir, VAULT_FILE_NAME);
        File backupFile = new File(dir, VAULT_BACKUP_NAME);

        // Rotate previous vault file to backup
        if (targetFile.exists()) {
            if (backupFile.exists()) backupFile.delete();
            targetFile.renameTo(backupFile);
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    /**
     * Checks if the local database is empty, and if so, automatically restores
     * data from the safe vault silently so user never loses their data.
     */
    public static void autoRecoverIfEmpty(Context context) {
        if (context == null) return;

        vaultExecutor.execute(() -> {
            try {
                StudyDatabase db = StudyDatabase.getDatabase(context);
                StudyDao dao = db.studyDao();

                List<StudySession> existing = dao.getSessionsListSync();
                List<Subject> existingSubjects = dao.getSubjectsListSync();

                // Only recover if local database is currently blank
                if ((existing == null || existing.isEmpty()) && (existingSubjects == null || existingSubjects.isEmpty())) {
                    File vaultFile = findExistingVaultFile(context);
                    if (vaultFile != null && vaultFile.exists() && vaultFile.length() > 0) {
                        Log.i(TAG, "Database is empty, auto-recovering from Safe Vault: " + vaultFile.getAbsolutePath());
                        restoreFromVaultFile(context, vaultFile, new VaultCallback() {
                            @Override
                            public void onSuccess(String message) {
                                Log.i(TAG, "Safe Vault auto-recovery successful: " + message);
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Safe Vault auto-recovery failed: " + error);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking auto-recovery", e);
            }
        });
    }

    private static File findExistingVaultFile(Context context) {
        File[] candidates = new File[]{
                new File(getVaultDir(context), VAULT_FILE_NAME),
                new File(getInternalVaultDir(context), VAULT_FILE_NAME),
                new File(getVaultDir(context), VAULT_BACKUP_NAME),
                new File(getInternalVaultDir(context), VAULT_BACKUP_NAME)
        };

        for (File f : candidates) {
            if (f.exists() && f.length() > 0) return f;
        }
        return null;
    }

    /**
     * Restores all data from the safe vault file into the Room database.
     */
    public static void restoreFromVault(Context context, VaultCallback callback) {
        vaultExecutor.execute(() -> {
            File vaultFile = findExistingVaultFile(context);
            if (vaultFile == null || !vaultFile.exists()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("No Safe Vault backup file found on device."));
                }
                return;
            }
            restoreFromVaultFile(context, vaultFile, callback);
        });
    }

    private static void restoreFromVaultFile(Context context, File vaultFile, VaultCallback callback) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(vaultFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject root = new JSONObject(sb.toString());
            StudyDatabase db = StudyDatabase.getDatabase(context);
            StudyDao dao = db.studyDao();

            int restoredDegrees = 0;
            int restoredSubjects = 0;
            int restoredSessions = 0;

            // 1. Restore Degrees
            JSONArray degreesArr = root.optJSONArray("degrees");
            if (degreesArr != null) {
                for (int i = 0; i < degreesArr.length(); i++) {
                    JSONObject obj = degreesArr.getJSONObject(i);
                    Degree d = new Degree(obj.optString("name", "Degree"), obj.optString("colorHex", "#FACC15"));
                    d.id = obj.optInt("id", 0);
                    dao.insertOrReplaceDegree(d);
                    restoredDegrees++;
                }
            }

            // 2. Restore Subjects
            JSONArray subjectsArr = root.optJSONArray("subjects");
            if (subjectsArr != null) {
                for (int i = 0; i < subjectsArr.length(); i++) {
                    JSONObject obj = subjectsArr.getJSONObject(i);
                    Subject s = new Subject(obj.optInt("degreeId", 1), obj.optString("name", "Subject"), obj.optString("colorHex", "#FACC15"));
                    s.id = obj.optInt("id", 0);
                    dao.insertOrReplaceSubject(s);
                    restoredSubjects++;
                }
            }

            // 3. Restore Sessions
            JSONArray sessionsArr = root.optJSONArray("sessions");
            if (sessionsArr != null) {
                for (int i = 0; i < sessionsArr.length(); i++) {
                    JSONObject obj = sessionsArr.getJSONObject(i);
                    StudySession session = new StudySession(
                            obj.optInt("subjectId", 1),
                            obj.optLong("startTimestamp", System.currentTimeMillis()),
                            obj.optLong("endTimestamp", System.currentTimeMillis()),
                            obj.optLong("breakDurationMillis", 0),
                            obj.optInt("distractionCount", 0),
                            obj.optString("notes", "")
                    );
                    session.id = obj.optInt("id", 0);
                    dao.insertOrReplaceSession(session);
                    restoredSessions++;
                }
            }

            String msg = "Restored " + restoredSessions + " sessions, " + restoredSubjects + " subjects, and " + restoredDegrees + " degrees from Safe Vault! 🛡️";
            Log.i(TAG, msg);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(msg));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from vault file", e);
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Failed to parse Safe Vault: " + e.getMessage()));
            }
        }
    }

    /**
     * Returns human-readable status of the safe vault file.
     */
    public static String getVaultStatus(Context context) {
        File vault = findExistingVaultFile(context);
        if (vault == null || !vault.exists()) return "Not created yet";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return "Protected (" + sdf.format(new Date(vault.lastModified())) + ")";
    }
}
