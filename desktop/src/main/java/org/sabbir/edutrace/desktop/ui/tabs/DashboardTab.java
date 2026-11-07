package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.EduTraceDesktop;
import org.sabbir.edutrace.desktop.data.DataManager;
import org.sabbir.edutrace.desktop.data.DesktopQuoteManager;
import org.sabbir.edutrace.desktop.data.DesktopStudySession;
import org.sabbir.edutrace.desktop.data.DesktopTask;
import org.sabbir.edutrace.desktop.utils.ScientificMathEvaluator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Focus Dashboard matching the Android FocusLauncherActivity layout:
 * Day in progress tracker, weather forecast card, essential tools shelf,
 * inspiring quote card, spotlight solver, and study checklist.
 */
public class DashboardTab extends JPanel {

    private final DesktopQuoteManager quoteManager;
    private final DataManager dataManager;
    private final Consumer<String> openTabCallback;

    private JLabel quoteLabel;
    private JProgressBar dayProgressBar;
    private JLabel dayProgressLabel;
    private JLabel todayStudyTimeLabel;
    private JLabel tasksSummaryLabel;
    private JPanel taskListPanel;
    private JTextField taskInputField;
    private JTextField spotlightField;
    private JLabel mathResultLabel;
    private JButton btnOpenInCalc;
    private JButton btnToggleLang;

