package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.data.DataManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class SettingsTab extends JPanel {

    private final DataManager dataManager;
    private final Runnable onDataClearedCallback;

    public SettingsTab(Runnable onDataClearedCallback) {
        this.dataManager = DataManager.getInstance();
        this.onDataClearedCallback = onDataClearedCallback;

        setLayout(new BorderLayout(16, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        // 1. Data Storage Settings Card
        mainPanel.add(createStorageCard());
        mainPanel.add(Box.createVerticalStrut(16));

        // 2. Pomodoro & Preferences Card
        mainPanel.add(createPreferencesCard());
        mainPanel.add(Box.createVerticalStrut(16));

        // 3. About EduTrace Desktop Card
        mainPanel.add(createAboutCard());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createStorageCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 12));

        JLabel title = new JLabel("LOCAL DATA STORAGE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        File file = dataManager.getDataFile();
        JLabel pathLabel = new JLabel("<html><b>File Location:</b> <code style='color: #38BDF8;'>" + file.getAbsolutePath() + "</code><br>"
                + "<span style='color: #94A3B8;'>All study sessions, tasks, and settings are saved locally and privately on your machine.</span></html>");
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);

        JButton btnOpenFolder = new JButton("Open Storage Folder 📂");
        btnOpenFolder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(file.getParentFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton btnClear = new JButton("Reset All Data 🗑");
        btnClear.setForeground(new Color(0xEF, 0x44, 0x44));
        btnClear.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete all local study history and tasks? This cannot be undone.",
                    "Confirm Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                dataManager.clearAllData();
                JOptionPane.showMessageDialog(this, "All local data has been reset.", "EduTrace", JOptionPane.INFORMATION_MESSAGE);
                if (onDataClearedCallback != null) onDataClearedCallback.run();
            }
        });

        btnRow.add(btnOpenFolder);
        btnRow.add(btnClear);

        card.add(title, BorderLayout.NORTH);
        card.add(pathLabel, BorderLayout.CENTER);
        card.add(btnRow, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createPreferencesCard() {
        JPanel card = createStyledCard();
        card.setLayout(new GridLayout(2, 2, 14, 12));

        JLabel lblWork = new JLabel("Default Pomodoro Work (minutes):");
        lblWork.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblWork.setForeground(new Color(0xF1, 0xF5, 0xF9));

        JSpinner spinnerWork = new JSpinner(new SpinnerNumberModel(dataManager.getData().pomodoroWorkMinutes, 5, 120, 5));
        spinnerWork.addChangeListener(e -> {
            dataManager.getData().pomodoroWorkMinutes = (int) spinnerWork.getValue();
            dataManager.saveData();
        });

        JLabel lblBreak = new JLabel("Default Break (minutes):");
        lblBreak.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblBreak.setForeground(new Color(0xF1, 0xF5, 0xF9));

        JSpinner spinnerBreak = new JSpinner(new SpinnerNumberModel(dataManager.getData().pomodoroBreakMinutes, 1, 60, 1));
        spinnerBreak.addChangeListener(e -> {
            dataManager.getData().pomodoroBreakMinutes = (int) spinnerBreak.getValue();
            dataManager.saveData();
        });

        card.add(lblWork);
        card.add(spinnerWork);
        card.add(lblBreak);
        card.add(spinnerBreak);

        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setOpaque(false);

        JLabel title = new JLabel("STUDY PREFERENCES");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createAboutCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("ABOUT EDUTRACE DESKTOP");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        JLabel info = new JLabel("<html><body style='line-height: 1.6; color: #E2E8F0;'>"
                + "<b style='font-size: 14px; color: #E2B714;'>EduTrace Focus & Productivity Suite (Desktop Edition)</b><br>"
                + "Version 1.0.0 &bull; Pure Java Desktop Suite<br>"
                + "Developed by <b>sabbir28</b> &bull; Built with FlatLaf Dark UI & Java SE 11+<br><br>"
                + "Features: Minimalist Daily Dashboard, Pomodoro & Study Stopwatch, Procedural Ambient Audio Engine, "
                + "Full Scientific Math Evaluator, Focus Checklist, and Visual Analytics Breakdown."
                + "</body></html>");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        card.add(title, BorderLayout.NORTH);
        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private JPanel createStyledCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1E, 0x29, 0x3B));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(0x33, 0x41, 0x55));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        return card;
    }
}

