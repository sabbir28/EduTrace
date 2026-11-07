package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.EduTraceDesktop;
import org.sabbir.edutrace.desktop.audio.AmbientSoundPlayer;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudAuthManager;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudSessionManager;
import org.sabbir.edutrace.desktop.data.DataManager;
import org.sabbir.edutrace.desktop.data.DesktopQuoteManager;
import org.sabbir.edutrace.desktop.data.DesktopStudySession;
import org.sabbir.edutrace.desktop.ui.views.WaveformVisualizer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * EduTrace Desktop Study Timer.
 * Direct 1:1 port of Android TimerActivity and activity_timer.xml:
 * - Header Card with Subject selector, Live Status Badge (FOCUSING / ON BREAK / PAUSED), and Cloud Sync status.
 * - Central Square Timer Card with Flow Aura glow effect, high-contrast clock, and 6px slim gold progress bar.
 * - EduTrace Focus Audio & Waveform Widget with procedural audio synthesis and acoustic visualizer bars.
 * - Real-Time Intelligence Card with session comparison metrics, today's subject total, and dynamic flow quotes.
 * - Bottom Action Bar with Break/Resume, Distraction (+1), Primary Start/Pause, and End & Save Session.
 */
public class StudyTimerTab extends JPanel {

    public enum TimerMode {
        STOPWATCH("⏱ Stopwatch (Count Up)", 0, false),
        POMODORO_WORK("🍅 Pomodoro (25m)", 25 * 60, true),
        SHORT_BREAK("☕ Short Break (5m)", 5 * 60, true),
        LONG_BREAK("🌴 Long Break (15m)", 15 * 60, true);

        private final String label;
        private final int defaultSeconds;
        private final boolean isCountdown;

        TimerMode(String label, int defaultSeconds, boolean isCountdown) {
            this.label = label;
            this.defaultSeconds = defaultSeconds;
            this.isCountdown = isCountdown;
        }

        public String getLabel() { return label; }
        public int getDefaultSeconds() { return defaultSeconds; }
        public boolean isCountdown() { return isCountdown; }
    }

    private final DataManager dataManager;
    private final AmbientSoundPlayer soundPlayer;
    private final DesktopQuoteManager quoteManager;
    private final Runnable onSessionSavedCallback;

    // Timer State
    private TimerMode currentMode = TimerMode.STOPWATCH;
    private int secondsRemaining;
    private int secondsElapsed = 0;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private long sessionStartMillis = 0;
    private long breakStartTime = 0;
    private long totalBreakTimeMillis = 0;
    private int breakCount = 0;
    private int distractionCount = 0;

    // Flow Aura Intensity (0 = none, 1 = subtle gold, 2 = amber, 3 = deep flow)
    private int flowLevel = 0;

    // UI Components
    private JLabel tvSubjectName;
    private JComboBox<String> cbSubjectSelector;
    private JLabel tvStatus;
    private JLabel tvCloudStatus;

    // Timer Card
    private JPanel timerWrapperCard;
    private JLabel tvTimerDisplay;
    private JLabel tvModeSubtitle;
    private JProgressBar timerProgressBar;
    private ButtonGroup modeButtonGroup;

    // Music / Audio Card
    private JPanel cardStudyMusic;
    private JLabel tvMusicTrackTitle;
    private JLabel tvMusicStatusBadge;
    private WaveformVisualizer waveformVisualizer;
    private JButton btnMusicPlayPause;
    private JSlider musicVolumeSlider;

    // Real-Time Intelligence
    private JLabel tvMetricSessionElapsed;
    private JLabel tvMetricSubjectToday;
    private JLabel tvMetricDailyTotal;
    private JLabel tvMetricDistractionsBreaks;
    private JProgressBar comparisonProgressBar;
    private JLabel tvQuoteText;

    // Bottom Action Bar
    private JButton btnBreak;
    private JButton btnDistracted;
    private JButton btnStartPause;
    private JButton btnEndSession;
    private JButton btnReset;

    private Timer swingTimer;
    private long lastQuoteUpdateTime = 0;

