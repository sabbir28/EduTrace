package org.sabbir.edutrace.utils;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.models.FocusAppItem;
import org.sabbir.edutrace.data.models.FocusTaskItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Core engine for EduTrace Focus Launcher.
 * Manages installed applications, AI tools detection, daily essentials categorization,
 * dynamic screen-time usage tracking, and the 5-Hour App Ghosting / Vanishing Engine.
 */
public class FocusLauncherManager {

    private static final String TAG = "FocusLauncherManager";
    private static final String PREFS_NAME = "EduTrace_Focus_Launcher_Prefs";
    private static final String KEY_FOCUS_TASKS = "focus_daily_tasks_json";
    public static final long DEFAULT_APP_LIMIT_MS = 5 * 60 * 60 * 1000; // 5 Hours Quota

    private static volatile FocusLauncherManager instance;

    private final List<FocusAppItem> allInstalledApps = new ArrayList<>();
    private final List<FocusAppItem> activeAllowedApps = new ArrayList<>();
    private final List<FocusAppItem> vanishedDistractions = new ArrayList<>();
    private final List<FocusAppItem> curatedFocusApps = new ArrayList<>();

    private FocusLauncherManager() {}

    public static FocusLauncherManager getInstance() {
        if (instance == null) {
            synchronized (FocusLauncherManager.class) {
                if (instance == null) {
                    instance = new FocusLauncherManager();
                }
            }
        }
        return instance;
    }

    /**
     * Scans installed launcher activities, maps today's foreground usage via UsageStatsManager,
     * and evaluates the 5-Hour Dynamic App Ghosting / Vanishing Engine.
     */
    public synchronized void reloadApps(Context context) {
        if (context == null) return;
        PackageManager pm = context.getPackageManager();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(launcherIntent, 0);
        Map<String, Long> usageMap = getTodayUsageMap(context);

        List<FocusAppItem> scanned = new ArrayList<>();
        List<FocusAppItem> allowed = new ArrayList<>();
        List<FocusAppItem> vanished = new ArrayList<>();
        List<FocusAppItem> curated = new ArrayList<>();

        String myPackage = context.getPackageName();

        for (ResolveInfo ri : activities) {
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            String activity = ri.activityInfo.name;

            // Don't show our own launcher in the app list
            if (myPackage.equals(pkg) && activity.contains("FocusLauncherActivity")) {
                continue;
            }

            CharSequence labelSeq = ri.loadLabel(pm);
            String label = (labelSeq != null) ? labelSeq.toString() : pkg;

            FocusAppItem item = new FocusAppItem(pkg, activity, label, ri.loadIcon(pm));

            // Categorization
            boolean isAi = isAiToolPackage(pkg, label);
            boolean isEssential = isEssentialDailyUtility(pkg, label) || myPackage.equals(pkg);
            item.setAiTool(isAi);
            item.setDailyEssential(isEssential);

            // Screen time today
            long usageMs = usageMap.containsKey(pkg) ? usageMap.get(pkg) : 0L;
            item.setUsageDurationMs(usageMs);

            // The 5-Hour Dynamic Ghosting / Vanishing Rule
            // Distracting / Social apps (Facebook, Instagram, TikTok, Games) that exceed 5 hours
            // completely vanish from the launcher interface ("like it never existed").
            if (!isEssential && usageMs >= DEFAULT_APP_LIMIT_MS) {
                item.setVanished(true);
                vanished.add(item);
            } else {
                item.setVanished(false);
                allowed.add(item);

                // Top curated list: AI tools, EduTrace, Books/Audio/Study tools
                if (isAi || isCuratedRecommendation(pkg, label)) {
                    item.setCurated(true);
                    curated.add(item);
                }
            }

            scanned.add(item);
        }

        // Sort allowed apps alphabetically for clean vertical A-Z listing
        Collections.sort(allowed, (a, b) -> a.getAppLabel().compareToIgnoreCase(b.getAppLabel()));
        Collections.sort(curated, (a, b) -> a.getAppLabel().compareToIgnoreCase(b.getAppLabel()));
        Collections.sort(vanished, (a, b) -> Long.compare(b.getUsageDurationMs(), a.getUsageDurationMs()));

        allInstalledApps.clear();
        allInstalledApps.addAll(scanned);

        activeAllowedApps.clear();
        activeAllowedApps.addAll(allowed);

        vanishedDistractions.clear();
        vanishedDistractions.addAll(vanished);

        curatedFocusApps.clear();
        curatedFocusApps.addAll(curated);

        Log.d(TAG, "Reloaded: Total=" + scanned.size() + ", Allowed=" + allowed.size() + ", Vanished=" + vanished.size());
    }

