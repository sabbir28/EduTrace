package org.sabbir.edutrace.data.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles core HTTP REST networking, connection retries, and server endpoint configuration
 * for EduTrace Cloud operations.
 */
public class CloudApiClient {
    public static final String TAG = "EduTraceCloud";
    public static final String PREFS_AUTH = "EduTrace_Cloud_Auth";
    public static final String KEY_SERVER_URL = "cloud_server_api_url";

    // Standard server endpoints (Vercel Production, Emulator, Host PC Wi-Fi, and Localhost)
    public static final String DEFAULT_PROD_URL = "https://edutrace-theta.vercel.app/api";
    public static final String DEFAULT_EMULATOR_URL = "http://10.0.2.2:5000/api";
    public static final String DEFAULT_WIFI_URL = "http://192.168.10.120:5000/api";
    public static final String DEFAULT_LOCAL_URL = "http://127.0.0.1:5000/api";

    public static final ExecutorService cloudExecutor = Executors.newSingleThreadExecutor();
    public static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class ApiResponse {
        public int statusCode;
        public JSONObject json;
        public String rawBody;
        public String errorMessage;

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    public static String getServerUrl(Context context) {
        if (context == null) return DEFAULT_PROD_URL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, DEFAULT_PROD_URL);
    }

    public static void setServerUrl(Context context, String url) {
        if (context == null || url == null) return;
        String cleanUrl = url.trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        if (!cleanUrl.endsWith("/api")) {
            cleanUrl = cleanUrl + "/api";
        }
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_URL, cleanUrl)
                .apply();
    }

    public static ApiResponse sendApiRequest(Context context, String endpoint, String method, JSONObject payload, String token) {
        String primaryBase = getServerUrl(context);
        ApiResponse resp = executeSingleRequest(primaryBase, endpoint, method, payload, token);

        // Fallback: if default emulator URL cannot connect, try Wi-Fi host URL automatically
        if (!resp.isSuccess() && resp.errorMessage != null && resp.errorMessage.contains("Connect")
                && DEFAULT_EMULATOR_URL.equals(primaryBase)) {
            Log.d(TAG, "Primary emulator URL failed, attempting Wi-Fi fallback: " + DEFAULT_WIFI_URL);
            ApiResponse fallback = executeSingleRequest(DEFAULT_WIFI_URL, endpoint, method, payload, token);
            if (fallback.isSuccess() || fallback.statusCode > 0) {
                setServerUrl(context, DEFAULT_WIFI_URL);
                return fallback;
            }
        }
        return resp;
    }

    public static ApiResponse executeSingleRequest(String baseUrl, String endpoint, String method, JSONObject payload, String token) {
        ApiResponse result = new ApiResponse();
        HttpURLConnection conn = null;
        try {
            String fullUrl = baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            if (payload != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                conn.setDoOutput(true);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }
            }

            int code = conn.getResponseCode();
            result.statusCode = code;

            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                result.rawBody = sb.toString();

                try {
                    result.json = new JSONObject(result.rawBody);
                } catch (Exception ignored) {
                    // Raw string body
                }
            }

            if (!result.isSuccess()) {
                String errMsg = "Request failed (" + code + ")";
                if (result.json != null) {
                    if (result.json.has("message")) errMsg = result.json.getString("message");
                    else if (result.json.has("error")) errMsg = result.json.getString("error");
                }
                result.errorMessage = errMsg;
            }

        } catch (ConnectException ce) {
            result.statusCode = 0;
            result.errorMessage = "Unable to connect to EduTrace Server. Please check network/host URL.";
        } catch (Exception e) {
            result.statusCode = 0;
            result.errorMessage = e.getMessage() != null ? e.getMessage() : "Network error";
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    public static void checkServerHealth(Context context, CloudDbManager.HealthCallback callback) {
        cloudExecutor.execute(() -> {
            ApiResponse res = sendApiRequest(context, "/health", "GET", null, null);
            if (res.isSuccess()) {
                mainHandler.post(() -> callback.onSuccess("EduTrace Server is Online 🟢"));
            } else {
                mainHandler.post(() -> callback.onError(res.errorMessage != null ? res.errorMessage : "Server unreachable"));
            }
        });
    }
}
