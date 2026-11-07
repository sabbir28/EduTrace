package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

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

    private DesktopCloudRankingsManager() {}

    public void fetchLeaderboard(String timeframe, LeaderboardCallback callback) {
        String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        String tf = (timeframe != null && !timeframe.isEmpty()) ? timeframe.toLowerCase() : "all_time";

        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/rankings?timeframe=" + tf, "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    List<DesktopRankingUser> users = new ArrayList<>();
                    JSONArray arr = res.json.optJSONArray("rankings");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            DesktopRankingUser u = DesktopRankingUser.fromJson(item);
                            if (u != null) users.add(u);
                        }
                    }

                    DesktopRankingUser currentUserRank = null;
                    JSONObject curObj = res.json.optJSONObject("current_user_rank");
                    if (curObj != null) {
                        currentUserRank = DesktopRankingUser.fromJson(curObj);
                    }
                    if (currentUserRank == null) {
                        for (DesktopRankingUser u : users) {
                            if (u.isCurrentUser) {
                                currentUserRank = u;
                                break;
                            }
                        }
                    }

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
}
