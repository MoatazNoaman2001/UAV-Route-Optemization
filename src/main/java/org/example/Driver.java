package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.example.MapVisualization.printRouteMap;

public class Driver {
    static List<WeatherZone> weatherZones = new ArrayList<>();
    static List<Obstacle> obstacles = new ArrayList<>();
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        try {
            System.out.println("Loading data...");

            // Validate file paths before loading
            validateFilePath("src/main/java/org/example/resource/locations.txt");
            validateFilePath("src/main/java/org/example/resource/drones.txt");
            validateFilePath("src/main/java/org/example/resource/weather_zone.txt");
            validateFilePath("src/main/java/org/example/resource/obstacles.txt");

            List<Location> locations = loadLocations("src/main/java/org/example/resource/locations.txt");
            List<Drone> drones = loadDrones("src/main/java/org/example/resource/drones.txt");
            weatherZones = loadWeatherZones("src/main/java/org/example/resource/weather_zone.txt");
            obstacles = loadObstacles("src/main/java/org/example/resource/obstacles.txt");

            System.out.println("Data loaded successfully in " + (System.currentTimeMillis() - startTime) + "ms");
            printInputSummary(locations, drones, weatherZones, obstacles);

            System.out.println("\n==== Running Hybrid Algorithm with Enhanced Timeout Protection ====\n");

            // Set up execution with multiple timeout mechanisms
            final int TIMEOUT_SECONDS = 180; // 3 minutes timeout
            ExecutorService executor = Executors.newSingleThreadExecutor();
            final AtomicBoolean algorithmRunning = new AtomicBoolean(true);

            // Create the algorithm with timeout
            final HybridAlgorithm algorithm = new HybridAlgorithm(
                    drones, locations, weatherZones, obstacles,
                    1000.0, 0.005, 1.0, TIMEOUT_SECONDS * 1000);

            // Create a watchdog thread to terminate the algorithm if it takes too long
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(TIMEOUT_SECONDS * 1000);
                    if (algorithmRunning.get()) {
                        System.out.println("Watchdog timeout reached. Attempting to terminate algorithm.");
                        algorithm.terminate(); // Signal termination
                        Thread.sleep(5000); // Give it 5 more seconds to finish gracefully

                        if (algorithmRunning.get()) {
                            System.out.println("Algorithm didn't terminate gracefully. Forcing shutdown.");
                            executor.shutdownNow(); // Force shutdown if it doesn't respond
                        }
                    }
                } catch (InterruptedException e) {
                    // Watchdog was interrupted, which is expected when algorithm completes normally
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();

            // Submit the task
            Future<List<Route>> future = executor.submit(() -> algorithm.findOptimalRoutes());

            List<Route> optimalRoutes;
            try {
                // Wait for result, but with a timeout
                optimalRoutes = future.get(TIMEOUT_SECONDS + 10, TimeUnit.SECONDS); // Add 10 seconds grace period

                // If we get here, the algorithm completed normally
                algorithmRunning.set(false);
                watchdog.interrupt(); // Stop the watchdog

                System.out.println("\n==== Results ====\n");

                if (optimalRoutes == null || optimalRoutes.isEmpty()) {
                    System.out.println("No feasible routes found.");
                    return;
                }

                System.out.println("Routes found: " + optimalRoutes.size());

                double totalCost = 0.0;
                int totalChargingStationsUsed = 0;

                for (int i = 0; i < optimalRoutes.size(); i++) {
                    Route route = optimalRoutes.get(i);
                    totalCost += route.getTotalCost();
                    totalChargingStationsUsed += countChargingStations(route.getLocations());

                    System.out.println("\nRoute #" + (i+1) + " (Drone #" + route.getDrone().getId() + "):");
                    printRouteDetails(route);
                }

                System.out.println("\nTotal cost: " + totalCost);
                System.out.println("Total charging stations used: " + totalChargingStationsUsed);

                long totalRuntime = (System.currentTimeMillis() - startTime) / 1000;
                System.out.println("Total runtime: " + totalRuntime + " seconds");

            } catch (TimeoutException e) {
                algorithmRunning.set(false);
                watchdog.interrupt();
                System.err.println("ERROR: Algorithm timed out after " + TIMEOUT_SECONDS + " seconds.");
                System.err.println("This typically happens when the problem is too complex or constraints are difficult to satisfy.");
                System.err.println("Consider simplifying the problem or adjusting algorithm parameters.");
                future.cancel(true);
            } catch (Exception e) {
                algorithmRunning.set(false);
                watchdog.interrupt();
                System.err.println("Error executing algorithm: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Make sure resources are cleaned up
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("Executor did not terminate in the expected time.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

        } catch (FileNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static void validateFilePath(String path) throws FileNotFoundException {
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        if (!file.canRead()) {
            throw new FileNotFoundException("Cannot read file: " + path);
        }
        System.out.println("File validated: " + path);
    }

    private static int countChargingStations(List<Location> locations) {
        int count = 0;
        for (Location loc : locations) {
            if (loc.getType().equalsIgnoreCase("charging_station")) {
                count++;
            }
        }
        return count;
    }

    public static List<Location> loadLocations(String filename) throws FileNotFoundException {
        List<Location> locations = new ArrayList<>();
        Scanner scanner = new Scanner(new File(filename));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("#") || line.isEmpty()) continue;

            String[] parts = line.split(",");
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
        return locations;
    }

    public static List<Drone> loadDrones(String filename) throws FileNotFoundException {
        List<Drone> drones = new ArrayList<>();
        Scanner scanner = new Scanner(new File(filename));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("#") || line.isEmpty()) continue;

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

            drones.add(new Drone(id, speed, maxBattery, minBattery, payloadCapacity,
                    baseEnergyConsumption, payloadEnergyCoefficient,
                    rechargingTime, rechargingCost));
        }

        scanner.close();
        return drones;
    }

    public static List<WeatherZone> loadWeatherZones(String filename) throws FileNotFoundException {
        List<WeatherZone> zones = new ArrayList<>();
        Scanner scanner = new Scanner(new File(filename));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
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

    public static List<Obstacle> loadObstacles(String filename) throws FileNotFoundException {
        List<Obstacle> obstacles = new ArrayList<>();
        Scanner scanner = new Scanner(new File(filename));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("#") || line.isEmpty()) continue;

            String[] parts = line.split(",");
            double centerX = Double.parseDouble(parts[0]);
            double centerY = Double.parseDouble(parts[1]);
            double radius = Double.parseDouble(parts[2]);
            double penalty = Double.parseDouble(parts[3]);

            obstacles.add(new Obstacle(centerX, centerY, radius, penalty));
        }

        scanner.close();
        return obstacles;
    }

