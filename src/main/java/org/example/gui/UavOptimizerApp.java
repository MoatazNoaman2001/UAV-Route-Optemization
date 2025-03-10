package org.example.gui;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class UavOptimizerApp {
    public static void main(String[] args) {
        // Set the look and feel to the system default
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Launch the GUI on the EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new UavOptimizerGUI().setVisible(true);
            }
        });
    }
}
