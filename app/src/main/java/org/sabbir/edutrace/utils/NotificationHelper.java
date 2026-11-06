package org.sabbir.edutrace.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.sabbir.edutrace.R;

public class NotificationHelper {
    public static final String FOCUS_CHANNEL_ID = "focus_channel";
    public static final String ALERT_CHANNEL_ID = "app_limit_channel";
    public static final String MUSIC_CHANNEL_ID = "study_music_channel";
    public static final int MUSIC_NOTIFICATION_ID = 2001;

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel focusChannel = new NotificationChannel(
                        FOCUS_CHANNEL_ID,
                        "Focus Mode Notifications",
                        NotificationManager.IMPORTANCE_LOW
                );
                focusChannel.setDescription("Notifications for active study sessions");
                manager.createNotificationChannel(focusChannel);

                NotificationChannel alertChannel = new NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "Study Limits & App Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                );
                alertChannel.setDescription("Alerts when apps exceed daily study usage limits");
                alertChannel.enableVibration(true);
                manager.createNotificationChannel(alertChannel);

                NotificationChannel musicChannel = new NotificationChannel(
                        MUSIC_CHANNEL_ID,
                        "Study Focus Music Controls",
                        NotificationManager.IMPORTANCE_LOW
                );
                musicChannel.setDescription("Control focus study music playback");
                musicChannel.setShowBadge(false);
                manager.createNotificationChannel(musicChannel);
            }
        }
    }

    public static void showFocusNotification(Context context, String title, String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, builder.build());
        }
    }

    public static void clearFocusNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(1);
        }
    }

    public static void showAppLimitAlert(Context context, String appName, String packageName) {
        android.content.Intent openAppIntent = new android.content.Intent(context, org.sabbir.edutrace.ui.activities.MainActivity.class);
        openAppIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                context,
                1003,
                openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Study Limit Reached")
                .setContentText(appName + " used 5+ hours today. Time to study!")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(appName + " has been used for more than 5 hours today. Please focus on your studies!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1003, builder.build());
        }
    }

    public static void showMusicNotification(Context context, String title, String subtitle, boolean isPlaying) {
        createNotificationChannel(context);

        // Intent to open TimerActivity
        android.content.Intent openIntent = new android.content.Intent(context, org.sabbir.edutrace.ui.activities.TimerActivity.class);
        openIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pendingOpen = android.app.PendingIntent.getActivity(
                context, 201, openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Previous
        android.content.Intent prevIntent = new android.content.Intent(context, org.sabbir.edutrace.widget.StudyAudioWidget.class);
        prevIntent.setAction(org.sabbir.edutrace.widget.StudyAudioWidget.ACTION_WIDGET_PREV);
        android.app.PendingIntent pendingPrev = android.app.PendingIntent.getBroadcast(
                context, 202, prevIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Play/Pause
        android.content.Intent playIntent = new android.content.Intent(context, org.sabbir.edutrace.widget.StudyAudioWidget.class);
        playIntent.setAction(org.sabbir.edutrace.widget.StudyAudioWidget.ACTION_WIDGET_PLAY_PAUSE);
        android.app.PendingIntent pendingPlay = android.app.PendingIntent.getBroadcast(
                context, 203, playIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Next
        android.content.Intent nextIntent = new android.content.Intent(context, org.sabbir.edutrace.widget.StudyAudioWidget.class);
        nextIntent.setAction(org.sabbir.edutrace.widget.StudyAudioWidget.ACTION_WIDGET_NEXT);
        android.app.PendingIntent pendingNext = android.app.PendingIntent.getBroadcast(
                context, 204, nextIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MUSIC_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title != null ? title : "EduTrace Focus Audio")
                .setContentText(subtitle != null ? subtitle : "Deep Focus Flow")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setContentIntent(pendingOpen)
                .addAction(R.drawable.ic_skip_previous, "Prev", pendingPrev)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow, isPlaying ? "Pause" : "Play", pendingPlay)
                .addAction(R.drawable.ic_skip_next, "Next", pendingNext);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(MUSIC_NOTIFICATION_ID, builder.build());
        }
    }

    public static void clearMusicNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(MUSIC_NOTIFICATION_ID);
        }
    }
}
