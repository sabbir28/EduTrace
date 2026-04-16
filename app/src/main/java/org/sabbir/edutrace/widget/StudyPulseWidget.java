package org.sabbir.edutrace.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.sabbir.edutrace.ui.activities.MainActivity;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudyPulseWidget extends AppWidgetProvider {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_study_pulse);
        
        // Intent to open the app
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_time, pendingIntent);

        // Fetch Data Async
        executor.execute(() -> {
            StudyDatabase db = StudyDatabase.getDatabase(context);
            StudyDao dao = db.studyDao();
            
            long todayStart = getStartOfDay();
            long totalMillis = dao.getTotalTimeFromSync(todayStart);
            
            float hours = (float) totalMillis / (1000 * 60 * 60);
            String timeStr = String.format("%.1fh", hours);
            
            // Daily goal: 4 hours (can be made configurable later)
            int progress = (int) (hours * 100 / 4);
            if (progress > 100) progress = 100;

            views.setTextViewText(R.id.widget_time, timeStr);
            views.setProgressBar(R.id.widget_progress, 100, progress, false);
            
            appWidgetManager.updateAppWidget(appWidgetId, views);
        });
    }

    private static long getStartOfDay() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // Force update on certain events if needed
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, StudyPulseWidget.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }
}
