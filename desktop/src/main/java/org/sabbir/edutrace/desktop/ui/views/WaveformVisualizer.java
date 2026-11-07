package org.sabbir.edutrace.desktop.ui.views;

import org.sabbir.edutrace.desktop.EduTraceDesktop;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

/**
 * Procedural Audio Energy Waveform visualizer reproducing the Android
 * AudioEnergyWaveformScrubber from activity_timer.xml:
 * Visualizes dynamic acoustic energy bars with gold/amber gradients and playhead pulse.
 */
public class WaveformVisualizer extends JComponent {

    private static final int BAR_COUNT = 42;
    private final float[] baseHeights = new float[BAR_COUNT];
    private final float[] currentHeights = new float[BAR_COUNT];
    private boolean isPlaying = false;
    private float playheadProgress = 0.0f; // 0.0 to 1.0
    private final Timer animTimer;
    private final Random random = new Random(42);

    public WaveformVisualizer() {
        setPreferredSize(new Dimension(320, 48));
        setMinimumSize(new Dimension(200, 36));
        setOpaque(false);

        // Generate pleasant harmonic wave envelope
        for (int i = 0; i < BAR_COUNT; i++) {
            float x = (float) i / BAR_COUNT;
            float envelope = (float) (Math.sin(x * Math.PI) * 0.7 + Math.sin(x * 3 * Math.PI) * 0.15 + 0.15);
            baseHeights[i] = Math.max(0.12f, Math.min(0.95f, envelope + (random.nextFloat() * 0.2f - 0.1f)));
            currentHeights[i] = baseHeights[i] * 0.4f;
        }

        animTimer = new Timer(50, e -> {
            if (isPlaying) {
                playheadProgress += 0.005f;
                if (playheadProgress > 1.0f) playheadProgress = 0.0f;

                for (int i = 0; i < BAR_COUNT; i++) {
                    float jitter = (random.nextFloat() * 0.3f - 0.15f);
                    float target = Math.max(0.15f, Math.min(1.0f, baseHeights[i] + jitter));
                    currentHeights[i] += (target - currentHeights[i]) * 0.35f;
                }
                repaint();
            }
        });
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (playing) {
            if (!animTimer.isRunning()) animTimer.start();
        } else {
            animTimer.stop();
            for (int i = 0; i < BAR_COUNT; i++) {
                currentHeights[i] = baseHeights[i] * 0.3f;
            }
            repaint();
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        float barWidth = Math.max(3f, (float) w / BAR_COUNT - 2.5f);
        float spacing = (w - (barWidth * BAR_COUNT)) / (BAR_COUNT - 1);
        int centerY = h / 2;

        Color goldPlayed = EduTraceDesktop.COLOR_ELITE_GOLD;
        Color amberPlayed = EduTraceDesktop.COLOR_AMBER_GLOW;
        Color unplayed = new Color(0x33, 0x33, 0x48);

        for (int i = 0; i < BAR_COUNT; i++) {
            float x = i * (barWidth + spacing);
            float barH = Math.max(6f, currentHeights[i] * (h - 8));
            float y = centerY - (barH / 2f);

            float barProgress = (float) i / BAR_COUNT;
            if (isPlaying && barProgress <= playheadProgress) {
                // Played gradient
                GradientPaint gp = new GradientPaint(x, y, amberPlayed, x, y + barH, goldPlayed);
                g2.setPaint(gp);
            } else {
                g2.setColor(unplayed);
            }

            g2.fillRoundRect((int) x, (int) y, (int) barWidth, (int) barH, 4, 4);
        }

        // Draw glowing playhead thumb if playing
        if (isPlaying) {
            int playheadX = (int) (playheadProgress * w);
            g2.setColor(new Color(0xFF, 0xB8, 0x00, 180));
            g2.drawLine(playheadX, 2, playheadX, h - 2);
            g2.setColor(Color.WHITE);
            g2.fillOval(playheadX - 3, centerY - 3, 6, 6);
        }

        g2.dispose();
    }
}
