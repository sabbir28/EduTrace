package org.sabbir.edutrace.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.EduTraceMusicTrack;
import org.sabbir.edutrace.ui.activities.TimerActivity;
import org.sabbir.edutrace.utils.EduTraceMusicPlayer;

/**
 * Small Home Screen AppWidget dedicated exclusively to playing EduTrace focus audio.
 * Allows instant 1-tap play/pause, track skipping, and status viewing directly
 * from the Android home screen.
 */
public class StudyAudioWidget extends AppWidgetProvider {

    public static final String ACTION_WIDGET_PLAY_PAUSE = "org.sabbir.edutrace.widget.ACTION_PLAY_PAUSE";
    public static final String ACTION_WIDGET_NEXT = "org.sabbir.edutrace.widget.ACTION_NEXT";
    public static final String ACTION_WIDGET_PREV = "org.sabbir.edutrace.widget.ACTION_PREV";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidgetUI(context, appWidgetManager, widgetId);
        }
    }

    private static void updateWidgetUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_study_audio);
        EduTraceMusicPlayer player = EduTraceMusicPlayer.getInstance(context);

        EduTraceMusicTrack track = player.getCurrentTrack();
        boolean isPlaying = player.isPlaying();

        if (track != null) {
            views.setTextViewText(R.id.widget_track_title, track.getDisplayTitle(isPlaying, player.getCurrentTrackIndex()));
            views.setTextViewText(R.id.widget_track_frequency, track.getDominantHzDisplay());
        } else {
            views.setTextViewText(R.id.widget_track_title, "Focus Audio • Ready");
            views.setTextViewText(R.id.widget_track_frequency, "CDN Master");
        }

        // Set play/pause icon
        int playIconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
        views.setImageViewResource(R.id.widget_btn_play_pause, playIconRes);

        // Tap on widget root opens TimerActivity
        Intent openTimerIntent = new Intent(context, TimerActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(
                context, 101, openTimerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingOpen);

        // Play/Pause button
        Intent playIntent = new Intent(context, StudyAudioWidget.class);
        playIntent.setAction(ACTION_WIDGET_PLAY_PAUSE);
        PendingIntent pendingPlay = PendingIntent.getBroadcast(
                context, 102, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, pendingPlay);

        // Next button
        Intent nextIntent = new Intent(context, StudyAudioWidget.class);
        nextIntent.setAction(ACTION_WIDGET_NEXT);
        PendingIntent pendingNext = PendingIntent.getBroadcast(
                context, 103, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_next, pendingNext);

        // Prev button
        Intent prevIntent = new Intent(context, StudyAudioWidget.class);
        prevIntent.setAction(ACTION_WIDGET_PREV);
        PendingIntent pendingPrev = PendingIntent.getBroadcast(
                context, 104, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_prev, pendingPrev);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        EduTraceMusicPlayer player = EduTraceMusicPlayer.getInstance(context);

        if (ACTION_WIDGET_PLAY_PAUSE.equals(action)) {
            player.togglePlayPause();
            refreshAllWidgets(context);
        } else if (ACTION_WIDGET_NEXT.equals(action)) {
            player.next();
            refreshAllWidgets(context);
        } else if (ACTION_WIDGET_PREV.equals(action)) {
            player.previous();
            refreshAllWidgets(context);
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            refreshAllWidgets(context);
        }
    }

    public static void refreshAllWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName comp = new ComponentName(context, StudyAudioWidget.class);
        int[] ids = mgr.getAppWidgetIds(comp);
        for (int id : ids) {
            updateWidgetUI(context, mgr, id);
        }
    }
}