    public DashboardTab(DesktopQuoteManager quoteManager, Consumer<String> openTabCallback) {
        this.quoteManager = quoteManager;
        this.dataManager = DataManager.getInstance();
        this.openTabCallback = openTabCallback;

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 24, 20, 24));

        initUI();
        refreshData();
    }

    private void initUI() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // 1. Sleek Day Progress Header Bar (Matching Android tvDayProgressLabel & pbDayProgress)
        contentPanel.add(createDayProgressHeader());
        contentPanel.add(Box.createVerticalStrut(14));

        // 2. Weather Widget Card (Signature EduTrace Android Card)
        contentPanel.add(createWeatherCard());
        contentPanel.add(Box.createVerticalStrut(14));

        // 3. Essential Tools Shelf (Matching Android cardFocusTools)
        contentPanel.add(createEssentialToolsShelf());
        contentPanel.add(Box.createVerticalStrut(14));

        // 4. Inspiring Quote Card
        contentPanel.add(createQuoteCard());
        contentPanel.add(Box.createVerticalStrut(14));

        // 5. Spotlight Search & Instant Math Solver
        contentPanel.add(createSpotlightCard());
        contentPanel.add(Box.createVerticalStrut(14));

        // 6. Daily Focus Checklist Card
        contentPanel.add(createChecklistCard());
        contentPanel.add(Box.createVerticalStrut(20));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createDayProgressHeader() {
        JPanel panel = new JPanel(new BorderLayout(14, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        int progress = calculateDayProgress();
        dayProgressLabel = new JLabel("Day in progress " + progress + "%");
        dayProgressLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        dayProgressLabel.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        dayProgressBar = new JProgressBar(0, 100) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x22, 0x22, 0x30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                int filled = (int) (getWidth() * (getValue() / 100.0));
                if (filled > 0) {
                    g2.setColor(EduTraceDesktop.COLOR_ELITE_GOLD);
                    g2.fillRoundRect(0, 0, filled, getHeight(), 8, 8);
                }
                g2.dispose();
            }
        };
        dayProgressBar.setValue(progress);
        dayProgressBar.setPreferredSize(new Dimension(200, 6));
        dayProgressBar.setBorder(null);
        dayProgressBar.setOpaque(false);

        panel.add(dayProgressLabel, BorderLayout.WEST);
        panel.add(dayProgressBar, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createWeatherCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(12, 12));

        // Top Row: Title + Refresh
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel title = new JLabel("☁ Weather");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        JLabel refreshIcon = new JLabel("↻");
        refreshIcon.setFont(new Font("SansSerif", Font.BOLD, 14));
        refreshIcon.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
        refreshIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));

        topRow.add(title, BorderLayout.WEST);
        topRow.add(refreshIcon, BorderLayout.EAST);

        // Center Temperature Row
        JPanel tempRow = new JPanel(new BorderLayout(12, 0));
        tempRow.setOpaque(false);

        JPanel leftTemp = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTemp.setOpaque(false);

        JLabel sunIcon = new JLabel("☀");
        sunIcon.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 32));
        sunIcon.setForeground(EduTraceDesktop.COLOR_AMBER_GLOW);

        JLabel tempLabel = new JLabel("27°");
        tempLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        tempLabel.setForeground(Color.WHITE);

        leftTemp.add(sunIcon);
        leftTemp.add(tempLabel);

        JPanel rightCondition = new JPanel();
        rightCondition.setLayout(new BoxLayout(rightCondition, BoxLayout.Y_AXIS));
        rightCondition.setOpaque(false);

        JLabel condLabel = new JLabel("Clear Skies");
        condLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        condLabel.setForeground(Color.WHITE);
        condLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel locLabel = new JLabel("Study & Focus Station");
        locLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        locLabel.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        locLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        rightCondition.add(condLabel);
        rightCondition.add(Box.createVerticalStrut(2));
        rightCondition.add(locLabel);

        tempRow.add(leftTemp, BorderLayout.WEST);
        tempRow.add(rightCondition, BorderLayout.EAST);

        // Hourly Forecast Row
        JPanel hourlyRow = new JPanel(new GridLayout(1, 6, 8, 0));
        hourlyRow.setOpaque(false);
        hourlyRow.setBorder(new EmptyBorder(8, 0, 0, 0));

        String[][] forecast = {
                {"7 pm", "☀", "24°"},
                {"8 pm", "☀", "22°"},
                {"9 pm", "☁", "20°"},
                {"10 pm", "☁", "19°"},
                {"11 pm", "🌙", "18°"},
                {"12 am", "🌙", "18°"}
        };

        for (String[] h : forecast) {
            JPanel hourPill = new JPanel();
            hourPill.setLayout(new BoxLayout(hourPill, BoxLayout.Y_AXIS));
            hourPill.setOpaque(false);

            JLabel timeLbl = new JLabel(h[0]);
            timeLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            timeLbl.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
            timeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel iconLbl = new JLabel(h[1]);
            iconLbl.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
            iconLbl.setForeground(EduTraceDesktop.COLOR_AMBER_GLOW);
            iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel valLbl = new JLabel(h[2]);
            valLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
            valLbl.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);
            valLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            hourPill.add(timeLbl);
            hourPill.add(Box.createVerticalStrut(2));
            hourPill.add(iconLbl);
            hourPill.add(Box.createVerticalStrut(2));
            hourPill.add(valLbl);
            hourlyRow.add(hourPill);
        }

        card.add(topRow, BorderLayout.NORTH);
        card.add(tempRow, BorderLayout.CENTER);
        card.add(hourlyRow, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createEssentialToolsShelf() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(8, 10));

        JLabel title = new JLabel("::: Essential Focus Tools");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        JPanel toolsGrid = new JPanel(new GridLayout(1, 4, 10, 0));
        toolsGrid.setOpaque(false);

        toolsGrid.add(createToolTile("🧮", "Scientific Calc", "Math Evaluator", () -> {
            if (openTabCallback != null) openTabCallback.accept("CALCULATOR");
        }));
        toolsGrid.add(createToolTile("⏱", "Study Timer", "Pomodoro & Intervals", () -> {
            if (openTabCallback != null) openTabCallback.accept("TIMER");
        }));
        toolsGrid.add(createToolTile("🏆", "Leaderboard", "Global Scholar Ranks", () -> {
            if (openTabCallback != null) openTabCallback.accept("LEADERBOARD");
        }));
        toolsGrid.add(createToolTile("📊", "Study Analytics", "Subject Breakdown", () -> {
            if (openTabCallback != null) openTabCallback.accept("ANALYTICS");
        }));

        card.add(title, BorderLayout.NORTH);
        card.add(toolsGrid, BorderLayout.CENTER);
        return card;
    }

    private JPanel createToolTile(String icon, String title, String subtitle, Runnable onClick) {
        JPanel tile = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1B, 0x1B, 0x2A));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(EduTraceDesktop.COLOR_CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setOpaque(false);
        tile.setBorder(new EmptyBorder(12, 12, 12, 12));
        tile.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel iconLbl = new JLabel(icon);
        iconLbl.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 24));
        iconLbl.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLbl.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLbl = new JLabel(subtitle);
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        subLbl.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
        subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        tile.add(iconLbl);
        tile.add(Box.createVerticalStrut(4));
        tile.add(titleLbl);
        tile.add(Box.createVerticalStrut(2));
        tile.add(subLbl);

        tile.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (onClick != null) onClick.run();
            }
        });
        return tile;
    }

    private JPanel createQuoteCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("::: Daily Inspiration");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        quoteLabel = new JLabel("<html><body style='width: 620px; font-size: 13px; line-height: 1.6; font-family: SansSerif;'>"
                + quoteManager.getCurrentQuote() + "</body></html>");
        quoteLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        quoteLabel.setForeground(new Color(0xE2, 0xE8, 0xF0));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);

        btnToggleLang = new JButton(quoteManager.isBengali() ? "Language: BN (বাংলা)" : "Language: EN");
        btnToggleLang.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnToggleLang.setFocusPainted(false);
        btnToggleLang.addActionListener(e -> {
            boolean nextIsBn = !quoteManager.isBengali();
            quoteManager.setBengali(nextIsBn);
            dataManager.getData().isBengaliQuotes = nextIsBn;
            dataManager.saveData();
            btnToggleLang.setText(nextIsBn ? "Language: BN (বাংলা)" : "Language: EN");
            quoteLabel.setText("<html><body style='width: 620px; font-size: 13px; line-height: 1.6; font-family: SansSerif;'>"
                    + quoteManager.getCurrentQuote() + "</body></html>");
        });

        JButton btnNext = new JButton("Next Quote ↻");
        btnNext.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnNext.setFocusPainted(false);
        btnNext.addActionListener(e -> {
            quoteLabel.setText("<html><body style='width: 620px; font-size: 13px; line-height: 1.6; font-family: SansSerif;'>"
                    + quoteManager.getNextQuote() + "</body></html>");
        });

        JButton btnCopy = new JButton("Copy 📋");
        btnCopy.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnCopy.setFocusPainted(false);
        btnCopy.addActionListener(e -> {
            String text = quoteManager.getCurrentQuote();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(this, "Quote copied to clipboard!", "EduTrace", JOptionPane.INFORMATION_MESSAGE);
        });

        btnPanel.add(btnToggleLang);
        btnPanel.add(btnNext);
        btnPanel.add(btnCopy);

        card.add(title, BorderLayout.NORTH);
        card.add(quoteLabel, BorderLayout.CENTER);
        card.add(btnPanel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createSpotlightCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 8));

        JLabel title = new JLabel("🔍 Spotlight Math & Quick Solver");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);

        spotlightField = new JTextField();
        spotlightField.putClientProperty("JTextField.placeholderText", "Type an expression (e.g. 25 * 4, sqrt(144) + 10^2, sin(45))...");
        spotlightField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        spotlightField.setPreferredSize(new Dimension(300, 38));

        mathResultLabel = new JLabel("  = ...  ");
        mathResultLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        mathResultLabel.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);

        btnOpenInCalc = new JButton("Open in Calculator →");
        btnOpenInCalc.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnOpenInCalc.setVisible(false);
        btnOpenInCalc.addActionListener(e -> {
            if (openTabCallback != null) {
                openTabCallback.accept("CALCULATOR:" + spotlightField.getText().trim());
            }
        });

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBox.setOpaque(false);
        rightBox.add(mathResultLabel);
        rightBox.add(btnOpenInCalc);

        searchRow.add(spotlightField, BorderLayout.CENTER);
        searchRow.add(rightBox, BorderLayout.EAST);

        spotlightField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { evaluate(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { evaluate(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { evaluate(); }

            private void evaluate() {
                String text = spotlightField.getText().trim();
                if (text.isEmpty()) {
                    mathResultLabel.setText("  = ...  ");
                    btnOpenInCalc.setVisible(false);
                    return;
                }
                ScientificMathEvaluator.EvaluationResult res = ScientificMathEvaluator.evaluate(text, true);
                if (res.success) {
                    mathResultLabel.setText("  = " + res.formatted + "  ");
                    btnOpenInCalc.setVisible(true);
                } else {
                    mathResultLabel.setText("  = ...  ");
                    btnOpenInCalc.setVisible(false);
                }
            }
        });

        card.add(title, BorderLayout.NORTH);
        card.add(searchRow, BorderLayout.CENTER);
        return card;
    }

    private JPanel createChecklistCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 12));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel title = new JLabel("::: Study Focus Checklist");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        tasksSummaryLabel = new JLabel("0/0 completed");
        tasksSummaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tasksSummaryLabel.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        top.add(title, BorderLayout.WEST);
        top.add(tasksSummaryLabel, BorderLayout.EAST);

        // Input row
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);

        taskInputField = new JTextField();
        taskInputField.putClientProperty("JTextField.placeholderText", "Add focus task or study goal...");
        taskInputField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        taskInputField.setPreferredSize(new Dimension(300, 36));

        JButton btnAdd = new JButton("+ Add Task");
        btnAdd.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnAdd.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnAdd.setForeground(new Color(0x0A, 0x0A, 0x0C));
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnAdd.addActionListener(e -> addTask());
        taskInputField.addActionListener(e -> addTask());

        inputRow.add(taskInputField, BorderLayout.CENTER);
        inputRow.add(btnAdd, BorderLayout.EAST);

        taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setOpaque(false);

        card.add(top, BorderLayout.NORTH);
        card.add(inputRow, BorderLayout.CENTER);
        card.add(taskListPanel, BorderLayout.SOUTH);
        return card;
    }

    private void addTask() {
        String text = taskInputField.getText().trim();
        if (!text.isEmpty()) {
            dataManager.addTask(text);
            taskInputField.setText("");
            refreshData();
        }
    }

    public void refreshData() {
        int progress = calculateDayProgress();
        dayProgressBar.setValue(progress);
        dayProgressLabel.setText("Day in progress " + progress + "%");

        List<DesktopTask> tasks = dataManager.getTasks();
        int completed = 0;
        for (DesktopTask t : tasks) {
            if (t.isCompleted()) completed++;
        }
        tasksSummaryLabel.setText(completed + "/" + tasks.size() + " completed");

        taskListPanel.removeAll();
        for (DesktopTask task : tasks) {
            JPanel row = new JPanel(new BorderLayout(8, 4));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(4, 0, 4, 0));

            JCheckBox cb = new JCheckBox(task.getTitle(), task.isCompleted());
            cb.setOpaque(false);
            cb.setFont(new Font("SansSerif", task.isCompleted() ? Font.ITALIC : Font.PLAIN, 13));
            cb.setForeground(task.isCompleted() ? EduTraceDesktop.COLOR_TEXT_MUTED : EduTraceDesktop.COLOR_TEXT_PRIMARY);

            cb.addActionListener(e -> {
                dataManager.toggleTask(task.getId());
                refreshData();
            });

            JButton btnDel = new JButton("✕");
            btnDel.setForeground(new Color(0xEF, 0x44, 0x44));
            btnDel.setContentAreaFilled(false);
            btnDel.setBorder(null);
            btnDel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnDel.addActionListener(e -> {
                dataManager.deleteTask(task.getId());
                refreshData();
            });

            row.add(cb, BorderLayout.CENTER);
            row.add(btnDel, BorderLayout.EAST);
            taskListPanel.add(row);
        }

        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    private int calculateDayProgress() {
        LocalTime now = LocalTime.now();
        int secondsPassed = now.toSecondOfDay();
        return (int) Math.round((secondsPassed / 86400.0) * 100.0);
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
