package org.sabbir.edutrace.data.cloud;

import android.content.Context;

import org.json.JSONObject;
import org.sabbir.edutrace.data.models.CloudNotification;
import org.sabbir.edutrace.data.models.MyRankingSummary;
import org.sabbir.edutrace.data.models.RankingUser;
import org.sabbir.edutrace.data.models.TimelineDayData;
import org.sabbir.edutrace.data.models.TimerMatchStatus;

import java.util.List;

/**
 * Manages modern RESTful cloud persistence and synchronization with the EduTrace Server.
 * All operations communicate seamlessly via REST JSON endpoints (JWT Authentication, Sync, Backup, Restore).
 *
 * Facade providing clean, high-level cloud APIs while delegating domain logic to specialized managers:
 * - {@link CloudApiClient} for network communication and server endpoints
 * - {@link CloudAuthManager} for authentication and user accounts
 * - {@link CloudSessionManager} for study session tracking and real-time syncing
 * - {@link CloudSyncManager} for Room database backup and restore
 * - {@link CloudRankingsManager} for notifications, leaderboards, and timeline analysis
 */
public class CloudDbManager {
    public static final String TAG = CloudApiClient.TAG;
    public static final String KEY_AUTO_BACKUP = CloudSyncManager.KEY_AUTO_BACKUP;
    public static final String KEY_SERVER_URL = CloudApiClient.KEY_SERVER_URL;

    // Standard server endpoints (Vercel Production, Emulator, Host PC Wi-Fi, and Localhost)
    public static final String DEFAULT_PROD_URL = CloudApiClient.DEFAULT_PROD_URL;
    public static final String DEFAULT_EMULATOR_URL = CloudApiClient.DEFAULT_EMULATOR_URL;
    public static final String DEFAULT_WIFI_URL = CloudApiClient.DEFAULT_WIFI_URL;
    public static final String DEFAULT_LOCAL_URL = CloudApiClient.DEFAULT_LOCAL_URL;

    public static final String ACTION_NOTIFICATIONS_UPDATED = CloudRankingsManager.ACTION_NOTIFICATIONS_UPDATED;
    public static final String ACTION_SESSION_TIMED_OUT = CloudSessionManager.ACTION_SESSION_TIMED_OUT;

    // ==========================================
    // Public Callback Interfaces
    // ==========================================

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

    public interface ActiveSessionCallback {
        void onActiveSession(boolean isActive, JSONObject sessionObj, long elapsedSeconds, String serverTime);
        void onError(String message);
    }

    public interface SubjectStatsCallback {
        void onStats(long totalSeconds, double totalHours, int sessionCount);
        void onError(String message);
    }

    // ==========================================
    // Configuration & Server URL
    // ==========================================

    public static String getServerUrl(Context context) {
        return CloudApiClient.getServerUrl(context);
    }

    public static void setServerUrl(Context context, String url) {
        CloudApiClient.setServerUrl(context, url);
    }

    public static void checkServerHealth(Context context, HealthCallback callback) {
        CloudApiClient.checkServerHealth(context, callback);
    }

    // ==========================================
    // Authentication & Profile Operations
    // ==========================================

    public static boolean isLoggedIn(Context context) {
        return CloudAuthManager.isLoggedIn(context);
    }

    public static CloudUser getCurrentUser(Context context) {
        return CloudAuthManager.getCurrentUser(context);
    }

    public static String getAccessToken(Context context) {
        return CloudAuthManager.getAccessToken(context);
    }

    public static void logout(Context context) {
        CloudAuthManager.logout(context);
    }

    public static void checkUserAvailability(Context context, String username, String email, AvailabilityCallback callback) {
        CloudAuthManager.checkUserAvailability(context, username, email, callback);
    }

    public static void checkUserAvailability(String username, String email, AvailabilityCallback callback) {
        CloudAuthManager.checkUserAvailability(null, username, email, callback);
    }

    public static void register(Context context, String username, String email, String password, String displayName, AuthCallback callback) {
        CloudAuthManager.register(context, username, email, password, displayName, callback);
    }

    public static void login(Context context, String usernameOrEmail, String password, AuthCallback callback) {
        CloudAuthManager.login(context, usernameOrEmail, password, callback);
    }

    public static void findUserEmail(Context context, String usernameOrEmail, EmailLookupCallback callback) {
        CloudAuthManager.findUserEmail(context, usernameOrEmail, callback);
    }

    public static void findUserEmail(String usernameOrEmail, EmailLookupCallback callback) {
        CloudAuthManager.findUserEmail(null, usernameOrEmail, callback);
    }

    public static void resetPassword(Context context, String usernameOrEmail, String newPassword, AuthCallback callback) {
        CloudAuthManager.resetPassword(context, usernameOrEmail, newPassword, callback);
    }

    public static void resetPassword(String usernameOrEmail, String newPassword, AuthCallback callback) {
        CloudAuthManager.resetPassword(null, usernameOrEmail, newPassword, callback);
    }

    public static void changePassword(Context context, String currentPassword, String newPassword, ActionCallback callback) {
        CloudAuthManager.changePassword(context, currentPassword, newPassword, callback);
    }

    // ==========================================
    // Live Study Sessions
    // ==========================================

    public static void handleSessionConflictOrTimeout(Context context, String reason) {
        CloudSessionManager.handleSessionConflictOrTimeout(context, reason);
    }

    public static void startLiveServerSession(Context context, int localSubjectId, String subjectName) {
        CloudSessionManager.startLiveServerSession(context, localSubjectId, subjectName);
    }

