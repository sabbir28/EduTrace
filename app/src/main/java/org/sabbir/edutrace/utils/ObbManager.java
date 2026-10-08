package org.sabbir.edutrace.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Manages OBB (Opaque Binary Blob) asset expansion packs.
 * Enables Over-The-Air (OTA) content updates (quotes, soundscapes, templates)
 * without requiring the user to download or reinstall full APK updates.
 */
public class ObbManager {
    private static final String PREFS_NAME = "EduTrace_OBB";
    private static final String KEY_OBB_VERSION = "obb_version";
    private static final String GITHUB_REPO_API = "https://api.github.com/repos/sabbir28/EduTrace/releases/latest";
    private static final String OBB_FILE_NAME = "main.edutrace.obb";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ObbCheckCallback {
        void onCheckComplete(boolean isUpdateAvailable, String latestVersion, String downloadUrl);
    }

    public interface ObbDownloadListener {
        void onProgress(int progressPercent);
        void onSuccess(String version);
        void onError(String errorMessage);
    }

    /**
     * Resolves the primary OBB file location.
     */
    public static File getObbFile(Context context) {
        File obbDir = context.getObbDir();
        if (obbDir == null || (!obbDir.exists() && !obbDir.mkdirs())) {
            obbDir = new File(context.getFilesDir(), "obb");
            if (!obbDir.exists()) obbDir.mkdirs();
        }
        return new File(obbDir, OBB_FILE_NAME);
    }

    /**
     * Checks if an OBB expansion pack is downloaded and valid.
     */
    public static boolean hasObb(Context context) {
        File file = getObbFile(context);
        if (!file.exists() || file.length() == 0) return false;
        try {
            ZipFile zip = new ZipFile(file);
            zip.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the currently active OBB pack version string.
     */
    public static String getInstalledObbVersion(Context context) {
        if (!hasObb(context)) return "None (Bundled Assets)";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_OBB_VERSION, "v1.0");
    }

    /**
     * Loads a text or JSON resource directly from the OBB ZIP pack.
     * Returns null if the OBB is not present or does not contain the file.
     */
    public static String loadStringFromObb(Context context, String relativePath) {
        File obbFile = getObbFile(context);
        if (!hasObb(context)) return null;

        try (ZipFile zipFile = new ZipFile(obbFile)) {
            ZipEntry entry = zipFile.getEntry(relativePath);
            if (entry == null && relativePath.startsWith("/")) {
                entry = zipFile.getEntry(relativePath.substring(1));
            }
            if (entry == null) return null;

            try (InputStream is = zipFile.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extracts an audio or binary file from the OBB to the app's cache directory
     * so it can be played by MediaPlayer or referenced via file path.
     */
    public static File getFileFromObb(Context context, String relativePath) {
        File obbFile = getObbFile(context);
        if (!hasObb(context)) return null;

        try (ZipFile zipFile = new ZipFile(obbFile)) {
            ZipEntry entry = zipFile.getEntry(relativePath);
            if (entry == null) return null;

            File cacheFile = new File(context.getCacheDir(), "obb_cache/" + relativePath);
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            if (cacheFile.exists() && cacheFile.length() == entry.getSize()) {
                return cacheFile;
            }

            try (InputStream is = zipFile.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(cacheFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            return cacheFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks GitHub Releases for a newer OBB asset pack.
     */
    public static void checkForObbUpdates(Context context, boolean isManualCheck, ObbCheckCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_REPO_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
                    String tagName = json.optString("tag_name", "");
                    String remoteVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    String obbDownloadUrl = null;

                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".obb") || name.equalsIgnoreCase("EduTrace-assets.obb")) {
                                obbDownloadUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }

                    String currentObbVer = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .getString(KEY_OBB_VERSION, "");

                    boolean updateAvailable = (obbDownloadUrl != null) &&
                            (currentObbVer.isEmpty() || UpdateManager.isNewerVersion(remoteVersion, currentObbVer));

                    final String finalUrl = obbDownloadUrl;
                    mainHandler.post(() -> {
                        if (updateAvailable) {
                            showObbUpdateDialog(context, remoteVersion, finalUrl);
                        } else if (isManualCheck) {
                            Toast.makeText(context, "Asset pack is up to date (" + getInstalledObbVersion(context) + ")!", Toast.LENGTH_SHORT).show();
                        }
                        if (callback != null) {
                            callback.onCheckComplete(updateAvailable, remoteVersion, finalUrl);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (isManualCheck) {
                            Toast.makeText(context, "Could not reach update server.", Toast.LENGTH_SHORT).show();
                        }
                        if (callback != null) callback.onCheckComplete(false, null, null);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (isManualCheck) {
                        Toast.makeText(context, "Failed to check asset pack updates.", Toast.LENGTH_SHORT).show();
                    }
                    if (callback != null) callback.onCheckComplete(false, null, null);
                });
            }
        });
    }

    private static void showObbUpdateDialog(Context context, String newVersion, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle("Asset Pack Update 📦")
                .setMessage("A new study content and soundscape pack (v" + newVersion + ") is available!\n\n"
                        + "You can download this pack instantly to get the latest study quotes, soundscapes, and syllabus templates without reinstalling or updating the app.")
                .setPositiveButton("Download Pack", (dialog, which) -> {
                    downloadAndApplyObb(context, downloadUrl, newVersion, null);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    /**
     * Downloads an OBB pack in the background, verifies its integrity, and applies it.
     */
    public static void downloadAndApplyObb(Context context, String downloadUrl, String newVersion, ObbDownloadListener listener) {
        Toast.makeText(context, "Starting asset pack download in background...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            File targetFile = getObbFile(context);
            File tempFile = new File(targetFile.getParentFile(), OBB_FILE_NAME + ".tmp");

            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "EduTrace-Android-App");

                int fileLength = conn.getContentLength();
                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(tempFile)) {
                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        output.write(data, 0, count);
                        if (fileLength > 0 && listener != null) {
                            int percent = (int) (total * 100 / fileLength);
                            mainHandler.post(() -> listener.onProgress(percent));
                        }
                    }
                }
                conn.disconnect();

                // Verify ZIP integrity before replacing
                ZipFile testZip = new ZipFile(tempFile);
                testZip.close();

                if (targetFile.exists()) {
                    targetFile.delete();
                }
                boolean renamed = tempFile.renameTo(targetFile);
                if (!renamed) {
                    throw new Exception("Failed to rename temporary OBB file to target destination");
                }

                // Save new version
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_OBB_VERSION, "v" + newVersion)
                        .apply();

                mainHandler.post(() -> {
                    Toast.makeText(context, "Asset pack v" + newVersion + " installed successfully! 🎉", Toast.LENGTH_LONG).show();
                    if (listener != null) listener.onSuccess(newVersion);
                });

            } catch (Exception e) {
                e.printStackTrace();
                if (tempFile.exists()) tempFile.delete();

                mainHandler.post(() -> {
                    Toast.makeText(context, "Failed to download asset pack: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (listener != null) listener.onError(e.getMessage());
                });
            }
        });
    }
}
