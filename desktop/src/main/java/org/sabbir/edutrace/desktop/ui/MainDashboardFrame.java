package org.sabbir.edutrace.desktop.ui;

import org.sabbir.edutrace.desktop.data.DesktopQuoteManager;
import org.sabbir.edutrace.desktop.ui.tabs.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainDashboardFrame extends JFrame {

    private final DesktopQuoteManager quoteManager = new DesktopQuoteManager();

    private CardLayout cardLayout;
    private JPanel contentContainer;

    private DashboardTab dashboardTab;
    private StudyTimerTab timerTab;
    private CalculatorTab calculatorTab;
    private AnalyticsTab analyticsTab;
    private SettingsTab settingsTab;

    private JLabel headerTitleLabel;
    private JLabel headerSubtitleLabel;
    private JLabel liveClockLabel;

    private final Map<String, JButton> navButtons = new HashMap<>();
    private String currentTab = "DASHBOARD";

    public MainDashboardFrame() {
        super("EduTrace - Focus & Study Suite (Desktop)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(950, 640));
        setLocationRelativeTo(null);

        // Window icon
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/icons/edutrace.png"));
            setIconImage(icon.getImage());
        } catch (Exception ignored) {}

        initUI();
        initClockTimer();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x0F, 0x17, 0x2A)); // Dark Slate

        // Left Sidebar
        JPanel sidebar = createSidebar();
        root.add(sidebar, BorderLayout.WEST);

        // Right Main Area
        JPanel rightArea = new JPanel(new BorderLayout());
        rightArea.setOpaque(false);

        // Top App Header
        JPanel topHeader = createTopHeader();
        rightArea.add(topHeader, BorderLayout.NORTH);

        // Tabs Card Container
        cardLayout = new CardLayout();
        contentContainer = new JPanel(cardLayout);
        contentContainer.setOpaque(false);

        dashboardTab = new DashboardTab(quoteManager, expr -> {
            switchTab("CALCULATOR");
            calculatorTab.setExpression(expr);
        });
        timerTab = new StudyTimerTab();
        calculatorTab = new CalculatorTab();
        analyticsTab = new AnalyticsTab();
        settingsTab = new SettingsTab(() -> {
            dashboardTab.refreshData();
            analyticsTab.refreshData();
        });

        contentContainer.add(dashboardTab, "DASHBOARD");
        contentContainer.add(timerTab, "TIMER");
        contentContainer.add(calculatorTab, "CALCULATOR");
        contentContainer.add(analyticsTab, "ANALYTICS");
        contentContainer.add(settingsTab, "SETTINGS");

        rightArea.add(contentContainer, BorderLayout.CENTER);
        root.add(rightArea, BorderLayout.CENTER);

        setContentPane(root);
        switchTab("DASHBOARD");
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBackground(new Color(0x09, 0x0D, 0x16)); // Deepest slate
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x1E, 0x29, 0x3B)));

        // Brand / Logo Top
        JPanel brandPanel = new JPanel();
        brandPanel.setLayout(new BoxLayout(brandPanel, BoxLayout.Y_AXIS));
        brandPanel.setOpaque(false);
        brandPanel.setBorder(new EmptyBorder(24, 20, 24, 20));

        JLabel brandTitle = new JLabel("EduTrace");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        brandTitle.setForeground(new Color(0xE2, 0xB7, 0x14)); // Elite Gold

        JLabel brandSub = new JLabel("DESKTOP FOCUS SUITE");
        brandSub.setFont(new Font("Segoe UI", Font.BOLD, 10));
        brandSub.setForeground(new Color(0x64, 0x74, 0x8B));

        brandPanel.add(brandTitle);
        brandPanel.add(Box.createVerticalStrut(2));
        brandPanel.add(brandSub);

        // Nav Items List
        JPanel navList = new JPanel();
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setOpaque(false);
        navList.setBorder(new EmptyBorder(8, 12, 8, 12));

        navList.add(createNavButton("DASHBOARD", "⚡  Focus Dashboard"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("TIMER", "⏱  Study Timer"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("CALCULATOR", "🧮  Calculator"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("ANALYTICS", "📊  Analytics"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("SETTINGS", "⚙  Settings"));

        // Sidebar Footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(16, 20, 20, 20));

        JLabel version = new JLabel("EduTrace v1.0 Desktop");
        version.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        version.setForeground(new Color(0x47, 0x55, 0x69));
        footer.add(version, BorderLayout.SOUTH);

        sidebar.add(brandPanel, BorderLayout.NORTH);
        sidebar.add(navList, BorderLayout.CENTER);
        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton createNavButton(String tabKey, String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btn.setPreferredSize(new Dimension(200, 42));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> switchTab(tabKey));
        navButtons.put(tabKey, btn);
        return btn;
    }

    private JPanel createTopHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(18, 24, 8, 24));

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);

        headerTitleLabel = new JLabel("Focus Dashboard");
        headerTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerTitleLabel.setForeground(new Color(0xF1, 0xF5, 0xF9));

        headerSubtitleLabel = new JLabel("Your minimal, education-first study workstation.");
        headerSubtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        headerSubtitleLabel.setForeground(new Color(0x94, 0xA3, 0xB8));

        titles.add(headerTitleLabel);
        titles.add(Box.createVerticalStrut(2));
        titles.add(headerSubtitleLabel);

        liveClockLabel = new JLabel();
        liveClockLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        liveClockLabel.setForeground(new Color(0xE2, 0xB7, 0x14));
        updateClock();

        header.add(titles, BorderLayout.WEST);
        header.add(liveClockLabel, BorderLayout.EAST);
        return header;
    }

    private void switchTab(String tabKey) {
        currentTab = tabKey;
        cardLayout.show(contentContainer, tabKey);

        // Update nav button states
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton b = entry.getValue();
            boolean active = entry.getKey().equals(tabKey);
            if (active) {
                b.setBackground(new Color(0x1E, 0x29, 0x3B));
                b.setForeground(new Color(0xE2, 0xB7, 0x14));
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(0xE2, 0xB7, 0x14)),
                        new EmptyBorder(0, 12, 0, 0)
                ));
            } else {
                b.setBackground(new Color(0x09, 0x0D, 0x16));
                b.setForeground(new Color(0x94, 0xA3, 0xB8));
                b.setBorder(new EmptyBorder(0, 16, 0, 0));
            }
        }

        // Update titles
        switch (tabKey) {
            case "DASHBOARD":
                headerTitleLabel.setText("Focus Dashboard");
                headerSubtitleLabel.setText("Your daily study momentum, inspiring quotes, and task checklist.");
                dashboardTab.refreshData();
                break;
            case "TIMER":
                headerTitleLabel.setText("Study Timer & Pomodoro");
                headerSubtitleLabel.setText("Focus deeply with interval timers and synthesized ambient soundscapes.");
                break;
            case "CALCULATOR":
                headerTitleLabel.setText("Scientific Calculator");
                headerSubtitleLabel.setText("High-performance math evaluator with trigonometry, logs, and powers.");
                break;
            case "ANALYTICS":
                headerTitleLabel.setText("Analytics & Study Reports");
                headerSubtitleLabel.setText("Track completed study sessions, time per subject, and export history.");
                analyticsTab.refreshData();
                break;
            case "SETTINGS":
                headerTitleLabel.setText("Settings & Data Management");
                headerSubtitleLabel.setText("Manage local storage, pomodoro durations, and quote languages.");
                break;
        }
    }

    private void initClockTimer() {
        javax.swing.Timer clockTimer = new javax.swing.Timer(1000, e -> updateClock());
        clockTimer.start();
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss  •  EEEE, MMM d", Locale.getDefault());
        liveClockLabel.setText(sdf.format(new Date()));
    }
}
