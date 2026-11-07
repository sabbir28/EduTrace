package org.sabbir.edutrace.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;

/**
 * Procedural ambient focus sound generator for EduTrace Desktop.
 * Uses pure Java Sound synthesis to create realistic study soundscapes
 * without requiring external audio files.
 */
public class AmbientSoundPlayer {

    public enum SoundType {
        OFF("Audio Off"),
        PINK_NOISE("Soft Pink Noise Sanctuary"),
        RAIN("Calming Rain & Storm"),
        BINAURAL_ALPHA("Deep Binaural Alpha (432 Hz)"),
        BINAURAL_FOCUS("40Hz Gamma Focus"),
        WHITE_NOISE("Pure White Noise"),
        METRONOME("Gentle Clock Tick");

        private final String label;
        SoundType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private static AmbientSoundPlayer instance;

    public static synchronized AmbientSoundPlayer getInstance() {
        if (instance == null) {
            instance = new AmbientSoundPlayer();
        }
        return instance;
    }

    private Thread audioThread;
    private volatile boolean isRunning = false;
    private volatile SoundType currentType = SoundType.OFF;
    private volatile float volume = 0.7f; // 0.0 to 1.0

    private AmbientSoundPlayer() {}

    public synchronized void playSound(SoundType type) {
        if (currentType == type && isRunning) return;
        stopSound();

        if (type == SoundType.OFF) {
            currentType = SoundType.OFF;
            return;
        }

        currentType = type;
        isRunning = true;

        audioThread = new Thread(() -> generateAudioStream(type), "EduTrace-AmbientAudio");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public synchronized void stopSound() {
        isRunning = false;
        currentType = SoundType.OFF;
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
    }

    public synchronized SoundType getCurrentType() {
        return currentType;
    }

    public synchronized void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
    }

    public synchronized float getVolume() {
        return volume;
    }

    private void generateAudioStream(SoundType type) {
        final float sampleRate = 44100f;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);

        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, 4096);
            line.start();

            // Setup volume control if available
            try {
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float range = gainControl.getMaximum() - gainControl.getMinimum();
                    float gain = (range * volume) + gainControl.getMinimum();
                    gainControl.setValue(gain);
                }
            } catch (Exception ignored) {}

            byte[] buffer = new byte[2048];
            Random random = new Random();

            double b0 = 0, b1 = 0, b2 = 0; // Filter states for pink noise
            long sampleIndex = 0;

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < buffer.length; i += 2) {
                    short sample = 0;
                    sampleIndex++;

                    switch (type) {
                        case PINK_NOISE: {
                            // Paul Kellet's filter for pink noise
                            double white = (random.nextDouble() * 2.0 - 1.0);
                            b0 = 0.99886 * b0 + white * 0.0555179;
                            b1 = 0.99332 * b1 + white * 0.0750759;
                            b2 = 0.96900 * b2 + white * 0.1538520;
                            double pink = b0 + b1 + b2 + white * 0.5362;
                            sample = (short) (pink * 3500.0 * volume);
                            break;
                        }
                        case RAIN: {
                            // Pink noise modulated by low frequency rumble and random raindrops
                            double white = (random.nextDouble() * 2.0 - 1.0);
                            b0 = 0.995 * b0 + white * 0.06;
                            b1 = 0.985 * b1 + white * 0.09;
                            double base = b0 + b1;
                            // Modulate with slow wave (wind/rain ebb)
                            double mod = 0.75 + 0.25 * Math.sin(2.0 * Math.PI * 0.15 * (sampleIndex / sampleRate));
                            // Occasional drop impulse
                            double drop = (random.nextDouble() > 0.9992) ? (random.nextDouble() * 2.0 - 1.0) * 8000 : 0;
                            sample = (short) ((base * 3000.0 * mod + drop) * volume);
                            break;
                        }
                        case BINAURAL_ALPHA: {
                            // 432 Hz carrier frequency modulated with 10 Hz Alpha wave (relaxed deep focus)
                            double time = sampleIndex / sampleRate;
                            double carrier = Math.sin(2.0 * Math.PI * 432.0 * time);
                            double alphaMod = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * 10.0 * time);
                            sample = (short) (carrier * alphaMod * 5000.0 * volume);
                            break;
                        }
                        case WHITE_NOISE: {
                            sample = (short) ((random.nextDouble() * 2.0 - 1.0) * 3000.0 * volume);
                            break;
                        }
                        case BINAURAL_FOCUS: {
                            // 200 Hz carrier frequency modulated with 40 Hz Gamma wave (deep concentration)
                            double time = sampleIndex / sampleRate;
                            double carrier = Math.sin(2.0 * Math.PI * 200.0 * time);
                            double gammaMod = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * 40.0 * time);
                            sample = (short) (carrier * gammaMod * 6000.0 * volume);
                            break;
                        }
                        case METRONOME: {
                            // Gentle clock tick once per second
                            long secSample = sampleIndex % ((long) sampleRate);
                            if (secSample < (sampleRate * 0.02)) {
                                double tickTime = secSample / sampleRate;
                                double tick = Math.sin(2.0 * Math.PI * 1200.0 * tickTime) * Math.exp(-tickTime * 120.0);
                                sample = (short) (tick * 10000.0 * volume);
                            } else {
                                sample = 0;
                            }
                            break;
                        }
                        default:
                            sample = 0;
                            break;
                    }

                    // Little endian 16-bit PCM
                    buffer[i] = (byte) (sample & 0xFF);
                    buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
                }

                line.write(buffer, 0, buffer.length);
            }
        } catch (Exception e) {
            // Audio output device unavailable or interrupted
        } finally {
            if (line != null) {
                try {
                    line.drain();
                    line.stop();
                    line.close();
                } catch (Exception ignored) {}
            }
        }
    }
}

