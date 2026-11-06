package org.sabbir.edutrace.utils;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sabbir.edutrace.data.cloud.CloudApiClient;
import org.sabbir.edutrace.data.models.EduTraceMusicTrack;
import org.sabbir.edutrace.widget.StudyAudioWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Singleton Music & Audio Stream Manager for EduTrace.
 * Exclusively plays CDN Byte-Range streamed audio from EduTrace Music & Audio API
 * (docs/AUDIO_MUSIC_API.md).
 * Synchronizes playback state with Study Time Tracker (TimerActivity) and Home Screen Widgets.
 */
public class EduTraceMusicPlayer implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener {

    private static final String TAG = "EduTraceMusicPlayer";
    private static volatile EduTraceMusicPlayer instance;

    public static final String ACTION_TOGGLE_PLAY = "org.sabbir.edutrace.ACTION_STUDY_AUDIO_PLAY_PAUSE";
    public static final String ACTION_NEXT_TRACK = "org.sabbir.edutrace.ACTION_STUDY_AUDIO_NEXT";
    public static final String ACTION_PREV_TRACK = "org.sabbir.edutrace.ACTION_STUDY_AUDIO_PREV";
    public static final String ACTION_STATE_CHANGED = "org.sabbir.edutrace.ACTION_STUDY_AUDIO_STATE_CHANGED";

    public interface PlaybackListener {
        void onTrackSelected(EduTraceMusicTrack track, int index);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressUpdate(int currentMs, int totalMs, float percent);
        void onWaveformUpdated(EduTraceMusicTrack track);
        void onError(String message);
    }

