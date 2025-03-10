package org.example.gui;

import org.example.Driver;
import org.example.HybridAlgorithm;
import org.example.Route;
import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class UavOptimizerGUI extends JFrame {
    private JTextField dronesFilePath;
    private JTextField locationsFilePath;
    private JTextField weatherZonesFilePath;
    private JTextField obstaclesFilePath;
    private JButton optimizeButton;
    private JButton saveResultsButton;
    private JPanel outputPanel;
    private JScrollPane scrollPane;
    private JTextArea resultsTextArea;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    private List<Drone> drones;
    private List<Location> locations;
    private List<WeatherZone> weatherZones;
    private List<Obstacle> obstacles;
    private List<Route> optimalRoutes;
    private double totalCost;

    public UavOptimizerGUI() {
        setTitle("UAV Route Optimizer");
        setSize(800, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        addEventListeners();
    }

    private void initComponents() {
        // Input file fields
        dronesFilePath = new JTextField("src/main/java/org/example/resource/drones.txt");
        locationsFilePath = new JTextField("src/main/java/org/example/resource/locations.txt");
        weatherZonesFilePath = new JTextField("src/main/java/org/example/resource/weather_zone.txt");
        obstaclesFilePath = new JTextField("src/main/java/org/example/resource/obstacles.txt");

        // Buttons
        optimizeButton = new JButton("Run Optimization");
        saveResultsButton = new JButton("Save Results");
        saveResultsButton.setEnabled(false);

        // Output panels
        resultsTextArea = new JTextArea();
        resultsTextArea.setEditable(false);
        resultsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        scrollPane = new JScrollPane(resultsTextArea);

        // Status indicators
        statusLabel = new JLabel("Ready");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);

        // Initialize Lists
        drones = new ArrayList<>();
        locations = new ArrayList<>();
        weatherZones = new ArrayList<>();
        obstacles = new ArrayList<>();
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Files Panel
        JPanel filesPanel = new JPanel(new GridLayout(4, 3, 5, 10));

        filesPanel.add(new JLabel("Drones File:"));
        filesPanel.add(dronesFilePath);
        filesPanel.add(createBrowseButton(dronesFilePath));

        filesPanel.add(new JLabel("Locations File:"));
        filesPanel.add(locationsFilePath);
        filesPanel.add(createBrowseButton(locationsFilePath));

        filesPanel.add(new JLabel("Weather Zones File:"));
        filesPanel.add(weatherZonesFilePath);
        filesPanel.add(createBrowseButton(weatherZonesFilePath));

        filesPanel.add(new JLabel("Obstacles File:"));
        filesPanel.add(obstaclesFilePath);
        filesPanel.add(createBrowseButton(obstaclesFilePath));

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.add(optimizeButton);
        buttonPanel.add(saveResultsButton);

        // Status Panel
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        // Top Panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(filesPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Output Panel
        outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(BorderFactory.createTitledBorder("Optimization Results"));
        outputPanel.add(scrollPane, BorderLayout.CENTER);

        // Main Layout
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(outputPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JButton createBrowseButton(JTextField textField) {
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                textField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        return browseButton;
    }

    private void addEventListeners() {
        optimizeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runOptimization();
            }
        });

        saveResultsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveResults();
            }
        });
    }

    private void runOptimization() {
        // Reset UI
        resultsTextArea.setText("");
        statusLabel.setText("Loading data files...");
        progressBar.setIndeterminate(true);
        optimizeButton.setEnabled(false);
        saveResultsButton.setEnabled(false);

        // Load data in background
        SwingWorker<Boolean, String> dataLoader = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Loading data files...\n");

                    // Load all data files
                    locations = Driver.loadLocations(locationsFilePath.getText());
                    publish("✓ Loaded " + locations.size() + " locations\n");

                    drones = Driver.loadDrones(dronesFilePath.getText());
                    publish("✓ Loaded " + drones.size() + " drones\n");

                    weatherZones = Driver.loadWeatherZones(weatherZonesFilePath.getText());
                    publish("✓ Loaded " + weatherZones.size() + " weather zones\n");

                    obstacles = Driver.loadObstacles(obstaclesFilePath.getText());
                    publish("✓ Loaded " + obstacles.size() + " obstacles\n\n");

                    // Print input summary
                    publish("=== Input Summary ===\n");

                    int depotCount = 0, customerCount = 0, stationCount = 0;
                    for (Location loc : locations) {
                        if (loc.getType().equalsIgnoreCase("depot")) depotCount++;
                        else if (loc.getType().equalsIgnoreCase("charging_station")) stationCount++;
                        else customerCount++;
                    }

                    publish("Locations: " + locations.size() + "\n");
                    publish("  - Depots: " + depotCount + "\n");
                    publish("  - Customers: " + customerCount + "\n");
                    publish("  - Charging Stations: " + stationCount + "\n\n");

                    publish("Drones: " + drones.size() + "\n");
                    for (Drone drone : drones) {
                        publish(String.format("  - Drone #%d: Speed=%.1f, Capacity=%.1f, Battery=%.1f\n",
                                drone.getId(), drone.getSpeed(), drone.getPayloadCapacity(), drone.getMaxBattery()));
                    }

                    publish("\nWeather Zones: " + weatherZones.size() + "\n");
                    publish("Obstacles: " + obstacles.size() + "\n\n");

                    return true;
                } catch (FileNotFoundException e) {
                    publish("ERROR: " + e.getMessage() + "\n");
                    return false;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    resultsTextArea.append(chunk);
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        // Now run the optimization
                        runOptimizationAlgorithm();
                    } else {
                        // Error occurred during loading
                        statusLabel.setText("Error loading data files");
                        progressBar.setIndeterminate(false);
                        optimizeButton.setEnabled(true);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Error: " + ex.getMessage());
                    progressBar.setIndeterminate(false);
                    optimizeButton.setEnabled(true);
                }
            }
        };

        dataLoader.execute();
    }

    private void runOptimizationAlgorithm() {
        statusLabel.setText("Running optimization algorithm...");

        SwingWorker<List<Route>, String> optimizer = new SwingWorker<List<Route>, String>() {
            @Override
            protected List<Route> doInBackground() throws Exception {
                publish("\n==== Running Hybrid Algorithm ====\n\n");

                // Run the optimization algorithm
                HybridAlgorithm algorithm = new HybridAlgorithm(drones, locations, weatherZones, obstacles, 1000.0, 0.005, 1.0);
                return algorithm.findOptimalRoutes();
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    resultsTextArea.append(chunk);
                }
            }

            @Override
            protected void done() {
                try {
                    optimalRoutes = get();
                    displayResults();

                    statusLabel.setText("Optimization completed");
                    progressBar.setIndeterminate(false);
                    optimizeButton.setEnabled(true);
                    saveResultsButton.setEnabled(true);

                } catch (InterruptedException | ExecutionException ex) {
                    ex.printStackTrace();
                    resultsTextArea.append("ERROR: " + ex.getMessage() + "\n");

                    statusLabel.setText("Error during optimization");
                    progressBar.setIndeterminate(false);
                    optimizeButton.setEnabled(true);
                }
            }
        };

        optimizer.execute();
    }

    private void displayResults() {
        resultsTextArea.append("\n==== Results ====\n\n");
        resultsTextArea.append("Routes found: " + optimalRoutes.size() + "\n");

        totalCost = 0.0;
        for (int i = 0; i < optimalRoutes.size(); i++) {
            Route route = optimalRoutes.get(i);
            totalCost += route.getTotalCost();
            resultsTextArea.append("\nRoute #" + (i + 1) + " (Drone #" + route.getDrone().getId() + "):\n");
            printRouteDetails(route);
        }

        resultsTextArea.append("\nTotal cost: " + totalCost + "\n");

        // Create and add route map viewer for visualization
        JDialog mapDialog = new JDialog(this, "Route Map Visualization", false);
        mapDialog.setSize(900, 700);
        mapDialog.setLocationRelativeTo(this);

        RouteMapViewer mapViewer = new RouteMapViewer(optimalRoutes, obstacles, weatherZones);
        mapDialog.setContentPane(mapViewer);
        mapDialog.setVisible(true);

        // Scroll to top of results
        resultsTextArea.setCaretPosition(0);
    }

    private void printRouteDetails(Route route) {
        // Locations sequence
        resultsTextArea.append("  - Locations: " + formatLocationSequence(route.getLocations()) + "\n");

        // Cost details
        resultsTextArea.append(String.format("  - Cost: %.2f\n", route.getTotalCost()));
        resultsTextArea.append(String.format("  - Distance: %.2f\n", route.getTotalDistance()));
        resultsTextArea.append(String.format("  - Time: %.2f\n", route.getTotalTravelTime()));
        resultsTextArea.append(String.format("  - Payload: %.2f / %.2f\n",
                route.getTotalDemand(), route.getDrone().getPayloadCapacity()));

        // Violations
        if (route.getTimeWindowViolations() > 0) {
            resultsTextArea.append("  - Time Window Violations: " + route.getTimeWindowViolations() + "\n");
        }

        if (route.getCapacityViolations() > 0) {
            resultsTextArea.append("  - Capacity Violations: " + route.getCapacityViolations() + "\n");
        }

        // Recharging stations
        if (!route.getRechargingStations().isEmpty()) {
            resultsTextArea.append("  - Recharging Stations: " +
                    formatLocationSequence(route.getRechargingStations()) + "\n");
        }

        // Route sequence table
        printRouteSequence(route);
    }

    private void printRouteSequence(Route route) {
        List<Location> locations = route.getLocations();

        resultsTextArea.append("\nRoute Sequence:\n");
        resultsTextArea.append("┌───────┬─────────────┬─────────────┬──────────────┬────────────┐\n");
        resultsTextArea.append("│ Step  │ Location ID │ Coordinates │  Time Window │   Type     │\n");
        resultsTextArea.append("├───────┼─────────────┼─────────────┼──────────────┼────────────┤\n");

        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            String type = loc.getType();

            resultsTextArea.append(String.format("│ %5d │ %11d │ (%6.2f,%6.2f) │ %4.1f - %4.1f │ %-10s │\n",
                    i + 1, loc.getId(), loc.getX(), loc.getY(),
                    loc.getTimeWindow()[0], loc.getTimeWindow()[1], type));
        }

        resultsTextArea.append("└───────┴─────────────┴─────────────┴──────────────┴────────────┘\n");
    }

    private String formatLocationSequence(List<Location> locations) {
        StringBuilder sb = new StringBuilder();
        for (Location loc : locations) {
            sb.append(loc.getId());
            if (loc.getType().equalsIgnoreCase("depot")) sb.append("(D)");
            else if (loc.getType().equalsIgnoreCase("charging_station")) sb.append("(S)");
            sb.append(" → ");
        }

        if (sb.length() > 3) sb.setLength(sb.length() - 3);
        return sb.toString();
    }

    private void saveResults() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Optimization Results");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));

        // Try to set the same directory as the input files
        File initialDir = new File(locationsFilePath.getText()).getParentFile();
        if (initialDir != null && initialDir.exists()) {
            fileChooser.setCurrentDirectory(initialDir);
        }

        // Default filename
        fileChooser.setSelectedFile(new File("optimization_results.txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Add .txt extension if not present
            if (!file.getName().toLowerCase().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(resultsTextArea.getText());
                statusLabel.setText("Results saved to: " + file.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error saving results: " + e.getMessage(),
                        "Save Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

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