    public List<FocusAppItem> getAllowedApps(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(activeAllowedApps);
        }
        String cleanQuery = query.toLowerCase(Locale.getDefault()).trim();
        List<FocusAppItem> filtered = new ArrayList<>();
        for (FocusAppItem app : activeAllowedApps) {
            if (app.getAppLabel().toLowerCase(Locale.getDefault()).contains(cleanQuery) ||
                app.getPackageName().toLowerCase(Locale.getDefault()).contains(cleanQuery)) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    public List<FocusAppItem> getCuratedApps() {
        return new ArrayList<>(curatedFocusApps);
    }

    public List<FocusAppItem> getVanishedApps() {
        return new ArrayList<>(vanishedDistractions);
    }

    /**
     * Categorizes AI Tools (ChatGPT, Claude, Gemini, Copilot, Perplexity, DeepSeek, Poe, etc.)
     */
    public boolean isAiToolPackage(String pkg, String label) {
        String p = (pkg != null ? pkg : "").toLowerCase(Locale.US);
        String l = (label != null ? label : "").toLowerCase(Locale.US);

        return p.contains("openai") || p.contains("chatgpt") || l.contains("chatgpt") ||
                p.contains("anthropic") || p.contains("claude") || l.contains("claude") ||
                p.contains("bard") || p.contains("gemini") || l.contains("gemini") ||
                p.contains("copilot") || l.contains("copilot") ||
                p.contains("perplexity") || l.contains("perplexity") ||
                p.contains("poe") || p.contains("deepseek");
    }

    /**
     * Categorizes Daily Essentials (Phone, SMS, Browser, Camera, Clock, Calculator, EduTrace)
     */
    public boolean isEssentialDailyUtility(String pkg, String label) {
        String p = (pkg != null ? pkg : "").toLowerCase(Locale.US);
        String l = (label != null ? label : "").toLowerCase(Locale.US);

        return p.contains("dialer") || p.contains("telecom") || p.contains("phone") || l.equals("phone") ||
                p.contains("mms") || p.contains("messaging") || l.contains("message") ||
                p.contains("camera") || l.contains("camera") ||
                p.contains("calculator") || l.contains("calculator") ||
                p.contains("deskclock") || p.contains("clock") || l.contains("clock") ||
                p.contains("calendar") || l.contains("calendar") ||
                p.contains("chrome") || p.contains("browser") || l.contains("browser") ||
                p.contains("settings");
    }

    private boolean isCuratedRecommendation(String pkg, String label) {
        String p = (pkg != null ? pkg : "").toLowerCase(Locale.US);
        String l = (label != null ? label : "").toLowerCase(Locale.US);

        return p.contains("edutrace") || p.contains("audible") || p.contains("spotify") ||
                p.contains("chess") || p.contains("drive") || p.contains("forest") ||
                l.contains("study") || l.contains("notes") || l.contains("reader");
    }

    /**
     * Queries daily foreground usage using Android UsageStatsManager
     */
    private Map<String, Long> getTodayUsageMap(Context context) {
        Map<String, Long> map = new HashMap<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return map;

        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return map;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now);
            if (stats != null) {
                for (UsageStats u : stats) {
                    if (u != null && u.getPackageName() != null) {
                        long cur = map.containsKey(u.getPackageName()) ? map.get(u.getPackageName()) : 0L;
                        map.put(u.getPackageName(), cur + u.getTotalTimeInForeground());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed querying usage stats: " + e.getMessage());
        }
        return map;
    }

    /**
     * Calculates the Day In Progress percentage (0 - 100%)
     */
    public int getDayInProgressPercent() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int totalMinutes = (hour * 60) + minute;
        return (int) ((totalMinutes / (24f * 60f)) * 100);
    }

    // --- Today's Focus Checklist Task Engine ---

    public List<FocusTaskItem> getFocusTasks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_FOCUS_TASKS, null);
        List<FocusTaskItem> list = new ArrayList<>();

        if (json == null || json.trim().isEmpty()) {
            // Default motivational focus tasks (matches Image 1)
            list.add(new FocusTaskItem(UUID.randomUUID().toString(), "Deep study session (2h)", false));
            list.add(new FocusTaskItem(UUID.randomUUID().toString(), "Read 10 pages of book", false));
            list.add(new FocusTaskItem(UUID.randomUUID().toString(), "Go for a 30 min walk", false));
            list.add(new FocusTaskItem(UUID.randomUUID().toString(), "Drink 2 bottles of water", true));
            saveFocusTasks(context, list);
        } else {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new FocusTaskItem(o.getString("id"), o.getString("title"), o.optBoolean("completed", false)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing tasks: " + e.getMessage());
            }
        }
        return list;
    }

    public void saveFocusTasks(Context context, List<FocusTaskItem> tasks) {
        try {
            JSONArray arr = new JSONArray();
            for (FocusTaskItem t : tasks) {
                JSONObject o = new JSONObject();
                o.put("id", t.getId());
                o.put("title", t.getTitle());
                o.put("completed", t.isCompleted());
                arr.put(o);
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_FOCUS_TASKS, arr.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    public void addTask(Context context, String title) {
        if (title == null || title.trim().isEmpty()) return;
        List<FocusTaskItem> tasks = getFocusTasks(context);
        tasks.add(0, new FocusTaskItem(UUID.randomUUID().toString(), title.trim(), false));
        saveFocusTasks(context, tasks);
    }

    public void toggleTask(Context context, String id) {
        List<FocusTaskItem> tasks = getFocusTasks(context);
        for (FocusTaskItem t : tasks) {
            if (t.getId().equals(id)) {
                t.setCompleted(!t.isCompleted());
                break;
            }
        }
        saveFocusTasks(context, tasks);
    }

    public void deleteTask(Context context, String id) {
        List<FocusTaskItem> tasks = getFocusTasks(context);
        tasks.removeIf(t -> t.getId().equals(id));
        saveFocusTasks(context, tasks);
    }
}
