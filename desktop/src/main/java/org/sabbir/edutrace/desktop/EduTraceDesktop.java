package org.sabbir.edutrace.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import org.sabbir.edutrace.desktop.ui.MainDashboardFrame;

import javax.swing.*;
import java.awt.*;

public class EduTraceDesktop {

    public static void main(String[] args) {
        // Enable anti-aliasing and HiDPI scaling
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        try {
            // Setup FlatLaf Dark look and feel
            FlatDarkLaf.setup();

            // Custom UI styling for EduTrace Elite Gold aesthetics
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 10);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.innerFocusWidth", 0);
            UIManager.put("Focus.color", new Color(0xE2, 0xB7, 0x14, 120));

        } catch (Exception ignored) {
            // Fallback to system look and feel if FlatLaf fails
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
