package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Handles user authentication, registration, password management, profile synchronization,
 * and session credential persistence for EduTrace Cloud.
 */
public class CloudAuthManager {
    private static final String TAG = CloudApiClient.TAG;
    public static final String PREFS_AUTH = CloudApiClient.PREFS_AUTH;
    public static final String KEY_USER_ID = "cloud_user_id";
    public static final String KEY_USERNAME = "cloud_username";
    public static final String KEY_EMAIL = "cloud_email";
    public static final String KEY_DISPLAY_NAME = "cloud_display_name";
    public static final String KEY_ACCESS_TOKEN = "cloud_access_token";
    public static final String KEY_IS_LOGGED_IN = "cloud_logged_in";

    public static boolean isLoggedIn(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
                && prefs.getInt(KEY_USER_ID, -1) != -1
                && !prefs.getString(KEY_ACCESS_TOKEN, "").isEmpty();
    }

    public static CloudUser getCurrentUser(Context context) {
        if (context == null || !isLoggedIn(context)) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        int id = prefs.getInt(KEY_USER_ID, -1);
        String username = prefs.getString(KEY_USERNAME, "");
        String email = prefs.getString(KEY_EMAIL, "");
        String displayName = prefs.getString(KEY_DISPLAY_NAME, username);
        String token = prefs.getString(KEY_ACCESS_TOKEN, "");
        return new CloudUser(id, username, email, displayName, token);
    }

    public static String getAccessToken(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }

