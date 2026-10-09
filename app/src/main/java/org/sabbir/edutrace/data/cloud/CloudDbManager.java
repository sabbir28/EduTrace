package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.data.models.Subject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages secure cloud persistence and synchronization with the remote PostgreSQL database.
 * Connection credentials are fully obfuscated at compile-time to prevent plain string extraction.
 */
public class CloudDbManager {
    private static final String PREFS_AUTH = "EduTrace_Cloud_Auth";
    private static final String KEY_USER_ID = "cloud_user_id";
    private static final String KEY_USERNAME = "cloud_username";
    private static final String KEY_EMAIL = "cloud_email";
    private static final String KEY_DISPLAY_NAME = "cloud_display_name";
    private static final String KEY_IS_LOGGED_IN = "cloud_logged_in";
    private static final String KEY_LAST_BACKUP = "cloud_last_backup";
    public static final String KEY_AUTO_BACKUP = "cloud_auto_backup_enabled";

    // Obfuscated database connection parameters (protected from plain extraction)
    private static final byte[] ENC_HOST = new byte[] { (byte) 0x06, (byte) 0x19, (byte) 0x06, (byte) 0x51, (byte) 0xB3, (byte) 0xA7, (byte) 0xF0, (byte) 0xE8, (byte) 0xB2, (byte) 0xD5, (byte) 0xC2, (byte) 0xC1, (byte) 0xCF, (byte) 0xAA, (byte) 0xAC, (byte) 0xB1, (byte) 0xA4, (byte) 0xAA, (byte) 0xC8, (byte) 0xDD, (byte) 0xDD, (byte) 0x8A, (byte) 0x6E, (byte) 0x67, (byte) 0x63, (byte) 0x73, (byte) 0x6F, (byte) 0x0A, (byte) 0x58, (byte) 0x47, (byte) 0x49, (byte) 0x21, (byte) 0x25, (byte) 0x2F, (byte) 0x26, (byte) 0x39, (byte) 0x4D, (byte) 0x09, (byte) 0x1E, (byte) 0x15 };
    private static final byte[] ENC_PORT = new byte[] { (byte) 0x52, (byte) 0x5A, (byte) 0x46, (byte) 0x4E };
    private static final byte[] ENC_DB = new byte[] { (byte) 0x17, (byte) 0x01, (byte) 0x06, (byte) 0x08, (byte) 0xE4, (byte) 0xF8, (byte) 0xF4, (byte) 0xEB };
    private static final byte[] ENC_USER = new byte[] { (byte) 0x17, (byte) 0x01, (byte) 0x06, (byte) 0x08, (byte) 0xE4, (byte) 0xF8, (byte) 0xF4, (byte) 0xEB, (byte) 0xB1, (byte) 0xD1, (byte) 0xC4, (byte) 0xD0, (byte) 0xDA, (byte) 0xA9, (byte) 0xBA, (byte) 0xB4, (byte) 0xA1, (byte) 0xB9, (byte) 0x95, (byte) 0x80, (byte) 0x9B, (byte) 0x94, (byte) 0x71, (byte) 0x7A, (byte) 0x7F, (byte) 0x7E, (byte) 0x6C, (byte) 0x55, (byte) 0x49 };
    private static final byte[] ENC_PASS = new byte[] { (byte) 0x14, (byte) 0x0F, (byte) 0x17, (byte) 0x1E, (byte) 0xEA, (byte) 0xF8, (byte) 0xA3, (byte) 0xA0, (byte) 0xB1, (byte) 0xC1, (byte) 0xC4, (byte) 0xC0, (byte) 0xD3, (byte) 0xB7, (byte) 0xAB, (byte) 0xB2, (byte) 0xF9, (byte) 0xB7, (byte) 0x8A };
    private static final byte[] ENC_SSL = new byte[] { (byte) 0x15, (byte) 0x0B, (byte) 0x04, (byte) 0x09, (byte) 0xEA, (byte) 0xF8, (byte) 0xF4 };

    private static final ExecutorService cloudExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean tablesInitialized = false;

    public interface AuthCallback {
        void onSuccess(CloudUser user);
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