    private static void printInputSummary(List<Location> locations, List<Drone> drones,
                                          List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        System.out.println("=== Input Summary ===");

        int depotCount = 0, customerCount = 0, stationCount = 0;

        for (Location loc : locations) {
            if (loc.getType().equalsIgnoreCase("depot")) depotCount++;
            else if (loc.getType().equalsIgnoreCase("charging_station")) stationCount++;
            else customerCount++;
        }

        System.out.println("Locations: " + locations.size());
        System.out.println("  - Depots: " + depotCount);
        System.out.println("  - Customers: " + customerCount);
        System.out.println("  - Charging Stations: " + stationCount);

        System.out.println("Drones: " + drones.size());
        for (Drone drone : drones) {
            System.out.printf("  - Drone #%d: Speed=%.1f, Capacity=%.1f, Battery=%.1f\n",
                    drone.getId(), drone.getSpeed(), drone.getPayloadCapacity(), drone.getMaxBattery());
        }

        System.out.println("Weather Zones: " + weatherZones.size());
        System.out.println("Obstacles: " + obstacles.size());
    }

    private static void printRouteDetails(Route route) {
        System.out.println("  - Locations: " + formatLocationSequence(route.getLocations()));
        System.out.printf("  - Cost: %.2f\n", route.getTotalCost());
        System.out.printf("  - Distance: %.2f\n", route.getTotalDistance());
        System.out.printf("  - Time: %.2f\n", route.getTotalTravelTime());
        System.out.printf("  - Payload: %.2f / %.2f\n", route.getTotalDemand(), route.getDrone().getPayloadCapacity());

        // Print battery information
        List<Double> batteryLevels = route.getBatteryLevels();
        System.out.println("  - Battery: Initial=" + route.getDrone().getMaxBattery() +
                ", Minimum=" + route.getDrone().getMinBattery());

        if (route.getBatteryViolations() > 0) {
            System.out.println("  - Battery Violations: " + route.getBatteryViolations() + " (CRITICAL!)");
        }

        if (route.getTimeWindowViolations() > 0) {
            System.out.println("  - Time Window Violations: " + route.getTimeWindowViolations());
        }

        if (route.getCapacityViolations() > 0) {
            System.out.println("  - Capacity Violations: " + route.getCapacityViolations());
        }

        // Show the charging station usage
        List<Location> usedChargingStations = route.getUsedRechargingStations();
        if (!usedChargingStations.isEmpty()) {
            System.out.println("  - Recharging Stations Used: " + usedChargingStations.size());
            System.out.println("  - Recharging Stations: " + formatLocationSequence(usedChargingStations));
        }

        printRouteSequenceWithBattery(route);
        printRouteMap(route, obstacles, weatherZones);
    }

    private static void printRouteSequenceWithBattery(Route route) {
        List<Location> locations = route.getLocations();
        List<Double> batteryLevels = route.getBatteryLevels();

        System.out.println("\nRoute Sequence with Battery Levels:");
        System.out.println("┌───────┬─────────────┬─────────────┬──────────────┬────────────┬────────────┐");
        System.out.println("│ Step  │ Location ID │ Coordinates │  Time Window │   Type     │   Battery  │");
        System.out.println("├───────┼─────────────┼─────────────┼──────────────┼────────────┼────────────┤");

        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            String type = loc.getType();

            // Get battery level
            double batteryLevel = (i < batteryLevels.size()) ? batteryLevels.get(i) : 0.0;

            // Highlight critical battery levels in red
            String batteryStr;
            if (batteryLevel < route.getDrone().getMinBattery() * 1.2) {
                batteryStr = "\u001B[31m" + String.format("%.2f", batteryLevel) + "\u001B[0m"; // Red for critically low
            } else if (batteryLevel < route.getDrone().getMaxBattery() * 0.3) {
                batteryStr = "\u001B[33m" + String.format("%.2f", batteryLevel) + "\u001B[0m"; // Yellow for low
            } else {
                batteryStr = String.format("%.2f", batteryLevel);
            }

            // Highlight charging stations in red
            String formattedType = type;
            if (type.equalsIgnoreCase("charging_station")) {
                formattedType = "\u001B[31m" + type + "\u001B[0m"; // Red color
            }

            System.out.printf("│ %5d │ %11d │ (%6.2f,%6.2f) │ %4.1f - %4.1f │ %-10s │ %10s │%n",
                    i+1, loc.getId(), loc.getX(), loc.getY(),
                    loc.getTimeWindow()[0], loc.getTimeWindow()[1], formattedType, batteryStr);
        }

        System.out.println("└───────┴─────────────┴─────────────┴──────────────┴────────────┴────────────┘");
    }
    private static String formatLocationSequence(List<Location> locations) {
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
}