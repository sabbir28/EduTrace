package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.EduTraceDesktop;
import org.sabbir.edutrace.desktop.audio.AmbientSoundPlayer;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudSessionManager;
import org.sabbir.edutrace.desktop.data.DataManager;
import org.sabbir.edutrace.desktop.data.DesktopStudySession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.UUID;

public class StudyTimerTab extends JPanel {

    public enum TimerMode {
        POMODORO_WORK("🍅 Pomodoro (25m)", 25 * 60, true),
        SHORT_BREAK("☕ Short Break (5m)", 5 * 60, true),
        LONG_BREAK("🌴 Long Break (15m)", 15 * 60, true),
        STOPWATCH("⏱ Stopwatch (Count Up)", 0, false);

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

    private TimerMode currentMode = TimerMode.POMODORO_WORK;
    private int secondsRemaining;
    private int secondsElapsed;
    private boolean isRunning = false;
    private long sessionStartMillis = 0;

    private javax.swing.Timer swingTimer;

    private JLabel timerDisplayLabel;
    private JLabel modeSubtitleLabel;
    private JButton btnStartPause;
    private JButton btnReset;
    private JButton btnLogSession;
    private JComboBox<String> subjectComboBox;
    private JComboBox<AmbientSoundPlayer.SoundType> soundComboBox;
    private JSlider volumeSlider;

