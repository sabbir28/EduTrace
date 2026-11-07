package org.sabbir.edutrace.desktop.ui;

import org.sabbir.edutrace.desktop.cloud.DesktopCloudApiClient;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudAuthManager;
import org.sabbir.edutrace.desktop.cloud.DesktopCloudUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AuthDialog extends JDialog {

    private final DesktopCloudAuthManager authManager;
    private final Runnable onAuthStateChangedCallback;

    public AuthDialog(Frame parent, Runnable onAuthStateChangedCallback) {
        super(parent, "EduTrace Cloud Account", true);
        this.authManager = DesktopCloudAuthManager.getInstance();
        this.onAuthStateChangedCallback = onAuthStateChangedCallback;

        setSize(460, 480);
        setLocationRelativeTo(parent);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(0x0F, 0x17, 0x2A));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel(authManager.isLoggedIn() ? "Account Profile" : "EduTrace Cloud Sign In");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        JLabel serverLabel = new JLabel("<html><code style='color:#38BDF8; font-size:10px;'>" + DesktopCloudApiClient.getServerUrl() + "</code></html>");

        header.add(title, BorderLayout.NORTH);
        header.add(serverLabel, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        if (authManager.isLoggedIn()) {
            root.add(createProfilePanel(), BorderLayout.CENTER);
        } else {
            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
            tabbedPane.addTab("Sign In", createLoginPanel());
            tabbedPane.addTab("Create Account", createRegisterPanel());
            root.add(tabbedPane, BorderLayout.CENTER);
        }

        setContentPane(root);
    }

    private JPanel createProfilePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(20, 10, 20, 10));

        DesktopCloudUser u = authManager.getCurrentUser();
        JLabel nameLbl = new JLabel("Name: " + (u != null ? u.getDisplayName() : ""));
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        nameLbl.setForeground(Color.WHITE);

        JLabel userLbl = new JLabel("Username: " + (u != null ? u.getUsername() : ""));
        userLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userLbl.setForeground(new Color(0x94, 0xA3, 0xB8));

        JLabel emailLbl = new JLabel("Email: " + (u != null ? u.getEmail() : ""));
        emailLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        emailLbl.setForeground(new Color(0x94, 0xA3, 0xB8));

        JLabel statusLbl = new JLabel("Status: Connected to EduTrace Cloud");
        statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLbl.setForeground(new Color(0x10, 0xB9, 0x81));

        JButton btnLogout = new JButton("Log Out");
        btnLogout.setBackground(new Color(0xEF, 0x44, 0x44));
        btnLogout.setForeground(Color.WHITE);
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

        panel.add(nameLbl);
        panel.add(Box.createVerticalStrut(8));
        panel.add(userLbl);
        panel.add(Box.createVerticalStrut(6));
        panel.add(emailLbl);
        panel.add(Box.createVerticalStrut(8));
        panel.add(statusLbl);
        panel.add(Box.createVerticalStrut(24));
        panel.add(btnLogout);

        return panel;
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 6, 6));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTextField tfUser = new JTextField();
        tfUser.putClientProperty("JTextField.placeholderText", "Username or Email (e.g. student)");

        JPasswordField pfPass = new JPasswordField();
        pfPass.putClientProperty("JTextField.placeholderText", "Password");

        JLabel lblErr = new JLabel("");
        lblErr.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblErr.setForeground(new Color(0xEF, 0x44, 0x44));

        JButton btnLogin = new JButton("Sign In to EduTrace");
        btnLogin.setBackground(new Color(0xE2, 0xB7, 0x14));
        btnLogin.setForeground(new Color(0x0F, 0x17, 0x2A));
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));

        btnLogin.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pfPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) {
                lblErr.setText("Please enter username and password.");
                return;
            }
            lblErr.setText("Signing in to EduTrace Cloud...");
            btnLogin.setEnabled(false);

            authManager.login(u, p, new DesktopCloudAuthManager.AuthCallback() {
                @Override
                public void onSuccess(DesktopCloudUser user) {
                    JOptionPane.showMessageDialog(AuthDialog.this,
                            "Welcome back, " + user.getDisplayName() + "! Cloud sync is active.",
                            "Sign In Successful", JOptionPane.INFORMATION_MESSAGE);
                    if (onAuthStateChangedCallback != null) onAuthStateChangedCallback.run();
                    dispose();
                }

                @Override
                public void onError(String message) {
                    btnLogin.setEnabled(true);
                    lblErr.setText("<html>" + message + "</html>");
                }
            });
        });

        panel.add(new JLabel("Username or Email:"));
        panel.add(tfUser);
        panel.add(new JLabel("Password:"));
        panel.add(pfPass);
        panel.add(lblErr);
        panel.add(btnLogin);
        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridLayout(9, 1, 4, 4));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JTextField tfUser = new JTextField();
        tfUser.putClientProperty("JTextField.placeholderText", "Choose a unique username");

        JTextField tfName = new JTextField();
        tfName.putClientProperty("JTextField.placeholderText", "Full Name or Display Name");

        JTextField tfEmail = new JTextField();
        tfEmail.putClientProperty("JTextField.placeholderText", "Email address");

        JPasswordField pfPass = new JPasswordField();
        pfPass.putClientProperty("JTextField.placeholderText", "Password (min 6 characters)");

        JLabel lblErr = new JLabel("");
        lblErr.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblErr.setForeground(new Color(0xEF, 0x44, 0x44));

        JButton btnReg = new JButton("Create Cloud Account");
        btnReg.setBackground(new Color(0x10, 0xB9, 0x81));
        btnReg.setForeground(Color.WHITE);
        btnReg.setFont(new Font("Segoe UI", Font.BOLD, 13));

        btnReg.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String name = tfName.getText().trim();
            String email = tfEmail.getText().trim();
            String p = new String(pfPass.getPassword()).trim();

            if (u.isEmpty() || email.isEmpty() || p.isEmpty()) {
                lblErr.setText("Please fill in all required fields.");
                return;
            }
            lblErr.setText("Registering account on EduTrace Cloud...");
            btnReg.setEnabled(false);

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
                    btnReg.setEnabled(true);
                    lblErr.setText("<html>" + message + "</html>");
                }
            });
        });

        panel.add(new JLabel("Username:"));
        panel.add(tfUser);
        panel.add(new JLabel("Display / Full Name:"));
        panel.add(tfName);
        panel.add(new JLabel("Email Address:"));
        panel.add(tfEmail);
        panel.add(new JLabel("Password:"));
        panel.add(pfPass);
        panel.add(btnReg);
        return panel;
    }
}

