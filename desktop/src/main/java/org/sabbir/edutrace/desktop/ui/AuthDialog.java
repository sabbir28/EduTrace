package org.sabbir.edutrace.desktop.ui;

import org.sabbir.edutrace.desktop.EduTraceDesktop;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudAuthManager;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Authentication dialog replicating the Android activity_auth.xml design language:
 * Obsidian dark background, cloud badge icon, material pill toggles, and zero API URL exposure.
 */
public class AuthDialog extends JDialog {

    private final DesktopCloudAuthManager authManager;
    private final Runnable onAuthStateChangedCallback;

    private JPanel formContainer;
    private CardLayout formCardLayout;
    private JButton btnPillLogin;
    private JButton btnPillRegister;

    public AuthDialog(Frame parent, Runnable onAuthStateChangedCallback) {
        super(parent, "EduTrace Cloud", true);
        this.authManager = DesktopCloudAuthManager.getInstance();
        this.onAuthStateChangedCallback = onAuthStateChangedCallback;

        setSize(480, 540);
        setLocationRelativeTo(parent);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(EduTraceDesktop.COLOR_OBSIDIAN_BG);

        // Header Section (Matching Android activity_auth.xml)
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(28, 24, 16, 24));

        JLabel iconLabel = new JLabel("☁");
        iconLabel.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 44));
        iconLabel.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel(authManager.isLoggedIn() ? "EduTrace Cloud Profile" : "Welcome to EduTrace Cloud");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("<html><center style='color:#94A3B8; font-size:11px;'>"
                + "Sync your study timer, degrees, and sessions securely with EduTrace Cloud</center></html>");
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(iconLabel);
        headerPanel.add(Box.createVerticalStrut(8));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(subtitleLabel);

        root.add(headerPanel, BorderLayout.NORTH);

        if (authManager.isLoggedIn()) {
            root.add(createLoggedInPanel(), BorderLayout.CENTER);
        } else {
            JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
            centerPanel.setOpaque(false);
            centerPanel.setBorder(new EmptyBorder(0, 24, 24, 24));

            // Pill toggle group (Sign In / Register)
            centerPanel.add(createPillToggleRow(), BorderLayout.NORTH);

            // Card container for Login and Register forms
            formCardLayout = new CardLayout();
            formContainer = new JPanel(formCardLayout);
            formContainer.setOpaque(false);

            formContainer.add(createLoginForm(), "LOGIN");
            formContainer.add(createRegisterForm(), "REGISTER");

            centerPanel.add(formContainer, BorderLayout.CENTER);
            root.add(centerPanel, BorderLayout.CENTER);
        }

        setContentPane(root);
    }

    private JPanel createPillToggleRow() {
        JPanel pillWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pillWrapper.setOpaque(false);

        JPanel pill = new JPanel(new GridLayout(1, 2, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1A, 0x1A, 0x28));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(EduTraceDesktop.COLOR_CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setOpaque(false);
        pill.setPreferredSize(new Dimension(240, 36));

        btnPillLogin = new JButton("Sign In");
        btnPillLogin.setFocusPainted(false);
        btnPillLogin.setBorder(null);
        btnPillLogin.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnPillLogin.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnPillRegister = new JButton("Register");
        btnPillRegister.setFocusPainted(false);
        btnPillRegister.setBorder(null);
        btnPillRegister.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnPillRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));

        updatePillState(true);

        btnPillLogin.addActionListener(e -> {
            updatePillState(true);
            formCardLayout.show(formContainer, "LOGIN");
        });

        btnPillRegister.addActionListener(e -> {
            updatePillState(false);
            formCardLayout.show(formContainer, "REGISTER");
        });

        pill.add(btnPillLogin);
        pill.add(btnPillRegister);
        pillWrapper.add(pill);
        return pillWrapper;
    }

    private void updatePillState(boolean isLoginSelected) {
        if (isLoginSelected) {
            btnPillLogin.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
            btnPillLogin.setForeground(new Color(0x0A, 0x0A, 0x0C));
            btnPillRegister.setBackground(new Color(0x1A, 0x1A, 0x28));
            btnPillRegister.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        } else {
            btnPillRegister.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
            btnPillRegister.setForeground(new Color(0x0A, 0x0A, 0x0C));
            btnPillLogin.setBackground(new Color(0x1A, 0x1A, 0x28));
            btnPillLogin.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        }
    }

    private JPanel createLoginForm() {
        JPanel card = createStyledCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JTextField tfUser = createStyledTextField("Username or Email");
        JPasswordField pfPass = createStyledPasswordField("Password");

        JLabel lblErr = new JLabel(" ");
        lblErr.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblErr.setForeground(new Color(0xEF, 0x44, 0x44));
        lblErr.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnSubmit = new JButton("Sign In to EduTrace");
        btnSubmit.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnSubmit.setBackground(EduTraceDesktop.COLOR_ELITE_GOLD);
        btnSubmit.setForeground(new Color(0x0A, 0x0A, 0x0C));
        btnSubmit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnSubmit.setFocusPainted(false);
        btnSubmit.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnSubmit.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pfPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) {
                lblErr.setText("Please enter username and password.");
                return;
            }
            lblErr.setText("Signing in...");
            btnSubmit.setEnabled(false);

            authManager.login(u, p, new DesktopCloudAuthManager.AuthCallback() {
                @Override
                public void onSuccess(DesktopCloudUser user) {
                    JOptionPane.showMessageDialog(AuthDialog.this,
                            "Welcome back, " + user.getDisplayName() + "!",
                            "Sign In Successful", JOptionPane.INFORMATION_MESSAGE);
                    if (onAuthStateChangedCallback != null) onAuthStateChangedCallback.run();
                    dispose();
                }

                @Override
                public void onError(String message) {
                    btnSubmit.setEnabled(true);
                    lblErr.setText("<html>" + message + "</html>");
                }
            });
        });

        card.add(createFieldLabel("USERNAME OR EMAIL"));
        card.add(Box.createVerticalStrut(4));
        card.add(tfUser);
        card.add(Box.createVerticalStrut(12));
        card.add(createFieldLabel("PASSWORD"));
        card.add(Box.createVerticalStrut(4));
        card.add(pfPass);

        JButton btnForgot = new JButton("Forgot Password? 🔒");
        btnForgot.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnForgot.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);
        btnForgot.setContentAreaFilled(false);
        btnForgot.setBorder(null);
        btnForgot.setFocusPainted(false);
        btnForgot.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnForgot.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnForgot.addActionListener(e -> showResetPasswordDialog());

        card.add(Box.createVerticalStrut(6));
        card.add(btnForgot);
        card.add(Box.createVerticalStrut(4));
        card.add(lblErr);
        card.add(Box.createVerticalStrut(8));
        card.add(btnSubmit);
        return card;
    }

    private JPanel createRegisterForm() {
        JPanel card = createStyledCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JTextField tfUser = createStyledTextField("Choose a username");
        JTextField tfName = createStyledTextField("Full name (e.g. Sabbir)");
        JTextField tfEmail = createStyledTextField("Email address");
        JPasswordField pfPass = createStyledPasswordField("Password (min 6 chars)");

        JLabel lblErr = new JLabel(" ");
        lblErr.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblErr.setForeground(new Color(0xEF, 0x44, 0x44));
        lblErr.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnSubmit = new JButton("Create Cloud Account");
        btnSubmit.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnSubmit.setBackground(EduTraceDesktop.COLOR_STATUS_BREAK); // Emerald
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnSubmit.setFocusPainted(false);
        btnSubmit.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnSubmit.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String name = tfName.getText().trim();
            String email = tfEmail.getText().trim();
            String p = new String(pfPass.getPassword()).trim();

            if (u.isEmpty() || email.isEmpty() || p.isEmpty()) {
                lblErr.setText("Please fill in all required fields.");
                return;
            }
            lblErr.setText("Creating account...");
            btnSubmit.setEnabled(false);

            authManager.register(u, email, p, name, new DesktopCloudAuthManager.AuthCallback() {
                @Override
                public void onSuccess(DesktopCloudUser user) {
                    JOptionPane.showMessageDialog(AuthDialog.this,
                            "Account created successfully! Welcome, " + user.getDisplayName() + "!",
                            "Registration Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (onAuthStateChangedCallback != null) onAuthStateChangedCallback.run();
                    dispose();
                }

                @Override
                public void onError(String message) {
                    btnSubmit.setEnabled(true);
                    lblErr.setText("<html>" + message + "</html>");
                }
            });
        });

        card.add(createFieldLabel("USERNAME"));
        card.add(Box.createVerticalStrut(2));
        card.add(tfUser);
        card.add(Box.createVerticalStrut(8));
        card.add(createFieldLabel("DISPLAY NAME"));
        card.add(Box.createVerticalStrut(2));
        card.add(tfName);
        card.add(Box.createVerticalStrut(8));
        card.add(createFieldLabel("EMAIL"));
        card.add(Box.createVerticalStrut(2));
        card.add(tfEmail);
        card.add(Box.createVerticalStrut(8));
        card.add(createFieldLabel("PASSWORD"));
        card.add(Box.createVerticalStrut(2));
        card.add(pfPass);
        card.add(Box.createVerticalStrut(6));
        card.add(lblErr);
        card.add(Box.createVerticalStrut(6));
        card.add(btnSubmit);
        return card;
    }

    private JPanel createLoggedInPanel() {
        JPanel card = createStyledCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(24, 24, 24, 24));

        DesktopCloudUser u = authManager.getCurrentUser();
        JLabel nameLbl = new JLabel(u != null ? u.getDisplayName() : "Scholar");
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        nameLbl.setForeground(EduTraceDesktop.COLOR_TEXT_PRIMARY);

        JLabel userLbl = new JLabel("@" + (u != null ? u.getUsername() : ""));
        userLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        userLbl.setForeground(EduTraceDesktop.COLOR_ELITE_GOLD);

        JLabel emailLbl = new JLabel(u != null ? u.getEmail() : "");
        emailLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        emailLbl.setForeground(EduTraceDesktop.COLOR_TEXT_SECONDARY);

        JLabel statusLbl = new JLabel("● Cloud Synchronized & Active");
        statusLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLbl.setForeground(EduTraceDesktop.COLOR_STATUS_BREAK);

        JButton btnLogout = new JButton("Sign Out");
        btnLogout.setBackground(new Color(0xEF, 0x44, 0x44));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnLogout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btnLogout.addActionListener(e -> {
            authManager.logout(new DesktopCloudAuthManager.ActionCallback() {
                @Override
                public void onSuccess(String message) {
                    if (onAuthStateChangedCallback != null) onAuthStateChangedCallback.run();
                    dispose();
                }

                @Override
                public void onError(String message) {
                    dispose();
                }
            });
        });

        card.add(nameLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(userLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(emailLbl);
        card.add(Box.createVerticalStrut(14));
        card.add(statusLbl);
        card.add(Box.createVerticalStrut(24));
        card.add(btnLogout);
        return card;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setForeground(EduTraceDesktop.COLOR_TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private void showResetPasswordDialog() {
        JTextField tfUser = new JTextField();
        JPasswordField pfNewPass = new JPasswordField();

        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.add(new JLabel("Username or Email:"));
        panel.add(tfUser);
        panel.add(new JLabel("New Password:"));
        panel.add(pfNewPass);

        int result = JOptionPane.showConfirmDialog(this, panel, "Reset Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String u = tfUser.getText().trim();
            String p = new String(pfNewPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter both username and new password.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            authManager.resetPassword(u, p, new DesktopCloudAuthManager.ActionCallback() {
                @Override
                public void onSuccess(String message) {
                    JOptionPane.showMessageDialog(AuthDialog.this, message, "Password Reset", JOptionPane.INFORMATION_MESSAGE);
                }

                @Override
                public void onError(String message) {
                    JOptionPane.showMessageDialog(AuthDialog.this, message, "Notice", JOptionPane.WARNING_MESSAGE);
                }
            });
        }
    }

    private JTextField createStyledTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        tf.setPreferredSize(new Dimension(300, 38));
        return tf;
    }

    private JPasswordField createStyledPasswordField(String placeholder) {
        JPasswordField pf = new JPasswordField();
        pf.putClientProperty("JTextField.placeholderText", placeholder);
        pf.setFont(new Font("SansSerif", Font.PLAIN, 13));
        pf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        pf.setPreferredSize(new Dimension(300, 38));
        return pf;
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
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        return card;
    }
}
