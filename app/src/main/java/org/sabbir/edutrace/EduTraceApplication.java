package org.sabbir.edutrace;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class EduTraceApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Applies dynamic colors (Material You) to all activities if supported (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
