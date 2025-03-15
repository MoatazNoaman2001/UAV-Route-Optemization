package org.example.gui;

import javax.swing.*;

public class UavOptimizerApp {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new UavOptimizerGUI().setVisible(true));
    }
}
