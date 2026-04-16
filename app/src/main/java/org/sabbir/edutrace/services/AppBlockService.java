package org.sabbir.edutrace.services;

import android.app.Notification;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.utils.NotificationHelper;
import java.util.Calendar;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class AppBlockService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private Handler checkHandler = new Handler();
    private long checkInterval = 5000; // Default 5 seconds
    private static final long FIVE_HOURS_MS = 5 * 60 * 60 * 1000;

    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkUsageAndBlock();
            checkHandler.postDelayed(this, checkInterval);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Android Go / Low RAM optimization
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null && am.isLowRamDevice()) {
            checkInterval = 15000; // Check every 15 seconds on Go devices to save battery/RAM
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, NotificationHelper.FOCUS_CHANNEL_ID)
                .setContentTitle("App Usage Monitor Active")
                .setContentText("Monitoring for excessive app usage.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();

        startForeground(1002, notification);
        checkHandler.post(checkRunnable);

        return START_STICKY;
    }

    private void checkUsageAndBlock() {
        if (!hasUsageStatsPermission()) return;

        String currentApp = getForegroundApp();
        if (currentApp == null || getPackageName().equals(currentApp)) {
            removeOverlay();
            return;
        }

        long usageTime = getTodayUsage(currentApp);
        if (usageTime > FIVE_HOURS_MS) {
            showOverlay(currentApp);
        } else {
            removeOverlay();
        }
    }

    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, System.currentTimeMillis() - 1000 * 10, System.currentTimeMillis());
                return stats != null && !stats.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String getForegroundApp() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000, time);
        if (appList != null && !appList.isEmpty()) {
            SortedMap<Long, UsageStats> mySortedMap = new TreeMap<>();
            for (UsageStats usageStats : appList) {
                mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if (!mySortedMap.isEmpty()) {
                return mySortedMap.get(mySortedMap.lastKey()).getPackageName();
            }
        }
        return null;
    }

    private long getTodayUsage(String packageName) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfDay = calendar.getTimeInMillis();

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, System.currentTimeMillis());
        if (stats != null) {
            for (UsageStats usageStats : stats) {
                if (usageStats.getPackageName().equals(packageName)) {
                    return usageStats.getTotalTimeInForeground();
                }
            }
        }
        return 0;
    }

    private void showOverlay(String packageName) {
        if (overlayView != null) return;
        if (!Settings.canDrawOverlays(this)) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(org.sabbir.edutrace.R.layout.overlay_blocked, null);
        
        TextView tvMessage = overlayView.findViewById(R.id.tvBlockedMessage);
        tvMessage.setText(packageName + " has been used for more than 5 hours today. Please focus on your studies!");

        Button btnClose = overlayView.findViewById(R.id.btnCloseOverlay);
        btnClose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        windowManager.addView(overlayView, params);
    }

    private void removeOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        checkHandler.removeCallbacks(checkRunnable);
        removeOverlay();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
