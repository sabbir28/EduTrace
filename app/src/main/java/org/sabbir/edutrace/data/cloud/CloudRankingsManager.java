package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.models.CloudNotification;
import org.sabbir.edutrace.data.models.MyRankingSummary;
import org.sabbir.edutrace.data.models.RankingUser;
import org.sabbir.edutrace.data.models.TimelineDayData;
import org.sabbir.edutrace.data.models.TimerMatchStatus;
import org.sabbir.edutrace.utils.NotificationCacheManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages notification center alerts, leaderboards, rankings prefetching and caching,
 * timer match status, 24h timeline analysis, and device telemetry.
 */
public class CloudRankingsManager {
    private static final String TAG = CloudApiClient.TAG;
    public static final String ACTION_NOTIFICATIONS_UPDATED = "org.sabbir.edutrace.ACTION_NOTIFICATIONS_UPDATED";
    public static final String PREF_RANKING_CACHE = "EduTrace_Ranking_Cache";

    public static void saveCachedRankingJson(Context context, String timeframe, String json) {
        if (context == null || timeframe == null || json == null) return;
        try {
            context.getSharedPreferences(PREF_RANKING_CACHE, Context.MODE_PRIVATE)
                    .edit()
                    .putString("ranking_" + timeframe.toLowerCase(Locale.ROOT), json)
                    .putLong("ranking_" + timeframe.toLowerCase(Locale.ROOT) + "_time", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {}
    }

    public static String getCachedRankingJson(Context context, String timeframe) {
        if (context == null || timeframe == null) return null;
        try {
            return context.getSharedPreferences(PREF_RANKING_CACHE, Context.MODE_PRIVATE)
                    .getString("ranking_" + timeframe.toLowerCase(Locale.ROOT), null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void doPrefetchNotifications(Context context) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        if (token.isEmpty()) return;
        try {
            CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/notifications?unread_only=false&limit=50", "GET", null, token);
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
                NotificationCacheManager.saveNotifications(context, list, unreadCount);
                try {
                    Intent intent = new Intent(ACTION_NOTIFICATIONS_UPDATED);
                    intent.putExtra("unread_count", unreadCount);
                    context.sendBroadcast(intent);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.w(TAG, "Notifications prefetch notice: " + t.getMessage());
        }
    }

    public static void doPrefetchRankings(Context context) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        try {
            CloudApiClient.ApiResponse resAll = CloudApiClient.sendApiRequest(context, "/rankings?timeframe=all_time", "GET", null, token);
            if (resAll.isSuccess() && resAll.json != null && resAll.rawBody != null) {
                saveCachedRankingJson(context, "all_time", resAll.rawBody);
            }
            CloudApiClient.ApiResponse resMe = CloudApiClient.sendApiRequest(context, "/rankings/me", "GET", null, token);
            if (resMe.isSuccess() && resMe.json != null && resMe.rawBody != null) {
                saveCachedRankingJson(context, "me", resMe.rawBody);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Rankings prefetch notice: " + t.getMessage());
        }
    }

    public static void fetchNotifications(Context context, boolean unreadOnly, CloudDbManager.NotificationsCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Sign in required to view notifications."));
            return;
        }
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                String endpoint = "/notifications?unread_only=" + (unreadOnly ? "true" : "false") + "&limit=50";
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, endpoint, "GET", null, token);
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
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(list, unreadCount));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load notifications.";
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Error loading notifications: " + e.getMessage()));
            }
        });
    }

    public static void fetchUnreadNotificationCount(Context context, CloudDbManager.CountCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(0));
            return;
        }
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/notifications/unread-count", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    int count = res.json.optInt("count", res.json.optInt("unread_count", 0));
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(count));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(0));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(0));
            }
        });
    }

    public static void markNotificationRead(Context context, int notificationId, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/notifications/" + notificationId + "/read", "PUT", null, token);
                if (res.isSuccess()) {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Marked as read"));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void markAllNotificationsRead(Context context, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/notifications/read-all", "PUT", null, token);
                if (res.isSuccess()) {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("All notifications marked as read"));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void deleteNotification(Context context, int notificationId, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/notifications/" + notificationId, "DELETE", null, token);
                if (res.isSuccess()) {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Notification removed"));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void checkTimerMatch(Context context, CloudDbManager.TimerMatchCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/notifications/timer-match", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    TimerMatchStatus status = TimerMatchStatus.fromJson(res.json);
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(status));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void fetchLeaderboard(Context context, String timeframe, CloudDbManager.LeaderboardCallback callback) {
        final String token = CloudAuthManager.isLoggedIn(context) ? CloudAuthManager.getAccessToken(context) : null;
        final String tf = (timeframe != null && !timeframe.isEmpty()) ? timeframe.toLowerCase(Locale.ROOT) : "all_time";

        // 1. Instant Cached Return (0ms loading if cached)
        String cachedRaw = getCachedRankingJson(context, tf);
        if (cachedRaw != null && !cachedRaw.isEmpty()) {
            try {
                JSONObject cachedObj = new JSONObject(cachedRaw);
                List<RankingUser> cachedUsers = parseRankingsUsers(cachedObj);
                RankingUser cachedCurrentRank = parseCurrentRank(cachedObj, cachedUsers);
                int cachedTotal = cachedObj.optInt("total_participants", cachedUsers.size());
                if (!cachedUsers.isEmpty()) {
                    CloudApiClient.mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(cachedUsers, cachedCurrentRank, cachedTotal);
                    });
                }
            } catch (Throwable ignored) {}
        }

        // 2. Network Fetch & Revalidate
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/rankings?timeframe=" + tf, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    if (res.rawBody != null) {
                        saveCachedRankingJson(context, tf, res.rawBody);
                    }
                    List<RankingUser> users = parseRankingsUsers(res.json);
                    RankingUser currentRank = parseCurrentRank(res.json, users);
                    int total = res.json.optInt("total_participants", users.size());
                    final RankingUser fCurrentRank = currentRank;
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(users, fCurrentRank, total));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load leaderboard rankings.";
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Leaderboard error: " + e.getMessage()));
            }
        });
    }

    public static List<RankingUser> parseRankingsUsers(JSONObject json) {
        List<RankingUser> users = new ArrayList<>();
        if (json == null) return users;
        JSONArray arr = json.optJSONArray("rankings");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item != null) {
                    RankingUser u = RankingUser.fromJson(item);
                    if (u != null) users.add(u);
                }
            }
        }
        return users;
    }

    public static RankingUser parseCurrentRank(JSONObject json, List<RankingUser> users) {
        if (json == null) return null;
        RankingUser currentRank = null;
        JSONObject curObj = json.optJSONObject("current_user");
        if (curObj == null) curObj = json.optJSONObject("current_user_rank");
        if (curObj == null) curObj = json.optJSONObject("current_user_stats");
        if (curObj != null) {
            currentRank = RankingUser.fromJson(curObj);
        }
        if (currentRank == null && users != null) {
            for (RankingUser u : users) {
                if (u.isCurrentUser) {
                    currentRank = u;
                    break;
                }
            }
        }
        return currentRank;
    }

    public static void fetchMyRanking(Context context, CloudDbManager.MyRankingCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Sign in to view your personal ranking."));
            return;
        }

        // Check cache first
        String cachedRaw = getCachedRankingJson(context, "me");
        if (cachedRaw != null && !cachedRaw.isEmpty()) {
            try {
                JSONObject cachedObj = new JSONObject(cachedRaw);
                MyRankingSummary summary = MyRankingSummary.fromJson(cachedObj);
                if (summary != null) {
                    CloudApiClient.mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(summary);
                    });
                }
            } catch (Throwable ignored) {}
        }

        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/rankings/me", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    if (res.rawBody != null) {
                        saveCachedRankingJson(context, "me", res.rawBody);
                    }
                    MyRankingSummary summary = MyRankingSummary.fromJson(res.json);
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(summary));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load user ranking.";
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Ranking error: " + e.getMessage()));
            }
        });
    }

    public static void fetch24hTimeline(Context context, String dateYmd, CloudDbManager.TimelineCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Sign in to analyze 24h daily routine."));
            return;
        }
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                String endpoint = "/analytics/timeline/24h" + (dateYmd != null && !dateYmd.isEmpty() ? "?date=" + dateYmd : "");
                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, endpoint, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    TimelineDayData data = TimelineDayData.fromJson(res.json);
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(data));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load 24h timeline data.";
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Timeline error: " + e.getMessage()));
            }
        });
    }

    public static void recordDeviceActivity(Context context, String eventType, CloudDbManager.ActionCallback callback) {
        if (!CloudAuthManager.isLoggedIn(context) || eventType == null || eventType.isEmpty()) return;
        final String token = CloudAuthManager.getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event_type", eventType);
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                payload.put("timestamp", iso.format(new Date()));

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/analytics/timeline/activity", "POST", payload, token);
                if (res.isSuccess()) {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess("Activity recorded"));
                } else {
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void recordDeviceActivity(Context context, String eventType) {
        recordDeviceActivity(context, eventType, null);
    }
}