    private static String decode(byte[] data) {
        byte key = 0x5A;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ ((key + (i * 7 + 13)) & 0xFF));
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    private static void ensureDriverRegistered() {
        try {
            java.sql.DriverManager.registerDriver(new org.postgresql.Driver());
        } catch (Throwable ignored) {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static Connection getDirectConnection() throws SQLException {
        ensureDriverRegistered();
        String host = decode(ENC_HOST);
        String port = decode(ENC_PORT);
        String db = decode(ENC_DB);
        String user = decode(ENC_USER);
        String pass = decode(ENC_PASS);
        String ssl = decode(ENC_SSL);

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", ssl);
        props.setProperty("loginTimeout", "8");
        props.setProperty("connectTimeout", "8");
        props.setProperty("socketTimeout", "15");

        String primaryUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        Connection conn = null;

        try {
            DriverManager.setLoginTimeout(8);
            conn = DriverManager.getConnection(primaryUrl, props);
        } catch (SQLException primaryEx) {
            // Try with NonValidatingFactory in case mobile network CA or truststore fails
            java.util.Properties fallbackProps = new java.util.Properties();
            fallbackProps.putAll(props);
            fallbackProps.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");

            try {
                conn = DriverManager.getConnection(primaryUrl, fallbackProps);
            } catch (SQLException sslEx) {
                // Secondary fallback on Supabase transaction pooler port (6543)
                String fallbackUrl = "jdbc:postgresql://" + host + ":6543/" + db;
                try {
                    conn = DriverManager.getConnection(fallbackUrl, fallbackProps);
                } catch (SQLException secondaryEx) {
                    throw primaryEx; // Throw initial exception if all fallbacks failed
                }
            }
        }

        if (conn != null && !tablesInitialized) {
            initTablesIfNotExist(conn);
            tablesInitialized = true;
        }
        return conn;
    }

    private static void initTablesIfNotExist(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS edutrace_users (" +
                    "id SERIAL PRIMARY KEY, " +
                    "username VARCHAR(100) UNIQUE NOT NULL, " +
                    "email VARCHAR(150), " +
                    "password_hash VARCHAR(128) NOT NULL, " +
                    "salt VARCHAR(64) NOT NULL, " +
                    "display_name VARCHAR(150), " +
                    "created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS edutrace_user_degrees (" +
                    "id SERIAL PRIMARY KEY, " +
                    "user_id INT NOT NULL, " +
                    "local_id INT NOT NULL, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "color_hex VARCHAR(50), " +
                    "synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, " +
                    "CONSTRAINT uq_edutrace_degree UNIQUE (user_id, local_id)" +
                    ");");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS edutrace_user_subjects (" +
                    "id SERIAL PRIMARY KEY, " +
                    "user_id INT NOT NULL, " +
                    "local_id INT NOT NULL, " +
                    "degree_local_id INT, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "color_hex VARCHAR(50), " +
                    "synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, " +
                    "CONSTRAINT uq_edutrace_subject UNIQUE (user_id, local_id)" +
                    ");");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS edutrace_user_sessions (" +
                    "id SERIAL PRIMARY KEY, " +
                    "user_id INT NOT NULL, " +
                    "local_id INT NOT NULL, " +
                    "subject_local_id INT, " +
                    "start_timestamp BIGINT NOT NULL, " +
                    "end_timestamp BIGINT NOT NULL, " +
                    "break_duration_millis BIGINT DEFAULT 0, " +
                    "distraction_count INT DEFAULT 0, " +
                    "notes TEXT, " +
                    "synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, " +
                    "CONSTRAINT uq_edutrace_session UNIQUE (user_id, local_id)" +
                    ");");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isLoggedIn(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) && prefs.getInt(KEY_USER_ID, -1) != -1;
    }

    public static CloudUser getCurrentUser(Context context) {
        if (context == null || !isLoggedIn(context)) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        int id = prefs.getInt(KEY_USER_ID, -1);
        String username = prefs.getString(KEY_USERNAME, "");
        String email = prefs.getString(KEY_EMAIL, "");
        String displayName = prefs.getString(KEY_DISPLAY_NAME, username);
        return new CloudUser(id, username, email, displayName);
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
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_EMAIL)
                .remove(KEY_DISPLAY_NAME)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply();
    }

    private static void saveUserSession(Context context, CloudUser user) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_USER_ID, user.getId())
                .putString(KEY_USERNAME, user.getUsername())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_DISPLAY_NAME, user.getDisplayName())
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    private static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((password + ":" + salt).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    private static String generateSalt() {
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static void register(Context context, String username, String email, String password, String displayName, AuthCallback callback) {
        cloudExecutor.execute(() -> {
            try (Connection conn = getDirectConnection()) {
                String cleanUsername = username.trim().toLowerCase(Locale.ROOT);
                String cleanEmail = (email != null) ? email.trim().toLowerCase(Locale.ROOT) : "";

                // Check username uniqueness
                try (PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM edutrace_users WHERE LOWER(username) = ?")) {
                    checkStmt.setString(1, cleanUsername);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            postAuthError(callback, "Username is already registered.");
                            return;
                        }
                    }
                }

                // Check email uniqueness if email provided
                if (!cleanEmail.isEmpty()) {
                    try (PreparedStatement checkEmailStmt = conn.prepareStatement("SELECT id FROM edutrace_users WHERE LOWER(email) = ?")) {
                        checkEmailStmt.setString(1, cleanEmail);
                        try (ResultSet rs = checkEmailStmt.executeQuery()) {
                            if (rs.next()) {
                                postAuthError(callback, "Email address is already in use.");
                                return;
                            }
                        }
                    }
                }

                String salt = generateSalt();
                String hash = hashPassword(password, salt);
                String finalDisplayName = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim();

                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO edutrace_users (username, email, password_hash, salt, display_name) VALUES (?, ?, ?, ?, ?) RETURNING id")) {
                    insertStmt.setString(1, cleanUsername);
                    insertStmt.setString(2, cleanEmail);
                    insertStmt.setString(3, hash);
                    insertStmt.setString(4, salt);
                    insertStmt.setString(5, finalDisplayName);

                    try (ResultSet rs = insertStmt.executeQuery()) {
                        if (rs.next()) {
                            int newId = rs.getInt(1);
                            CloudUser user = new CloudUser(newId, cleanUsername, cleanEmail, finalDisplayName);
                            saveUserSession(context, user);
                            postAuthSuccess(callback, user);
                        } else {
                            postAuthError(callback, "Failed to create user account.");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                postAuthError(callback, "Database error: " + e.getMessage());
            }
        });
    }

    public static void login(Context context, String usernameOrEmail, String password, AuthCallback callback) {
        cloudExecutor.execute(() -> {
            try (Connection conn = getDirectConnection()) {
                String cleanInput = usernameOrEmail.trim().toLowerCase(Locale.ROOT);

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id, username, email, password_hash, salt, display_name FROM edutrace_users WHERE LOWER(username) = ? OR LOWER(email) = ?")) {
                    stmt.setString(1, cleanInput);
                    stmt.setString(2, cleanInput);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int id = rs.getInt("id");
                            String username = rs.getString("username");
                            String email = rs.getString("email");
                            String storedHash = rs.getString("password_hash");
                            String salt = rs.getString("salt");
                            String displayName = rs.getString("display_name");

                            String computedHash = hashPassword(password, salt);
                            if (storedHash.equals(computedHash)) {
                                CloudUser user = new CloudUser(id, username, email, displayName);
                                saveUserSession(context, user);
                                postAuthSuccess(callback, user);
                            } else {
                                postAuthError(callback, "Incorrect password. Please try again.");
                            }
                        } else {
                            postAuthError(callback, "Account not found for username/email.");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                postAuthError(callback, "Connection failed: " + e.getMessage());
            }
        });
    }

    public interface AvailabilityCallback {
        void onAvailable();
        void onError(String message);
    }

    public interface EmailLookupCallback {
        void onFound(String username, String email);
        void onError(String message);
    }

    public static void checkUserAvailability(String username, String email, AvailabilityCallback callback) {
        cloudExecutor.execute(() -> {
            try (Connection conn = getDirectConnection()) {
                String cleanUsername = username.trim().toLowerCase(Locale.ROOT);
                String cleanEmail = (email != null) ? email.trim().toLowerCase(Locale.ROOT) : "";

                try (PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM edutrace_users WHERE LOWER(username) = ?")) {
                    checkStmt.setString(1, cleanUsername);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            if (callback != null) mainHandler.post(() -> callback.onError("Username is already registered."));
                            return;
                        }
                    }
                }

                if (!cleanEmail.isEmpty()) {
                    try (PreparedStatement checkEmailStmt = conn.prepareStatement("SELECT id FROM edutrace_users WHERE LOWER(email) = ?")) {
                        checkEmailStmt.setString(1, cleanEmail);
                        try (ResultSet rs = checkEmailStmt.executeQuery()) {
                            if (rs.next()) {
                                if (callback != null) mainHandler.post(() -> callback.onError("Email is already in use."));
                                return;
                            }
                        }
                    }
                }

                if (callback != null) mainHandler.post(callback::onAvailable);
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) mainHandler.post(() -> callback.onError("Connection error: " + e.getMessage()));
            }
        });
    }

    public static void findUserEmail(String usernameOrEmail, EmailLookupCallback callback) {
        cloudExecutor.execute(() -> {
            try (Connection conn = getDirectConnection()) {
                String clean = usernameOrEmail.trim().toLowerCase(Locale.ROOT);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT username, email FROM edutrace_users WHERE LOWER(username) = ? OR LOWER(email) = ?")) {
                    stmt.setString(1, clean);
                    stmt.setString(2, clean);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String user = rs.getString("username");
                            String email = rs.getString("email");
                            if (email == null || email.trim().isEmpty()) {
                                if (callback != null) mainHandler.post(() -> callback.onError("No email address associated with this account."));
                            } else {
                                if (callback != null) mainHandler.post(() -> callback.onFound(user, email));
                            }
                        } else {
                            if (callback != null) mainHandler.post(() -> callback.onError("User account not found."));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) mainHandler.post(() -> callback.onError("Database error: " + e.getMessage()));
            }
        });
    }

    public static void resetPassword(String usernameOrEmail, String newPassword, AuthCallback callback) {
        cloudExecutor.execute(() -> {
            try (Connection conn = getDirectConnection()) {
                String clean = usernameOrEmail.trim().toLowerCase(Locale.ROOT);
                String salt = generateSalt();
                String hash = hashPassword(newPassword, salt);

                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE edutrace_users SET password_hash = ?, salt = ? WHERE LOWER(username) = ? OR LOWER(email) = ? RETURNING id, username, email, display_name")) {
                    stmt.setString(1, hash);
                    stmt.setString(2, salt);
                    stmt.setString(3, clean);
                    stmt.setString(4, clean);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int id = rs.getInt("id");
                            String username = rs.getString("username");
                            String email = rs.getString("email");
                            String displayName = rs.getString("display_name");
                            CloudUser user = new CloudUser(id, username, email, displayName);
                            postAuthSuccess(callback, user);
                        } else {
                            postAuthError(callback, "Account not found.");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                postAuthError(callback, "Database error: " + e.getMessage());
            }
        });
    }

    public static void backupData(Context context, SyncProgressCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Please sign in to backup data to the cloud."));
            return;
        }

        final int userId = getCurrentUser(context).getId();

        cloudExecutor.execute(() -> {
            try {
                if (callback != null) mainHandler.post(() -> callback.onProgress("Connecting to PostgreSQL cloud database..."));
                StudyDatabase localDb = StudyDatabase.getDatabase(context);
                StudyDao dao = localDb.studyDao();

                List<Degree> degrees = dao.getDegreesListSync();
                List<Subject> subjects = dao.getSubjectsListSync();
                List<StudySession> sessions = dao.getSessionsListSync();

                try (Connection conn = getDirectConnection()) {
                    conn.setAutoCommit(false);

                    if (callback != null) mainHandler.post(() -> callback.onProgress("Uploading " + degrees.size() + " degrees & " + subjects.size() + " subjects..."));
                    // 1. Sync Degrees
                    String degreeSql = "INSERT INTO edutrace_user_degrees (user_id, local_id, name, color_hex, synced_at) " +
                            "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (user_id, local_id) " +
                            "DO UPDATE SET name = EXCLUDED.name, color_hex = EXCLUDED.color_hex, synced_at = CURRENT_TIMESTAMP;";
                    try (PreparedStatement stmt = conn.prepareStatement(degreeSql)) {
                        for (Degree d : degrees) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, d.id);
                            stmt.setString(3, d.name);
                            stmt.setString(4, d.colorHex);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    // 2. Sync Subjects
                    String subjectSql = "INSERT INTO edutrace_user_subjects (user_id, local_id, degree_local_id, name, color_hex, synced_at) " +
                            "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (user_id, local_id) " +
                            "DO UPDATE SET degree_local_id = EXCLUDED.degree_local_id, name = EXCLUDED.name, color_hex = EXCLUDED.color_hex, synced_at = CURRENT_TIMESTAMP;";
                    try (PreparedStatement stmt = conn.prepareStatement(subjectSql)) {
                        for (Subject s : subjects) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, s.id);
                            stmt.setInt(3, s.degreeId);
                            stmt.setString(4, s.name);
                            stmt.setString(5, s.colorHex);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    if (callback != null) mainHandler.post(() -> callback.onProgress("Uploading " + sessions.size() + " study sessions..."));
                    // 3. Sync Sessions
                    String sessionSql = "INSERT INTO edutrace_user_sessions (user_id, local_id, subject_local_id, start_timestamp, end_timestamp, break_duration_millis, distraction_count, notes, synced_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (user_id, local_id) " +
                            "DO UPDATE SET subject_local_id = EXCLUDED.subject_local_id, start_timestamp = EXCLUDED.start_timestamp, end_timestamp = EXCLUDED.end_timestamp, break_duration_millis = EXCLUDED.break_duration_millis, distraction_count = EXCLUDED.distraction_count, notes = EXCLUDED.notes, synced_at = CURRENT_TIMESTAMP;";
                    try (PreparedStatement stmt = conn.prepareStatement(sessionSql)) {
                        for (StudySession s : sessions) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, s.id);
                            stmt.setInt(3, s.subjectId);
                            stmt.setLong(4, s.startTimestamp);
                            stmt.setLong(5, s.endTimestamp);
                            stmt.setLong(6, s.breakDurationMillis);
                            stmt.setInt(7, s.distractionCount);
                            stmt.setString(8, s.notes != null ? s.notes : "");
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    conn.commit();
                    conn.setAutoCommit(true);

                    long now = System.currentTimeMillis();
                    context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_BACKUP, now)
                            .apply();

                    // Also auto-update local safe vault
                    org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(context);

                    String msg = "Successfully backed up " + sessions.size() + " sessions & " + subjects.size() + " subjects to cloud! ☁️";
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(msg));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) mainHandler.post(() -> callback.onError("Backup failed: " + e.getMessage()));
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

    public static void restoreData(Context context, SyncProgressCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) mainHandler.post(() -> callback.onError("Please sign in to restore data from the cloud."));
            return;
        }

        final int userId = getCurrentUser(context).getId();

        cloudExecutor.execute(() -> {
            try {
                if (callback != null) mainHandler.post(() -> callback.onProgress("Connecting to PostgreSQL cloud database..."));
                try (Connection conn = getDirectConnection()) {
                    StudyDatabase localDb = StudyDatabase.getDatabase(context);
                    StudyDao dao = localDb.studyDao();

                    int restoredDegrees = 0;
                    int restoredSubjects = 0;
                    int restoredSessions = 0;

                    if (callback != null) mainHandler.post(() -> callback.onProgress("Restoring academic degrees & subjects..."));
                    // 1. Restore Degrees
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT local_id, name, color_hex FROM edutrace_user_degrees WHERE user_id = ? ORDER BY local_id ASC")) {
                        stmt.setInt(1, userId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                Degree d = new Degree(rs.getString("name"), rs.getString("color_hex"));
                                d.id = rs.getInt("local_id");
                                dao.insertOrReplaceDegree(d);
                                restoredDegrees++;
                            }
                        }
                    }

                    // 2. Restore Subjects
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT local_id, degree_local_id, name, color_hex FROM edutrace_user_subjects WHERE user_id = ? ORDER BY local_id ASC")) {
                        stmt.setInt(1, userId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                Subject s = new Subject(rs.getInt("degree_local_id"), rs.getString("name"), rs.getString("color_hex"));
                                s.id = rs.getInt("local_id");
                                dao.insertOrReplaceSubject(s);
                                restoredSubjects++;
                            }
                        }
                    }

                    if (callback != null) mainHandler.post(() -> callback.onProgress("Restoring study session history..."));
                    // 3. Restore Sessions
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT local_id, subject_local_id, start_timestamp, end_timestamp, break_duration_millis, distraction_count, notes FROM edutrace_user_sessions WHERE user_id = ? ORDER BY local_id ASC")) {
                        stmt.setInt(1, userId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                StudySession s = new StudySession(
                                        rs.getInt("subject_local_id"),
                                        rs.getLong("start_timestamp"),
                                        rs.getLong("end_timestamp"),
                                        rs.getLong("break_duration_millis"),
                                        rs.getInt("distraction_count"),
                                        rs.getString("notes")
                                );
                                s.id = rs.getInt("local_id");
                                dao.insertOrReplaceSession(s);
                                restoredSessions++;
                            }
                        }
                    }

                    // Also auto-update local safe vault
                    org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(context);

                    String msg = "Restored " + restoredSessions + " sessions, " + restoredSubjects + " subjects, and " + restoredDegrees + " degrees from cloud! 📥";
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(msg));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) mainHandler.post(() -> callback.onError("Restore failed: " + e.getMessage()));
            }
        });
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

    private static void postAuthSuccess(AuthCallback callback, CloudUser user) {
        if (callback != null) mainHandler.post(() -> callback.onSuccess(user));
    }

    private static void postAuthError(AuthCallback callback, String msg) {
        if (callback != null) mainHandler.post(() -> callback.onError(msg));
    }

    private static void postSyncSuccess(SyncCallback callback, String msg) {
        if (callback != null) mainHandler.post(() -> callback.onSuccess(msg));
    }

    private static void postSyncError(SyncCallback callback, String msg) {
        if (callback != null) mainHandler.post(() -> callback.onError(msg));
    }
}