    public StudyTimerTab() {
        this.dataManager = DataManager.getInstance();
        this.soundPlayer = AmbientSoundPlayer.getInstance();
        this.secondsRemaining = currentMode.getDefaultSeconds();
        this.secondsElapsed = 0;

        setLayout(new BorderLayout(16, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
        initTimer();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        // 1. Mode Selector Buttons
        mainPanel.add(createModeSelectorPanel());
        mainPanel.add(Box.createVerticalStrut(16));

        // 2. Large Central Timer Card
        mainPanel.add(createTimerCard());
        mainPanel.add(Box.createVerticalStrut(16));

        // 3. Ambient Audio & Study Atmosphere Card
        mainPanel.add(createAmbientAudioCard());

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createModeSelectorPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        panel.setOpaque(false);

        ButtonGroup group = new ButtonGroup();
        for (TimerMode mode : TimerMode.values()) {
            JToggleButton btn = new JToggleButton(mode.getLabel(), mode == currentMode);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btn.addActionListener(e -> switchTimerMode(mode));
            group.add(btn);
            panel.add(btn);
        }
        return panel;
    }

    private JPanel createTimerCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        modeSubtitleLabel = new JLabel("FOCUS SESSION");
        modeSubtitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        modeSubtitleLabel.setForeground(new Color(0xE2, 0xB7, 0x14));
        modeSubtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerDisplayLabel = new JLabel(formatTime(secondsRemaining));
        timerDisplayLabel.setFont(new Font("Segoe UI", Font.BOLD, 72));
        timerDisplayLabel.setForeground(new Color(0xF1, 0xF5, 0xF9));
        timerDisplayLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subject selector row
        JPanel subjectRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        subjectRow.setOpaque(false);

        JLabel lblSubject = new JLabel("Target Subject:");
        lblSubject.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSubject.setForeground(new Color(0x94, 0xA3, 0xB8));

        String[] subjects = {"Mathematics", "Physics", "Chemistry", "Computer Science", "Literature", "History", "Biology", "General Focus"};
        subjectComboBox = new JComboBox<>(subjects);
        subjectComboBox.setSelectedItem(dataManager.getData().lastSelectedSubject != null ? dataManager.getData().lastSelectedSubject : "Mathematics");
        subjectComboBox.addActionListener(e -> {
            dataManager.getData().lastSelectedSubject = (String) subjectComboBox.getSelectedItem();
            dataManager.saveData();
        });

        subjectRow.add(lblSubject);
        subjectRow.add(subjectComboBox);

        // Buttons row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
        btnRow.setOpaque(false);

        btnStartPause = new JButton("▶ Start Focus");
        btnStartPause.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnStartPause.setBackground(new Color(0x10, 0xB9, 0x81));
        btnStartPause.setForeground(Color.WHITE);
        btnStartPause.setPreferredSize(new Dimension(140, 40));
        btnStartPause.addActionListener(e -> toggleTimer());

        btnReset = new JButton("↺ Reset");
        btnReset.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnReset.setPreferredSize(new Dimension(100, 40));
        btnReset.addActionListener(e -> resetTimer());

        btnLogSession = new JButton("✔ Finish & Log Session");
        btnLogSession.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogSession.setForeground(new Color(0xE2, 0xB7, 0x14));
        btnLogSession.setPreferredSize(new Dimension(180, 40));
        btnLogSession.addActionListener(e -> logCurrentSession());

        btnRow.add(btnStartPause);
        btnRow.add(btnReset);
        btnRow.add(btnLogSession);

        card.add(Box.createVerticalStrut(10));
        card.add(modeSubtitleLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(timerDisplayLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(subjectRow);
        card.add(Box.createVerticalStrut(16));
        card.add(btnRow);
        card.add(Box.createVerticalStrut(10));

        return card;
    }

    private JPanel createAmbientAudioCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(12, 10));

        JLabel title = new JLabel("🎧 PROCEDURAL AMBIENT FOCUS AUDIO");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        controlRow.setOpaque(false);

        JLabel lblSound = new JLabel("Atmosphere:");
        lblSound.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSound.setForeground(new Color(0x94, 0xA3, 0xB8));

        soundComboBox = new JComboBox<>(AmbientSoundPlayer.SoundType.values());
        soundComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AmbientSoundPlayer.SoundType) {
                    setText(((AmbientSoundPlayer.SoundType) value).getLabel());
                }
                return this;
            }
        });
        soundComboBox.addActionListener(e -> {
            AmbientSoundPlayer.SoundType selected = (AmbientSoundPlayer.SoundType) soundComboBox.getSelectedItem();
            if (selected != null) {
                soundPlayer.playSound(selected);
            }
        });

        JLabel lblVol = new JLabel("Volume:");
        lblVol.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblVol.setForeground(new Color(0x94, 0xA3, 0xB8));

        volumeSlider = new JSlider(0, 100, 70);
        volumeSlider.setPreferredSize(new Dimension(140, 24));
        volumeSlider.addChangeListener(e -> {
            soundPlayer.setVolume(volumeSlider.getValue() / 100f);
        });

        controlRow.add(lblSound);
        controlRow.add(soundComboBox);
        controlRow.add(lblVol);
        controlRow.add(volumeSlider);

        card.add(title, BorderLayout.NORTH);
        card.add(controlRow, BorderLayout.CENTER);
        return card;
    }

    private void initTimer() {
        swingTimer = new javax.swing.Timer(1000, e -> {
            if (currentMode.isCountdown()) {
                if (secondsRemaining > 0) {
                    secondsRemaining--;
                    secondsElapsed++;
                    timerDisplayLabel.setText(formatTime(secondsRemaining));
                } else {
                    // Timer finished
                    isRunning = false;
                    swingTimer.stop();
                    btnStartPause.setText("▶ Start Focus");
                    btnStartPause.setBackground(new Color(0x10, 0xB9, 0x81));
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(this,
                            "Session Completed! Great job focusing. Log your session to track progress.",
                            "Session Finished",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                secondsElapsed++;
                timerDisplayLabel.setText(formatTime(secondsElapsed));
            }
        });
    }

    private void toggleTimer() {
        if (!isRunning) {
            isRunning = true;
            if (sessionStartMillis == 0) {
                sessionStartMillis = System.currentTimeMillis();
                DesktopCloudSessionManager.getInstance()
                        .startLiveServerSession((String) subjectComboBox.getSelectedItem());
            }
            swingTimer.start();
            btnStartPause.setText("⏸ Pause");
            btnStartPause.setBackground(new Color(0xE2, 0xB7, 0x14));
        } else {
            isRunning = false;
            swingTimer.stop();
            btnStartPause.setText("▶ Resume");
            btnStartPause.setBackground(new Color(0x10, 0xB9, 0x81));
        }
    }

    private void resetTimer() {
        boolean wasRunning = isRunning;
        isRunning = false;
        swingTimer.stop();
        if (wasRunning || secondsElapsed > 0) {
            DesktopCloudSessionManager.getInstance().stopLiveServerSession(secondsElapsed, "Reset");
        }
        sessionStartMillis = 0;
        secondsElapsed = 0;
        secondsRemaining = currentMode.getDefaultSeconds();
        timerDisplayLabel.setText(formatTime(currentMode.isCountdown() ? secondsRemaining : 0));
        btnStartPause.setText("▶ Start Focus");
        btnStartPause.setBackground(new Color(0x10, 0xB9, 0x81));
    }

    private void switchTimerMode(TimerMode mode) {
        if (isRunning) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "A study timer is currently active. Switch mode and reset timer?",
                    "Change Mode", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        currentMode = mode;
        modeSubtitleLabel.setText(mode == TimerMode.POMODORO_WORK ? "FOCUS SESSION" :
                (mode == TimerMode.STOPWATCH ? "STUDY STOPWATCH" : "RECHARGE BREAK"));
        resetTimer();
    }

    private void logCurrentSession() {
        if (secondsElapsed < 30) {
            JOptionPane.showMessageDialog(this, "Session is under 30 seconds. Study a bit longer before logging!", "EduTrace", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String notes = JOptionPane.showInputDialog(this,
                "Add session notes (optional):",
                "Log Study Session (" + (secondsElapsed / 60) + " min)",
                JOptionPane.PLAIN_MESSAGE);

        if (notes != null) {
            String subject = (String) subjectComboBox.getSelectedItem();
            DesktopStudySession session = new DesktopStudySession(
                    UUID.randomUUID().toString(),
                    subject != null ? subject : "General",
                    sessionStartMillis > 0 ? sessionStartMillis : (System.currentTimeMillis() - secondsElapsed * 1000L),
                    System.currentTimeMillis(),
                    secondsElapsed,
                    currentMode.name(),
                    notes.trim()
            );
            dataManager.addSession(session);
            DesktopCloudSessionManager.getInstance().stopLiveServerSession(secondsElapsed, notes.trim());
            JOptionPane.showMessageDialog(this, "Session logged successfully! Synced with EduTrace Cloud.", "Success", JOptionPane.INFORMATION_MESSAGE);
            resetTimer();
        }
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private JPanel createStyledCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(EduTraceDesktop.COLOR_CARD_SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.setColor(EduTraceDesktop.COLOR_CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        return card;
    }
}

