package org.sabbir.edutrace.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import org.sabbir.edutrace.desktop.ui.MainDashboardFrame;

import javax.swing.*;
import java.awt.*;

/**
 * EduTrace Desktop Focus Suite.
 * Pure Java Desktop edition replicating the Android app's Obsidian Dark & Elite Gold design language,
 * cloud synchronization, study timer, and focus launcher tools.
 */
public class EduTraceDesktop {

    // Exact color palette ported from EduTrace Android (colors.xml & themes.xml)
    public static final Color COLOR_OBSIDIAN_BG = new Color(0x0A, 0x0A, 0x0C);
    public static final Color COLOR_SLATE_DARK = new Color(0x12, 0x12, 0x1A);
    public static final Color COLOR_CARD_SURFACE = new Color(0x16, 0x16, 0x22);
    public static final Color COLOR_CARD_BORDER = new Color(0x24, 0x24, 0x36);
    public static final Color COLOR_ELITE_GOLD = new Color(0xFF, 0xB8, 0x00);
    public static final Color COLOR_AMBER_GLOW = new Color(0xFA, 0xCC, 0x15);
    public static final Color COLOR_STATUS_FOCUS = new Color(0x63, 0x66, 0xF1);
    public static final Color COLOR_STATUS_BREAK = new Color(0x10, 0xB9, 0x81);
    public static final Color COLOR_TEXT_PRIMARY = new Color(0xFF, 0xFF, 0xFF);
    public static final Color COLOR_TEXT_SECONDARY = new Color(0x94, 0xA3, 0xB8);
    public static final Color COLOR_TEXT_MUTED = new Color(0x64, 0x74, 0x8B);

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        try {
            FlatDarkLaf.setup();

            // Match Android Obsidian & Elite Gold styling
            UIManager.put("Panel.background", COLOR_OBSIDIAN_BG);
            UIManager.put("control", COLOR_SLATE_DARK);
            UIManager.put("text", COLOR_TEXT_PRIMARY);

            UIManager.put("Button.arc", 14);
            UIManager.put("Component.arc", 14);
            UIManager.put("ProgressBar.arc", 14);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ScrollBar.thumbArc", 10);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            UIManager.put("Button.background", COLOR_CARD_SURFACE);
            UIManager.put("Button.foreground", COLOR_TEXT_PRIMARY);
            UIManager.put("Button.borderColor", COLOR_CARD_BORDER);

            UIManager.put("TextField.background", new Color(0x1A, 0x1A, 0x28));
            UIManager.put("TextField.foreground", COLOR_TEXT_PRIMARY);
            UIManager.put("TextField.caretColor", COLOR_ELITE_GOLD);

            UIManager.put("Focus.color", new Color(0xFF, 0xB8, 0x00, 100));
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.innerFocusWidth", 0);

            // Universal composite font fallback for English and Bengali
            Font baseFont = new Font("SansSerif", Font.PLAIN, 13);
            UIManager.put("Label.font", baseFont);
            UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 12));
            UIManager.put("TextField.font", baseFont);

        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored2) {}
        }

        SwingUtilities.invokeLater(() -> {
            MainDashboardFrame frame = new MainDashboardFrame();
            frame.setVisible(true);
        });
    }
}
