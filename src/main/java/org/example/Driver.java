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

import static org.example.MapVisualization.printRouteMap;

public class Driver {
    static List<WeatherZone> weatherZones = new ArrayList<>();
    static List<Obstacle> obstacles = new ArrayList<>();
    public static void main(String[] args) {
        try {
            List<Location> locations = loadLocations("src/main/java/org/example/resource/locations.txt");
            List<Drone> drones = loadDrones("src/main/java/org/example/resource/drones.txt");
            weatherZones = loadWeatherZones("src/main/java/org/example/resource/weather_zone.txt");
            obstacles = loadObstacles("src/main/java/org/example/resource/obstacles.txt");

            printInputSummary(locations, drones, weatherZones, obstacles);

            System.out.println("\n==== Running Hybrid Algorithm with Timeout Protection ====\n");

            // Set up execution with timeout
            ExecutorService executor = Executors.newSingleThreadExecutor();

            // Create the algorithm
            HybridAlgorithm algorithm = new HybridAlgorithm(drones, locations, weatherZones, obstacles, 1000.0, 0.005, 1.0);

            // Submit the task
            Future<List<Route>> future = executor.submit(() -> algorithm.findOptimalRoutes());

            List<Route> optimalRoutes;
            try {
                // Wait for result, but with a timeout (10 minutes)
                optimalRoutes = future.get(10, TimeUnit.MINUTES);

                System.out.println("\n==== Results ====\n");
                System.out.println("Routes found: " + optimalRoutes.size());

                double totalCost = 0.0;
                for (int i = 0; i < optimalRoutes.size(); i++) {
                    Route route = optimalRoutes.get(i);
                    totalCost += route.getTotalCost();
                    System.out.println("\nRoute #" + (i+1) + " (Drone #" + route.getDrone().getId() + "):");
                    printRouteDetails(route);
                }

                System.out.println("\nTotal cost: " + totalCost);

            } catch (TimeoutException e) {
                System.err.println("ERROR: Algorithm timed out after 10 minutes.");
                System.err.println("This might indicate an infinite loop or a very computationally expensive problem.");
                System.err.println("Consider modifying algorithm parameters or checking for issues in the code.");
                future.cancel(true);
            } catch (Exception e) {
                System.err.println("Error executing algorithm: " + e.getMessage());
                e.printStackTrace();
            } finally {
                executor.shutdownNow();
            }

        } catch (FileNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
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

        if (route.getTimeWindowViolations() > 0) {
            System.out.println("  - Time Window Violations: " + route.getTimeWindowViolations());
        }

        if (route.getCapacityViolations() > 0) {
            System.out.println("  - Capacity Violations: " + route.getCapacityViolations());
        }

        if (!route.getRechargingStations().isEmpty()) {
            System.out.println("  - Recharging Stations: " + formatLocationSequence(route.getRechargingStations()));
        }

        printRouteSequence(route);
        printRouteMap(route , obstacles, weatherZones);
    }
    private static void printRouteSequence(Route route) {
        List<Location> locations = route.getLocations();

        System.out.println("\nRoute Sequence:");
        System.out.println("┌───────┬─────────────┬─────────────┬──────────────┬────────────┐");
        System.out.println("│ Step  │ Location ID │ Coordinates │  Time Window │   Type     │");
        System.out.println("├───────┼─────────────┼─────────────┼──────────────┼────────────┤");

        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            String type = loc.getType();

            // Highlight charging stations in red
            String formattedType = type;
            if (type.equalsIgnoreCase("charging_station")) {
                formattedType = "\u001B[31m" + type + "\u001B[0m"; // Red color
            }

            System.out.printf("│ %5d │ %11d │ (%6.2f,%6.2f) │ %4.1f - %4.1f │ %-10s │%n",
                    i+1, loc.getId(), loc.getX(), loc.getY(),
                    loc.getTimeWindow()[0], loc.getTimeWindow()[1], formattedType);
        }

        System.out.println("└───────┴─────────────┴─────────────┴──────────────┴────────────┘");
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