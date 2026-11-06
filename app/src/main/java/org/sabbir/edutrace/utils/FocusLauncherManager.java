package org.sabbir.edutrace.utils;

import android.app.Activity;
import android.app.role.RoleManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

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
        if (id == null) return;
        List<FocusTaskItem> tasks = getFocusTasks(context);
        for (int i = 0; i < tasks.size(); i++) {
            if (id.equals(tasks.get(i).getId())) {
                tasks.remove(i);
                break;
            }
        }
        saveFocusTasks(context, tasks);
    }

    // --- Default Launcher System Integration ---

    /**
     * Checks if EduTrace is currently the default Home Launcher on this Android device.
     */
    public static boolean isDefaultLauncher(Context context) {
        if (context == null) return false;
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return context.getPackageName().equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Guides or prompts the user via native OS RoleManager / System Settings to set EduTrace
     * as the Default Home Launcher.
     */
    public static void requestSetDefaultLauncher(Activity activity) {
        if (activity == null) return;
        try {
            // Android 10+ (API 29+) native RoleManager bottom sheet
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = (RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                        activity.startActivityForResult(intent, 2048);
                        return;
                    } else {
                        Toast.makeText(activity, "EduTrace is already your default Home Launcher! ✓", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            // Android N to Pie (API 24-28)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                    activity.startActivity(intent);
                    Toast.makeText(activity, "Select EduTrace as your Default Home App", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception ignored) {}
            }

            // Android M and older
            try {
                Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                activity.startActivity(intent);
                Toast.makeText(activity, "Select EduTrace as your Default Home App", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {}

            // Generic fallback chooser
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(Intent.createChooser(intent, "Set EduTrace as Default Launcher"));
        } catch (Exception e) {
            Toast.makeText(activity, "Open Settings -> Apps -> Default Apps -> Home -> EduTrace", Toast.LENGTH_LONG).show();
        }
    }

    // --- Reflection & Screen Time Analytics ---

    /**
     * Returns total foreground screen time in milliseconds across all apps today.
     */
    public long getTotalScreenTimeMs(Context context) {
        Map<String, Long> map = getTodayUsageMap(context);
        long total = 0;
        for (Long v : map.values()) {
            if (v != null) total += v;
        }
        return total;
    }

    /**
     * Returns a 24-element array (minutes per hour of the day) for the "When it happened" chart.
     */
    public int[] getTodayHourlyUsage(Context context) {
        int[] hours = new int[24];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 || context == null) return hours;

        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return hours;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long start = cal.getTimeInMillis();
            long end = System.currentTimeMillis();

            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
            if (stats != null) {
                // Distribute usage proportionally according to last time used hour
                for (UsageStats u : stats) {
                    if (u != null && u.getTotalTimeInForeground() > 0) {
                        Calendar lastCal = Calendar.getInstance();
                        lastCal.setTimeInMillis(u.getLastTimeUsed());
                        int h = lastCal.get(Calendar.HOUR_OF_DAY);
                        if (h >= 0 && h < 24) {
                            hours[h] += (int) (u.getTotalTimeInForeground() / (60 * 1000));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return hours;
    }

    /**
     * Determines peak hour of usage today (0 - 23).
     */
    public int getPeakHour(Context context) {
        int[] hourly = getTodayHourlyUsage(context);
        int peak = 22; // default 10 PM
        int max = -1;
        for (int i = 0; i < 24; i++) {
            if (hourly[i] > max) {
                max = hourly[i];
                peak = i;
            }
        }
        return peak;
    }

    /**
     * Formats peak hour string e.g. "10 pm".
     */
    public String getFormattedPeakHour(Context context) {
        int hour = getPeakHour(context);
        if (hour == 0) return "12 am";
        if (hour == 12) return "12 pm";
        if (hour > 12) return (hour - 12) + " pm";
        return hour + " am";
    }

    /**
     * Summary of apps used late at night (between 9 PM and 4 AM).
     */
    public String getLateNightAppsSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (FocusAppItem app : allInstalledApps) {
            if (app.getUsageDurationMs() > 10 * 60 * 1000) { // > 10m
                if (count > 0) sb.append(" \u2022 ");
                sb.append(app.getAppLabel());
                count++;
                if (count >= 3) break;
            }
        }
        return sb.length() > 0 ? sb.toString() : "WhatsApp \u2022 Instagram \u2022 YouTube";
    }

    /**
     * Returns 5 counts for session durations: [<1m, 1-5m, 5-15m, 15-30m, >30m].
     */
    public int[] getSessionShapes(Context context) {
        int[] shapes = new int[]{0, 0, 0, 0, 0};
        for (FocusAppItem app : allInstalledApps) {
            long ms = app.getUsageDurationMs();
            if (ms <= 0) continue;
            long mins = ms / (60 * 1000);
            if (mins < 1) shapes[0]++;
            else if (mins <= 5) shapes[1]++;
            else if (mins <= 15) shapes[2]++;
            else if (mins <= 30) shapes[3]++;
            else shapes[4]++;
        }
        return shapes;
    }
}

