package org.example.gui;

import org.example.Route;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class RouteMapViewer extends JPanel {
    private JPanel mapPanel;
    private RouteMapPanel currentMapPanel;
    private Route currentRoute;
    private List<Obstacle> obstacles;
    private List<WeatherZone> weatherZones;
    private JComboBox<String> routeSelector;
    private List<Route> routes;
    private JButton saveMapButton;

    public RouteMapViewer(List<Route> routes, List<Obstacle> obstacles, List<WeatherZone> weatherZones) {
        this.routes = routes;
        this.obstacles = obstacles;
        this.weatherZones = weatherZones;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createTitledBorder("Route Map Visualization"));

        // Control panel at the top
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // Route selector
        JLabel routeLabel = new JLabel("Select Route:");
        routeSelector = new JComboBox<>();

        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            routeSelector.addItem("Route #" + (i+1) + " (Drone #" + route.getDrone().getId() + ")");
        }

        // Save map button
        saveMapButton = new JButton("Save Map as SVG");

        controlPanel.add(routeLabel);
        controlPanel.add(routeSelector);
        controlPanel.add(saveMapButton);

        add(controlPanel, BorderLayout.NORTH);

        // Map panel (center)
        mapPanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(mapPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Add event listeners
        routeSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMapDisplay();
            }
        });

        saveMapButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveMapAsSVG();
            }
        });

        // Initialize with first route
        if (!routes.isEmpty()) {
            updateMapDisplay();
        }
    }

    private void updateMapDisplay() {
        int selectedIndex = routeSelector.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < routes.size()) {
            currentRoute = routes.get(selectedIndex);

            // Remove old map panel
            mapPanel.removeAll();

            // Create new map panel
            currentMapPanel = new RouteMapPanel(currentRoute, obstacles, weatherZones);
            mapPanel.add(currentMapPanel, BorderLayout.CENTER);

            // Refresh the display
            mapPanel.revalidate();
            mapPanel.repaint();
        }
    }

    private void saveMapAsSVG() {
        if (currentRoute == null) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Map as SVG");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SVG files", "svg"));

        // Default filename based on route
        int routeIndex = routeSelector.getSelectedIndex();
        fileChooser.setSelectedFile(new File("route_" + (routeIndex + 1) + "_map.svg"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Add .svg extension if not present
            if (!file.getName().toLowerCase().endsWith(".svg")) {
                file = new File(file.getAbsolutePath() + ".svg");
            }

            try {
                // Generate SVG content
                String svgContent = currentMapPanel.generateSVG();

                // Write to file
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(svgContent);
                }

                JOptionPane.showMessageDialog(this,
                        "Map saved to: " + file.getAbsolutePath(),
                        "Save Successful",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error saving map: " + e.getMessage(),
                        "Save Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Inner class for the map panel that renders the route
     */
    private class RouteMapPanel extends JPanel {
        private Route route;
        private List<Obstacle> obstacles;
        private List<WeatherZone> weatherZones;

        // View bounds
        private double minX, maxX, minY, maxY;
        private double padding = 50.0;

        public RouteMapPanel(Route route, List<Obstacle> obstacles, List<WeatherZone> weatherZones) {
            this.route = route;
            this.obstacles = obstacles;
            this.weatherZones = weatherZones;

            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.WHITE);

            // Calculate bounds
            calculateMapBounds();
        }

        private void calculateMapBounds() {
            // Initialize with first location
            if (!route.getLocations().isEmpty()) {
                minX = maxX = route.getLocations().getFirst().getX();
                minY = maxY = route.getLocations().getFirst().getY();
            } else {
                minX = minY = 0;
                maxX = maxY = 100;
            }

            // Expand to include all locations
            route.getLocations().forEach(loc -> {
                minX = Math.min(minX, loc.getX());
                maxX = Math.max(maxX, loc.getX());
                minY = Math.min(minY, loc.getY());
                maxY = Math.max(maxY, loc.getY());
            });

            // Expand to include obstacles
            obstacles.forEach(obs -> {
                minX = Math.min(minX, obs.getCenterX() - obs.getRadius());
                maxX = Math.max(maxX, obs.getCenterX() + obs.getRadius());
                minY = Math.min(minY, obs.getCenterY() - obs.getRadius());
                maxY = Math.max(maxY, obs.getCenterY() + obs.getRadius());
            });

            // Expand to include weather zones
            weatherZones.forEach(zone -> {
                minX = Math.min(minX, zone.getXMin());
                maxX = Math.max(maxX, zone.getXMax());
                minY = Math.min(minY, zone.getYMin());
                maxY = Math.max(maxY, zone.getYMax());
            });

            // Add padding
            double width = maxX - minX;
            double height = maxY - minY;

            minX -= width * 0.1;
            maxX += width * 0.1;
            minY -= height * 0.1;
            maxY += height * 0.1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Calculate scaling factors
            double xScale = (getWidth() - 2 * padding) / (maxX - minX);
            double yScale = (getHeight() - 2 * padding) / (maxY - minY);
            double scale = Math.min(xScale, yScale);

            // Draw weather zones
            weatherZones.forEach(zone -> drawWeatherZone(g2d, zone, scale));

            // Draw obstacles
            obstacles.forEach(obstacle -> drawObstacle(g2d, obstacle, scale));

            // Draw route
            drawRoute(g2d, route, scale);

            // Draw location markers
            route.getLocations().forEach(loc -> drawLocation(g2d, loc, scale));

            // Draw route info
            drawRouteInfo(g2d);
        }

        private void drawWeatherZone(Graphics2D g2d, WeatherZone zone, double scale) {
            int x1 = (int) (padding + (zone.getXMin() - minX) * scale);
            int y1 = (int) (padding + (zone.getYMin() - minY) * scale);
            int width = (int) ((zone.getXMax() - zone.getXMin()) * scale);
            int height = (int) ((zone.getYMax() - zone.getYMin()) * scale);

            // Draw semi-transparent blue rectangle
            g2d.setColor(new Color(0, 100, 255, 30));
            g2d.fillRect(x1, y1, width, height);

            // Draw border
            g2d.setColor(new Color(0, 100, 255, 100));
            g2d.setStroke(new BasicStroke(1.0f));
            g2d.drawRect(x1, y1, width, height);

            // Draw wind direction arrow
            int centerX = x1 + width / 2;
            int centerY = y1 + height / 2;
            int arrowLength = 20;
            double angle = Math.toRadians(zone.getWindAngle());

            int endX = centerX + (int) (Math.cos(angle) * arrowLength * zone.getWindSpeed() / 10.0);
            int endY = centerY + (int) (Math.sin(angle) * arrowLength * zone.getWindSpeed() / 10.0);

            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawLine(centerX, centerY, endX, endY);

            // Draw arrowhead
            double arrowAngle = Math.atan2(endY - centerY, endX - centerX);
            int arrowSize = 6;

            int[] xPoints = new int[3];
            int[] yPoints = new int[3];

            xPoints[0] = endX;
            yPoints[0] = endY;
            xPoints[1] = (int) (endX - arrowSize * Math.cos(arrowAngle - Math.PI / 6));
            yPoints[1] = (int) (endY - arrowSize * Math.sin(arrowAngle - Math.PI / 6));
            xPoints[2] = (int) (endX - arrowSize * Math.cos(arrowAngle + Math.PI / 6));
            yPoints[2] = (int) (endY - arrowSize * Math.sin(arrowAngle + Math.PI / 6));

            g2d.fillPolygon(xPoints, yPoints, 3);

            // Draw wind speed text
            g2d.setColor(Color.BLUE);
            g2d.drawString(String.format("%.1f m/s", zone.getWindSpeed()), centerX - 20, centerY - 10);
        }

        private void drawObstacle(Graphics2D g2d, Obstacle obstacle, double scale) {
            int centerX = (int) (padding + (obstacle.getCenterX() - minX) * scale);
            int centerY = (int) (padding + (obstacle.getCenterY() - minY) * scale);
            int radius = (int) (obstacle.getRadius() * scale);

            // Draw semi-transparent red circle
            g2d.setColor(new Color(255, 0, 0, 50));
            g2d.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Draw border
            g2d.setColor(new Color(255, 0, 0, 150));
            g2d.setStroke(new BasicStroke(1.0f));
            g2d.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Draw penalty text
            String penaltyText = String.format("P:%.1f", obstacle.getPenalty());
            g2d.setColor(Color.RED);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));

            FontMetrics metrics = g2d.getFontMetrics();
            int textWidth = metrics.stringWidth(penaltyText);
            g2d.drawString(penaltyText, centerX - textWidth / 2, centerY + 4);
        }

        private void drawRoute(Graphics2D g2d, Route route, double scale) {
            List<Location> locations = route.getLocations();

            if (locations.size() < 2) return;

            // Draw route lines
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(2.0f));

            for (int i = 0; i < locations.size() - 1; i++) {
                Location loc1 = locations.get(i);
                Location loc2 = locations.get(i + 1);

                int x1 = (int) (padding + (loc1.getX() - minX) * scale);
                int y1 = (int) (padding + (loc1.getY() - minY) * scale);
                int x2 = (int) (padding + (loc2.getX() - minX) * scale);
                int y2 = (int) (padding + (loc2.getY() - minY) * scale);

                g2d.drawLine(x1, y1, x2, y2);

                // Draw direction arrow
                if (Math.hypot(x2 - x1, y2 - y1) > 30) {
                    // Find midpoint
                    int midX = (x1 + x2) / 2;
                    int midY = (y1 + y2) / 2;

                    // Calculate angle
                    double angle = Math.atan2(y2 - y1, x2 - x1);
                    int arrowSize = 6;

                    int[] xPoints = new int[3];
                    int[] yPoints = new int[3];

                    xPoints[0] = midX + (int) (arrowSize * Math.cos(angle));
                    yPoints[0] = midY + (int) (arrowSize * Math.sin(angle));
                    xPoints[1] = midX + (int) (arrowSize * Math.cos(angle - Math.PI * 3 / 4));
                    yPoints[1] = midY + (int) (arrowSize * Math.sin(angle - Math.PI * 3 / 4));
                    xPoints[2] = midX + (int) (arrowSize * Math.cos(angle + Math.PI * 3 / 4));
                    yPoints[2] = midY + (int) (arrowSize * Math.sin(angle + Math.PI * 3 / 4));

                    g2d.fillPolygon(xPoints, yPoints, 3);
                }
            }
        }

        private void drawLocation(Graphics2D g2d, Location loc, double scale) {
            int x = (int) (padding + (loc.getX() - minX) * scale);
            int y = (int) (padding + (loc.getY() - minY) * scale);
            String type = loc.getType().toLowerCase();
            int size = 8;

            // Different shapes and colors based on location type
            if (type.equals("depot")) {
                // Depot: yellow square
                g2d.setColor(Color.YELLOW);
                g2d.fillRect(x - size, y - size, size * 2, size * 2);
                g2d.setColor(Color.BLACK);
                g2d.drawRect(x - size, y - size, size * 2, size * 2);
            } else if (type.equals("charging_station")) {
                // Charging station: green triangle
                g2d.setColor(Color.GREEN);
                int[] xPoints = {x, x - size, x + size};
                int[] yPoints = {y - size, y + size, y + size};
                g2d.fillPolygon(xPoints, yPoints, 3);
                g2d.setColor(Color.BLACK);
                g2d.drawPolygon(xPoints, yPoints, 3);
            } else {
                // Customer: blue circle
                g2d.setColor(Color.BLUE);
                g2d.fillOval(x - size, y - size, size * 2, size * 2);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(x - size, y - size, size * 2, size * 2);
            }

            // Draw ID
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            String idText = String.valueOf(loc.getId());
            FontMetrics metrics = g2d.getFontMetrics();
            int textWidth = metrics.stringWidth(idText);
            g2d.drawString(idText, x - textWidth / 2, y - size - 2);
        }

        private void drawRouteInfo(Graphics2D g2d) {
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));

            int y = 20;
            int x = 20;

            // Draw route information
            String droneInfo = String.format("Drone #%d (Speed: %.1f, Battery: %.1f/%.1f)",
                    route.getDrone().getId(),
                    route.getDrone().getSpeed(),
                    route.getDrone().getMaxBattery(),
                    route.getDrone().getMaxBattery());
            g2d.drawString(droneInfo, x, y);
            y += 20;

            String costInfo = String.format("Cost: %.2f, Distance: %.2f, Time: %.2f",
                    route.getTotalCost(),
                    route.getTotalDistance(),
                    route.getTotalTravelTime());
            g2d.drawString(costInfo, x, y);
            y += 20;

            // Draw legend
            y += 10;
            g2d.drawString("Legend:", x, y);
            y += 20;

            // Depot
            g2d.setColor(Color.YELLOW);
            g2d.fillRect(x, y - 8, 16, 16);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, y - 8, 16, 16);
            g2d.drawString("Depot", x + 25, y + 4);
            y += 20;

            // Customer
            g2d.setColor(Color.BLUE);
            g2d.fillOval(x, y - 8, 16, 16);
            g2d.setColor(Color.BLACK);
            g2d.drawOval(x, y - 8, 16, 16);
            g2d.drawString("Customer", x + 25, y + 4);
            y += 20;

            // Charging Station
            g2d.setColor(Color.GREEN);
            int[] xPoints = {x + 8, x, x + 16};
            int[] yPoints = {y - 8, y + 8, y + 8};
            g2d.fillPolygon(xPoints, yPoints, 3);
            g2d.setColor(Color.BLACK);
            g2d.drawPolygon(xPoints, yPoints, 3);
            g2d.drawString("Charging Station", x + 25, y + 4);
            y += 20;

            // Obstacle
            g2d.setColor(new Color(255, 0, 0, 50));
            g2d.fillOval(x, y - 8, 16, 16);
            g2d.setColor(new Color(255, 0, 0, 150));
            g2d.drawOval(x, y - 8, 16, 16);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Obstacle", x + 25, y + 4);
            y += 20;

            // Weather Zone
            g2d.setColor(new Color(0, 100, 255, 30));
            g2d.fillRect(x, y - 8, 16, 16);
            g2d.setColor(new Color(0, 100, 255, 100));
            g2d.drawRect(x, y - 8, 16, 16);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Weather Zone", x + 25, y + 4);
        }

        public String generateSVG() {
            // Calculate scaling factors for SVG
            double svgWidth = 800;
            double svgHeight = 600;
            double svgPadding = 50;

            double xScale = (svgWidth - 2 * svgPadding) / (maxX - minX);
            double yScale = (svgHeight - 2 * svgPadding) / (maxY - minY);
            double scale = Math.min(xScale, yScale);

            StringBuilder svg = new StringBuilder();
            svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(svgWidth)
                    .append("\" height=\"").append(svgHeight).append("\">\n");

            // Background
            svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");

            // Weather zones
            for (WeatherZone zone : weatherZones) {
                double x = svgPadding + (zone.getXMin() - minX) * scale;
                double y = svgPadding + (zone.getYMin() - minY) * scale;
                double width = (zone.getXMax() - zone.getXMin()) * scale;
                double height = (zone.getYMax() - zone.getYMin()) * scale;

                svg.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                        .append("\" width=\"").append(width).append("\" height=\"").append(height)
                        .append("\" fill=\"rgba(0,100,255,0.3)\" stroke=\"rgba(0,100,255,0.5)\" stroke-width=\"1\"/>\n");

                // Wind direction arrow
                double centerX = x + width / 2;
                double centerY = y + height / 2;
                double arrowLength = 20;
                double angle = Math.toRadians(zone.getWindAngle());

                double endX = centerX + Math.cos(angle) * arrowLength * zone.getWindSpeed() / 10.0;
                double endY = centerY + Math.sin(angle) * arrowLength * zone.getWindSpeed() / 10.0;

                svg.append("  <line x1=\"").append(centerX).append("\" y1=\"").append(centerY)
                        .append("\" x2=\"").append(endX).append("\" y2=\"").append(endY)
                        .append("\" stroke=\"blue\" stroke-width=\"1.5\"/>\n");

                // Wind speed text
                svg.append("  <text x=\"").append(centerX - 20).append("\" y=\"").append(centerY - 10)
                        .append("\" fill=\"blue\" font-size=\"10\">").append(String.format("%.1f m/s", zone.getWindSpeed())).append("</text>\n");
            }

            // Obstacles
            for (Obstacle obstacle : obstacles) {
                double centerX = svgPadding + (obstacle.getCenterX() - minX) * scale;
                double centerY = svgPadding + (obstacle.getCenterY() - minY) * scale;
                double radius = obstacle.getRadius() * scale;

                svg.append("  <circle cx=\"").append(centerX).append("\" cy=\"").append(centerY)
                        .append("\" r=\"").append(radius).append("\" fill=\"rgba(255,0,0,0.2)\" stroke=\"rgba(255,0,0,0.6)\" stroke-width=\"1\"/>\n");

                // Penalty text
                svg.append("  <text x=\"").append(centerX).append("\" y=\"").append(centerY + 4)
                        .append("\" text-anchor=\"middle\" fill=\"red\" font-size=\"10\" font-weight=\"bold\">")
                        .append(String.format("P:%.1f", obstacle.getPenalty())).append("</text>\n");
            }

            // Route lines
            List<Location> locations = route.getLocations();
            for (int i = 0; i < locations.size() - 1; i++) {
                Location loc1 = locations.get(i);
                Location loc2 = locations.get(i + 1);

                double x1 = svgPadding + (loc1.getX() - minX) * scale;
                double y1 = svgPadding + (loc1.getY() - minY) * scale;
                double x2 = svgPadding + (loc2.getX() - minX) * scale;
                double y2 = svgPadding + (loc2.getY() - minY) * scale;

                svg.append("  <line x1=\"").append(x1).append("\" y1=\"").append(y1)
                        .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
                        .append("\" stroke=\"blue\" stroke-width=\"2\"/>\n");
            }

            // Location markers
            for (Location loc : route.getLocations()) {
                double x = svgPadding + (loc.getX() - minX) * scale;
                double y = svgPadding + (loc.getY() - minY) * scale;
                String type = loc.getType().toLowerCase();
                double size = 8;

                if (type.equals("depot")) {
                    // Depot: yellow square
                    svg.append("  <rect x=\"").append(x - size).append("\" y=\"").append(y - size)
                            .append("\" width=\"").append(size * 2).append("\" height=\"").append(size * 2)
                            .append("\" fill=\"yellow\" stroke=\"black\" stroke-width=\"1\"/>\n");
                } else if (type.equals("charging_station")) {
                    // Charging station: green triangle
                    String points = (x) + "," + (y - size) + " " +
                            (x - size) + "," + (y + size) + " " +
                            (x + size) + "," + (y + size);
                    svg.append("  <polygon points=\"").append(points)
                            .append("\" fill=\"green\" stroke=\"black\" stroke-width=\"1\"/>\n");
                } else {
                    // Customer: blue circle
                    svg.append("  <circle cx=\"").append(x).append("\" cy=\"").append(y)
                            .append("\" r=\"").append(size).append("\" fill=\"blue\" stroke=\"black\" stroke-width=\"1\"/>\n");
                }

                // Location ID
                svg.append("  <text x=\"").append(x).append("\" y=\"").append(y - size - 2)
                        .append("\" text-anchor=\"middle\" fill=\"black\" font-size=\"10\" font-weight=\"bold\">")
                        .append(loc.getId()).append("</text>\n");
            }

            // Route info
            double textY = 20;
            svg.append("  <text x=\"20\" y=\"").append(textY)
                    .append("\" fill=\"black\" font-size=\"12\" font-weight=\"bold\">")
                    .append(String.format("Drone #%d (Speed: %.1f, Battery: %.1f/%.1f)",
                            route.getDrone().getId(),
                            route.getDrone().getSpeed(),
                            route.getDrone().getMaxBattery(),
                            route.getDrone().getMaxBattery()))
                    .append("</text>\n");

            textY += 20;
            svg.append("  <text x=\"20\" y=\"").append(textY)
                    .append("\" fill=\"black\" font-size=\"12\" font-weight=\"bold\">")
                    .append(String.format("Cost: %.2f, Distance: %.2f, Time: %.2f",
                            route.getTotalCost(),
                            route.getTotalDistance(),
                            route.getTotalTravelTime()))
                    .append("</text>\n");

            svg.append("</svg>");
            return svg.toString();
        }
    }
}