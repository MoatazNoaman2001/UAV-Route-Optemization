package org.example;

import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.util.List;

    public class MapVisualization {

        public static void printRouteMap(Route route, List<Obstacle> obstacles, List<WeatherZone> weatherZones) {
            // Define grid dimensions
            final int GRID_WIDTH = 80;
            final int GRID_HEIGHT = 30;

            // Get locations on the route
            List<Location> locations = route.getLocations();

            // Find min/max coordinates from all relevant data
            double minX = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;
            double minY = Double.MAX_VALUE;
            double maxY = Double.MIN_VALUE;

            // Check locations
            for (Location loc : locations) {
                minX = Math.min(minX, loc.getX());
                maxX = Math.max(maxX, loc.getX());
                minY = Math.min(minY, loc.getY());
                maxY = Math.max(maxY, loc.getY());
            }

            // Check weather zones
            for (WeatherZone zone : weatherZones) {
                minX = Math.min(minX, zone.getXMin());
                maxX = Math.max(maxX, zone.getXMax());
                minY = Math.min(minY, zone.getYMin());
                maxY = Math.max(maxY, zone.getYMax());
            }

            // Check obstacles
            for (Obstacle obstacle : obstacles) {
                minX = Math.min(minX, obstacle.getCenterX() - obstacle.getRadius());
                maxX = Math.max(maxX, obstacle.getCenterX() + obstacle.getRadius());
                minY = Math.min(minY, obstacle.getCenterY() - obstacle.getRadius());
                maxY = Math.max(maxY, obstacle.getCenterY() + obstacle.getRadius());
            }

            // Add padding
            minX -= 5;
            maxX += 5;
            minY -= 5;
            maxY += 5;

            // Create grid
            char[][] grid = new char[GRID_HEIGHT][GRID_WIDTH];

            // Initialize grid with dots
            for (int y = 0; y < GRID_HEIGHT; y++) {
                for (int x = 0; x < GRID_WIDTH; x++) {
                    grid[y][x] = '.';
                }
            }

            // Draw weather zones (W - blue)
            for (WeatherZone zone : weatherZones) {
                drawWeatherZone(grid, zone, minX, maxX, minY, maxY, GRID_WIDTH, GRID_HEIGHT);
            }

            // Draw obstacles (O - red)
            for (Obstacle obstacle : obstacles) {
                drawObstacle(grid, obstacle, minX, maxX, minY, maxY, GRID_WIDTH, GRID_HEIGHT);
            }

            // Draw route path (* - green)
            drawRoute(grid, locations, minX, maxX, minY, maxY, GRID_WIDTH, GRID_HEIGHT);

            // Draw locations with labels (C for customer, D for depot, S for charging station)
            for (Location loc : locations) {
                int x = (int) ((loc.getX() - minX) / (maxX - minX) * (GRID_WIDTH - 1));
                int y = (int) ((loc.getY() - minY) / (maxY - minY) * (GRID_HEIGHT - 1));

                // Ensure within bounds
                x = Math.max(0, Math.min(GRID_WIDTH - 1, x));
                y = Math.max(0, Math.min(GRID_HEIGHT - 1, y));

                // Mark with location type
                if (loc.getType().equalsIgnoreCase("depot")) {
                    grid[y][x] = 'D';
                } else if (loc.getType().equalsIgnoreCase("charging_station")) {
                    grid[y][x] = 'S';
                } else {
                    grid[y][x] = 'C';
                }
            }

            // Print legend
            System.out.println("\nMap Visualization:");
            System.out.println("[W] Weather Zone [O] Obstacle [*] Route Path");
            System.out.println("[D] Depot [C] Customer [S] Charging Station");

            // Print grid with colors
            for (int y = 0; y < GRID_HEIGHT; y++) {
                for (int x = 0; x < GRID_WIDTH; x++) {
                    char cell = grid[y][x];

                    // Apply colors
                    if (cell == 'W') {
                        System.out.print("\u001B[34mW\u001B[0m"); // Blue
                    } else if (cell == 'O') {
                        System.out.print("\u001B[31mO\u001B[0m"); // Red
                    } else if (cell == '*') {
                        System.out.print("\u001B[32m*\u001B[0m"); // Green
                    } else if (cell == 'C' || cell == 'D' || cell == 'S') {
                        System.out.print("\u001B[33m" + cell + "\u001B[0m"); // Yellow
                    } else {
                        System.out.print(cell);
                    }
                }
                System.out.println();
            }
        }

        private static void drawWeatherZone(char[][] grid, WeatherZone zone, double minX, double maxX, double minY, double maxY, int gridWidth, int gridHeight) {
            int startX = (int) ((zone.getXMin() - minX) / (maxX - minX) * (gridWidth - 1));
            int endX = (int) ((zone.getXMax() - minX) / (maxX - minX) * (gridWidth - 1));
            int startY = (int) ((zone.getYMin() - minY) / (maxY - minY) * (gridHeight - 1));
            int endY = (int) ((zone.getYMax() - minY) / (maxY - minY) * (gridHeight - 1));

            // Ensure within bounds
            startX = Math.max(0, Math.min(gridWidth - 1, startX));
            endX = Math.max(0, Math.min(gridWidth - 1, endX));
            startY = Math.max(0, Math.min(gridHeight - 1, startY));
            endY = Math.max(0, Math.min(gridHeight - 1, endY));

            // Fill the weather zone
            for (int y = startY; y <= endY; y++) {
                for (int x = startX; x <= endX; x++) {
                    if (grid[y][x] == '.') {
                        grid[y][x] = 'W';
                    }
                }
            }
        }

        private static void drawObstacle(char[][] grid, Obstacle obstacle, double minX, double maxX, double minY, double maxY, int gridWidth, int gridHeight) {
            int centerX = (int) ((obstacle.getCenterX() - minX) / (maxX - minX) * (gridWidth - 1));
            int centerY = (int) ((obstacle.getCenterY() - minY) / (maxY - minY) * (gridHeight - 1));
            int radius = (int) (obstacle.getRadius() / (maxX - minX) * gridWidth);

            // Ensure radius is at least 1
            radius = Math.max(1, radius);

            // Draw the obstacle (circle approximation)
            for (int y = Math.max(0, centerY - radius); y <= Math.min(gridHeight - 1, centerY + radius); y++) {
                for (int x = Math.max(0, centerX - radius); x <= Math.min(gridWidth - 1, centerX + radius); x++) {
                    // Check if point is within circle
                    double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
                    if (distance <= radius && grid[y][x] == '.') {
                        grid[y][x] = 'O';
                    }
                }
            }
        }

        private static void drawRoute(char[][] grid, List<Location> locations, double minX, double maxX, double minY, double maxY, int gridWidth, int gridHeight) {
            // Draw route segments between consecutive locations
            for (int i = 0; i < locations.size() - 1; i++) {
                Location current = locations.get(i);
                Location next = locations.get(i + 1);

                int x1 = (int) ((current.getX() - minX) / (maxX - minX) * (gridWidth - 1));
                int y1 = (int) ((current.getY() - minY) / (maxY - minY) * (gridHeight - 1));
                int x2 = (int) ((next.getX() - minX) / (maxX - minX) * (gridWidth - 1));
                int y2 = (int) ((next.getY() - minY) / (maxY - minY) * (gridHeight - 1));

                // Ensure within bounds
                x1 = Math.max(0, Math.min(gridWidth - 1, x1));
                y1 = Math.max(0, Math.min(gridHeight - 1, y1));
                x2 = Math.max(0, Math.min(gridWidth - 1, x2));
                y2 = Math.max(0, Math.min(gridHeight - 1, y2));

                // Draw line (Bresenham's algorithm)
                drawLine(grid, x1, y1, x2, y2);
            }
        }

        private static void drawLine(char[][] grid, int x1, int y1, int x2, int y2) {
            int dx = Math.abs(x2 - x1);
            int dy = Math.abs(y2 - y1);
            int sx = x1 < x2 ? 1 : -1;
            int sy = y1 < y2 ? 1 : -1;
            int err = dx - dy;

            while (x1 != x2 || y1 != y2) {
                // Only update if it's a plain dot or already part of the route
                if (grid[y1][x1] == '.' || grid[y1][x1] == '*') {
                    grid[y1][x1] = '*';
                }

                int e2 = 2 * err;
                if (e2 > -dy) {
                    err -= dy;
                    x1 += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    y1 += sy;
                }
            }
        }
    }

