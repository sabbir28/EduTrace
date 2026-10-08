package org.sabbir.edutrace.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles checking GitHub Releases for updates, notifying the user,
 * and downloading the latest APK release package.
 */
public class UpdateManager {
    private static final String GITHUB_REPO_API = "https://api.github.com/repos/sabbir28/EduTrace/releases/latest";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UpdateCheckCallback {
        void onCheckComplete(boolean isUpdateAvailable, String latestVersion);
    }

    public static class ReleaseInfo {
        public String tagName;
        public String versionName;
        public String releaseTitle;
        public String releaseNotes;
        public String apkDownloadUrl;
        public String releaseHtmlUrl;
    }

    /**
     * Checks for updates from GitHub Releases.
     *
     * @param context Context of the calling activity
     * @param isManualCheck If true, shows feedback toasts even if up to date or on error
     */
    public static void checkForUpdates(Context context, boolean isManualCheck) {
        checkForUpdates(context, isManualCheck, null);
    }

    /**
     * Checks for updates from GitHub Releases with an optional callback.
     */
    public static void checkForUpdates(Context context, boolean isManualCheck, UpdateCheckCallback callback) {
        executor.execute(() -> {
            try {
                String currentVersion = getCurrentVersion(context);
                ReleaseInfo releaseInfo = fetchLatestRelease();

                if (releaseInfo != null && isNewerVersion(releaseInfo.versionName, currentVersion)) {
                    mainHandler.post(() -> {
                        showUpdateDialog(context, releaseInfo, currentVersion);
                        if (callback != null) {
                            callback.onCheckComplete(true, releaseInfo.versionName);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (isManualCheck) {
                            Toast.makeText(context, "You are already using the latest version (v" + currentVersion + ")! 🎉", Toast.LENGTH_SHORT).show();
                        }
                        if (callback != null) {
                            callback.onCheckComplete(false, releaseInfo != null ? releaseInfo.versionName : currentVersion);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (isManualCheck) {
                        Toast.makeText(context, "Unable to check for updates. Please check your connection.", Toast.LENGTH_SHORT).show();
                    }
                    if (callback != null) {
                        callback.onCheckComplete(false, null);
                    }
                });
            }
        });
    }

    private static ReleaseInfo fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_REPO_API);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "EduTrace-Android-App");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                ReleaseInfo info = new ReleaseInfo();
                info.tagName = json.optString("tag_name", "");
                info.versionName = info.tagName.startsWith("v") ? info.tagName.substring(1) : info.tagName;
                info.releaseTitle = json.optString("name", "EduTrace " + info.tagName);
                info.releaseNotes = json.optString("body", "Bug fixes and improvements.");
                info.releaseHtmlUrl = json.optString("html_url", "https://github.com/sabbir28/EduTrace/releases");

                // Locate preferred release APK asset
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        String downloadUrl = asset.optString("browser_download_url", "");

                        if (name.equalsIgnoreCase("EduTrace-release.apk")) {
                            info.apkDownloadUrl = downloadUrl;
                            break;
                        } else if (name.endsWith(".apk") && info.apkDownloadUrl == null) {
                            info.apkDownloadUrl = downloadUrl;
                        }
                    }
                }

                if (info.apkDownloadUrl == null) {
                    info.apkDownloadUrl = info.releaseHtmlUrl;
                }

                return info;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    public static String getCurrentVersion(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    /**
     * Compares remote and local version strings.
     * E.g. "1.0.14" vs "1.0.13" -> true (newer).
     */
    public static boolean isNewerVersion(String remoteVersion, String localVersion) {
        if (remoteVersion == null || remoteVersion.isEmpty()) return false;
        if (localVersion == null || localVersion.isEmpty()) return true;

        try {
            String[] remoteParts = remoteVersion.replaceAll("[^0-9.]", "").split("\\.");
            String[] localParts = localVersion.replaceAll("[^0-9.]", "").split("\\.");

            int length = Math.max(remoteParts.length, localParts.length);
            for (int i = 0; i < length; i++) {
                int r = i < remoteParts.length && !remoteParts[i].isEmpty() ? Integer.parseInt(remoteParts[i]) : 0;
                int l = i < localParts.length && !localParts[i].isEmpty() ? Integer.parseInt(localParts[i]) : 0;

                if (r > l) return true;
                if (r < l) return false;
            }
        } catch (Exception e) {
            return !remoteVersion.equals(localVersion);
        }
        return false;
    }

    private static void showUpdateDialog(Context context, ReleaseInfo info, String currentVersion) {
        String message = "A new version of EduTrace is available!\n\n"
                + "• Current Version: v" + currentVersion + "\n"
                + "• Latest Version: v" + info.versionName + "\n\n"
                + "What's New:\n" + info.releaseNotes;

        new AlertDialog.Builder(context)
                .setTitle("Update Available 🚀")
                .setMessage(message)
                .setPositiveButton("Download & Update", (dialog, which) -> {
                    startDownload(context, info.apkDownloadUrl, info.versionName);
                })
                .setNeutralButton("View Release", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseHtmlUrl));
                    context.startActivity(browserIntent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    public static void startDownload(Context context, String downloadUrl, String versionName) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("EduTrace v" + versionName);
            request.setDescription("Downloading latest EduTrace release APK...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EduTrace-v" + versionName + ".apk");

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(context, "Downloading update... Check your notification bar or Downloads to install.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback to opening browser directly
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
            context.startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(context, "Could not open download link.", Toast.LENGTH_SHORT).show();
        }
    }
}
