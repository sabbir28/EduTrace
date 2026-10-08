package org.sabbir.edutrace;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import org.sabbir.edutrace.utils.NotificationHelper;

public class EduTraceApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Initialize notification channels early so services and notifications have valid channels
            NotificationHelper.createNotificationChannel(this);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            // Applies dynamic colors (Material You) to all activities if supported (Android 12+)
            DynamicColors.applyToActivitiesIfAvailable(this);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

