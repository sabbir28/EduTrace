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
}