    public static void pauseLiveServerSession(Context context, String notes, ActionCallback callback) {
        CloudSessionManager.pauseLiveServerSession(context, notes, callback);
    }

    public static void pauseLiveServerSession(Context context, String notes) {
        CloudSessionManager.pauseLiveServerSession(context, notes);
    }

    public static void resumeLiveServerSession(Context context, ActionCallback callback) {
        CloudSessionManager.resumeLiveServerSession(context, callback);
    }

    public static void resumeLiveServerSession(Context context) {
        CloudSessionManager.resumeLiveServerSession(context);
    }

    public static void sendLiveSessionHeartbeat(Context context, long elapsedMillis, boolean isPaused, ActionCallback callback) {
        CloudSessionManager.sendLiveSessionHeartbeat(context, elapsedMillis, isPaused, callback);
    }

    public static void sendLiveSessionHeartbeat(Context context, long elapsedMillis, boolean isPaused) {
        CloudSessionManager.sendLiveSessionHeartbeat(context, elapsedMillis, isPaused);
    }

    public static void stopLiveServerSession(Context context, long elapsedMillis, String notes) {
        CloudSessionManager.stopLiveServerSession(context, elapsedMillis, notes);
    }

    public static void stopLiveServerSession(Context context, String notes) {
        CloudSessionManager.stopLiveServerSession(context, notes);
    }

    public static void fetchActiveSession(Context context, ActiveSessionCallback callback) {
        CloudSessionManager.fetchActiveSession(context, callback);
    }

    public static void consolidateDailySessions(Context context, Integer subjectId, String dateIso, ActionCallback callback) {
        CloudSessionManager.consolidateDailySessions(context, subjectId, dateIso, callback);
    }

    public static void fetchSubjectStats(Context context, String subjectName, SubjectStatsCallback callback) {
        CloudSessionManager.fetchSubjectStats(context, subjectName, callback);
    }

    // ==========================================
    // Data Backup, Restore & Sync
    // ==========================================

    public static String getLastBackupTime(Context context) {
        return CloudSyncManager.getLastBackupTime(context);
    }

    public static void autoSyncOnStartup(Context context) {
        CloudSyncManager.autoSyncOnStartup(context);
    }

    public static void backupData(Context context, SyncProgressCallback callback) {
        CloudSyncManager.backupData(context, callback);
    }

    public static void backupData(Context context, SyncCallback callback) {
        CloudSyncManager.backupData(context, callback);
    }

    public static void backupData(Context context) {
        CloudSyncManager.backupData(context);
    }

    public static void restoreData(Context context, SyncProgressCallback callback) {
        CloudSyncManager.restoreData(context, callback);
    }

    public static void restoreData(Context context, SyncCallback callback) {
        CloudSyncManager.restoreData(context, callback);
    }

    public static void restoreData(Context context) {
        CloudSyncManager.restoreData(context);
    }

    // ==========================================
    // Notifications, Rankings & Analytics
    // ==========================================

    public static void saveCachedRankingJson(Context context, String timeframe, String json) {
        CloudRankingsManager.saveCachedRankingJson(context, timeframe, json);
    }

    public static String getCachedRankingJson(Context context, String timeframe) {
        return CloudRankingsManager.getCachedRankingJson(context, timeframe);
    }

    public static void fetchNotifications(Context context, boolean unreadOnly, NotificationsCallback callback) {
        CloudRankingsManager.fetchNotifications(context, unreadOnly, callback);
    }

    public static void fetchUnreadNotificationCount(Context context, CountCallback callback) {
        CloudRankingsManager.fetchUnreadNotificationCount(context, callback);
    }

    public static void markNotificationRead(Context context, int notificationId, ActionCallback callback) {
        CloudRankingsManager.markNotificationRead(context, notificationId, callback);
    }

    public static void markAllNotificationsRead(Context context, ActionCallback callback) {
        CloudRankingsManager.markAllNotificationsRead(context, callback);
    }

    public static void deleteNotification(Context context, int notificationId, ActionCallback callback) {
        CloudRankingsManager.deleteNotification(context, notificationId, callback);
    }

    public static void checkTimerMatch(Context context, TimerMatchCallback callback) {
        CloudRankingsManager.checkTimerMatch(context, callback);
    }

    public static void fetchLeaderboard(Context context, String timeframe, LeaderboardCallback callback) {
        CloudRankingsManager.fetchLeaderboard(context, timeframe, callback);
    }

    public static List<RankingUser> parseRankingsUsers(JSONObject json) {
        return CloudRankingsManager.parseRankingsUsers(json);
    }

    public static RankingUser parseCurrentRank(JSONObject json, List<RankingUser> users) {
        return CloudRankingsManager.parseCurrentRank(json, users);
    }

    public static void fetchMyRanking(Context context, MyRankingCallback callback) {
        CloudRankingsManager.fetchMyRanking(context, callback);
    }

    public static void fetch24hTimeline(Context context, String dateYmd, TimelineCallback callback) {
        CloudRankingsManager.fetch24hTimeline(context, dateYmd, callback);
    }

    public static void recordDeviceActivity(Context context, String eventType, ActionCallback callback) {
        CloudRankingsManager.recordDeviceActivity(context, eventType, callback);
    }

    public static void recordDeviceActivity(Context context, String eventType) {
        CloudRankingsManager.recordDeviceActivity(context, eventType);
    }
}
