package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONObject;
import org.sabbir.edutrace.desktop.data.DataManager;

import javax.swing.*;

/**
 * Manages user accounts, authentication tokens, profile sync, and password recovery
 * via the EduTrace REST API. Direct port of Android CloudAuthManager.java.
 */
public class DesktopCloudAuthManager {

    private static DesktopCloudAuthManager instance;

    public static synchronized DesktopCloudAuthManager getInstance() {
        if (instance == null) {
            instance = new DesktopCloudAuthManager();
        }
        return instance;
    }

    public interface AuthCallback {
        void onSuccess(DesktopCloudUser user);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess(String message);
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

    private DesktopCloudUser currentUser;

    private DesktopCloudAuthManager() {
        loadSession();
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null && currentUser.getToken() != null && !currentUser.getToken().isEmpty();
    }

    public synchronized DesktopCloudUser getCurrentUser() {
        return currentUser;
    }

    public synchronized String getAccessToken() {
        return (currentUser != null && currentUser.getToken() != null) ? currentUser.getToken() : "";
    }

    private synchronized void loadSession() {
        DataManager dm = DataManager.getInstance();
        if (dm.getData().currentUser != null && dm.getData().currentUser.getToken() != null) {
            this.currentUser = dm.getData().currentUser;
        }
    }

    public synchronized void saveSession(DesktopCloudUser user) {
        this.currentUser = user;
        DataManager dm = DataManager.getInstance();
        dm.getData().currentUser = user;
        dm.saveData();
    }

    public synchronized void logout(ActionCallback callback) {
        String token = getAccessToken();
        currentUser = null;
        DataManager dm = DataManager.getInstance();
        dm.getData().currentUser = null;
        dm.saveData();

        if (!token.isEmpty()) {
            DesktopCloudApiClient.cloudExecutor.execute(() -> {
                DesktopCloudApiClient.sendApiRequest("/auth/logout", "POST", null, token);
            });
        }
        if (callback != null) {
            SwingUtilities.invokeLater(() -> callback.onSuccess("Logged out"));
        }
    }

    public void login(String usernameOrEmail, String password, AuthCallback callback) {
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail.trim());
                payload.put("email", usernameOrEmail.trim());
                payload.put("password", password);

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/login", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String token = res.json.optString("access_token", "");
                    JSONObject userObj = res.json.optJSONObject("user");
                    int userId = (userObj != null) ? userObj.optInt("id", 1) : 1;
                    String retEmail = (userObj != null) ? userObj.optString("email", "") : "";
                    String retName = (userObj != null) ? userObj.optString("first_name",
                            userObj.optString("display_name", usernameOrEmail)) : usernameOrEmail;

                    DesktopCloudUser user = new DesktopCloudUser(userId, usernameOrEmail, retEmail, retName, token);
                    saveSession(user);
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(user));
                    }
                } else {
                    String err = (res.errorMessage != null) ? res.errorMessage : "Login failed. Check credentials.";
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onError("Network error: " + e.getMessage()));
                }
            }
        });
    }

    public void register(String username, String email, String password, String displayName, AuthCallback callback) {
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username.trim());
                payload.put("email", email.trim());
                payload.put("password", password);
                payload.put("display_name", (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim());
                payload.put("first_name", (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim());

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/register", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String token = res.json.optString("access_token", "");
                    JSONObject userObj = res.json.optJSONObject("user");
                    int userId = (userObj != null) ? userObj.optInt("id", 1) : 1;
                    String retEmail = (userObj != null) ? userObj.optString("email", email) : email;
                    String retName = (userObj != null) ? userObj.optString("first_name", displayName) : displayName;

                    DesktopCloudUser user = new DesktopCloudUser(userId, username, retEmail, retName, token);
                    saveSession(user);
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess(user));
                    }
                } else {
                    String err = (res.errorMessage != null) ? res.errorMessage : "Registration failed.";
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onError(err));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onError("Network error: " + e.getMessage()));
                }
            }
        });
    }

    public void checkUserAvailability(String username, String email, AvailabilityCallback callback) {
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (username != null && !username.trim().isEmpty()) payload.put("username", username.trim());
                if (email != null && !email.trim().isEmpty()) payload.put("email", email.trim());

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/check-availability", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    boolean available = res.json.optBoolean("available", true);
                    if (available) {
                        if (callback != null) SwingUtilities.invokeLater(callback::onAvailable);
                    } else {
                        String msg = res.json.optString("message", "User identifier already in use.");
                        if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(msg));
                    }
                } else {
                    String msg = res.errorMessage != null ? res.errorMessage : "Validation error";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public void findUserEmail(String usernameOrEmail, EmailLookupCallback callback) {
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username_or_email", usernameOrEmail.trim());

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/find-email", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String user = res.json.optString("username", usernameOrEmail);
                    String email = res.json.optString("email", "");
                    if (email.isEmpty()) {
                        if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("No email found for this user."));
                    } else {
                        if (callback != null) SwingUtilities.invokeLater(() -> callback.onFound(user, email));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "User account not found.";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Lookup error: " + e.getMessage()));
            }
        });
    }

    public void resetPassword(String usernameOrEmail, String newPassword, ActionCallback callback) {
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username_or_email", usernameOrEmail.trim());
                payload.put("new_password", newPassword);

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/reset-password", "POST", payload, null);
                if (res.isSuccess()) {
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess("Password has been reset successfully!"));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Password reset failed.";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    public void changePassword(String currentPassword, String newPassword, ActionCallback callback) {
        if (!isLoggedIn()) {
            if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Please sign in to change password."));
            return;
        }
        String token = getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("current_password", currentPassword);
                payload.put("new_password", newPassword);

                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/change-password", "PUT", payload, token);
                if (res.isSuccess()) {
                    String msg = "Password updated successfully! 🔒";
                    if (res.json != null && res.json.has("message")) {
                        msg = res.json.getString("message");
                    }
                    final String successMsg = msg;
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onSuccess(successMsg));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to update password.";
                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) SwingUtilities.invokeLater(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    public void syncUserProfile(ActionCallback callback) {
        if (!isLoggedIn()) return;
        String token = getAccessToken();
        DesktopCloudApiClient.cloudExecutor.execute(() -> {
            try {
                DesktopCloudApiClient.ApiResponse res = DesktopCloudApiClient.sendApiRequest("/auth/me", "GET", null, token);
                if (res.isSuccess() && res.json != null) {
                    JSONObject userObj = res.json.optJSONObject("user");
                    if (userObj != null && currentUser != null) {
                        if (userObj.has("id")) currentUser.setId(userObj.getInt("id"));
                        if (userObj.has("username")) currentUser.setUsername(userObj.getString("username"));
                        if (userObj.has("email")) currentUser.setEmail(userObj.getString("email"));
                        String dName = userObj.optString("first_name", userObj.optString("display_name", ""));
                        if (!dName.isEmpty()) currentUser.setDisplayName(dName);
                        saveSession(currentUser);
                    }
                    if (callback != null) {
                        SwingUtilities.invokeLater(() -> callback.onSuccess("Profile synced"));
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}
