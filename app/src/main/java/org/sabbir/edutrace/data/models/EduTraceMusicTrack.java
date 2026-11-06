package org.sabbir.edutrace.data.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model strictly matching the EduTrace Audio, Music & CDN Streaming API structure
 * defined in docs/AUDIO_MUSIC_API.md.
 */
public class EduTraceMusicTrack implements Serializable {
    private String trackId;
    private String originalFilename;
    private String format;
    private String encoding;
    private String storagePath;
    private long originalSizeBytes;
    private long encodedSizeBytes;
    private String sha256;
    private String status;
    private String createdAt;
    private String streamUrl;

    // Acoustic & Spectral Intelligence Profile
    private double dominantFrequencyHz;
    private double spectralCentroidHz;
    private int spectrumBinsCount;
    private float[] spectrumDb;
    private Map<String, Double> frequencyBandsDb = new HashMap<>();

    // Per-second time-series energy trend for waveform visualization & scrubbing
    private float[] energyTrend;
    private double durationSeconds;

    public EduTraceMusicTrack() {
    }

    /**
     * Factory constructor parsing canonical track JSON from /api/music/tracks
     * or /api/frequency-audio/tracks.
     */
    public static EduTraceMusicTrack fromJson(JSONObject obj, String serverBaseUrl) {
        EduTraceMusicTrack track = new EduTraceMusicTrack();
        if (obj == null) return track;

        track.trackId = obj.optString("track_id", "");
        track.originalFilename = obj.optString("original_filename", "Focus Audio");
        track.format = obj.optString("format", "mp3");
        track.encoding = obj.optString("encoding", "base64");
        track.storagePath = obj.optString("storage_path", "");
        track.originalSizeBytes = obj.optLong("original_size_bytes", 0);
        track.encodedSizeBytes = obj.optLong("encoded_size_bytes", 0);
        track.sha256 = obj.optString("sha256", "");
        track.status = obj.optString("status", "uploaded");
        track.createdAt = obj.optString("created_at", "");

        // Build direct CDN stream URL: e.g. https://edutrace-theta.vercel.app/music/<track_id>
        String baseHost = serverBaseUrl;
        if (baseHost != null && baseHost.endsWith("/api")) {
            baseHost = baseHost.substring(0, baseHost.length() - 4);
        }
        if (baseHost == null || baseHost.trim().isEmpty()) {
            baseHost = "https://edutrace-theta.vercel.app";
        }
        track.streamUrl = baseHost + "/music/" + track.trackId;

        // Parse Frequency Profile (8,192-point FFT)
        JSONObject freqProfile = obj.optJSONObject("frequency_profile");
        if (freqProfile != null) {
            track.dominantFrequencyHz = freqProfile.optDouble("dominant_frequency_hz", 0.0);
            track.spectralCentroidHz = freqProfile.optDouble("spectral_centroid_hz", 0.0);
            track.spectrumBinsCount = freqProfile.optInt("spectrum_bins", 128);

            JSONArray specArray = freqProfile.optJSONArray("spectrum_db");
            if (specArray != null && specArray.length() > 0) {
                track.spectrumDb = new float[specArray.length()];
                for (int i = 0; i < specArray.length(); i++) {
                    track.spectrumDb[i] = (float) specArray.optDouble(i, 0.0);
                }
            }

            JSONObject bandsObj = freqProfile.optJSONObject("frequency_bands_db");
            if (bandsObj != null) {
                java.util.Iterator<String> keys = bandsObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    track.frequencyBandsDb.put(key, bandsObj.optDouble(key, 0.0));
                }
            }
        }

        // Initialize default or synthesized energy waveform from spectrum if not yet loaded from AI visualizer
        track.energyTrend = synthesizeWaveformFromAcousticData(track);

