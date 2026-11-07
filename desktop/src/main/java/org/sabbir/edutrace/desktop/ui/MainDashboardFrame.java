package org.sabbir.edutrace.desktop.ui;

import org.sabbir.edutrace.desktop.EduTraceDesktop;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudAuthManager;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudUser;
import org.sabbir.edutrace.desktop.ui.tabs.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Main application window for EduTrace Desktop Study Timer:
 * Porting the Android Study Timer, Degree List, and Subject List directly to desktop with
 * Obsidian dark theme, user avatar chip, sleek sidebar navigation, and zero exposed URLs.
 */
public class MainDashboardFrame extends JFrame {

    private final DesktopCloudAuthManager authManager = DesktopCloudAuthManager.getInstance();

    private CardLayout cardLayout;
    private JPanel contentContainer;

    private StudyTimerTab timerTab;
    private DegreesSubjectsTab degreesTab;
    private LeaderboardTab leaderboardTab;
    private AnalyticsTab analyticsTab;
    private CalculatorTab calculatorTab;
    private SettingsTab settingsTab;

    private JLabel headerTitleLabel;
    private JLabel headerSubtitleLabel;
    private JLabel liveClockLabel;
    private JButton userProfileBtn;

    private final Map<String, JButton> navButtons = new HashMap<>();
    private String currentTab = "TIMER";

    public MainDashboardFrame() {
        super("EduTrace - Study Timer & Curriculum Suite");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1160, 790);
        setMinimumSize(new Dimension(1000, 680));
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
        root.setBackground(EduTraceDesktop.COLOR_OBSIDIAN_BG);

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

        timerTab = new StudyTimerTab(() -> {
            if (analyticsTab != null) {
                analyticsTab.refreshData();
            }
            if (degreesTab != null) {
                degreesTab.refreshSubjectGrid();
                degreesTab.refreshDegreeList();
            }
        });

        degreesTab = new DegreesSubjectsTab(subjectName -> {
            timerTab.selectSubject(subjectName);
            switchTab("TIMER");
        });

        leaderboardTab = new LeaderboardTab(this::openAuthDialog);
        analyticsTab = new AnalyticsTab();
        calculatorTab = new CalculatorTab();
        settingsTab = new SettingsTab(() -> {
            analyticsTab.refreshData();
            degreesTab.refreshDegreeList();
            degreesTab.refreshSubjectGrid();
            timerTab.reloadSubjects();
            timerTab.updateIntelligenceMetrics();
            onAuthStateChanged();
        });

        contentContainer.add(timerTab, "TIMER");
        contentContainer.add(degreesTab, "DEGREES");
        contentContainer.add(leaderboardTab, "LEADERBOARD");
        contentContainer.add(analyticsTab, "ANALYTICS");
        contentContainer.add(calculatorTab, "CALCULATOR");
        contentContainer.add(settingsTab, "SETTINGS");

        rightArea.add(contentContainer, BorderLayout.CENTER);
        root.add(rightArea, BorderLayout.CENTER);

