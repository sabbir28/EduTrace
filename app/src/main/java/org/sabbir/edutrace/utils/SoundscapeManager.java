package org.sabbir.edutrace.utils;

import android.content.Context;
import android.media.MediaPlayer;
import org.sabbir.edutrace.R;

public class SoundscapeManager {
    private MediaPlayer mediaPlayer;
    private Context context;
    private int currentResource = -1;

    public SoundscapeManager(Context context) {
        this.context = context;
    }

    public void playSound(int resId) {
        if (currentResource == resId) {
            stop();
            return;
        }

        stop();
        currentResource = resId;
        
        // Note: resId would usually be something like R.raw.rain
        // Since we don't have the files yet, this will fail if called with real IDs.
        // We handle this gracefully.
        try {
            mediaPlayer = MediaPlayer.create(context, resId);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playSoundscape(String soundscapeName) {
        stop();
        try {
            java.io.File audioFile = ObbManager.getFileFromObb(context, "soundscapes/" + soundscapeName + ".mp3");
            if (audioFile != null && audioFile.exists()) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentResource = -1;
    }

    public boolean isPlaying(int resId) {
        return currentResource == resId;
    }
}