    public StudyTimerTab(Runnable onSessionSavedCallback) {
        this.dataManager = DataManager.getInstance();
        this.soundPlayer = AmbientSoundPlayer.getInstance();
        this.quoteManager = new DesktopQuoteManager();
        this.onSessionSavedCallback = onSessionSavedCallback;
        this.secondsRemaining = currentMode.getDefaultSeconds();

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 24, 20, 24));

        initUI();
        initTimerLoop();
        updateIntelligenceMetrics();
    }

    private void initUI() {
        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setOpaque(false);

        // 1. Top Header Glass Card (Matching activity_timer.xml @id/headerCard)
        scrollContent.add(createTopHeaderCard());
        scrollContent.add(Box.createVerticalStrut(16));

        // 2. Central Square Timer Card (Matching activity_timer.xml @id/timerWrapper & auraView)
        scrollContent.add(createCentralTimerCard());
        scrollContent.add(Box.createVerticalStrut(16));

        // 3. EduTrace Study Focus Audio & Waveform Widget (Matching @id/cardStudyMusic)
        cardStudyMusic = createAudioWaveformCard();
        scrollContent.add(cardStudyMusic);
        scrollContent.add(Box.createVerticalStrut(16));

        // 4. Real-Time Intelligence Card (Matching @id/intelligenceCard & comparisonBars)
        scrollContent.add(createIntelligenceCard());
        scrollContent.add(Box.createVerticalStrut(16));

        // Wrap in smooth scroll pane
        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // 5. Bottom Action Bar Glass Card (Matching activity_timer.xml @id/bottomBarCard)
        add(createBottomActionBar(), BorderLayout.SOUTH);
    }

    /**
     * Top Header Card matching Android activity_timer.xml headerCard.
     */
    private JPanel createTopHeaderCard() {
        JPanel card = createGlassPanel(18);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Left: Subject Icon, Quick Switcher, and Status subtitle
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JPanel subjectRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        subjectRow.setOpaque(false);

        JLabel icon = new JLabel("📚");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

        cbSubjectSelector = new JComboBox<>();
        cbSubjectSelector.setEditable(true);
        reloadSubjects();

        String lastSubject = dataManager.getData().lastSelectedSubject != null ?
                dataManager.getData().lastSelectedSubject : "Data Structures";
        cbSubjectSelector.setSelectedItem(lastSubject);
        cbSubjectSelector.setFont(new Font("SansSerif", Font.BOLD, 16));
        cbSubjectSelector.setForeground(Color.WHITE);
        cbSubjectSelector.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        cbSubjectSelector.setPreferredSize(new Dimension(240, 32));
        cbSubjectSelector.addActionListener(e -> {
            String sel = (String) cbSubjectSelector.getSelectedItem();
            if (sel != null && !sel.trim().isEmpty()) {
                dataManager.getData().lastSelectedSubject = sel.trim();
                dataManager.saveData();
                updateIntelligenceMetrics();
            }
        });

        subjectRow.add(icon);
        subjectRow.add(cbSubjectSelector);

        // Status Subtitle (FOCUSING / ON BREAK / PAUSED)
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusRow.setOpaque(false);

        tvStatus = new JLabel("READY TO FOCUS");
        tvStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        tvStatus.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        statusRow.add(Box.createHorizontalStrut(28)); // Indent to align with subject combo
        statusRow.add(tvStatus);

        left.add(subjectRow);
        left.add(statusRow);

        // Right: Ambient Audio quick button + Cloud sync badge
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        right.setOpaque(false);

        JButton btnToggleAudioCard = new JButton("🎧 Focus Audio");
        btnToggleAudioCard.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnToggleAudioCard.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnToggleAudioCard.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnToggleAudioCard.setFocusPainted(false);
        btnToggleAudioCard.addActionListener(e -> {
            cardStudyMusic.setVisible(!cardStudyMusic.isVisible());
            revalidate();
            repaint();
        });

        tvCloudStatus = new JLabel(DesktopCloudAuthManager.getInstance().isLoggedIn() ?
                "● Cloud Synchronized" : "○ Local Vault");
        tvCloudStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        tvCloudStatus.setForeground(DesktopCloudAuthManager.getInstance().isLoggedIn() ?
                EduTraceDesktop.COLOR_STATUS_BREAK : EduTraceDesktop.COLOR_TEXT_MUTED);

        right.add(btnToggleAudioCard);
        right.add(tvCloudStatus);

        card.add(left, BorderLayout.WEST);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    /**
     * Central Square Timer Card with Flow Aura glow effect.
     */
    private JPanel createCentralTimerCard() {
        timerWrapperCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Draw Flow Aura background glow if focusing
                if (isRunning && !isPaused && flowLevel > 0) {
                    float alpha = (flowLevel == 1) ? 0.18f : (flowLevel == 2 ? 0.32f : 0.50f);
                    Color glowColor = (flowLevel == 1) ? EduTraceDesktop.COLOR_ELITE_GOLD :
                            (flowLevel == 2 ? EduTraceDesktop.COLOR_AMBER_GLOW : new Color(0xFA, 0x8C, 0x16));

                    for (int r = 12; r >= 1; r -= 3) {
                        float ringAlpha = (alpha / 12f) * r;
                        g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), (int) (ringAlpha * 255)));
                        g2.fillRoundRect(8 - r, 8 - r, w - 16 + (r * 2), h - 16 + (r * 2), 28 + r, 28 + r);
                    }
                }

                // Card surface
                g2.setColor(EduTraceDesktop.COLOR_CARD_SURFACE);
                g2.fillRoundRect(8, 8, w - 16, h - 16, 26, 26);

                // Card border
                Color borderColor = (isRunning && !isPaused) ?
                        (flowLevel > 0 ? EduTraceDesktop.COLOR_ELITE_GOLD : EduTraceDesktop.COLOR_STATUS_FOCUS) :
                        EduTraceDesktop.COLOR_CARD_BORDER;
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(8, 8, w - 17, h - 17, 26, 26);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        timerWrapperCard.setOpaque(false);
        timerWrapperCard.setLayout(new BoxLayout(timerWrapperCard, BoxLayout.Y_AXIS));
        timerWrapperCard.setBorder(new EmptyBorder(22, 28, 22, 28));
        timerWrapperCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 1. Mode Switcher Segmented Pills Row
        JPanel modePillRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        modePillRow.setOpaque(false);

        modeButtonGroup = new ButtonGroup();
        for (TimerMode mode : TimerMode.values()) {
            JToggleButton pill = new JToggleButton(mode.getLabel(), mode == currentMode);
            pill.setFont(new Font("SansSerif", Font.BOLD, 12));
            pill.setForeground(mode == currentMode ? Color.BLACK : EduTraceDesktop.COLOR_TEXT_SECONDARY);
            pill.setBackground(mode == currentMode ? EduTraceDesktop.COLOR_ELITE_GOLD : EduTraceDesktop.COLOR_SLATE_DARK);
            pill.setFocusPainted(false);
            pill.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(EduTraceDesktop.COLOR_CARD_BORDER, 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));

            pill.addActionListener(e -> switchTimerMode(mode));
            modeButtonGroup.add(pill);
            modePillRow.add(pill);
        }

        // 2. Subtitle Label
        tvModeSubtitle = new JLabel("DEEP FOCUS & STOPWATCH");
        tvModeSubtitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        tvModeSubtitle.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        tvModeSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 3. Huge Digital Timer Display (Matching tvTimer in activity_timer.xml)
        tvTimerDisplay = new JLabel("00:00:00");
        tvTimerDisplay.setFont(new Font("SansSerif", Font.BOLD, 68));
        tvTimerDisplay.setForeground(Color.WHITE);
        tvTimerDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 4. Sleek 6px Linear Progress Bar at bottom (Matching @id/timerProgress)
        timerProgressBar = new JProgressBar(0, 100);
        timerProgressBar.setValue(0);
        timerProgressBar.setPreferredSize(new Dimension(340, 6));
        timerProgressBar.setMaximumSize(new Dimension(420, 6));
        timerProgressBar.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        timerProgressBar.setBackground(new Color(0x24, 0x24, 0x36));
        timerProgressBar.setBorder(null);
        timerProgressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerWrapperCard.add(modePillRow);
        timerWrapperCard.add(Box.createVerticalStrut(14));
        timerWrapperCard.add(tvModeSubtitle);
        timerWrapperCard.add(Box.createVerticalStrut(10));
        timerWrapperCard.add(tvTimerDisplay);
        timerWrapperCard.add(Box.createVerticalStrut(16));
        timerWrapperCard.add(timerProgressBar);

        return timerWrapperCard;
    }

    /**
     * Focus Audio & Waveform Widget matching activity_timer.xml cardStudyMusic.
     */
    private JPanel createAudioWaveformCard() {
        JPanel card = createGlassPanel(20);
        card.setLayout(new BorderLayout(14, 12));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Track Header Row
        JPanel headerRow = new JPanel(new BorderLayout(10, 0));
        headerRow.setOpaque(false);

        JPanel infoLeft = new JPanel();
        infoLeft.setLayout(new BoxLayout(infoLeft, BoxLayout.Y_AXIS));
        infoLeft.setOpaque(false);

        tvMusicTrackTitle = new JLabel("🎵 Deep Binaural Alpha (432 Hz)");
        tvMusicTrackTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        tvMusicTrackTitle.setForeground(Color.WHITE);

        tvMusicStatusBadge = new JLabel("Dominant: 432 Hz • Pure Synthetic Audio • Continuous Flow");
        tvMusicStatusBadge.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tvMusicStatusBadge.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        infoLeft.add(tvMusicTrackTitle);
        infoLeft.add(Box.createVerticalStrut(2));
        infoLeft.add(tvMusicStatusBadge);

        JComboBox<AmbientSoundPlayer.SoundType> cbAtmosphere = new JComboBox<>(AmbientSoundPlayer.SoundType.values());
        cbAtmosphere.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbAtmosphere.setBackground(EduTraceDesktop.COLOR_SLATE_DARK);
        cbAtmosphere.setForeground(Color.WHITE);
        cbAtmosphere.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AmbientSoundPlayer.SoundType) {
                    setText(((AmbientSoundPlayer.SoundType) value).getLabel());
                }
                return this;
            }
        });
        cbAtmosphere.addActionListener(e -> {
            AmbientSoundPlayer.SoundType sel = (AmbientSoundPlayer.SoundType) cbAtmosphere.getSelectedItem();
            if (sel != null) {
                tvMusicTrackTitle.setText("🎵 " + sel.getLabel());
                if (sel == AmbientSoundPlayer.SoundType.OFF) {
                    soundPlayer.stopSound();
                    waveformVisualizer.setPlaying(false);
                    btnMusicPlayPause.setText("▶ Play");
                    btnMusicPlayPause.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
                } else {
                    soundPlayer.playSound(sel);
                    waveformVisualizer.setPlaying(true);
                    btnMusicPlayPause.setText("⏸ Pause");
                    btnMusicPlayPause.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
                }
            }
        });

        headerRow.add(infoLeft, BorderLayout.WEST);
        headerRow.add(cbAtmosphere, BorderLayout.EAST);

        // Center: Waveform visualizer
        waveformVisualizer = new WaveformVisualizer();

        // Bottom Controls Row: Play/Pause, Shuffle, Volume
        JPanel controlsRow = new JPanel(new BorderLayout());
        controlsRow.setOpaque(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);

        btnMusicPlayPause = new JButton("▶ Play");
        btnMusicPlayPause.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnMusicPlayPause.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnMusicPlayPause.setForeground(Color.WHITE);
        btnMusicPlayPause.setFocusPainted(false);
        btnMusicPlayPause.addActionListener(e -> {
            if (soundPlayer.getCurrentType() == AmbientSoundPlayer.SoundType.OFF) {
                soundPlayer.playSound(AmbientSoundPlayer.SoundType.BINAURAL_ALPHA);
                cbAtmosphere.setSelectedItem(AmbientSoundPlayer.SoundType.BINAURAL_ALPHA);
                waveformVisualizer.setPlaying(true);
                btnMusicPlayPause.setText("⏸ Pause");
                btnMusicPlayPause.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
            } else {
                soundPlayer.stopSound();
                cbAtmosphere.setSelectedItem(AmbientSoundPlayer.SoundType.OFF);
                waveformVisualizer.setPlaying(false);
                btnMusicPlayPause.setText("▶ Play");
                btnMusicPlayPause.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
            }
        });

        btnRow.add(btnMusicPlayPause);

        JPanel volumeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        volumeRow.setOpaque(false);

        JLabel lblVol = new JLabel("🔊 Volume:");
        lblVol.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblVol.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        musicVolumeSlider = new JSlider(0, 100, 70);
        musicVolumeSlider.setPreferredSize(new Dimension(130, 24));
        musicVolumeSlider.addChangeListener(e -> soundPlayer.setVolume(musicVolumeSlider.getValue() / 100f));

        volumeRow.add(lblVol);
        volumeRow.add(musicVolumeSlider);

        controlsRow.add(btnRow, BorderLayout.WEST);
        controlsRow.add(volumeRow, BorderLayout.EAST);

        card.add(headerRow, BorderLayout.NORTH);
        card.add(waveformVisualizer, BorderLayout.CENTER);
        card.add(controlsRow, BorderLayout.SOUTH);

        return card;
    }

    /**
     * Real-Time Intelligence Card matching Android activity_timer.xml intelligenceCard.
     */
    private JPanel createIntelligenceCard() {
        JPanel card = createGlassPanel(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 22, 18, 22));

        // Header Title
        JLabel title = new JLabel("REAL-TIME INTELLIGENCE");
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);

        card.add(title);
        card.add(Box.createVerticalStrut(14));

        // 4 Metrics Grid
        JPanel metricsGrid = new JPanel(new GridLayout(1, 4, 14, 0));
        metricsGrid.setOpaque(false);

        tvMetricSessionElapsed = new JLabel("00:00");
        tvMetricSubjectToday = new JLabel("0m");
        tvMetricDailyTotal = new JLabel("0m");
        tvMetricDistractionsBreaks = new JLabel("0 / 0");

        metricsGrid.add(createMetricTile("ACTIVE SESSION", tvMetricSessionElapsed, EduTraceDesktop.COLOR_ELITE_GOLD));
        metricsGrid.add(createMetricTile("TODAY'S SUBJECT", tvMetricSubjectToday, new Color(0x38, 0xBD, 0xF8)));
        metricsGrid.add(createMetricTile("DAILY TOTAL", tvMetricDailyTotal, EduTraceDesktop.COLOR_STATUS_BREAK));
        metricsGrid.add(createMetricTile("DISTRACT / BREAKS", tvMetricDistractionsBreaks, new Color(0xF4, 0x72, 0xB6)));

        card.add(metricsGrid);
        card.add(Box.createVerticalStrut(16));

        // Comparison Progress Bar (Subject vs Daily Goal)
        comparisonProgressBar = new JProgressBar(0, 100);
        comparisonProgressBar.setValue(25);
        comparisonProgressBar.setPreferredSize(new Dimension(0, 8));
        comparisonProgressBar.setForeground(new Color(0x38, 0xBD, 0xF8));
        comparisonProgressBar.setBackground(new Color(0x24, 0x24, 0x36));
        comparisonProgressBar.setBorder(null);

        card.add(comparisonProgressBar);
        card.add(Box.createVerticalStrut(14));

        // Motivational Insight (Matching @id/tvQuoteText)
        tvQuoteText = new JLabel("\"Focus on being productive instead of busy.\"");
        tvQuoteText.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tvQuoteText.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        tvQuoteText.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(tvQuoteText);

        return card;
    }

    private JPanel createMetricTile(String label, JLabel valueLabel, Color accent) {
        JPanel tile = new JPanel();
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setBackground(EduTraceDesktop.COLOR_SLATE_DARK);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EduTraceDesktop.COLOR_CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        JLabel title = new JLabel(label);
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        valueLabel.setForeground(accent);

        tile.add(title);
        tile.add(Box.createVerticalStrut(4));
        tile.add(valueLabel);
        return tile;
    }

    /**
     * Bottom Action Bar matching activity_timer.xml bottomBarCard:
     * [ ☕ Break / Resume ]  [ ⚡ Distracted (+1) ]  [ ▶ Start / ⏸ Pause ]  [ ✔ End Session ]  [ ↺ Reset ]
     */
    private JPanel createBottomActionBar() {
        JPanel bar = createGlassPanel(20);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER, 14, 12));
        bar.setBorder(new EmptyBorder(6, 16, 6, 16));

        // 1. Break / Resume Button
        btnBreak = new JButton("☕ Break");
        btnBreak.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnBreak.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK);
        btnBreak.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnBreak.setPreferredSize(new Dimension(120, 42));
        btnBreak.setFocusPainted(false);
        btnBreak.addActionListener(e -> toggleBreak());

        // 2. Distracted Button (+1 counter)
        btnDistracted = new JButton("⚡ Distracted (+1)");
        btnDistracted.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnDistracted.setForeground(new Color(0xF4, 0x72, 0xB6));
        btnDistracted.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnDistracted.setPreferredSize(new Dimension(150, 42));
        btnDistracted.setFocusPainted(false);
        btnDistracted.addActionListener(e -> {
            distractionCount++;
            updateIntelligenceMetrics();
            JOptionPane.showMessageDialog(this,
                    "Distraction recorded. Take a breath and bring your attention back!",
                    "Distraction Warden", JOptionPane.INFORMATION_MESSAGE);
        });

        // 3. Primary Start / Pause Button
        btnStartPause = new JButton("▶ Start Focus");
        btnStartPause.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnStartPause.setForeground(Color.BLACK);
        btnStartPause.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnStartPause.setPreferredSize(new Dimension(160, 42));
        btnStartPause.setFocusPainted(false);
        btnStartPause.addActionListener(e -> toggleTimer());

        // 4. End & Save Session
        btnEndSession = new JButton("✔ End & Save");
        btnEndSession.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnEndSession.setForeground(Color.WHITE);
        btnEndSession.setBackground(new Color(0x3B, 0x82, 0xF6));
        btnEndSession.setPreferredSize(new Dimension(140, 42));
        btnEndSession.setFocusPainted(false);
        btnEndSession.addActionListener(e -> endAndSaveSession());

        // 5. Reset
        btnReset = new JButton("↺ Reset");
        btnReset.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnReset.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        btnReset.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnReset.setPreferredSize(new Dimension(90, 42));
        btnReset.setFocusPainted(false);
        btnReset.addActionListener(e -> resetTimer());

        bar.add(btnBreak);
        bar.add(btnDistracted);
        bar.add(btnStartPause);
        bar.add(btnEndSession);
        bar.add(btnReset);

        return bar;
    }

    private void initTimerLoop() {
        swingTimer = new Timer(1000, e -> {
            if (isRunning && !isPaused) {
                secondsElapsed++;

                if (currentMode.isCountdown()) {
                    if (secondsRemaining > 0) {
                        secondsRemaining--;
                        tvTimerDisplay.setText(formatTime(secondsRemaining));
                        int progress = (int) (((currentMode.getDefaultSeconds() - secondsRemaining) * 100.0) / currentMode.getDefaultSeconds());
                        timerProgressBar.setValue(Math.min(100, Math.max(0, progress)));
                    } else {
                        // Countdown completed!
                        onCountdownFinished();
                    }
                } else {
                    // Stopwatch count up
                    tvTimerDisplay.setText(formatTime(secondsElapsed));
                    int progress = (int) ((secondsElapsed % 3600) * 100 / 3600);
                    timerProgressBar.setValue(progress);
                }

                // Update Flow Aura level based on elapsed focus minutes
                updateFlowAuraLevel();

                // Dynamic quote update every 5 minutes
                long now = System.currentTimeMillis();
                if (now - lastQuoteUpdateTime > 5 * 60 * 1000) {
                    tvQuoteText.setText(quoteManager.getRandomQuote());
                    lastQuoteUpdateTime = now;
                }

                updateIntelligenceMetrics();
            } else if (isPaused) {
                // Break elapsed
                long breakSeconds = (System.currentTimeMillis() - breakStartTime) / 1000L;
                tvStatus.setText("☕ ON BREAK (" + formatTime((int) breakSeconds) + ")");
            }
        });
    }

    private void updateFlowAuraLevel() {
        int minutes = secondsElapsed / 60;
        int newLevel;
        if (minutes >= 45) {
            newLevel = 3;
        } else if (minutes >= 20) {
            newLevel = 2;
        } else if (minutes >= 5) {
            newLevel = 1;
        } else {
            newLevel = 0;
        }

        if (newLevel != flowLevel) {
            flowLevel = newLevel;
            timerWrapperCard.repaint();
        }
    }

    private void toggleTimer() {
        if (!isRunning) {
            // Start
            isRunning = true;
            isPaused = false;
            if (sessionStartMillis == 0) {
                sessionStartMillis = System.currentTimeMillis();
                String subject = (String) cbSubjectSelector.getSelectedItem();
                DesktopCloudSessionManager.getInstance().startLiveServerSession(subject != null ? subject : "General");
            }
            swingTimer.start();
            btnStartPause.setText("⏸ Pause");
            btnStartPause.setBackground(new Color(0xEF, 0x44, 0x44));
            btnStartPause.setForeground(Color.WHITE);
            tvStatus.setText("● FOCUSING");
            tvStatus.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
            timerWrapperCard.repaint();
        } else {
            // Pause
            isRunning = false;
            swingTimer.stop();
            btnStartPause.setText("▶ Resume");
            btnStartPause.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
            btnStartPause.setForeground(Color.BLACK);
            tvStatus.setText("⏸ PAUSED");
            tvStatus.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
            timerWrapperCard.repaint();
        }
    }

    private void toggleBreak() {
        if (!isRunning && !isPaused) {
            JOptionPane.showMessageDialog(this, "Start a study session first before taking a break!", "EduTrace", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!isPaused) {
            // Enter break mode
            isPaused = true;
            breakCount++;
            breakStartTime = System.currentTimeMillis();
            btnBreak.setText("☕ Resume Focus");
            btnBreak.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
            tvStatus.setText("☕ ON BREAK");
            tvStatus.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK);
            DesktopCloudSessionManager.getInstance().pauseLiveServerSession("Student took a break");
        } else {
            // Resume focus
            isPaused = false;
            totalBreakTimeMillis += (System.currentTimeMillis() - breakStartTime);
            btnBreak.setText("☕ Break");
            btnBreak.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK);
            tvStatus.setText("● FOCUSING");
            tvStatus.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
            DesktopCloudSessionManager.getInstance().resumeLiveServerSession();
        }
        updateIntelligenceMetrics();
        timerWrapperCard.repaint();
    }

    private void endAndSaveSession() {
        if (secondsElapsed < 30) {
            JOptionPane.showMessageDialog(this,
                    "Study session is under 30 seconds. Focus for at least 1 minute before saving!",
                    "EduTrace", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String notes = JOptionPane.showInputDialog(this,
                "Enter optional session notes or key takeaways:",
                "Save Study Session (" + (secondsElapsed / 60) + " minutes)",
                JOptionPane.PLAIN_MESSAGE);

        if (notes != null) {
            String subject = (String) cbSubjectSelector.getSelectedItem();
            if (subject == null || subject.trim().isEmpty()) subject = "General Focus";

            DesktopStudySession session = new DesktopStudySession(
                    UUID.randomUUID().toString(),
                    subject.trim(),
                    sessionStartMillis > 0 ? sessionStartMillis : (System.currentTimeMillis() - secondsElapsed * 1000L),
                    System.currentTimeMillis(),
                    secondsElapsed,
                    currentMode.name(),
                    notes.trim()
            );

            dataManager.addSession(session);
            DesktopCloudSessionManager.getInstance().stopLiveServerSession(secondsElapsed, notes.trim());

            JOptionPane.showMessageDialog(this,
                    "Study session saved successfully! Synced with EduTrace Cloud.",
                    "Session Saved", JOptionPane.INFORMATION_MESSAGE);

            if (onSessionSavedCallback != null) {
                onSessionSavedCallback.run();
            }

            resetTimer();
            updateIntelligenceMetrics();
        }
    }

    private void onCountdownFinished() {
        isRunning = false;
        swingTimer.stop();
        btnStartPause.setText("▶ Start Focus");
        btnStartPause.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnStartPause.setForeground(Color.BLACK);
        tvStatus.setText("✔ SESSION COMPLETED");
        tvStatus.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK);
        Toolkit.getDefaultToolkit().beep();

        JOptionPane.showMessageDialog(this,
                "Time's up! Great job finishing your " + currentMode.getLabel() + ".\nDon't forget to save your session to record progress.",
                "Timer Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resetTimer() {
        boolean wasRunning = isRunning;
        isRunning = false;
        isPaused = false;
        swingTimer.stop();

        if (wasRunning || secondsElapsed > 0) {
            DesktopCloudSessionManager.getInstance().stopLiveServerSession(secondsElapsed, "Reset");
        }

        sessionStartMillis = 0;
        breakStartTime = 0;
        totalBreakTimeMillis = 0;
        breakCount = 0;
        distractionCount = 0;
        secondsElapsed = 0;
        flowLevel = 0;
        secondsRemaining = currentMode.getDefaultSeconds();

        tvTimerDisplay.setText(formatTime(currentMode.isCountdown() ? secondsRemaining : 0));
        timerProgressBar.setValue(0);
        btnStartPause.setText("▶ Start Focus");
        btnStartPause.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnStartPause.setForeground(Color.BLACK);
        btnBreak.setText("☕ Break");
        btnBreak.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK);
        tvStatus.setText("READY TO FOCUS");
        tvStatus.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        timerWrapperCard.repaint();
        updateIntelligenceMetrics();
    }

    private void switchTimerMode(TimerMode mode) {
        if (isRunning) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "A study timer is currently active. Switch mode and reset?",
                    "Change Mode", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                // Revert button selection
                for (java.util.Enumeration<AbstractButton> e = modeButtonGroup.getElements(); e.hasMoreElements(); ) {
                    AbstractButton b = e.nextElement();
                    if (b.getText().equals(currentMode.getLabel())) {
                        b.setSelected(true);
                    }
                }
                return;
            }
        }

        currentMode = mode;
        tvModeSubtitle.setText(mode == TimerMode.STOPWATCH ? "DEEP FOCUS & STOPWATCH" :
                (mode == TimerMode.POMODORO_WORK ? "POMODORO FOCUS SESSION" : "RECHARGE BREAK"));

        resetTimer();
    }

    public void reloadSubjects() {
        if (cbSubjectSelector == null) return;
        String current = (String) cbSubjectSelector.getSelectedItem();
        cbSubjectSelector.removeAllItems();
        java.util.List<org.sabbir.edutrace.desktop.data.DesktopSubject> subs = dataManager.getSubjects();
        for (org.sabbir.edutrace.desktop.data.DesktopSubject s : subs) {
            cbSubjectSelector.addItem(s.getName());
        }
        if (current != null) {
            cbSubjectSelector.setSelectedItem(current);
        }
    }

    public void selectSubject(String subjectName) {
        if (subjectName == null || subjectName.trim().isEmpty()) return;
        reloadSubjects();
        cbSubjectSelector.setSelectedItem(subjectName.trim());
        dataManager.getData().lastSelectedSubject = subjectName.trim();
        dataManager.saveData();
        updateIntelligenceMetrics();
    }

    public void updateIntelligenceMetrics() {
        String currentSubject = (String) cbSubjectSelector.getSelectedItem();
        long subjectSec = dataManager.getTodaySubjectStudySeconds(currentSubject);
        long totalSec = dataManager.getTodayTotalStudySeconds();

        // Add active unsaved session seconds if running
        long currentSessionSec = secondsElapsed;
        subjectSec += currentSessionSec;
        totalSec += currentSessionSec;

        tvMetricSessionElapsed.setText(formatTime((int) currentSessionSec));
        tvMetricSubjectToday.setText(formatMinutes(subjectSec));
        tvMetricDailyTotal.setText(formatMinutes(totalSec));
        tvMetricDistractionsBreaks.setText(distractionCount + " / " + breakCount);

        // Daily goal target: 4 hours (14,400 seconds)
        int goalProgress = (int) Math.min(100, (totalSec * 100) / 14400);
        comparisonProgressBar.setValue(goalProgress);
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatMinutes(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    private JPanel createGlassPanel(int radius) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(EduTraceDesktop.COLOR_CARD_SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g2.setColor(EduTraceDesktop.COLOR_CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        return card;
    }
}