    public static void saveUserSession(Context context, CloudUser user) {
        if (context == null || user == null) return;
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_USER_ID, user.getId())
                .putString(KEY_USERNAME, user.getUsername())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_DISPLAY_NAME, user.getDisplayName())
                .putString(KEY_ACCESS_TOKEN, user.getToken() != null ? user.getToken() : "")
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public static void logout(Context context) {
        if (context == null) return;
        final String token = getAccessToken(context);
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_EMAIL)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_ACCESS_TOKEN)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply();

        // Notify server asynchronously
        if (!token.isEmpty()) {
            CloudApiClient.cloudExecutor.execute(() -> {
                try {
                    CloudApiClient.sendApiRequest(context, "/auth/logout", "POST", null, token);
                } catch (Throwable ignored) {}
            });
        }
    }

    public static void doSyncUserProfile(Context context) {
        if (!isLoggedIn(context)) return;
        final String token = getAccessToken(context);
        if (token.isEmpty()) return;
        try {
            CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/me", "GET", null, token);
            if (res.isSuccess() && res.json != null) {
                JSONObject userObj = res.json.optJSONObject("user");
                if (userObj != null) {
                    SharedPreferences.Editor edit = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit();
                    if (userObj.has("id")) edit.putInt(KEY_USER_ID, userObj.getInt("id"));
                    if (userObj.has("username")) edit.putString(KEY_USERNAME, userObj.getString("username"));
                    if (userObj.has("email")) edit.putString(KEY_EMAIL, userObj.getString("email"));
                    String displayName = userObj.optString("first_name", userObj.optString("display_name", ""));
                    if (!displayName.isEmpty()) edit.putString(KEY_DISPLAY_NAME, displayName);
                    edit.apply();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Profile sync notice: " + t.getMessage());
        }
    }

    public static void checkUserAvailability(Context context, String username, String email, CloudDbManager.AvailabilityCallback callback) {
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (username != null && !username.trim().isEmpty()) payload.put("username", username.trim());
                if (email != null && !email.trim().isEmpty()) payload.put("email", email.trim());

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/check-availability", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    boolean available = res.json.optBoolean("available", true);
                    if (available) {
                        CloudApiClient.mainHandler.post(callback::onAvailable);
                    } else {
                        String msg = res.json.optString("message", "User identifier already in use.");
                        CloudApiClient.mainHandler.post(() -> callback.onError(msg));
                    }
                } else {
                    String msg = res.errorMessage != null ? res.errorMessage : "Server validation error";
                    CloudApiClient.mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public static void register(Context context, String username, String email, String password, String displayName, CloudDbManager.AuthCallback callback) {
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username.trim());
                payload.put("display_name", (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim());
                payload.put("first_name", (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : username.trim());
                payload.put("email", email.trim().toLowerCase(Locale.ROOT));
                payload.put("password", password);

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/register", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String token = res.json.optString("access_token", "");
                    JSONObject userObj = res.json.optJSONObject("user");
                    int userId = (userObj != null) ? userObj.optInt("id", 1) : 1;
                    String retEmail = (userObj != null) ? userObj.optString("email", email) : email;
                    String retName = (userObj != null) ? userObj.optString("first_name", displayName) : displayName;

                    CloudUser user = new CloudUser(userId, username, retEmail, retName, token);
                    saveUserSession(context, user);
                    CloudApiClient.mainHandler.post(() -> callback.onSuccess(user));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Registration failed.";
                    CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError("Registration error: " + e.getMessage()));
            }
        });
    }

    public static void login(Context context, String usernameOrEmail, String password, CloudDbManager.AuthCallback callback) {
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail.trim());
                payload.put("email", usernameOrEmail.trim());
                payload.put("password", password);

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/login", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String token = res.json.optString("access_token", "");
                    JSONObject userObj = res.json.optJSONObject("user");
                    int userId = (userObj != null) ? userObj.optInt("id", 1) : 1;
                    String retEmail = (userObj != null) ? userObj.optString("email", "") : "";
                    String retName = (userObj != null) ? userObj.optString("first_name", usernameOrEmail) : usernameOrEmail;

                    CloudUser user = new CloudUser(userId, usernameOrEmail, retEmail, retName, token);
                    saveUserSession(context, user);
                    CloudApiClient.mainHandler.post(() -> callback.onSuccess(user));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Login failed. Please check credentials.";
                    CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError("Login error: " + e.getMessage()));
            }
        });
    }

    public static void findUserEmail(Context context, String usernameOrEmail, CloudDbManager.EmailLookupCallback callback) {
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username_or_email", usernameOrEmail.trim());

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/find-email", "POST", payload, null);
                if (res.isSuccess() && res.json != null) {
                    String user = res.json.optString("username", usernameOrEmail);
                    String email = res.json.optString("email", "");
                    if (email.isEmpty()) {
                        CloudApiClient.mainHandler.post(() -> callback.onError("No email found for this user."));
                    } else {
                        CloudApiClient.mainHandler.post(() -> callback.onFound(user, email));
                    }
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "User account not found.";
                    CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError("Lookup error: " + e.getMessage()));
            }
        });
    }

    public static void resetPassword(Context context, String usernameOrEmail, String newPassword, CloudDbManager.AuthCallback callback) {
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username_or_email", usernameOrEmail.trim());
                payload.put("new_password", newPassword);

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/reset-password", "POST", payload, null);
                if (res.isSuccess()) {
                    CloudUser dummyUser = new CloudUser(-1, usernameOrEmail, "", usernameOrEmail, "");
                    CloudApiClient.mainHandler.post(() -> callback.onSuccess(dummyUser));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Password reset failed.";
                    CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                CloudApiClient.mainHandler.post(() -> callback.onError("Password reset error: " + e.getMessage()));
            }
        });
    }

    public static void changePassword(Context context, String currentPassword, String newPassword, CloudDbManager.ActionCallback callback) {
        if (!isLoggedIn(context)) {
            if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Please sign in to change your password."));
            return;
        }
        final String token = getAccessToken(context);
        CloudApiClient.cloudExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("current_password", currentPassword);
                payload.put("new_password", newPassword);

                CloudApiClient.ApiResponse res = CloudApiClient.sendApiRequest(context, "/auth/change-password", "PUT", payload, token);
                if (res.isSuccess()) {
                    String msg = "Password updated successfully! 🔒";
                    if (res.json != null && res.json.has("message")) {
                        msg = res.json.getString("message");
                    }
                    final String successMsg = msg;
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onSuccess(successMsg));
                } else {
                    String err = res.errorMessage != null ? res.errorMessage : "Failed to update password.";
                    if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (callback != null) CloudApiClient.mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }
}
