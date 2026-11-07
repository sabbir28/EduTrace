package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.cloud.DesktopCloudAuthManager;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudRankingsManager;
import org.sabbir.edutrace.desktop.cloud.DesktopRankingUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class LeaderboardTab extends JPanel {

    private final DesktopCloudRankingsManager rankingsManager;
    private final DesktopCloudAuthManager authManager;
    private final Runnable openAuthDialogCallback;

    private String currentTimeframe = "all_time";
    private JLabel currentRankCardLabel;
    private JLabel totalParticipantsLabel;
    private DefaultTableModel tableModel;
    private JTable rankingsTable;
    private JProgressBar loadingBar;

    public LeaderboardTab(Runnable openAuthDialogCallback) {
        this.rankingsManager = DesktopCloudRankingsManager.getInstance();
        this.authManager = DesktopCloudAuthManager.getInstance();
        this.openAuthDialogCallback = openAuthDialogCallback;

        setLayout(new BorderLayout(16, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
        loadLeaderboard("all_time");
    }

    private void initUI() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // 1. Timeframe Navigation Buttons Row
        contentPanel.add(createFilterRow());
        contentPanel.add(Box.createVerticalStrut(12));

        // 2. Current User Rank Banner
        contentPanel.add(createCurrentUserCard());
        contentPanel.add(Box.createVerticalStrut(12));

        // 3. Loading Bar
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        contentPanel.add(loadingBar);
        contentPanel.add(Box.createVerticalStrut(8));

        // 4. Leaderboard Table Card
        contentPanel.add(createTableCard());

        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createFilterRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnGroup.setOpaque(false);

        String[][] filters = {
                {"all_time", "🏆 All-Time"},
                {"monthly", "📊 This Month"},
                {"weekly", "🗓 This Week"},
                {"daily", "📅 Today"}
        };

        ButtonGroup bg = new ButtonGroup();
        for (String[] f : filters) {
            JToggleButton btn = new JToggleButton(f[1], f[0].equals(currentTimeframe));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btn.addActionListener(e -> loadLeaderboard(f[0]));
            bg.add(btn);
            btnGroup.add(btn);
        }

        JButton btnRefresh = new JButton("↻ Refresh");
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRefresh.addActionListener(e -> loadLeaderboard(currentTimeframe));

        row.add(btnGroup, BorderLayout.WEST);
        row.add(btnRefresh, BorderLayout.EAST);
        return row;
    }

    private JPanel createCurrentUserCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(12, 10));

        currentRankCardLabel = new JLabel("Loading your ranking from EduTrace Cloud...");
        currentRankCardLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        currentRankCardLabel.setForeground(new Color(0xF1, 0xF5, 0xF9));

        JButton btnAuth = new JButton("Sign In / Account");
        btnAuth.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAuth.setForeground(new Color(0xE2, 0xB7, 0x14));
        btnAuth.addActionListener(e -> {
            if (openAuthDialogCallback != null) openAuthDialogCallback.run();
        });

        card.add(currentRankCardLabel, BorderLayout.CENTER);
        card.add(btnAuth, BorderLayout.EAST);
        return card;
    }

    private JPanel createTableCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(10, 10));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel title = new JLabel("GLOBAL SCHOLAR STANDINGS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        totalParticipantsLabel = new JLabel("Total Scholars: --");
        totalParticipantsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        totalParticipantsLabel.setForeground(new Color(0x94, 0xA3, 0xB8));

        topRow.add(title, BorderLayout.WEST);
        topRow.add(totalParticipantsLabel, BorderLayout.EAST);

        String[] cols = {"Rank", "Scholar Name", "Tier", "Study Time", "Streak"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        rankingsTable = new JTable(tableModel);
        rankingsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rankingsTable.setRowHeight(36);
        rankingsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        rankingsTable.getTableHeader().setForeground(new Color(0xE2, 0xB7, 0x14));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        rankingsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        rankingsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        rankingsTable.getColumnModel().getColumn(1).setPreferredWidth(260);

        rankingsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        rankingsTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        rankingsTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        rankingsTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        rankingsTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        rankingsTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        JScrollPane scroll = new JScrollPane(rankingsTable);
        scroll.setPreferredSize(new Dimension(0, 360));

        card.add(topRow, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    public void loadLeaderboard(String timeframe) {
        this.currentTimeframe = timeframe;
        loadingBar.setVisible(true);

        rankingsManager.fetchLeaderboard(timeframe, new DesktopCloudRankingsManager.LeaderboardCallback() {
            @Override
            public void onSuccess(List<DesktopRankingUser> users, DesktopRankingUser currentUserRank, int totalParticipants) {
                loadingBar.setVisible(false);
                totalParticipantsLabel.setText("Total Scholars: " + totalParticipants);
                tableModel.setRowCount(0);

                for (DesktopRankingUser u : users) {
                    String rankBadge;
                    if (u.rank == 1) rankBadge = "🥇 #1";
                    else if (u.rank == 2) rankBadge = "🥈 #2";
                    else if (u.rank == 3) rankBadge = "🥉 #3";
                    else rankBadge = "#" + u.rank;

                    String name = u.displayName;
                    if (u.isCurrentUser) {
                        name = "★ " + name + " (You)";
                    }

                    tableModel.addRow(new Object[]{
                            rankBadge,
                            name,
                            u.tier,
                            u.formattedTime,
                            u.streakDays > 0 ? (u.streakDays + " 🔥") : "0"
                    });
                }

                // Update current user rank card
                if (authManager.isLoggedIn()) {
                    if (currentUserRank != null) {
                        currentRankCardLabel.setText("<html><b style='color:#E2B714;'>Your Standing:</b> Rank <b>#"
                                + currentUserRank.rank + "</b> &bull; Time: <b>" + currentUserRank.formattedTime
                                + "</b> &bull; Tier: <b>" + currentUserRank.tier + "</b> &bull; Streak: <b>"
                                + currentUserRank.streakDays + " days</b></html>");
                    } else {
                        currentRankCardLabel.setText("<html>Signed in as <b>" + authManager.getCurrentUser().getDisplayName()
                                + "</b>. Complete a focus session to appear on the " + timeframe + " leaderboard!</html>");
                    }
                } else {
                    currentRankCardLabel.setText("<html><span style='color:#94A3B8;'>Sign in with your EduTrace account to track your global rank and compete with students!</span></html>");
                }
            }

            @Override
            public void onError(String message) {
                loadingBar.setVisible(false);
                currentRankCardLabel.setText("<html><span style='color:#EF4444;'>Failed to load leaderboard: " + message + "</span></html>");
            }
        });
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
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        return card;
    }
}
