package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;

public class Driver {
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private static final String BORDER = "======================================================";

    public static void main(String[] args) {
        List<Location> locations = new ArrayList<>();
        List<Drone> drones = new ArrayList<>();
        List<WeatherZone> weatherZones = new ArrayList<>();
        List<Obstacle> obstacles = new ArrayList<>();

        // Read input from file
        try {

            locations = loadLocations();
            drones = loadDrones();
            weatherZones = generateWeatherZonesBasedOnLocations(locations, 3); // 10 weather zones
            obstacles = generateObstaclesBasedOnLocations(locations, 5);
            saveWeatherZonesToFile(weatherZones, "C:\\Users\\moata\\Downloads\\aniza\\task2\\src\\main\\java\\org\\example\\resource\\weather_zone.txt");
            saveObstaclesToFile(obstacles, "C:\\Users\\moata\\Downloads\\aniza\\task2\\src\\main\\java\\org\\example\\resource\\obstacles.txt");


            printInputSummary(locations, drones, weatherZones, obstacles);
            Algorithm algorithm = new Algorithm(drones, locations,weatherZones,obstacles, 1000, 0.005, 1.0);
            Route optimalRoute = algorithm.findOptimalRoute();
            printRouteDetails(optimalRoute);
            printPerformanceMetrics(optimalRoute);
            printVisualization(locations, weatherZones, obstacles, optimalRoute);
        } catch (FileNotFoundException e) {
            System.out.println("Input file not found!");
            e.printStackTrace();
        }

//        // Create the algorithm and find the optimal route
//        Algorithm algorithm = new Algorithm(drones, locations, 1000, 0.005, 1.0);
//        Route optimalRoute = algorithm.findOptimalRoute();
//
//        // Output the results
//        System.out.println("Optimal Route Cost: " + optimalRoute.getTotalCost());
//        System.out.println("Optimal Route Distance: " + optimalRoute.getTotalDistance());
//        System.out.println("Optimal Route: " + optimalRoute.getLocations());
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
    private static List<Location> loadLocations() throws FileNotFoundException {
        List<Location> locations= new ArrayList<>();
        File locations_file = new File("C:\\Users\\moata\\Downloads\\aniza\\task2\\src\\main\\java\\org\\example\\resource\\locations.txt");
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
    private static List<Drone> loadDrones() throws FileNotFoundException {
        List<Drone> drones = new ArrayList<>();
        File drons_file = new File("C:\\Users\\moata\\Downloads\\aniza\\task2\\src\\main\\java\\org\\example\\resource\\drones.txt");
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

    private static List<WeatherZone> loadWeatherZones() throws FileNotFoundException {
        List<WeatherZone> zones = new ArrayList<>();
        File weatherFile = new File("C:\\Users\\moata\\Downloads\\aniza\\task2\\src\\main\\java\\org\\example\\weather_zone.txt");
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

    private static List<Obstacle> loadObstacles() throws FileNotFoundException {
        List<Obstacle> obstacles = new ArrayList<>();
        File obstaclesFile = new File("C:\\Users\\moata\\Downloads\\aniza\\task2\\src\\main\\java\\org\\example\\obstacles.txt");
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

    private static void printInputSummary(List<Location> locations, List<Drone> drones,
                                          List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        System.out.println("\n" + BORDER);
        System.out.println("INPUT SUMMARY");
        System.out.println(BORDER);

        Map<String, Integer> locationTypes = new HashMap<>();
        for (Location loc : locations) {
            locationTypes.merge(loc.getType().toLowerCase(), 1, Integer::sum);
        }

        System.out.println("Locations (" + locations.size() + "):");
        locationTypes.forEach((type, count) ->
                System.out.println("  " + type + ": " + count));

        System.out.println("\nDrones (" + drones.size() + "):");
        drones.forEach(d -> System.out.printf("  Drone %d: %.1fkg capacity | %.1fkm range%n",
                d.getId(), d.getPayloadCapacity(),
                d.getMaxBattery()/d.getBaseEnergyConsumption()));

        System.out.println("\nEnvironmental Factors:");
        System.out.println("  Weather Zones: " + weatherZones.size());
        System.out.println("  Obstacles: " + obstacles.size());
    }

    private static void printRouteDetails(Route route) {
        System.out.println("\n" + BORDER);
        System.out.println("OPTIMAL ROUTE DETAILS");
        System.out.println(BORDER);

        Drone drone = route.getDrone();
        System.out.printf("Assigned Drone: #%d (%.1f km/h | %.1f kWh battery)%n%n",
                drone.getId(), drone.getSpeed(), drone.getMaxBattery());

        System.out.println("Route Sequence:");
        System.out.println("┌───────┬─────────────┬─────────────┬──────────────┬────────────┐");
        System.out.println("│ Step  │ Location ID │ Coordinates │  Time Window │   Type     │");
        System.out.println("├───────┼─────────────┼─────────────┼──────────────┼────────────┤");

        List<Location> path = route.getLocations();
        for (int i = 0; i < path.size(); i++) {
            Location loc = path.get(i);
            System.out.printf("│ %5d │ %11d │ (%6.2f,%6.2f) │ %4.1f - %4.1f │ %-10s │%n",
                    i+1, loc.getId(), loc.getX(), loc.getY(),
                    loc.getTimeWindow()[0], loc.getTimeWindow()[1], loc.getType());
        }
        System.out.println("└───────┴─────────────┴─────────────┴──────────────┴────────────┘");
    }

    private static void printPerformanceMetrics(Route route) {
        System.out.println("\n" + BORDER);
        System.out.println("PERFORMANCE METRICS");
        System.out.println(BORDER);

        System.out.printf("Total Distance: %s km%n", df.format(route.getTotalDistance()));
        System.out.printf("Total Cost: $%s%n", df.format(route.getTotalCost()));
        System.out.printf("Total Travel Time: %s hours%n%n", df.format(route.getTotalTravelTime()));

        System.out.println("Battery Usage Analysis:");
        System.out.printf("  Recharging Events: %d%n", route.getRechargingStations().size());
        System.out.printf("  Total Recharging Cost: $%s%n%n", df.format(route.getTotalCost()));

        System.out.println("Constraint Compliance:");
        System.out.printf("  Time Window Violations: %d%n", countTimeWindowViolations(route));
        System.out.printf("  Capacity Violations: %d%n", countCapacityViolations(route));
    }

    private static int countTimeWindowViolations(Route route) {
        return route.getTimeWindowViolations();
    }

    private static int countCapacityViolations(Route route) {
        return route.getCapacityViolations();
    }

    private static void printVisualization(List<Location> locations,
                                           List<WeatherZone> weatherZones,
                                           List<Obstacle> obstacles,
                                           Route route) {
        System.out.println("\n" + BORDER);
        System.out.println("ENHANCED ROUTE VISUALIZATION");
        System.out.println(BORDER);

        // ASCII map dimensions
        final int MAP_WIDTH = 80;
        final int MAP_HEIGHT = 30;
        final double CELL_SIZE = 2.0; // km per character

        // Find coordinate bounds with padding
        double minX = locations.stream().mapToDouble(Location::getX).min().orElse(0) - 5;
        double maxX = locations.stream().mapToDouble(Location::getX).max().orElse(1) + 5;
        double minY = locations.stream().mapToDouble(Location::getY).min().orElse(0) - 5;
        double maxY = locations.stream().mapToDouble(Location::getY).max().orElse(1) + 5;

        // Calculate scaling factors
        double xScale = (maxX - minX) / MAP_WIDTH;
        double yScale = (maxY - minY) / MAP_HEIGHT;

        // Legend
        System.out.println("Legend: [D] Depot [C] Customer [⚡] Charging Station");
        System.out.println("        [W] Weather Zone [O] Obstacle [*] Route Path\n");

        // Create grid
        char[][] grid = new char[MAP_HEIGHT][MAP_WIDTH];
        for (char[] row : grid) Arrays.fill(row, '.');

        // Draw weather zones
        for (WeatherZone zone : weatherZones) {
            int startX = (int) ((zone.getXMin() - minX) / xScale);
            int endX = (int) ((zone.getXMax() - minX) / xScale);
            int startY = (int) ((maxY - zone.getYMax()) / yScale);
            int endY = (int) ((maxY - zone.getYMin()) / yScale);

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    if (x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT) {
                        grid[y][x] = 'W';
                    }
                }
            }
        }

        // Draw obstacles
        for (Obstacle obs : obstacles) {
            int centerX = (int) ((obs.getCenterX() - minX) / xScale);
            int centerY = (int) ((maxY - obs.getCenterY()) / yScale);
            int radius = (int) (obs.getRadius() / (CELL_SIZE * xScale));

            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    if (x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT) {
                        double dx = (x - centerX) * xScale;
                        double dy = (y - centerY) * yScale;
                        if (Math.sqrt(dx*dx + dy*dy) <= obs.getRadius()) {
                            grid[y][x] = 'O';
                        }
                    }
                }
            }
        }

        // Draw route path
        List<Location> path = route.getLocations();
        Location prev = path.get(0);
        for (Location loc : path) {
            int x = (int) ((loc.getX() - minX) / xScale);
            int y = (int) ((maxY - loc.getY()) / yScale);
            if (x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT) {
                grid[y][x] = '*';

                // Draw line between consecutive locations
                int prevX = (int) ((prev.getX() - minX) / xScale);
                int prevY = (int) ((maxY - prev.getY()) / yScale);
                drawLine(grid, prevX, prevY, x, y);

                prev = loc;
            }
        }

        // Draw locations on top
        for (Location loc : locations) {
            int x = (int) ((loc.getX() - minX) / xScale);
            int y = (int) ((maxY - loc.getY()) / yScale);
            if (x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT) {
                grid[y][x] = getLocationSymbol(loc);
            }
        }

        // Print the grid
        for (char[] row : grid) {
            System.out.println(new String(row)
                    .replace("W", "\u001B[34mW\u001B[0m")  // Blue weather zones
                    .replace("O", "\u001B[31mO\u001B[0m")  // Red obstacles
                    .replace("*", "\u001B[32m*\u001B[0m"));// Green route path
        }
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

    private static double distance(double x1, double x2, double y1, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }
}