    private final Context appContext;
    private MediaPlayer mediaPlayer;
    private final List<EduTraceMusicTrack> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;
    private boolean isPreparing = false;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private float currentVolume = 1.0f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PlaybackListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && !isPreparing) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        int cur = mediaPlayer.getCurrentPosition();
                        int dur = mediaPlayer.getDuration();
                        float pct = dur > 0 ? (float) cur / dur : 0.0f;
                        notifyProgress(cur, dur, pct);
                    }
                } catch (IllegalStateException ignored) {}
            }
            if (isPlaying()) {
                handler.postDelayed(this, 250);
            }
        }
    };

    private EduTraceMusicPlayer(Context context) {
        this.appContext = context.getApplicationContext();
        initMediaPlayer();
    }

    public static EduTraceMusicPlayer getInstance(Context context) {
        if (instance == null) {
            synchronized (EduTraceMusicPlayer.class) {
                if (instance == null) {
                    instance = new EduTraceMusicPlayer(context);
                }
            }
        }
        return instance;
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
        }
        mediaPlayer = new MediaPlayer();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
        }
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
    }

    public void addListener(PlaybackListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            if (getCurrentTrack() != null) {
                listener.onTrackSelected(getCurrentTrack(), currentTrackIndex);
                listener.onPlaybackStateChanged(isPlaying());
                if (mediaPlayer != null) {
                    try {
                        int cur = mediaPlayer.getCurrentPosition();
                        int dur = mediaPlayer.getDuration();
                        listener.onProgressUpdate(cur, dur, dur > 0 ? (float) cur / dur : 0.0f);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void removeListener(PlaybackListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Loads playlist from EduTrace Music API (/api/music/tracks or /api/frequency-audio/tracks).
     */
    public void fetchCatalog(Runnable onComplete) {
        CloudApiClient.cloudExecutor.execute(() -> {
            String baseUrl = CloudApiClient.getServerUrl(appContext);
            Log.d(TAG, "Fetching EduTrace music catalog from: " + baseUrl);

            CloudApiClient.ApiResponse resp = CloudApiClient.sendApiRequest(appContext, "music/tracks", "GET", null, null);
            if (!resp.isSuccess() || resp.json == null) {
                // Fallback to frequency-audio/tracks
                resp = CloudApiClient.sendApiRequest(appContext, "frequency-audio/tracks", "GET", null, null);
            }

            final List<EduTraceMusicTrack> loaded = new ArrayList<>();
            if (resp.isSuccess() && resp.json != null) {
                JSONArray arr = resp.json.optJSONArray("tracks");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject tObj = arr.optJSONObject(i);
                        if (tObj != null) {
                            EduTraceMusicTrack track = EduTraceMusicTrack.fromJson(tObj, baseUrl);
                            loaded.add(track);
                        }
                    }
                }
            }

            handler.post(() -> {
                if (!loaded.isEmpty()) {
                    playlist.clear();
                    playlist.addAll(loaded);
                    Log.d(TAG, "Loaded " + playlist.size() + " EduTrace study tracks.");
                    if (currentTrackIndex < 0 && !playlist.isEmpty()) {
                        currentTrackIndex = 0;
                        fetchWaveformForTrack(playlist.get(0));
                    }
                } else if (playlist.isEmpty()) {
                    // Create default offline ambient track template if server is unreachable
                    createOfflineFallbackTracks(baseUrl);
                }
                updateWidgets();
                if (onComplete != null) onComplete.run();
            });
        });
    }

    private void createOfflineFallbackTracks(String baseUrl) {
        // Fallback default focus tracks matching documentation spec
        try {
            JSONObject obj1 = new JSONObject();
            obj1.put("track_id", "5082adf5-41f5-430d-acfa-6763168131c3");
            obj1.put("original_filename", "ambient_session.mp3");
            obj1.put("format", "mp3");
            obj1.put("sha256", "dbf01c416759b498e0d9baf7b8a0f4cc4010420755b7f20f63e32a7a3435d949");
            
            JSONObject freq = new JSONObject();
            freq.put("dominant_frequency_hz", 440.0);
            freq.put("spectral_centroid_hz", 1280.4);
            freq.put("spectrum_bins", 128);
            obj1.put("frequency_profile", freq);

            playlist.add(EduTraceMusicTrack.fromJson(obj1, baseUrl));
            currentTrackIndex = 0;
        } catch (Exception ignored) {}
    }

    /**
     * Asynchronously fetches detailed AI frequency and energy visualization
     * (/api/ai/visualize?track_id=<id>&bars=48) for the waveform scrubber.
     */
    public void fetchWaveformForTrack(EduTraceMusicTrack track) {
        if (track == null || track.getTrackId() == null || track.getTrackId().isEmpty()) return;

        CloudApiClient.cloudExecutor.execute(() -> {
            String endpoint = "ai/visualize?track_id=" + track.getTrackId() + "&bars=48";
            CloudApiClient.ApiResponse resp = CloudApiClient.sendApiRequest(appContext, endpoint, "GET", null, null);
            if (resp.isSuccess() && resp.json != null) {
                track.updateWithVisualizerJson(resp.json);
                handler.post(() -> {
                    for (PlaybackListener l : listeners) {
                        l.onWaveformUpdated(track);
                    }
                });
            }
        });
    }

    public synchronized void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentTrackIndex = index;
        EduTraceMusicTrack track = playlist.get(index);

        try {
            initMediaPlayer();
            isPreparing = true;
            notifyTrackSelected(track, index);
            notifyPlaybackState(true);

            String streamUrl = track.getStreamUrl();
            Log.d(TAG, "Streaming audio from: " + streamUrl);
            mediaPlayer.setDataSource(appContext, Uri.parse(streamUrl));
            mediaPlayer.setVolume(currentVolume, currentVolume);
            mediaPlayer.prepareAsync();

            fetchWaveformForTrack(track);
            updateWidgets();
        } catch (Exception e) {
            isPreparing = false;
            Log.e(TAG, "Failed to stream audio: " + e.getMessage(), e);
            notifyError("Cannot stream audio track: " + e.getMessage());
            notifyPlaybackState(false);
        }
    }

    public synchronized void togglePlayPause() {
        if (mediaPlayer == null || currentTrackIndex < 0) {
            if (!playlist.isEmpty()) {
                playTrack(0);
            } else {
                fetchCatalog(() -> {
                    if (!playlist.isEmpty()) playTrack(0);
                });
            }
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                handler.removeCallbacks(progressRunnable);
                notifyPlaybackState(false);
            } else {
                if (isPreparing) return;
                mediaPlayer.start();
                handler.post(progressRunnable);
                notifyPlaybackState(true);
            }
            updateWidgets();
        } catch (Exception e) {
            Log.e(TAG, "Error in togglePlayPause: " + e.getMessage());
        }
    }

    public synchronized void pause() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                handler.removeCallbacks(progressRunnable);
                notifyPlaybackState(false);
                updateWidgets();
            }
        } catch (Exception ignored) {}
    }

    public synchronized void resume() {
        try {
            if (mediaPlayer != null && !mediaPlayer.isPlaying() && !isPreparing) {
                mediaPlayer.start();
                handler.post(progressRunnable);
                notifyPlaybackState(true);
                updateWidgets();
            }
        } catch (Exception ignored) {}
    }

    public synchronized void next() {
        if (playlist.isEmpty()) return;
        int nextIndex;
        if (isShuffle) {
            nextIndex = new Random().nextInt(playlist.size());
        } else {
            nextIndex = (currentTrackIndex + 1) % playlist.size();
        }
        playTrack(nextIndex);
    }

    public synchronized void previous() {
        if (playlist.isEmpty()) return;
        try {
            if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
                mediaPlayer.seekTo(0);
                return;
            }
        } catch (Exception ignored) {}

        int prevIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        playTrack(prevIndex);
    }

    public synchronized void seekTo(int positionMs) {
        if (mediaPlayer != null && !isPreparing) {
            try {
                mediaPlayer.seekTo(positionMs);
                int dur = mediaPlayer.getDuration();
                notifyProgress(positionMs, dur, dur > 0 ? (float) positionMs / dur : 0.0f);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void seekToPercent(float percent) {
        if (mediaPlayer != null && !isPreparing) {
            try {
                int dur = mediaPlayer.getDuration();
                if (dur > 0) {
                    int target = (int) (percent * dur);
                    seekTo(target);
                }
            } catch (Exception ignored) {}
        }
    }

    public void setVolume(float volume) {
        this.currentVolume = Math.max(0.0f, Math.min(1.0f, volume));
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(currentVolume, currentVolume);
            } catch (Exception ignored) {}
        }
    }

    public void setShuffle(boolean shuffle) {
        this.isShuffle = shuffle;
    }

    public boolean isShuffle() { return isShuffle; }

    public void setRepeat(boolean repeat) {
        this.isRepeat = repeat;
    }

    public boolean isRepeat() { return isRepeat; }

    public boolean isPlaying() {
        if (mediaPlayer == null || isPreparing) return false;
        try {
            return mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public EduTraceMusicTrack getCurrentTrack() {
        if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
            return playlist.get(currentTrackIndex);
        }
        return null;
    }

    public int getCurrentTrackIndex() { return currentTrackIndex; }
    public List<EduTraceMusicTrack> getPlaylist() { return Collections.unmodifiableList(playlist); }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPreparing = false;
        try {
            mp.start();
            handler.post(progressRunnable);
            notifyPlaybackState(true);
            updateWidgets();
        } catch (Exception e) {
            Log.e(TAG, "Error starting after prepared: " + e.getMessage());
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        handler.removeCallbacks(progressRunnable);
        if (isRepeat) {
            seekTo(0);
            resume();
        } else {
            next();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPreparing = false;
        handler.removeCallbacks(progressRunnable);
        Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        notifyError("Streaming error encountered. Retrying...");
        notifyPlaybackState(false);
        updateWidgets();
        return true;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        // Partial byte-range buffering indicator
    }

    private void notifyTrackSelected(EduTraceMusicTrack track, int index) {
        for (PlaybackListener l : listeners) {
            l.onTrackSelected(track, index);
        }
    }

    private void notifyPlaybackState(boolean playing) {
        for (PlaybackListener l : listeners) {
            l.onPlaybackStateChanged(playing);
        }
    }

    private void notifyProgress(int cur, int dur, float pct) {
        for (PlaybackListener l : listeners) {
            l.onProgressUpdate(cur, dur, pct);
        }
    }

    private void notifyError(String msg) {
        for (PlaybackListener l : listeners) {
            l.onError(msg);
        }
    }

    public void updateWidgets() {
        try {
            Intent intent = new Intent(appContext, StudyAudioWidget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            AppWidgetManager mgr = AppWidgetManager.getInstance(appContext);
            ComponentName cn = new ComponentName(appContext, StudyAudioWidget.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            appContext.sendBroadcast(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed updating audio widgets: " + e.getMessage());
        }
    }

    public void release() {
        handler.removeCallbacks(progressRunnable);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        listeners.clear();
    }
}

