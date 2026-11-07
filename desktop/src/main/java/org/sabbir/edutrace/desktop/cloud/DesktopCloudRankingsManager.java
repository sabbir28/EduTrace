package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages leaderboard rankings, user ranking summaries, and daily timeline telemetry.
 * Direct port of Android CloudRankingsManager.java.
 */
public class DesktopCloudRankingsManager {

    private static DesktopCloudRankingsManager instance;

    public static synchronized DesktopCloudRankingsManager getInstance() {
        if (instance == null) {
            instance = new DesktopCloudRankingsManager();
        }
        return instance;
    }

    public interface LeaderboardCallback {
        void onSuccess(List<DesktopRankingUser> users, DesktopRankingUser currentUserRank, int totalParticipants);
        void onError(String message);
    }

    public interface MyRankingCallback {
        void onSuccess(DesktopMyRankingSummary summary);
        void onError(String message);
    }

    public interface TimelineCallback {
        void onSuccess(JSONObject timelineJson);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private final Map<String, String> rankingCache = new HashMap<>();

    private DesktopCloudRankingsManager() {}

    public void fetchLeaderboard(String timeframe, LeaderboardCallback callback) {
        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        String tf = (timeframe != null && !timeframe.isEmpty()) ? timeframe.toLowerCase() : "all_time";

        // Instant cached return
        if (rankingCache.containsKey(tf)) {
            try {
                JSONObject cachedObj = new JSONObject(rankingCache.get(tf));
                List<DesktopRankingUser> cachedUsers = parseRankingsUsers(cachedObj);
                DesktopRankingUser cachedCurrentRank = parseCurrentRank(cachedObj, cachedUsers);
                int cachedTotal = cachedObj.optInt("total_participants", cachedUsers.size());
                if (!cachedUsers.isEmpty()) {
                    SwingUtilities.invokeLater(() -> callback.onSuccess(cachedUsers, cachedCurrentRank, cachedTotal));
                }
            } catch (Exception ignored) {}
        }

        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/rankings?timeframe=" + tf, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    if (res.rawBody != null) {
                        rankingCache.put(tf, res.rawBody);
                    }
                    List<DesktopRankingUser> users = parseRankingsUsers(res.json);
                    DesktopRankingUser currentUserRank = parseCurrentRank(res.json, users);
                    int total = res.json.optInt("total_participants", users.size());

                    final DesktopRankingUser fCurrent = currentUserRank;
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(users, fCurrent, total));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load leaderboard rankings.";
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onError("Leaderboard error: " + e.getMessage()));
                }
            }
        });
    }

    public void fetchMyRanking(MyRankingCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sign in to view your personal ranking."));
            return;
        }

        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/rankings/me", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    DesktopMyRankingSummary summary = DesktopMyRankingSummary.fromJson(res.json);
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(summary));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load personal ranking.";
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onError("Ranking error: " + e.getMessage()));
                }
            }
        });
    }

    public void fetch24hTimeline(String dateYmd, TimelineCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Sign in to view timeline analysis."));
            return;
        }

        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                String endpoint = "/analytics/timeline/24h" + (dateYmd != null && !dateYmd.isEmpty() ? "?date=" + dateYmd : "");
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest(endpoint, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(res.json));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to load timeline data.";
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onError("Timeline error: " + e.getMessage()));
                }
            }
        });
    }

    public void recordActivity(String eventType, ActionCallback callback) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn() || eventType == null || eventType.isEmpty()) return;
        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event_type", eventType);
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                iso.setTimeZone(TimeZone.getTimeZone("UTC"));
                payload.put("timestamp", iso.format(new Date()));

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/analytics/timeline/activity", "POST", payload, token);
                if (res.isSuccess()) {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess("Activity recorded"));
                } else {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(res.errorMessage));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private List<DesktopRankingUser> parseRankingsUsers(JSONObject json) {
        List<DesktopRankingUser> users = new ArrayList<>();
        if (json == null) return users;
        JSONArray arr = json.optJSONArray("rankings");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                DesktopRankingUser u = DesktopRankingUser.fromJson(item);
                if (u != null) users.add(u);
            }
        }
        return users;
    }

    private DesktopRankingUser parseCurrentRank(JSONObject json, List<DesktopRankingUser> users) {
        if (json == null) return null;
        JSONObject curObj = json.optJSONObject("current_user_rank");
        if (curObj != null) {
            return DesktopRankingUser.fromJson(curObj);
        }
        for (DesktopRankingUser u : users) {
            if (u.isCurrentUser) return u;
        }
        return null;
    }
}
