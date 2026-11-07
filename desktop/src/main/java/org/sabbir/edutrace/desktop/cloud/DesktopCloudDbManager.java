package org.sabbir.edutrace.desktop.cloud;

import java.util.List;

/**
 * Unified Cloud Facade for EduTrace Desktop:
 * Direct port of Android CloudDbManager.java.
 * Connects desktop study tracking, user authentication, curriculum synchronization,
 * live study sessions, global rankings, and in-app notifications with the EduTrace Server.
 */
public class DesktopCloudDbManager {

    // ==========================================
    // Authentication
    // ==========================================

    public static boolean isLoggedIn() {
        return DesktopCloudAuthManager.getInstance().isLoggedIn();
    }

    public static DesktopCloudUser getCurrentUser() {
        return DesktopCloudAuthManager.getInstance().getCurrentUser();
    }

    public static String getAccessToken() {
        return DesktopCloudAuthManager.getInstance().getAccessToken();
    }

    public static void login(String usernameOrEmail, String password, DesktopCloudAuthManager.AuthCallback callback) {
        DesktopCloudAuthManager.getInstance().login(usernameOrEmail, password, callback);
    }

    public static void register(String username, String email, String password, String displayName, DesktopCloudAuthManager.AuthCallback callback) {
        DesktopCloudAuthManager.getInstance().register(username, email, password, displayName, callback);
    }

    public static void logout(DesktopCloudAuthManager.ActionCallback callback) {
        DesktopCloudAuthManager.getInstance().logout(callback);
    }

    public static void checkUserAvailability(String username, String email, DesktopCloudAuthManager.AvailabilityCallback callback) {
        DesktopCloudAuthManager.getInstance().checkUserAvailability(username, email, callback);
    }

    public static void findUserEmail(String usernameOrEmail, DesktopCloudAuthManager.EmailLookupCallback callback) {
        DesktopCloudAuthManager.getInstance().findUserEmail(usernameOrEmail, callback);
    }

    public static void resetPassword(String usernameOrEmail, String newPassword, DesktopCloudAuthManager.ActionCallback callback) {
        DesktopCloudAuthManager.getInstance().resetPassword(usernameOrEmail, newPassword, callback);
    }

    public static void changePassword(String currentPassword, String newPassword, DesktopCloudAuthManager.ActionCallback callback) {
        DesktopCloudAuthManager.getInstance().changePassword(currentPassword, newPassword, callback);
    }

    public static void syncUserProfile() {
        DesktopCloudAuthManager.getInstance().syncUserProfile(null);
    }

    // ==========================================
    // Curriculum Sync & Backup / Restore
    // ==========================================

    public static void syncCurriculum(DesktopCloudSyncManager.SyncCallback callback) {
        DesktopCloudSyncManager.getInstance().syncCurriculum(callback);
    }

    public static void backupData(DesktopCloudSyncManager.SyncCallback callback) {
        DesktopCloudSyncManager.getInstance().backupData(callback);
    }

    public static void restoreData(DesktopCloudSyncManager.SyncCallback callback) {
        DesktopCloudSyncManager.getInstance().restoreData(callback);
    }

    // ==========================================
    // Live Study Sessions
    // ==========================================

    public static void startLiveServerSession(String subjectName) {
        DesktopCloudSessionManager.getInstance().startLiveServerSession(subjectName);
    }

    public static void pauseLiveServerSession(String notes) {
        DesktopCloudSessionManager.getInstance().pauseLiveServerSession(notes);
    }

    public static void resumeLiveServerSession() {
        DesktopCloudSessionManager.getInstance().resumeLiveServerSession();
    }

    public static void stopLiveServerSession(long durationSeconds, String notes) {
        DesktopCloudSessionManager.getInstance().stopLiveServerSession(durationSeconds, notes);
    }

    public static int getActiveServerSessionId() {
        return DesktopCloudSessionManager.getInstance().getActiveServerSessionId();
    }

    // ==========================================
    // Rankings & Leaderboard
    // ==========================================

    public static void fetchLeaderboard(String timeframe, DesktopCloudRankingsManager.LeaderboardCallback callback) {
        DesktopCloudRankingsManager.getInstance().fetchLeaderboard(timeframe, callback);
    }

    public static void fetchMyRanking(DesktopCloudRankingsManager.MyRankingCallback callback) {
        DesktopCloudRankingsManager.getInstance().fetchMyRanking(callback);
    }

    public static void fetch24hTimeline(String dateYmd, DesktopCloudRankingsManager.TimelineCallback callback) {
        DesktopCloudRankingsManager.getInstance().fetch24hTimeline(dateYmd, callback);
    }

    public static void recordActivity(String eventType) {
        DesktopCloudRankingsManager.getInstance().recordActivity(eventType, null);
    }

    // ==========================================
    // Notifications & Match
    // ==========================================

    public static void fetchNotifications(boolean unreadOnly, DesktopCloudNotificationManager.NotificationsCallback callback) {
        DesktopCloudNotificationManager.getInstance().fetchNotifications(unreadOnly, callback);
    }

    public static void getUnreadNotificationCount(DesktopCloudNotificationManager.UnreadCountCallback callback) {
        DesktopCloudNotificationManager.getInstance().getUnreadNotificationCount(callback);
    }

    public static void markNotificationAsRead(int id) {
        DesktopCloudNotificationManager.getInstance().markNotificationAsRead(id, null);
    }

    public static void markAllNotificationsAsRead(DesktopCloudNotificationManager.ActionCallback callback) {
        DesktopCloudNotificationManager.getInstance().markAllNotificationsAsRead(callback);
    }

    public static void checkTimerMatch(DesktopCloudNotificationManager.TimerMatchCallback callback) {
        DesktopCloudNotificationManager.getInstance().checkTimerMatch(callback);
    }
}