        setContentPane(root);
        switchTab("TIMER");
        updateUserHeader();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(236, 0));
        sidebar.setBackground(EduTraceDesktop.COLOR_SLATE_DARK);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, EduTraceDesktop.COLOR_CARD_BORDER));

        // Brand / Logo Top
        JPanel brandPanel = new JPanel();
        brandPanel.setLayout(new BoxLayout(brandPanel, BoxLayout.Y_AXIS));
        brandPanel.setOpaque(false);
        brandPanel.setBorder(new EmptyBorder(24, 20, 24, 20));

        JLabel brandTitle = new JLabel("EduTrace");
        brandTitle.setFont(new Font("SansSerif", Font.BOLD, 24));
        brandTitle.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);

        JLabel brandSub = new JLabel("STUDY SUITE & TIMER");
        brandSub.setFont(new Font("SansSerif", Font.BOLD, 10));
        brandSub.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);

        brandPanel.add(brandTitle);
        brandPanel.add(Box.createVerticalStrut(2));
        brandPanel.add(brandSub);

        // Navigation Items
        JPanel navList = new JPanel();
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setOpaque(false);
        navList.setBorder(new EmptyBorder(8, 12, 8, 12));

        navList.add(createNavButton("TIMER", "⏱  Study Timer"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("DEGREES", "🎓  Degrees & Subjects"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("LEADERBOARD", "🏆  Leaderboard"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("ANALYTICS", "📊  Study Analytics"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("CALCULATOR", "🧮  Calculator"));
        navList.add(Box.createVerticalStrut(6));
        navList.add(createNavButton("SETTINGS", "⚙  Settings"));

        // Sidebar Footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(16, 20, 20, 20));

        JLabel serverStatus = new JLabel("<html><span style='color:#10B981;'>●</span> Cloud Synchronized<br><span style='color:#64748B;'>Encrypted & Active</span></html>");
        serverStatus.setFont(new Font("SansSerif", Font.PLAIN, 10));
        footer.add(serverStatus, BorderLayout.SOUTH);

        sidebar.add(brandPanel, BorderLayout.NORTH);
        sidebar.add(navList, BorderLayout.CENTER);
        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton createNavButton(String tabKey, String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
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

        headerTitleLabel = new JLabel("Study Focus Timer");
        headerTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        headerTitleLabel.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        headerSubtitleLabel = new JLabel("Focus deeply with flow aura, acoustic atmospheres, and live session syncing.");
        headerSubtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        headerSubtitleLabel.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        titles.add(headerTitleLabel);
        titles.add(Box.createVerticalStrut(2));
        titles.add(headerSubtitleLabel);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        rightSide.setOpaque(false);

        liveClockLabel = new JLabel();
        liveClockLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        liveClockLabel.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        updateClock();

        // User profile pill matching Android header (Avatar + Name)
        userProfileBtn = new JButton("👤 Sign In");
        userProfileBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        userProfileBtn.setFocusPainted(false);
        userProfileBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        userProfileBtn.addActionListener(e -> openAuthDialog());

        // Notification Bell Button
        JButton notificationBtn = new JButton("🔔");
        notificationBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        notificationBtn.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        notificationBtn.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        notificationBtn.setFocusPainted(false);
        notificationBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        notificationBtn.addActionListener(e -> showNotificationsDialog(notificationBtn));

        // Periodic unread check
        javax.swing.Timer notifTimer = new javax.swing.Timer(60000, e -> {
            org.sabbir.edutrace.desktop.cloud.DesktopCloudDbManager.getUnreadNotificationCount(count -> {
                if (count > 0) {
                    notificationBtn.setText("🔔 " + count);
                    notificationBtn.setForeground(new Color(0xEF, 0x44, 0x44));
                } else {
                    notificationBtn.setText("🔔");
                    notificationBtn.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
                }
            });
        });
        notifTimer.start();

        rightSide.add(liveClockLabel);
        rightSide.add(notificationBtn);
        rightSide.add(userProfileBtn);

        header.add(titles, BorderLayout.WEST);
        header.add(rightSide, BorderLayout.EAST);
        return header;
    }

    private void openAuthDialog() {
        AuthDialog dialog = new AuthDialog(this, this::onAuthStateChanged);
        dialog.setVisible(true);
    }

    private void showNotificationsDialog(JButton notificationBtn) {
        JDialog dialog = new JDialog(this, "Notification Center", true);
        dialog.setSize(480, 520);
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(EduTraceDesktop.COLOR_OBSIDIAN_BG);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("IN-APP NOTIFICATIONS & ALERTS");
        title.setFont(new Font("SansSerif", Font.BOLD, 12));
        title.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);

        JButton btnMarkAll = new JButton("Mark All Read");
        btnMarkAll.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnMarkAll.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
        btnMarkAll.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        btnMarkAll.setFocusPainted(false);

        header.add(title, BorderLayout.WEST);
        header.add(btnMarkAll, BorderLayout.EAST);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JLabel loading = new JLabel("Loading notifications from EduTrace Server...");
        loading.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
        listPanel.add(loading);

        org.sabbir.edutrace.desktop.cloud.DesktopCloudDbManager.fetchNotifications(false, (notifications, unreadCount) -> {
            listPanel.removeAll();
            if (notifications == null || notifications.isEmpty()) {
                JLabel empty = new JLabel("No new notifications at this time.");
                empty.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
                listPanel.add(empty);
            } else {
                for (org.sabbir.edutrace.desktop.cloud.DesktopCloudNotification n : notifications) {
                    JPanel item = new JPanel(new BorderLayout(8, 4));
                    item.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
                    item.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(EduTraceDesktop.COLOR_CARD_BORDER, 1),
                            new EmptyBorder(10, 12, 10, 12)
                    ));

                    JLabel nTitle = new JLabel((n.isRead ? "○ " : "● ") + n.title);
                    nTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
                    nTitle.setForeground(n.isRead ? EduTraceDesktop.COLOR_TEXT_SECONDARY : EduTraceDesktop.COLOR_ELITE_GOLD);

                    JLabel nMsg = new JLabel("<html><body style='color:#94A3B8; font-size:11px;'>" + n.message + "</body></html>");

                    item.add(nTitle, BorderLayout.NORTH);
                    item.add(nMsg, BorderLayout.CENTER);

                    listPanel.add(item);
                    listPanel.add(Box.createVerticalStrut(8));
                }
            }
            listPanel.revalidate();
            listPanel.repaint();
        });

        btnMarkAll.addActionListener(e -> {
            org.sabbir.edutrace.desktop.cloud.DesktopCloudDbManager.markAllNotificationsAsRead(msg -> {
                notificationBtn.setText("🔔");
                notificationBtn.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
                dialog.dispose();
            });
        });

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private void onAuthStateChanged() {
        updateUserHeader();
        leaderboardTab.loadLeaderboard("all_time");
        timerTab.updateIntelligenceMetrics();
    }

    private void updateUserHeader() {
        if (authManager.isLoggedIn()) {
            DesktopCloudUser u = authManager.getCurrentUser();
            userProfileBtn.setText("👤 " + (u != null ? u.getDisplayName() : "Scholar") + "  ●");
            userProfileBtn.setBackground(new Color(0x1B, 0x1B, 0x2A));
            userProfileBtn.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK); // Emerald
        } else {
            userProfileBtn.setText("👤 Sign In / Register");
            userProfileBtn.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
            userProfileBtn.setForeground(new Color(0x0A, 0x0A, 0x0C));
        }
    }

    public void switchTab(String tabKey) {
        currentTab = tabKey;
        cardLayout.show(contentContainer, tabKey);

        // Update nav button states
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton b = entry.getValue();
            boolean active = entry.getKey().equals(tabKey);
            if (active) {
                b.setBackground(EduTraceDesktop.COLOR_CARD_SURFACE);
                b.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, EduTraceDesktop.COLOR_ELITE_GOLD),
                        new EmptyBorder(0, 12, 0, 0)
                ));
            } else {
                b.setBackground(EduTraceDesktop.COLOR_SLATE_DARK);
                b.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
                b.setBorder(new EmptyBorder(0, 16, 0, 0));
            }
        }

        // Update titles (NO exposed URLs)
        switch (tabKey) {
            case "TIMER":
                headerTitleLabel.setText("Study Focus Timer");
                headerSubtitleLabel.setText("Focus deeply with flow aura, acoustic atmospheres, and live session syncing.");
                timerTab.reloadSubjects();
                timerTab.updateIntelligenceMetrics();
                break;
            case "DEGREES":
                headerTitleLabel.setText("Degree Programs & Subjects");
                headerSubtitleLabel.setText("Manage your degree programs, courses, and view focus progress per subject.");
                degreesTab.refreshDegreeList();
                degreesTab.refreshSubjectGrid();
                break;
            case "LEADERBOARD":
                headerTitleLabel.setText("Global Scholar Leaderboard");
                headerSubtitleLabel.setText("Real-time scholar rankings powered by EduTrace Cloud.");
                leaderboardTab.loadLeaderboard("all_time");
                break;
            case "ANALYTICS":
                headerTitleLabel.setText("Analytics & Study Reports");
                headerSubtitleLabel.setText("Track completed study sessions, time per subject, and export history.");
                analyticsTab.refreshData();
                break;
            case "CALCULATOR":
                headerTitleLabel.setText("Scientific Calculator");
                headerSubtitleLabel.setText("High-performance math evaluator with trigonometry, logs, and powers.");
                break;
            case "SETTINGS":
                headerTitleLabel.setText("Settings & Data Management");
                headerSubtitleLabel.setText("Manage cloud synchronization, local storage, pomodoro, and preferences.");
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
