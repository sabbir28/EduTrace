package org.sabbir.edutrace.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.Calendar;
import java.util.Random;

/**
 * Manages Dynamic Android App Icon Switching and Randomization.
 * Dynamically swaps between the Default EduTrace icon and the Company Brand Icon (company_icon.png)
 * using Android activity-alias component state management.
 */
public class AppIconManager {

    private static final String TAG = "AppIconManager";
    private static final String PREFS_NAME = "AppIconPrefs";
    private static final String KEY_DYNAMIC_ENABLED = "key_dynamic_icon_enabled";
    private static final String KEY_CURRENT_VARIANT = "key_current_icon_variant";
    private static final String KEY_LAST_RANDOM_DAY = "key_last_random_day";

    public static final String VARIANT_DEFAULT = "default";
    public static final String VARIANT_COMPANY = "company";

    public static final String COMPONENT_DEFAULT = "org.sabbir.edutrace.LauncherDefault";
    public static final String COMPONENT_COMPANY = "org.sabbir.edutrace.LauncherCompany";

    public static boolean isDynamicIconEnabled(Context context) {
        if (context == null) return true;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DYNAMIC_ENABLED, true);
    }

    public static void setDynamicIconEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DYNAMIC_ENABLED, enabled)
                .apply();

        if (!enabled) {
            setIcon(context, VARIANT_DEFAULT);
        } else {
            maybeRandomizeIcon(context, true);
        }
    }

    public static String getCurrentVariant(Context context) {
        if (context == null) return VARIANT_DEFAULT;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CURRENT_VARIANT, VARIANT_DEFAULT);
    }

    public static boolean isCompanyIconActive(Context context) {
        return VARIANT_COMPANY.equalsIgnoreCase(getCurrentVariant(context));
    }

    /**
     * Randomly rolls to feature the special Company Icon vs the Default icon.
     * Evaluated periodically (once daily or on fresh startup session).
     */
    public static void maybeRandomizeIcon(Context context, boolean forceRoll) {
        if (context == null || !isDynamicIconEnabled(context)) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int lastDay = prefs.getInt(KEY_LAST_RANDOM_DAY, -1);

        if (forceRoll || today != lastDay) {
            prefs.edit().putInt(KEY_LAST_RANDOM_DAY, today).apply();

            // 50% probability roll for Company Brand icon, 50% for Default icon
            boolean rollCompany = new Random().nextBoolean();
            String targetVariant = rollCompany ? VARIANT_COMPANY : VARIANT_DEFAULT;
            setIcon(context, targetVariant);
            Log.d(TAG, "Dynamic icon randomized for day " + today + " -> Selected: " + targetVariant);
        }
    }

    /**
     * Explicitly sets the active app icon variant.
     */
    public static void setIcon(Context context, String variant) {
        if (context == null) return;
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName defComp = new ComponentName(context, COMPONENT_DEFAULT);
            ComponentName compComp = new ComponentName(context, COMPONENT_COMPANY);

            boolean isCompany = VARIANT_COMPANY.equalsIgnoreCase(variant);

            if (isCompany) {
                // Enable Company Icon alias, disable default
                pm.setComponentEnabledSetting(compComp,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(defComp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } else {
                // Enable default, disable Company Icon alias
                pm.setComponentEnabledSetting(defComp,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(compComp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CURRENT_VARIANT, isCompany ? VARIANT_COMPANY : VARIANT_DEFAULT)
                    .apply();

            Log.i(TAG, "App icon updated to variant: " + variant);
        } catch (Throwable t) {
            Log.e(TAG, "Failed setting app icon state: " + t.getMessage(), t);
        }
    }

    /**
     * Toggles between Default icon and Company icon manually.
     */
    public static String toggleIcon(Context context) {
        String current = getCurrentVariant(context);
        String next = VARIANT_COMPANY.equals(current) ? VARIANT_DEFAULT : VARIANT_COMPANY;
        setIcon(context, next);
        return next;
    }
}
