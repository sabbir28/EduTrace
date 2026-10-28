package org.sabbir.edutrace.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.utils.NotificationHelper;
import org.sabbir.edutrace.utils.SessionManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class StudyTimerService extends Service {

    private Handler wardenHandler = new Handler();
    private Handler timerHandler = new Handler();
    private AudioManager audioManager;
    private boolean isWardenRunning = false;
    private SessionManager sessionManager;
    private NotificationManager notificationManager;
    private String subjectName;
    private long lastScreenOffTime = 0;
    private long lastHeartbeatTime = 0;
    private static final long INACTIVITY_THRESHOLD = 2 * 60 * 60 * 1000; // 2 hours
    private static final long HEARTBEAT_INTERVAL = 60 * 1000; // 60 seconds live tick

    private Runnable wardenRunnable = new Runnable() {
        @Override
        public void run() {
            if (isWardenRunning) {
                enforceSilence();
                wardenHandler.postDelayed(this, 5000);
            }
        }
    };

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                lastScreenOffTime = System.currentTimeMillis();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (lastScreenOffTime > 0 && (System.currentTimeMillis() - lastScreenOffTime) > INACTIVITY_THRESHOLD) {
                    // Auto-pause if screen was off for too long
                    if (sessionManager != null && sessionManager.isActive() && !sessionManager.isPaused()) {
                        sessionManager.setPaused(true, lastScreenOffTime, sessionManager.getTotalBreakTime());
                    }
                }
                lastScreenOffTime = 0;
            }
        }
    };

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionManager != null && sessionManager.isActive()) {
                long elapsed = sessionManager.getActiveSessionElapsed();
                boolean isPaused = sessionManager.isPaused();

                if (!isPaused) {
                    int seconds = (int) (elapsed / 1000);
                    int minutes = seconds / 60;
                    int hours = minutes / 60;
                    
                    String timeText = String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
                    updateNotification("Focusing on " + subjectName, "Elapsed Time: " + timeText);
                }

                // Live heartbeat tick mechanism: sends periodic heartbeat every 60s
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
                    lastHeartbeatTime = now;
                    org.sabbir.edutrace.data.cloud.CloudDbManager.sendLiveSessionHeartbeat(getApplicationContext(), elapsed, isPaused);
                }
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        sessionManager = new SessionManager(this);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        
        enableDND();
    }

    private void enableDND() {
        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        }
    }

    private void disableDND() {
        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            subjectName = intent.getStringExtra("SUBJECT_NAME");
        }
        
        if (subjectName == null && sessionManager.isActive()) {
            subjectName = sessionManager.getSubjectName();
        }
        
        if (subjectName == null) subjectName = "Focus Session";
        
        updateNotification("Study Distraction Warden Active", "Monitoring " + subjectName);

        isWardenRunning = true;
        wardenHandler.post(wardenRunnable);
        timerHandler.post(timerRunnable);

        lastHeartbeatTime = System.currentTimeMillis();
        if (sessionManager != null && sessionManager.isActive()) {
            org.sabbir.edutrace.data.cloud.CloudDbManager.sendLiveSessionHeartbeat(getApplicationContext(), sessionManager.getActiveSessionElapsed(), sessionManager.isPaused());
        }

        return START_STICKY;
    }

    private void updateNotification(String title, String text) {
        Notification notification = new NotificationCompat.Builder(this, NotificationHelper.FOCUS_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        startForeground(1001, notification);
    }

    private void enforceSilence() {
        // Automatically mute media, ring, and alarms
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isWardenRunning = false;
        wardenHandler.removeCallbacks(wardenRunnable);
        timerHandler.removeCallbacks(timerRunnable);
        unregisterReceiver(screenReceiver);
        disableDND();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Started service, not bound
    }
}
