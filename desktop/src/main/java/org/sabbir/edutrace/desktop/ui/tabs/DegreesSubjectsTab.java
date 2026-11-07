package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.EduTraceDesktop;
import org.sabbir.edutrace.desktop.data.DataManager;
import org.sabbir.edutrace.desktop.data.DesktopDegree;
import org.sabbir.edutrace.desktop.data.DesktopSubject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Degrees & Subjects curriculum management tab for EduTrace Desktop:
 * Direct 1:1 port of Android MainActivity (DegreeAdapter) and SubjectActivity (SubjectAdapter).
 * Displays registered Degrees, subjects under each degree, today's focus times, and
 * direct one-click study timer launch.
 */
public class DegreesSubjectsTab extends JPanel {

    private final DataManager dataManager;
    private final Consumer<String> onStartTimerForSubject;

    private DesktopDegree selectedDegree;

    private JPanel degreeListContainer;
    private JPanel subjectGridContainer;
    private JLabel lblSubjectsHeader;

    public DegreesSubjectsTab(Consumer<String> onStartTimerForSubject) {
        this.dataManager = DataManager.getInstance();
        this.onStartTimerForSubject = onStartTimerForSubject;

        List<DesktopDegree> degrees = dataManager.getDegrees();
        if (!degrees.isEmpty()) {
            this.selectedDegree = degrees.get(0);
        }

        setLayout(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
    }

    private void initUI() {
        // Left Column: Degree Programs (Width: 320px)
        JPanel leftPanel = createDegreesPanel();
        leftPanel.setPreferredSize(new Dimension(340, 0));

        // Right Column: Subjects for Selected Degree
        JPanel rightPanel = createSubjectsPanel();

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        refreshDegreeList();
        refreshSubjectGrid();
    }

    private JPanel createDegreesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        JLabel title = new JLabel("DEGREE PROGRAMS");
        title.setFont(new Font("SansSerif", Font.BOLD, 12));
        title.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);

        JButton btnAddDegree = new JButton("+ Add Degree");
        btnAddDegree.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnAddDegree.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnAddDegree.setForeground(Color.WHITE);
        btnAddDegree.setFocusPainted(false);
        btnAddDegree.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAddDegree.addActionListener(e -> showAddDegreeDialog());

        header.add(title, BorderLayout.WEST);
        header.add(btnAddDegree, BorderLayout.EAST);

