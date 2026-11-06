package org.sabbir.edutrace.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Interactive Audio Energy Waveform Scrubber.
 * Visualizes 8,192-point FFT acoustic energy bars from EduTrace server,
 * renders played/unplayed gradients, playhead glow thumb, and allows direct
 * touch/drag scrubbing with seek callbacks.
 */
public class AudioEnergyWaveformScrubber extends View {

    public interface OnScrubListener {
        void onScrubStart();
        void onScrubbing(float progressPercent, int estimatedMs);
        void onScrubStop(float progressPercent, int targetMs);
    }

    private Paint playedPaint;
    private Paint unplayedPaint;
    private Paint playheadPaint;
    private Paint playheadGlowPaint;
    private Paint thumbPaint;

    private float[] waveformEnergy;
    private float currentProgress = 0.0f; // 0.0 to 1.0
    private int currentMs = 0;
    private int totalDurationMs = 0;

    private boolean isPlaying = false;
    private boolean isScrubbing = false;
    private float scrubProgress = 0.0f;

    private OnScrubListener scrubListener;
    private RectF barRect = new RectF();

    // Pulse animation state
    private float pulsePhase = 0.0f;
    private long lastAnimTime = 0;

    public AudioEnergyWaveformScrubber(Context context) {
        super(context);
        init();
    }

    public AudioEnergyWaveformScrubber(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioEnergyWaveformScrubber(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playedPaint.setStyle(Paint.Style.FILL);

        unplayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unplayedPaint.setStyle(Paint.Style.FILL);
        unplayedPaint.setColor(0x4094A3B8); // Glassy slate unplayed bars

        playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playheadPaint.setStyle(Paint.Style.STROKE);
        playheadPaint.setStrokeWidth(3.0f);
        playheadPaint.setColor(0xFFFFFFFF);

        playheadGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playheadGlowPaint.setStyle(Paint.Style.FILL);
        playheadGlowPaint.setColor(0x55FFB800); // Amber glow aura

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(0xFFFFB800); // Elite Gold thumb

        // Default aesthetic fallback energy wave
        generateDefaultWaveform(48);
    }

    public void setWaveformData(float[] energyTrend) {
        if (energyTrend != null && energyTrend.length > 0) {
            this.waveformEnergy = energyTrend;
        } else {
            generateDefaultWaveform(48);
        }
        invalidate();
    }

    public void setProgress(int currentMs, int totalMs) {
        if (isScrubbing) return; // Don't override while user is actively scrubbing
        this.currentMs = currentMs;
        this.totalDurationMs = totalMs;
        if (totalMs > 0) {
            this.currentProgress = Math.max(0.0f, Math.min(1.0f, (float) currentMs / totalMs));
        } else {
            this.currentProgress = 0.0f;
        }
        postInvalidateOnAnimation();
    }

    public void setProgressPercent(float percent) {
        if (isScrubbing) return;
        this.currentProgress = Math.max(0.0f, Math.min(1.0f, percent));
        postInvalidateOnAnimation();
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        postInvalidateOnAnimation();
    }

    public void setOnScrubListener(OnScrubListener listener) {
        this.scrubListener = listener;
    }

    private void generateDefaultWaveform(int count) {
        waveformEnergy = new float[count];
        for (int i = 0; i < count; i++) {
            double p = (double) i / count;
            waveformEnergy[i] = (float) (0.25 + 0.5 * Math.sin(p * Math.PI) * Math.sin(p * Math.PI * 5));
            waveformEnergy[i] = Math.max(0.15f, Math.min(0.95f, waveformEnergy[i]));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            // Gradient for played waveform (Electric Sky Blue to Elite Amber Gold)
            Shader playedShader = new LinearGradient(
                    0, 0, 0, h,
                    new int[]{0xFF38BDF8, 0xFFFFB800},
                    new float[]{0.0f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            playedPaint.setShader(playedShader);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || waveformEnergy == null || waveformEnergy.length == 0) return;

        float activeProgress = isScrubbing ? scrubProgress : currentProgress;
        float playheadX = activeProgress * width;

        int barCount = waveformEnergy.length;
        float totalSpacing = 2.5f; // gap in px
        float availableWidth = width - (barCount - 1) * totalSpacing;
        float barWidth = Math.max(2.0f, availableWidth / barCount);

        long now = SystemClock.uptimeMillis();
        if (isPlaying) {
            long dt = (lastAnimTime > 0) ? (now - lastAnimTime) : 16;
            pulsePhase = (pulsePhase + (dt / 1000.0f) * 3.5f) % (float) (Math.PI * 2);
        }
        lastAnimTime = now;

        // Draw waveform bars
        float curX = 0f;
        int activeBarIndex = (int) (activeProgress * barCount);

        for (int i = 0; i < barCount; i++) {
            float energy = waveformEnergy[i];

            // Subtly pulse active bar near playhead when playing
            if (isPlaying && Math.abs(i - activeBarIndex) <= 1) {
                float pulse = (float) (0.15 * Math.sin(pulsePhase));
                energy = Math.min(1.0f, energy + pulse);
            }

            float barH = Math.max(height * 0.12f, energy * height * 0.85f);
            float top = (height - barH) / 2.0f;
            float bottom = top + barH;

            barRect.set(curX, top, curX + barWidth, bottom);
            float cornerRadius = barWidth / 2.0f;

            // Bar is played if its center is <= playheadX
            float barCenter = curX + barWidth / 2.0f;
            if (barCenter <= playheadX) {
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, playedPaint);
            } else {
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, unplayedPaint);
            }

            curX += barWidth + totalSpacing;
        }

        // Draw Playhead line and glowing thumb
        playheadX = Math.max(4.0f, Math.min(width - 4.0f, playheadX));
        
        // Playhead vertical line
        canvas.drawLine(playheadX, 4.0f, playheadX, height - 4.0f, playheadPaint);

        // Glow aura & thumb circle
        float thumbY = height / 2.0f;
        float auraRadius = isScrubbing ? 14.0f : 10.0f;
        float thumbRadius = isScrubbing ? 6.5f : 4.5f;

        canvas.drawCircle(playheadX, thumbY, auraRadius, playheadGlowPaint);
        canvas.drawCircle(playheadX, thumbY, thumbRadius, thumbPaint);

        if (isPlaying) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float x = event.getX();
        float width = getWidth();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                isScrubbing = true;
                scrubProgress = Math.max(0.0f, Math.min(1.0f, x / width));
                if (scrubListener != null) {
                    scrubListener.onScrubStart();
                    scrubListener.onScrubbing(scrubProgress, (int) (scrubProgress * totalDurationMs));
                }
                invalidate();
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isScrubbing) {
                    scrubProgress = Math.max(0.0f, Math.min(1.0f, x / width));
                    if (scrubListener != null) {
                        scrubListener.onScrubbing(scrubProgress, (int) (scrubProgress * totalDurationMs));
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (isScrubbing) {
                    isScrubbing = false;
                    currentProgress = Math.max(0.0f, Math.min(1.0f, x / width));
                    int targetMs = (int) (currentProgress * totalDurationMs);
                    if (scrubListener != null) {
                        scrubListener.onScrubStop(currentProgress, targetMs);
                    }
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                }
                return true;
        }

        return super.onTouchEvent(event);
    }
}

