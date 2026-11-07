package org.sabbir.edutrace.desktop.cloud;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DesktopCloudApiClient {

    public static final String DEFAULT_PROD_URL = "https://edutrace-theta.vercel.app/api";

    private static String serverUrl = DEFAULT_PROD_URL;
    public static final ExecutorService cloudExecutor = Executors.newCachedThreadPool();

    public static class ApiResponse {
        public int statusCode;
        public JSONObject json;
        public String rawBody;
        public String errorMessage;

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    public static synchronized String getServerUrl() {
        return serverUrl;
    }

    public static synchronized void setServerUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            serverUrl = DEFAULT_PROD_URL;
            return;
        }
        String cleanUrl = url.trim();
        if (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        if (!cleanUrl.endsWith("/api")) cleanUrl = cleanUrl + "/api";
        serverUrl = cleanUrl;
    }

    public static ApiResponse sendApiRequest(String endpoint, String method, JSONObject payload, String token) {
        ApiResponse result = new ApiResponse();
        HttpURLConnection conn = null;
        try {
            String base = getServerUrl();
            String fullUrl = base + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json");

            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            if (payload != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }
            }

            result.statusCode = conn.getResponseCode();

            InputStream is = (result.statusCode >= 200 && result.statusCode < 400)
                    ? conn.getInputStream() : conn.getErrorStream();

            if (is != null) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                result.rawBody = sb.toString();
                if (!result.rawBody.trim().isEmpty()) {
                    try {
                        result.json = new JSONObject(result.rawBody);
                    } catch (Exception ignored) {}
                }
            }

            if (!result.isSuccess()) {
                if (result.json != null) {
                    result.errorMessage = result.json.optString("message",
                            result.json.optString("error", "Server HTTP Error: " + result.statusCode));
                } else {
                    result.errorMessage = (result.rawBody != null && !result.rawBody.isEmpty())
                            ? result.rawBody : "Server HTTP Error: " + result.statusCode;
                }
            }

        } catch (Exception e) {
            result.statusCode = -1;
            result.errorMessage = "Connection error: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }
}