        return track;
    }

    /**
     * Updates track with deep time-series visualizer data from /api/ai/visualize endpoint.
     */
    public void updateWithVisualizerJson(JSONObject visJson) {
        if (visJson == null) return;

        this.durationSeconds = visJson.optDouble("duration_seconds", 0.0);

        JSONObject summary = visJson.optJSONObject("overall_summary");
        if (summary != null) {
            JSONArray energyArray = summary.optJSONArray("energy_trend");
            if (energyArray != null && energyArray.length() > 0) {
                this.energyTrend = new float[energyArray.length()];
                for (int i = 0; i < energyArray.length(); i++) {
                    this.energyTrend[i] = (float) energyArray.optDouble(i, 0.2);
                }
            }
        }

        // Fallback: extract from seconds_data if energy_trend was absent
        if ((this.energyTrend == null || this.energyTrend.length == 0) && visJson.has("seconds_data")) {
            JSONArray secData = visJson.optJSONArray("seconds_data");
            if (secData != null && secData.length() > 0) {
                this.energyTrend = new float[secData.length()];
                for (int i = 0; i < secData.length(); i++) {
                    JSONObject sec = secData.optJSONObject(i);
                    if (sec != null) {
                        float rms = (float) sec.optDouble("rms_amplitude", 0.3);
                        float peak = (float) sec.optDouble("peak_amplitude", 0.5);
                        this.energyTrend[i] = (rms + peak) / 2.0f;
                    } else {
                        this.energyTrend[i] = 0.25f;
                    }
                }
            }
        }

        // Ensure minimum bars for smooth waveform scrubber
        if (this.energyTrend == null || this.energyTrend.length < 8) {
            this.energyTrend = synthesizeWaveformFromAcousticData(this);
        }
    }

    /**
     * Synthesizes 48 normalized energy waveform bars from spectrum dB or deterministic audio seeds,
     * ensuring immediate smooth scrubber visualization even before remote AI visualize finishes.
     */
    private static float[] synthesizeWaveformFromAcousticData(EduTraceMusicTrack track) {
        int barCount = 48;
        float[] bars = new float[barCount];

        if (track.spectrumDb != null && track.spectrumDb.length >= barCount) {
            // Normalize spectrum dB (typically -60 dB to 0 dB) to 0.12 - 0.95
            for (int i = 0; i < barCount; i++) {
                int specIndex = (int) ((float) i / barCount * track.spectrumDb.length);
                float db = track.spectrumDb[specIndex];
                float norm = Math.max(0.12f, Math.min(1.0f, (db + 70.0f) / 70.0f));
                bars[i] = norm;
            }
        } else {
            // Deterministic musical waveform envelope based on dominant frequency
            double baseFreq = track.dominantFrequencyHz > 0 ? track.dominantFrequencyHz : 440.0;
            for (int i = 0; i < barCount; i++) {
                double phase = (double) i / barCount;
                double wave = 0.45 + 0.35 * Math.sin(phase * Math.PI * 4 + (baseFreq % 7))
                        + 0.15 * Math.cos(phase * Math.PI * 8);
                bars[i] = (float) Math.max(0.15, Math.min(0.95, wave));
            }
        }
        return bars;
    }

    // Getters and Setters
    public String getTrackId() { return trackId; }
    public String getOriginalFilename() { return originalFilename != null ? originalFilename : "Focus Track"; }
    public String getFormat() { return format; }
    public String getSha256() { return sha256; }
    public String getShortSha256() {
        if (sha256 != null && sha256.length() > 8) {
            return sha256.substring(0, 8);
        }
        return sha256 != null ? sha256 : "Verified";
    }
    public long getOriginalSizeBytes() { return originalSizeBytes; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String url) { this.streamUrl = url; }
    public double getDominantFrequencyHz() { return dominantFrequencyHz; }
    public double getSpectralCentroidHz() { return spectralCentroidHz; }
    public float[] getEnergyTrend() { return energyTrend; }
    public void setEnergyTrend(float[] trend) { this.energyTrend = trend; }
    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double sec) { this.durationSeconds = sec; }

    public String getDominantHzDisplay() {
        if (dominantFrequencyHz > 0) {
            return String.format(java.util.Locale.US, "%.0f Hz", dominantFrequencyHz);
        }
        return "CDN Master";
    }

    public String getCentroidDisplay() {
        if (spectralCentroidHz > 0) {
            return String.format(java.util.Locale.US, "%.0f Hz", spectralCentroidHz);
        }
        return "Acoustic";
    }
}

