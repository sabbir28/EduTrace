package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DesktopCloudSessionManager {

    private static DesktopCloudSessionManager instance;

    public static synchronized DesktopCloudSessionManager getInstance() {
        if (instance == null) {
            instance = new DesktopCloudSessionManager();
        }
        return instance;
    }

    private int activeServerSessionId = -1;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatTask;
    private long sessionStartElapsed = 0;

    private DesktopCloudSessionManager() {}

    public synchronized int getActiveServerSessionId() {
        return activeServerSessionId;
    }

    public synchronized void startLiveServerSession(String subjectName) {
        if (!DesktopCloudAuthManager.getInstance().isLoggedIn()) return;
        final String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        if (token.isEmpty()) return;

        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                // 1. Find or auto-provision subject on server
                DesktopCloudApiClient.ApiResponse subRes = DesktopCloudApiClient.sendApiRequest("/subjects", "GET", null, token);
                int serverSubId = -1;
                if (subRes.isSuccess() && subRes.json != null) {
                    JSONArray items = subRes.json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject s = items.getJSONObject(i);
                            if (subjectName != null && subjectName.equalsIgnoreCase(s.optString("name", ""))) {
                                serverSubId = s.optInt("id", -1);
                                break;
                            }
                        }
                    }
                }

                if (serverSubId == -1 && subjectName != null && !subjectName.isEmpty()) {
                    JSONObject createSub = new JSONObject();
                    createSub.put("name", subjectName);
                    DesktopCloudApiClient.ApiResponse createRes = DesktopCloudApiClient.sendApiRequest("/subjects", "POST", createSub, token);
                    if (createRes.isSuccess() && createRes.json != null) {
                        JSONObject created = createRes.json.optJSONObject("subject");
                        if (created != null) {
                            serverSubId = created.optInt("id", -1);
                        }
                    }
                }

                if (serverSubId != -1) {
                    JSONObject payload = new JSONObject();
                    payload.put("subject_id", serverSubId);
                    payload.put("title", "Desktop Study: " + subjectName);

                    DesktopCloudApiClient.ApiResponse startRes = DesktopCloudApiClient.sendApiRequest("/sessions/start", "POST", payload, token);
                    if (startRes.isSuccess() && startRes.json != null) {
                        JSONObject sess = startRes.json.optJSONObject("session");
                        if (sess != null) {
                            synchronized (DesktopCloudSessionManager.this) {
                                activeServerSessionId = sess.optInt("id", -1);
                                startHeartbeatLoop();
                            }
                        }
                    } else if (startRes.statusCode == 409) {
                        // 409 Conflict auto-recovery: close stale session and retry
                        DesktopCloudApiClient.ApiResponse activeRes = DesktopCloudApiClient.sendApiRequest("/sessions/active", "GET", null, token);
                        if (activeRes.isSuccess() && activeRes.json != null) {
                            JSONObject activeSess = activeRes.json.optJSONObject("session");
                            if (activeSess != null) {
                                int staleId = activeSess.optInt("id", -1);
                                if (staleId != -1) {
                                    JSONObject stopPayload = new JSONObject();
                                    stopPayload.put("status", "completed");
                                    stopPayload.put("notes", "Auto-closed previous session from Desktop");
                                    DesktopCloudApiClient.sendApiRequest("/sessions/" + staleId + "/stop", "POST", stopPayload, token);
                                }
                            }
                        }
                        // Retry start
                        DesktopCloudApiClient.ApiResponse retryRes = DesktopCloudApiClient.sendApiRequest("/sessions/start", "POST", payload, token);
                        if (retryRes.isSuccess() && retryRes.json != null) {
                            JSONObject sess = retryRes.json.optJSONObject("session");
                            if (sess != null) {
                                synchronized (DesktopCloudSessionManager.this) {
                                    activeServerSessionId = sess.optInt("id", -1);
                                    startHeartbeatLoop();
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private synchronized void startHeartbeatLoop() {
        stopHeartbeatLoop();
        sessionStartElapsed = 0;
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            synchronized (DesktopCloudSessionManager.this) {
                if (activeServerSessionId == -1) return;
                sessionStartElapsed += 60;
                String token = DesktopCloudAuthManager.getInstance().getAccessToken();
                if (!token.isEmpty()) {
                    try {
                        JSONObject hb = new JSONObject();
                        hb.put("elapsed_seconds", sessionStartElapsed);
                        DesktopCloudApiClient.sendApiRequest("/sessions/" + activeServerSessionId + "/heartbeat", "POST", hb, token);
                    } catch (Exception ignored) {}
                }
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    private synchronized void stopHeartbeatLoop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
    }

    public synchronized void stopLiveServerSession(long durationSeconds, String notes) {
        stopHeartbeatLoop();
        final int sessId = activeServerSessionId;
        activeServerSessionId = -1;
        if (sessId == -1 || !DesktopCloudAuthManager.getInstance().isLoggedIn()) return;
        final String token = DesktopCloudAuthManager.getInstance().getAccessToken();
        if (token.isEmpty()) return;

        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("status", "completed");
                payload.put("duration_seconds", durationSeconds);
                if (notes != null && !notes.isEmpty()) {
                    payload.put("notes", notes);
                }
                DesktopCloudApiClient.sendApiRequest("/sessions/" + sessId + "/stop", "POST", payload, token);
            } catch (Exception ignored) {}
        });
    }
}

