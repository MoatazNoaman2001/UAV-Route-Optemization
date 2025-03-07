package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class UAVGUI extends JFrame {
    private JTextField droneFileField;
    private JTextField locationFileField;
    private JTextField weatherFileField;
    private JTextField obstacleFileField;
    private JTextArea resultArea;
    private JButton browseDroneButton;
    private JButton browseLocationButton;
    private JButton browseWeatherButton;
    private JButton browseObstacleButton;
    private JButton generateButton;
    private JButton findOptimalButton;
    private JButton downloadButton;

    private List<Drone> drones;
    private List<Location> locations;
    private List<WeatherZone> weatherZones;
    private List<Obstacle> obstacles;
    private Route optimalRoute;
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private static final String BORDER = "======================================================";


    public UAVGUI() {
        setTitle("UAV Route Optimization");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Input Panel
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Drone File
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Drone File:"), gbc);
        gbc.gridx = 1;
        droneFileField = new JTextField(20);
        inputPanel.add(droneFileField, gbc);
        gbc.gridx = 2;
        browseDroneButton = new JButton("Browse");
        inputPanel.add(browseDroneButton, gbc);

        // Location File
        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Location File:"), gbc);
        gbc.gridx = 1;
        locationFileField = new JTextField(20);
        inputPanel.add(locationFileField, gbc);
        gbc.gridx = 2;
        browseLocationButton = new JButton("Browse");
        inputPanel.add(browseLocationButton, gbc);

        // Weather File
        gbc.gridx = 0;
        gbc.gridy = 2;
        inputPanel.add(new JLabel("Weather File:"), gbc);
        gbc.gridx = 1;
        weatherFileField = new JTextField(20);
        inputPanel.add(weatherFileField, gbc);
        gbc.gridx = 2;
        browseWeatherButton = new JButton("Browse");
        inputPanel.add(browseWeatherButton, gbc);

        // Obstacle File
        gbc.gridx = 0;
        gbc.gridy = 3;
        inputPanel.add(new JLabel("Obstacle File:"), gbc);
        gbc.gridx = 1;
        obstacleFileField = new JTextField(20);
        inputPanel.add(obstacleFileField, gbc);
        gbc.gridx = 2;
        browseObstacleButton = new JButton("Browse");
        inputPanel.add(browseObstacleButton, gbc);

        // Buttons
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        generateButton = new JButton("Generate");
        inputPanel.add(generateButton, gbc);

        gbc.gridy = 5;
        findOptimalButton = new JButton("Find Optimal Solution");
        inputPanel.add(findOptimalButton, gbc);

        gbc.gridy = 6;
        downloadButton = new JButton("Download Result");
        inputPanel.add(downloadButton, gbc);

        // Result Area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(resultArea);

        // Add components to frame
        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        // Button Actions
        generateButton.addActionListener(e -> {
            try {
                drones = loadDrones(new File(droneFileField.getText()));
                locations = loadLocations(new File(locationFileField.getText()));
                weatherZones = loadWeatherZones(new File(weatherFileField.getText()));
                obstacles = loadObstacles(new File(obstacleFileField.getText()));
                resultArea.setText("Files loaded successfully!\n");
            } catch (FileNotFoundException ex) {
                resultArea.setText("Error loading files: " + ex.getMessage() + "\n");
            }
        });

        browseDroneButton.addActionListener(e -> droneFileField.setText(chooseFile("Select Drone File")));
        browseLocationButton.addActionListener(e -> locationFileField.setText(chooseFile("Select Location File")));
        browseWeatherButton.addActionListener(e -> weatherFileField.setText(chooseFile("Select Weather File")));
        browseObstacleButton.addActionListener(e -> obstacleFileField.setText(chooseFile("Select Obstacle File")));
        findOptimalButton.addActionListener(e -> {
            if (drones == null || locations == null || weatherZones == null || obstacles == null) {
                resultArea.setText("Please load all files first!\n");
                return;
            }

            // Show loading screen
            JDialog loadingDialog = new JDialog(this, "Processing", true);
            loadingDialog.add(new JLabel("Finding optimal route..."));
            loadingDialog.setSize(200, 100);
            loadingDialog.setLocationRelativeTo(this);
            loadingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            // Run algorithm in background
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    Algorithm algorithm = new Algorithm(drones, locations, weatherZones, obstacles, 1000, 0.005, 1.0);
                    optimalRoute = algorithm.findOptimalRoute();
                    return null;
                }

                @Override
                protected void done() {
                    loadingDialog.dispose();
                    displayResults();
                }
            };

            worker.execute();
            loadingDialog.setVisible(true);
        });
        downloadButton.addActionListener(e -> {
            if (optimalRoute == null) {
                resultArea.setText("No optimal route found to download!\n");
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Result");
            int userSelection = fileChooser.showSaveDialog(UAVGUI.this);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                try (FileWriter writer = new FileWriter(fileToSave)) {
                    writer.write("Optimal Route Details:\n");
                    writer.write("Total Distance: " + optimalRoute.getTotalDistance() + " km\n");
                    writer.write("Total Cost: $" + optimalRoute.getTotalCost() + "\n");
                    writer.write("Total Travel Time: " + optimalRoute.getTotalTravelTime() + " hours\n");
                    writer.write("Time Window Violations: " + optimalRoute.getTimeWindowViolations() + "\n");
                    writer.write("Capacity Violations: " + optimalRoute.getCapacityViolations() + "\n");

                    writer.write(generateReport(optimalRoute));
                    resultArea.setText("Result saved to " + fileToSave.getAbsolutePath() + "\n");
                } catch (IOException ex) {
                    resultArea.setText("Error saving file: " + ex.getMessage() + "\n");
                }
            }
        });
    }

    private String chooseFile(String title) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getAbsolutePath();
        }
        return "";
    }
    private static void saveWeatherZonesToFile(List<WeatherZone> zones, String filename) throws FileNotFoundException {
        try (PrintWriter writer = new PrintWriter(filename)) {
            writer.println("# xMin,xMax,yMin,yMax,windSpeed,windAngle");
            for (WeatherZone zone : zones) {
                writer.printf("%.1f,%.1f,%.1f,%.1f,%.1f,%.1f%n",
                        zone.getXMin(), zone.getXMax(),
                        zone.getYMin(), zone.getYMax(),
                        zone.getWindSpeed(), zone.getWindAngle());
            }
        }
    }

    private static void saveObstaclesToFile(List<Obstacle> obstacles, String filename) throws FileNotFoundException {
        try (PrintWriter writer = new PrintWriter(filename)) {
            writer.println("# centerX,centerY,radius,penalty");
            for (Obstacle obstacle : obstacles) {
                writer.printf("%.1f,%.1f,%.1f,%.1f%n",
                        obstacle.getCenterX(), obstacle.getCenterY(),
                        obstacle.getRadius(), obstacle.getPenalty());
            }
        }
    }
    private static List<Location> loadLocations(File locations_file) throws FileNotFoundException {
        List<Location> locations= new ArrayList<>();
        Scanner scanner = new Scanner(locations_file);

        // Read locations
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("#") || line.isEmpty()) {
                continue; // Skip comments and empty lines
            }

            String[] parts = line.split(",");
            System.out.println(parts[0]);
            int id = Integer.parseInt(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double demand = Double.parseDouble(parts[3]);
            double et = Double.parseDouble(parts[4]);
            double lt = Double.parseDouble(parts[5]);
            double serviceTime = Double.parseDouble(parts[6]);
            String type = parts[7];

            locations.add(new Location(id, x, y, demand, new double[]{et, lt}, serviceTime, type));
        }
        scanner.close();
        return  locations;
    }
    private static List<Drone> loadDrones(File drons_file) throws FileNotFoundException {
        List<Drone> drones = new ArrayList<>();
        Scanner drons_scanner = new Scanner(drons_file);

        // Read drones
        while (drons_scanner.hasNextLine()) {
            String line = drons_scanner.nextLine();
            if (line.startsWith("#") || line.isEmpty()) {
                continue; // Skip comments and empty lines
            }

            String[] parts = line.split(",");
            int id = Integer.parseInt(parts[0]);
            double speed = Double.parseDouble(parts[1]);
            double maxBattery = Double.parseDouble(parts[2]);
            double minBattery = Double.parseDouble(parts[3]);
            double payloadCapacity = Double.parseDouble(parts[4]);
            double baseEnergyConsumption = Double.parseDouble(parts[5]);
            double payloadEnergyCoefficient = Double.parseDouble(parts[6]);
            double rechargingTime = Double.parseDouble(parts[7]);
            double rechargingCost = Double.parseDouble(parts[8]);

            drones.add(new Drone(id, speed, maxBattery, minBattery, payloadCapacity, baseEnergyConsumption, payloadEnergyCoefficient, rechargingTime, rechargingCost));
        }
        return  drones;
    }

    private static List<WeatherZone> generateWeatherZonesBasedOnLocations(List<Location> locations, int count) {
        List<WeatherZone> zones = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            // Select a random location as the center point for the weather zone
            Location location = locations.get(random.nextInt(locations.size()));
            double xMin = Math.max(0, location.getX() - 5 + random.nextDouble() * 10);
            double xMax = xMin + 5 + random.nextDouble() * 5;
            double yMin = Math.max(0, location.getY() - 5 + random.nextDouble() * 10);
            double yMax = yMin + 5 + random.nextDouble() * 5;
            double windSpeed = 10 + random.nextDouble() * 30;
            double windAngle = random.nextDouble() * 360;

            zones.add(new WeatherZone(xMin, xMax, yMin, yMax, windSpeed, windAngle));
        }
        return zones;
    }

    private static List<Obstacle> generateObstaclesBasedOnLocations(List<Location> locations, int count) {
        List<Obstacle> obstacles = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            // Select a random location to place the obstacle nearby
            Location location = locations.get(random.nextInt(locations.size()));
            double cx = location.getX() + (random.nextDouble() * 10 - 5); // Within 5 units radius
            double cy = location.getY() + (random.nextDouble() * 10 - 5);
            double radius = 2 + random.nextDouble() * 3; // Radius between 2 and 5
            double penalty = 50 + random.nextDouble() * 150; // Penalty between 50 and 200

            obstacles.add(new Obstacle(cx, cy, radius, penalty));
        }
        return obstacles;
    }

    private static List<WeatherZone> loadWeatherZones(File weatherFile) throws FileNotFoundException {
        List<WeatherZone> zones = new ArrayList<>();
        Scanner scanner = new Scanner(weatherFile);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.startsWith("#") || line.isEmpty()) continue;

            String[] parts = line.split(",");
            double xMin = Double.parseDouble(parts[0]);
            double xMax = Double.parseDouble(parts[1]);
            double yMin = Double.parseDouble(parts[2]);
            double yMax = Double.parseDouble(parts[3]);
            double windSpeed = Double.parseDouble(parts[4]);
            double windAngle = Double.parseDouble(parts[5]);

            zones.add(new WeatherZone(xMin, xMax, yMin, yMax, windSpeed, windAngle));
        }
        scanner.close();
        return zones;
    }

    private static List<Obstacle> loadObstacles(File obstaclesFile) throws FileNotFoundException {
        List<Obstacle> obstacles = new ArrayList<>();
        Scanner scanner = new Scanner(obstaclesFile);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.startsWith("#") || line.isEmpty()) continue;

            String[] parts = line.split(",");
            double cx = Double.parseDouble(parts[0]);
            double cy = Double.parseDouble(parts[1]);
            double radius = Double.parseDouble(parts[2]);
            double penalty = Double.parseDouble(parts[3]);

            obstacles.add(new Obstacle(cx, cy, radius, penalty));
        }
        scanner.close();
        return obstacles;
    }
    private void displayResults() {
        resultArea.setText(generateReport(optimalRoute));
    }

    private static void appendInputSummary(StringBuilder sb, List<Location> locations, List<Drone> drones,
                                           List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        sb.append("\n").append(BORDER).append("\nINPUT SUMMARY\n").append(BORDER).append("\n");

        Map<String, Integer> locationTypes = new HashMap<>();
        for (Location loc : locations) {
            locationTypes.merge(loc.getType().toLowerCase(), 1, Integer::sum);
        }

        sb.append("Locations (").append(locations.size()).append("):\n");
        locationTypes.forEach((type, count) ->
                sb.append("  ").append(type).append(": ").append(count).append("\n"));

        sb.append("\nDrones (").append(drones.size()).append("):\n");
        for (Drone d : drones) {
            sb.append(String.format("  Drone %d: %.1fkg capacity | %.1fkm range%n",
                    d.getId(), d.getPayloadCapacity(), d.getMaxBattery() / d.getBaseEnergyConsumption()));
        }

        sb.append("\nEnvironmental Factors:\n")
                .append("  Weather Zones: ").append(weatherZones.size()).append("\n")
                .append("  Obstacles: ").append(obstacles.size()).append("\n");
    }

    private void appendRouteDetails(StringBuilder sb, Route route) {
        sb.append("\n").append(BORDER).append("\nOPTIMAL ROUTE DETAILS\n").append(BORDER).append("\n");

        Drone drone = route.getDrone();
        sb.append(String.format("Assigned Drone: #%d (%.1f km/h | %.1f kWh battery)%n%n",
                drone.getId(), drone.getSpeed(), drone.getMaxBattery()));

        sb.append("Route Sequence:\n");
        sb.append("┌───────┬─────────────┬─────────────┬──────────────┬────────────┐\n");
        sb.append("│ Step  │ Location ID │ Coordinates │  Time Window │   Type     │\n");
        sb.append("├───────┼─────────────┼─────────────┼──────────────┼────────────┤\n");

        List<Location> path = route.getLocations();
        for (int i = 0; i < path.size(); i++) {
            Location loc = path.get(i);
            sb.append(String.format("│ %5d │ %11d │ (%6.2f,%6.2f) │ %4.1f - %4.1f │ %-10s │%n",
                    i + 1, loc.getId(), loc.getX(), loc.getY(),
                    loc.getTimeWindow()[0], loc.getTimeWindow()[1], loc.getType()));
        }
        sb.append("└───────┴─────────────┴─────────────┴──────────────┴────────────┘\n");
    }

    private void appendPerformanceMetrics(StringBuilder sb, Route route) {
        sb.append("\n").append(BORDER).append("\nPERFORMANCE METRICS\n").append(BORDER).append("\n");

        sb.append(String.format("Total Distance: %s km%n", df.format(route.getTotalDistance())));
        sb.append(String.format("Total Cost: $%s%n", df.format(route.getTotalCost())));
        sb.append(String.format("Total Travel Time: %s hours%n%n", df.format(route.getTotalTravelTime())));

        sb.append("Battery Usage Analysis:\n");
        sb.append(String.format("  Recharging Events: %d%n", route.getRechargingStations().size()));
        sb.append(String.format("  Total Recharging Cost: $%s%n%n", df.format(route.getTotalCost())));

        sb.append("Constraint Compliance:\n");
        sb.append(String.format("  Time Window Violations: %d%n", countTimeWindowViolations(route)));
        sb.append(String.format("  Capacity Violations: %d%n", countCapacityViolations(route)));
    }

    private int countTimeWindowViolations(Route route) {
        return route.getTimeWindowViolations();
    }
    private int countCapacityViolations(Route route) {
        return route.getCapacityViolations();
    }

    public String generateReport( Route route) {
        StringBuilder sb = new StringBuilder();
        appendInputSummary(sb, locations, drones, weatherZones, obstacles);
        appendRouteDetails(sb, route);
        appendPerformanceMetrics(sb, route);
        return sb.toString();
    }


    // Bresenham's line algorithm for drawing routes
    private static void drawLine(char[][] grid, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x0 >= 0 && x0 < grid[0].length && y0 >= 0 && y0 < grid.length) {
                if (grid[y0][x0] == '.') grid[y0][x0] = '*';
            }

            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
    private static char getLocationSymbol(Location loc) {
        return switch (loc.getType().toLowerCase()) {
            case "depot" -> 'D';
            case "charging station" -> '⚡';
            default -> 'C';
        };
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new UAVGUI().setVisible(true);
            }
        });
    }
}