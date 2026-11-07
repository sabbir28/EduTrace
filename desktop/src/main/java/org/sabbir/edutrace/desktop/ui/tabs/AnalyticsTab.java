package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.data.DataManager;
import org.sabbir.edutrace.desktop.data.DesktopStudySession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

public class AnalyticsTab extends JPanel {

    private final DataManager dataManager;

    private JLabel todayFocusLabel;
    private JLabel totalFocusLabel;
    private JLabel totalSessionsLabel;
    private JPanel subjectBreakdownPanel;
    private DefaultTableModel tableModel;
    private JTable sessionTable;

    public AnalyticsTab() {
        this.dataManager = DataManager.getInstance();

        setLayout(new BorderLayout(16, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
        refreshData();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        // 1. Summary Metrics Cards (Row of 3 cards)
        mainPanel.add(createMetricsRow());
        mainPanel.add(Box.createVerticalStrut(16));

        // 2. Subject Breakdown Panel
        mainPanel.add(createSubjectBreakdownCard());
        mainPanel.add(Box.createVerticalStrut(16));

        // 3. Session Log Table Card
        mainPanel.add(createSessionLogCard());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createMetricsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 14, 0));
        row.setOpaque(false);

        todayFocusLabel = new JLabel("0h 0m");
        totalFocusLabel = new JLabel("0h 0m");
        totalSessionsLabel = new JLabel("0");

        row.add(createMetricCard("TODAY'S STUDY", todayFocusLabel, new Color(0x10, 0xB9, 0x81)));
        row.add(createMetricCard("TOTAL STUDY TIME", totalFocusLabel, new Color(0xE2, 0xB7, 0x14)));
        row.add(createMetricCard("SESSIONS COMPLETED", totalSessionsLabel, new Color(0x38, 0xBD, 0xF8)));

        return row;
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(6, 6));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTitle.setForeground(new Color(0x94, 0xA3, 0xB8));

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLabel.setForeground(accentColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createSubjectBreakdownCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("SUBJECT FOCUS BREAKDOWN");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        subjectBreakdownPanel = new JPanel();
        subjectBreakdownPanel.setLayout(new BoxLayout(subjectBreakdownPanel, BoxLayout.Y_AXIS));
        subjectBreakdownPanel.setOpaque(false);

        card.add(title, BorderLayout.NORTH);
        card.add(subjectBreakdownPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createSessionLogCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("STUDY SESSION HISTORY");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        String[] cols = {"Date & Time", "Subject", "Duration", "Mode", "Notes"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        sessionTable = new JTable(tableModel);
        sessionTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sessionTable.setRowHeight(28);
        sessionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        sessionTable.getTableHeader().setForeground(new Color(0xE2, 0xB7, 0x14));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        sessionTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        sessionTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        sessionTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        sessionTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        sessionTable.getColumnModel().getColumn(4).setPreferredWidth(200);

        JScrollPane tableScroll = new JScrollPane(sessionTable);
        tableScroll.setPreferredSize(new Dimension(0, 200));

        JButton btnExport = new JButton("Export History to CSV 📁");
        btnExport.addActionListener(e -> exportToCsv());

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.add(title, BorderLayout.WEST);
        headerRow.add(btnExport, BorderLayout.EAST);

        card.add(headerRow, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        return card;
    }

    public void refreshData() {
        List<DesktopStudySession> sessions = dataManager.getSessions();
        long todaySeconds = 0;
        long totalSeconds = 0;
        long startOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        Map<String, Long> subjectDurationMap = new HashMap<>();

        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());

        for (int i = sessions.size() - 1; i >= 0; i--) {
            DesktopStudySession s = sessions.get(i);
            totalSeconds += s.getDurationSeconds();
            if (s.getStartTimestamp() >= startOfDay) {
                todaySeconds += s.getDurationSeconds();
            }

            String subj = s.getSubjectName() != null ? s.getSubjectName() : "General";
            subjectDurationMap.put(subj, subjectDurationMap.getOrDefault(subj, 0L) + s.getDurationSeconds());

            long mins = s.getDurationSeconds() / 60;
            String dur = (mins >= 60 ? (mins / 60) + "h " : "") + (mins % 60) + "m";
            tableModel.addRow(new Object[]{
                    sdf.format(new Date(s.getStartTimestamp())),
                    subj,
                    dur,
                    s.getMode(),
                    s.getNotes() != null ? s.getNotes() : ""
            });
        }

        // Today format
        long tHours = (todaySeconds / 60) / 60;
        long tMins = (todaySeconds / 60) % 60;
        todayFocusLabel.setText(tHours + "h " + tMins + "m");

        // Total format
        long totHours = (totalSeconds / 60) / 60;
        long totMins = (totalSeconds / 60) % 60;
        totalFocusLabel.setText(totHours + "h " + totMins + "m");

        totalSessionsLabel.setText(String.valueOf(sessions.size()));

        // Rebuild subject breakdown bars
        subjectBreakdownPanel.removeAll();
        if (subjectDurationMap.isEmpty()) {
            JLabel empty = new JLabel("No study sessions logged yet. Complete a focus session to see your breakdown!");
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            empty.setForeground(new Color(0x64, 0x74, 0x8B));
            subjectBreakdownPanel.add(empty);
        } else {
            for (Map.Entry<String, Long> entry : subjectDurationMap.entrySet()) {
                JPanel row = new JPanel(new BorderLayout(8, 2));
                row.setOpaque(false);
                row.setBorder(new EmptyBorder(4, 0, 4, 0));

                double percent = totalSeconds > 0 ? (entry.getValue() * 100.0 / totalSeconds) : 0;
                long m = entry.getValue() / 60;
                String timeStr = (m >= 60 ? (m / 60) + "h " : "") + (m % 60) + "m (" + String.format("%.1f", percent) + "%)";

                JLabel lblName = new JLabel(entry.getKey());
                lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lblName.setForeground(new Color(0xF1, 0xF5, 0xF9));

                JLabel lblTime = new JLabel(timeStr);
                lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                lblTime.setForeground(new Color(0x94, 0xA3, 0xB8));

                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue((int) Math.round(percent));
                bar.setForeground(new Color(0xE2, 0xB7, 0x14));
                bar.setPreferredSize(new Dimension(100, 10));

                JPanel top = new JPanel(new BorderLayout());
                top.setOpaque(false);
                top.add(lblName, BorderLayout.WEST);
                top.add(lblTime, BorderLayout.EAST);

                row.add(top, BorderLayout.NORTH);
                row.add(bar, BorderLayout.CENTER);

                subjectBreakdownPanel.add(row);
            }
        }

        subjectBreakdownPanel.revalidate();
        subjectBreakdownPanel.repaint();
    }

    private void exportToCsv() {
        List<DesktopStudySession> sessions = dataManager.getSessions();
        if (sessions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No session data to export.", "EduTrace", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("edutrace_study_sessions.csv"));
        int choice = fileChooser.showSaveDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write("Timestamp,Date,Subject,DurationSeconds,Mode,Notes\n");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (DesktopStudySession s : sessions) {
                    writer.write(String.format("%d,\"%s\",\"%s\",%d,\"%s\",\"%s\"\n",
                            s.getStartTimestamp(),
                            sdf.format(new Date(s.getStartTimestamp())),
                            s.getSubjectName() != null ? s.getSubjectName() : "",
                            s.getDurationSeconds(),
                            s.getMode() != null ? s.getMode() : "",
                            s.getNotes() != null ? s.getNotes().replace("\"", "\"\"") : ""
                    ));
                }
                JOptionPane.showMessageDialog(this, "Exported successfully to: " + file.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to export CSV: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
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