        // Degrees list scroll area
        degreeListContainer = new JPanel();
        degreeListContainer.setLayout(new BoxLayout(degreeListContainer, BoxLayout.Y_AXIS));
        degreeListContainer.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(degreeListContainer);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSubjectsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        lblSubjectsHeader = new JLabel("CURRICULUM SUBJECTS");
        lblSubjectsHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblSubjectsHeader.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);

        JButton btnAddSubject = new JButton("+ Add Subject");
        btnAddSubject.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnAddSubject.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnAddSubject.setForeground(Color.WHITE);
        btnAddSubject.setFocusPainted(false);
        btnAddSubject.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAddSubject.addActionListener(e -> showAddSubjectDialog());

        header.add(lblSubjectsHeader, BorderLayout.WEST);
        header.add(btnAddSubject, BorderLayout.EAST);

        // Subjects cards scroll area
        subjectGridContainer = new JPanel();
        subjectGridContainer.setLayout(new BoxLayout(subjectGridContainer, BoxLayout.Y_AXIS));
        subjectGridContainer.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(subjectGridContainer);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    public void refreshDegreeList() {
        degreeListContainer.removeAll();
        List<DesktopDegree> degrees = dataManager.getDegrees();

        if (selectedDegree == null && !degrees.isEmpty()) {
            selectedDegree = degrees.get(0);
        }

        for (DesktopDegree degree : degrees) {
            boolean isSelected = selectedDegree != null && selectedDegree.getId() == degree.getId();
            JPanel degreeCard = createDegreeCard(degree, isSelected);
            degreeListContainer.add(degreeCard);
            degreeListContainer.add(Box.createVerticalStrut(10));
        }

        degreeListContainer.revalidate();
        degreeListContainer.repaint();
    }

    private JPanel createDegreeCard(DesktopDegree degree, boolean isSelected) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected ? new Color(0x1E, 0x1E, 0x2E) : EduTraceDesktop.COLOR_CARD_SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(isSelected ? EduTraceDesktop.COLOR_ELITE_GOLD : EduTraceDesktop.COLOR_CARD_BORDER);
                g2.setStroke(new BasicStroke(isSelected ? 1.5f : 1.0f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setLayout(new BorderLayout(12, 4));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color dotColor;
        try {
            dotColor = Color.decode(degree.getColorHex());
        } catch (Exception e) {
            dotColor = EduTraceDesktop.COLOR_ELITE_GOLD;
        }

        // Color indicator pill
        final Color finalDotColor = dotColor;
        JPanel colorIndicator = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(finalDotColor);
                g2.fillOval(2, 6, 10, 10);
                g2.dispose();
            }
        };
        colorIndicator.setPreferredSize(new Dimension(16, 24));
        colorIndicator.setOpaque(false);

        // Center: Degree Name & Subject count
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(degree.getName());
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        nameLabel.setForeground(isSelected ? EduTraceDesktop.COLOR_ELITE_GOLD : Color.WHITE);

        int subCount = dataManager.getSubjectsForDegree(degree.getId()).size();
        long totalSec = dataManager.getTotalDegreeStudySeconds(degree.getId());
        JLabel statsLabel = new JLabel(subCount + " Subjects  •  " + formatHoursMinutes(totalSec) + " focus");
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statsLabel.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        infoPanel.add(nameLabel);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(statsLabel);

        // Right side: Delete button
        JButton btnDelete = new JButton("×");
        btnDelete.setFont(new Font("SansSerif", Font.BOLD, 16));
        btnDelete.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
        btnDelete.setContentAreaFilled(false);
        btnDelete.setBorder(null);
        btnDelete.setFocusPainted(false);
        btnDelete.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDelete.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete degree program \"" + degree.getName() + "\" and all its subjects?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dataManager.deleteDegree(degree.getId());
                if (selectedDegree != null && selectedDegree.getId() == degree.getId()) {
                    List<DesktopDegree> remaining = dataManager.getDegrees();
                    selectedDegree = remaining.isEmpty() ? null : remaining.get(0);
                }
                refreshDegreeList();
                refreshSubjectGrid();
            }
        });

        card.add(colorIndicator, BorderLayout.WEST);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(btnDelete, BorderLayout.EAST);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                selectedDegree = degree;
                refreshDegreeList();
                refreshSubjectGrid();
            }
        });

        return card;
    }

    public void refreshSubjectGrid() {
        subjectGridContainer.removeAll();

        if (selectedDegree == null) {
            lblSubjectsHeader.setText("CURRICULUM SUBJECTS");
            JLabel empty = new JLabel("No degree program selected. Add or choose a degree program.");
            empty.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
            subjectGridContainer.add(empty);
            subjectGridContainer.revalidate();
            subjectGridContainer.repaint();
            return;
        }

        lblSubjectsHeader.setText("SUBJECTS IN " + selectedDegree.getName().toUpperCase());
        List<DesktopSubject> subjects = dataManager.getSubjectsForDegree(selectedDegree.getId());

        if (subjects.isEmpty()) {
            JPanel emptyPanel = new JPanel();
            emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
            emptyPanel.setOpaque(false);
            emptyPanel.setBorder(new EmptyBorder(32, 20, 20, 20));

            JLabel emptyTitle = new JLabel("No subjects registered yet for this program.");
            emptyTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
            emptyTitle.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

            JLabel emptySub = new JLabel("Click \"+ Add Subject\" above to add courses, modules, or focus topics.");
            emptySub.setFont(new Font("SansSerif", Font.PLAIN, 12));
            emptySub.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

            emptyPanel.add(emptyTitle);
            emptyPanel.add(Box.createVerticalStrut(4));
            emptyPanel.add(emptySub);
            subjectGridContainer.add(emptyPanel);
        } else {
            for (DesktopSubject subject : subjects) {
                JPanel subCard = createSubjectCard(subject);
                subjectGridContainer.add(subCard);
                subjectGridContainer.add(Box.createVerticalStrut(12));
            }
        }

        subjectGridContainer.revalidate();
        subjectGridContainer.repaint();
    }

    private JPanel createSubjectCard(DesktopSubject subject) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(EduTraceDesktop.COLOR_CARD_SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(EduTraceDesktop.COLOR_CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setLayout(new BorderLayout(14, 0));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        Color color;
        try {
            color = Color.decode(subject.getColorHex());
        } catch (Exception e) {
            color = new Color(0x60, 0xA5, 0xFA);
        }

        // Left: Subject Color Indicator & Title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        final Color finalColor = color;
        JPanel pill = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(finalColor);
                g2.fillRoundRect(0, 4, 8, 30, 4, 4);
                g2.dispose();
            }
        };
        pill.setPreferredSize(new Dimension(10, 38));
        pill.setOpaque(false);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        JLabel nameLabel = new JLabel(subject.getName());
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        nameLabel.setForeground(Color.WHITE);

        long todaySec = dataManager.getTodaySubjectStudySeconds(subject.getName());
        long totalSec = dataManager.getTotalSubjectStudySeconds(subject.getName());

        JLabel statsLabel = new JLabel(String.format("Today: %s  •  All-Time: %s", formatHoursMinutes(todaySec), formatHoursMinutes(totalSec)));
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statsLabel.setForeground(todaySec > 0 ? EduTraceDesktop.COLOR_STATUS_BREAK : EduTraceDesktop.COLOR_TEXT_SECONDARY);

        details.add(nameLabel);
        details.add(Box.createVerticalStrut(3));
        details.add(statsLabel);

        left.add(pill);
        left.add(details);

        // Right: Start Study Timer Button + Delete
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        right.setOpaque(false);

        JButton btnStart = new JButton("⏱ Start Timer");
        btnStart.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnStart.setForeground(Color.BLACK);
        btnStart.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnStart.setFocusPainted(false);
        btnStart.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnStart.addActionListener(e -> {
            if (onStartTimerForSubject != null) {
                onStartTimerForSubject.accept(subject.getName());
            }
        });

        JButton btnDelete = new JButton("🗑");
        btnDelete.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
        btnDelete.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
        btnDelete.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnDelete.setBorder(BorderFactory.createLineBorder(EduTraceDesktop.COLOR_CARD_BORDER, 1));
        btnDelete.setPreferredSize(new Dimension(34, 30));
        btnDelete.setFocusPainted(false);
        btnDelete.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDelete.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete subject \"" + subject.getName() + "\"?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dataManager.deleteSubject(subject.getId());
                refreshSubjectGrid();
                refreshDegreeList();
            }
        });

        right.add(btnStart);
        right.add(btnDelete);

        card.add(left, BorderLayout.WEST);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private void showAddDegreeDialog() {
        JTextField tfName = new JTextField();
        String[] colors = {"#3B82F6 (Blue)", "#8B5CF6 (Purple)", "#10B981 (Emerald)", "#FFB800 (Gold)", "#F59E0B (Amber)", "#EC4899 (Pink)", "#EF4444 (Red)"};
        JComboBox<String> cbColor = new JComboBox<>(colors);

        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.add(new JLabel("Degree / Major Title:"));
        panel.add(tfName);
        panel.add(new JLabel("Theme Color:"));
        panel.add(cbColor);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add New Degree Program", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = tfName.getText().trim();
            if (!name.isEmpty()) {
                String colorStr = (String) cbColor.getSelectedItem();
                String hex = colorStr != null ? colorStr.split(" ")[0] : "#3B82F6";
                DesktopDegree created = dataManager.addDegree(name, hex);
                if (created != null) {
                    selectedDegree = created;
                    refreshDegreeList();
                    refreshSubjectGrid();
                }
            }
        }
    }

    private void showAddSubjectDialog() {
        if (selectedDegree == null) {
            JOptionPane.showMessageDialog(this, "Please select or create a degree program first.", "EduTrace", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField tfName = new JTextField();
        String[] colors = {"#60A5FA (Light Blue)", "#FACC15 (Yellow)", "#10B981 (Emerald)", "#EC4899 (Pink)", "#A78BFA (Violet)", "#F97316 (Orange)"};
        JComboBox<String> cbColor = new JComboBox<>(colors);

        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.add(new JLabel("Subject / Course Name:"));
        panel.add(tfName);
        panel.add(new JLabel("Theme Color:"));
        panel.add(cbColor);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Subject to " + selectedDegree.getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = tfName.getText().trim();
            if (!name.isEmpty()) {
                String colorStr = (String) cbColor.getSelectedItem();
                String hex = colorStr != null ? colorStr.split(" ")[0] : "#60A5FA";
                dataManager.addSubject(selectedDegree.getId(), name, hex);
                refreshSubjectGrid();
                refreshDegreeList();
            }
        }
    }

    private String formatHoursMinutes(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
}

