package org.sabbir.edutrace.data.models;

import android.graphics.drawable.Drawable;

public class FocusAppItem {
    private String packageName;
    private String activityName;
    private String appLabel;
    private Drawable icon;
    private long usageDurationMs;
    private boolean isVanished;
    private boolean isAiTool;
    private boolean isDailyEssential;
    private boolean isCurated;

    public FocusAppItem(String packageName, String activityName, String appLabel, Drawable icon) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.appLabel = appLabel;
        this.icon = icon;
    }

    public String getPackageName() { return packageName; }
    public String getActivityName() { return activityName; }
    public String getAppLabel() { return appLabel != null ? appLabel : ""; }
    public Drawable getIcon() { return icon; }
    public void setIcon(Drawable icon) { this.icon = icon; }

    public long getUsageDurationMs() { return usageDurationMs; }
    public void setUsageDurationMs(long ms) { this.usageDurationMs = ms; }

    public boolean isVanished() { return isVanished; }
    public void setVanished(boolean vanished) { this.isVanished = vanished; }

    public boolean isAiTool() { return isAiTool; }
    public void setAiTool(boolean aiTool) { this.isAiTool = aiTool; }

    public boolean isDailyEssential() { return isDailyEssential; }
    public void setDailyEssential(boolean dailyEssential) { this.isDailyEssential = dailyEssential; }

    public boolean isCurated() { return isCurated; }
    public void setCurated(boolean curated) { this.isCurated = curated; }

    public String getFormattedUsage() {
        if (usageDurationMs <= 0) return "";
        long minutes = (usageDurationMs / (1000 * 60));
        long hours = minutes / 60;
        if (hours > 0) {
            return String.format(java.util.Locale.US, "%dh %dm", hours, minutes % 60);
        } else {
            return String.format(java.util.Locale.US, "%dm", minutes);
        }
    }
}

