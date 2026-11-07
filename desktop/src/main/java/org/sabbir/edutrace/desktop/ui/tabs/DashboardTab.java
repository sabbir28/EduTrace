package org.sabbir.edutrace.desktop.ui.tabs;

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

public class DashboardTab extends JPanel {

    private final DesktopQuoteManager quoteManager;
    private final DataManager dataManager;
    private final Consumer<String> openCalculatorCallback;

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

    public DashboardTab(DesktopQuoteManager quoteManager, Consumer<String> openCalculatorCallback) {
        this.quoteManager = quoteManager;
        this.dataManager = DataManager.getInstance();
        this.openCalculatorCallback = openCalculatorCallback;

        setLayout(new BorderLayout(16, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
        refreshData();
    }

    private void initUI() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // 1. Inspiring Quote Card
        contentPanel.add(createQuoteCard());
        contentPanel.add(Box.createVerticalStrut(16));

        // 2. Day In Progress & Quick Stats Card
        contentPanel.add(createDayProgressCard());
        contentPanel.add(Box.createVerticalStrut(16));

        // 3. Spotlight Math & Quick Search Card
        contentPanel.add(createSpotlightCard());
        contentPanel.add(Box.createVerticalStrut(16));

        // 4. Daily Focus Checklist Card
        contentPanel.add(createChecklistCard());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createQuoteCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("INSPIRING DAILY QUOTE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14)); // Elite Gold

        quoteLabel = new JLabel("<html><body style='width: 600px; font-size: 13px; line-height: 1.5;'>"
                + quoteManager.getCurrentQuote() + "</body></html>");
        quoteLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        quoteLabel.setForeground(new Color(0xF1, 0xF5, 0xF9));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);

        JButton btnNext = new JButton("Next Quote ↻");
        btnNext.addActionListener(e -> {
            quoteLabel.setText("<html><body style='width: 600px; font-size: 13px; line-height: 1.5;'>"
                    + quoteManager.getNextQuote() + "</body></html>");
        });

        JButton btnToggleLang = new JButton(quoteManager.isBengali() ? "English" : "বাংলা");
        btnToggleLang.addActionListener(e -> {
            boolean nextIsBn = !quoteManager.isBengali();
            quoteManager.setBengali(nextIsBn);
            dataManager.getData().isBengaliQuotes = nextIsBn;
            dataManager.saveData();
            btnToggleLang.setText(nextIsBn ? "English" : "বাংলা");
            quoteLabel.setText("<html><body style='width: 600px; font-size: 13px; line-height: 1.5;'>"
                    + quoteManager.getCurrentQuote() + "</body></html>");
        });

        JButton btnCopy = new JButton("Copy 📋");
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

    private JPanel createDayProgressCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(12, 10));

        JLabel title = new JLabel("DAY IN PROGRESS & METRICS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        dayProgressBar = new JProgressBar(0, 100);
        dayProgressBar.setValue(calculateDayProgress());
        dayProgressBar.setStringPainted(true);
        dayProgressBar.setForeground(new Color(0xE2, 0xB7, 0x14));
        dayProgressBar.setPreferredSize(new Dimension(100, 22));

        dayProgressLabel = new JLabel("Day in progress: " + dayProgressBar.getValue() + "%");
        dayProgressLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        dayProgressLabel.setForeground(new Color(0x94, 0xA3, 0xB8));

        JPanel metricsRow = new JPanel(new GridLayout(1, 2, 16, 0));
        metricsRow.setOpaque(false);

        todayStudyTimeLabel = new JLabel("Today's Study: 0m");
        todayStudyTimeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        todayStudyTimeLabel.setForeground(new Color(0x10, 0xB9, 0x81)); // Emerald

        tasksSummaryLabel = new JLabel("Tasks: 0/0 completed");
        tasksSummaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tasksSummaryLabel.setForeground(new Color(0x38, 0xBD, 0xF8)); // Sky Blue

        metricsRow.add(todayStudyTimeLabel);
        metricsRow.add(tasksSummaryLabel);

        JPanel topBox = new JPanel(new BorderLayout());
        topBox.setOpaque(false);
        topBox.add(title, BorderLayout.WEST);
        topBox.add(dayProgressLabel, BorderLayout.EAST);

        card.add(topBox, BorderLayout.NORTH);
        card.add(dayProgressBar, BorderLayout.CENTER);
        card.add(metricsRow, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createSpotlightCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("⚡ SPOTLIGHT SEARCH & MATH SOLVER");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);

        spotlightField = new JTextField();
        spotlightField.putClientProperty("JTextField.placeholderText", "Type math expression (e.g. 25 * 4, sqrt(144) + 10^2, sin(45)) or quick query...");
        spotlightField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        mathResultLabel = new JLabel("  = ...  ");
        mathResultLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        mathResultLabel.setForeground(new Color(0xE2, 0xB7, 0x14));

        btnOpenInCalc = new JButton("Open in Calculator →");
        btnOpenInCalc.setVisible(false);
        btnOpenInCalc.addActionListener(e -> {
            if (openCalculatorCallback != null) {
                openCalculatorCallback.accept(spotlightField.getText().trim());
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

        JLabel title = new JLabel("DAILY FOCUS CHECKLIST");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        // Input row
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);

        taskInputField = new JTextField();
        taskInputField.putClientProperty("JTextField.placeholderText", "Add new focus task or study goal...");
        taskInputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JButton btnAdd = new JButton("+ Add Task");
        btnAdd.addActionListener(e -> addTask());
        taskInputField.addActionListener(e -> addTask());

        inputRow.add(taskInputField, BorderLayout.CENTER);
        inputRow.add(btnAdd, BorderLayout.EAST);

        // Tasks list container
        taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setOpaque(false);

        card.add(title, BorderLayout.NORTH);
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
        // Update day progress
        int progress = calculateDayProgress();
        dayProgressBar.setValue(progress);
        dayProgressLabel.setText("Day in progress: " + progress + "%");

        // Update metrics
        long todaySeconds = 0;
        long startOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (DesktopStudySession s : dataManager.getSessions()) {
            if (s.getStartTimestamp() >= startOfDay) {
                todaySeconds += s.getDurationSeconds();
            }
        }
        long minutes = todaySeconds / 60;
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        todayStudyTimeLabel.setText("Today's Study: " + (hours > 0 ? hours + "h " : "") + remMinutes + "m");

        // Update tasks
        List<DesktopTask> tasks = dataManager.getTasks();
        int completed = 0;
        for (DesktopTask t : tasks) {
            if (t.isCompleted()) completed++;
        }
        tasksSummaryLabel.setText("Tasks: " + completed + "/" + tasks.size() + " completed");

        // Rebuild task rows
        taskListPanel.removeAll();
        for (DesktopTask task : tasks) {
            JPanel row = new JPanel(new BorderLayout(8, 4));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(4, 0, 4, 0));

            JCheckBox cb = new JCheckBox(task.getTitle(), task.isCompleted());
            cb.setOpaque(false);
            cb.setFont(new Font("Segoe UI", task.isCompleted() ? Font.ITALIC : Font.PLAIN, 13));
            if (task.isCompleted()) {
                cb.setForeground(new Color(0x64, 0x74, 0x8B));
            } else {
                cb.setForeground(new Color(0xF1, 0xF5, 0xF9));
            }

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
                g2.setColor(new Color(0x1E, 0x29, 0x3B)); // Dark surface
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
