package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.util.ArrayList;
import java.util.List;

/**
 * Route class represents a path for a drone to follow, including cost calculations
 * Modified to properly handle battery constraints and charging stations
 */
public class Route {
    private Drone drone;
    private List<Location> locations;
    private List<WeatherZone> weatherZones;
    private List<Obstacle> obstacles;
    private List<Location> rechargingStations;

    // Cache for computed values to improve performance
    private double totalDistance = -1;
    private double totalTravelTime = -1;
    private double totalCost = -1;
    private double totalDemand = -1;
    private int timeWindowViolations = -1;
    private int capacityViolations = -1;
    private int batteryViolations = -1;
    private List<Location> usedRechargingStations = new ArrayList<>();
    private List<Double> batteryLevels = new ArrayList<>(); // Track battery at each location

    /**
     * Constructor with all parameters
     */
    public Route(Drone drone, List<Location> locations, List<WeatherZone> weatherZones,
                 List<Obstacle> obstacles, List<Location> rechargingStations) {
        this.drone = drone;
        this.locations = new ArrayList<>(locations);
        this.weatherZones = weatherZones;
        this.obstacles = obstacles;
        this.rechargingStations = rechargingStations;
    }

    /**
     * Calculate total distance of the route including penalties for obstacles and weather
     */
    public double getTotalDistance() {
        if (totalDistance >= 0) {
            return totalDistance;
        }

        totalDistance = 0;

        for (int i = 0; i < locations.size() - 1; i++) {
            Location current = locations.get(i);
            Location next = locations.get(i + 1);

            // Basic distance
            double distance = calculateDistance(current, next);

            // Add obstacle penalties
            for (Obstacle obstacle : obstacles) {
                if (intersectsObstacle(current, next, obstacle)) {
                    distance += obstacle.getPenalty();
                }
            }

            // Add weather zone penalties
            for (WeatherZone zone : weatherZones) {
                if (intersectsWeatherZone(current, next, zone)) {
                    distance += calculateWeatherPenalty(current, next, zone);
                }
            }

            totalDistance += distance;
        }

        return totalDistance;
    }

    /**
     * Calculate total travel time including waiting and service times
     * Modified to track battery consumption and recharging
     */
    public double getTotalTravelTime() {
        if (totalTravelTime >= 0) {
            return totalTravelTime;
        }

        totalTravelTime = 0;
        double currentTime = 0;
        double currentBattery = drone.getMaxBattery(); // Start with full battery
        batteryLevels.clear();
        batteryLevels.add(currentBattery); // Initial battery level

        // Reset violation counts
        timeWindowViolations = 0;
        batteryViolations = 0;
        usedRechargingStations.clear();

        for (int i = 0; i < locations.size() - 1; i++) {
            Location current = locations.get(i);
            Location next = locations.get(i + 1);

            // Calculate travel time and energy consumption based on distance
            double distance = calculateDistance(current, next);
            double travelTime = distance / drone.getSpeed();

            // Calculate energy consumption for this segment
            double payload = getTotalDemand(); // Use the total demand as current payload
            double energyConsumption = calculateEnergyConsumption(distance, payload);

            // Subtract energy consumption from current battery
            currentBattery -= energyConsumption;

            // Check if battery would go below minimum
            if (currentBattery < drone.getMinBattery()) {
                // Battery violation occurred
                batteryViolations++;

                // If current location is not a charging station, calculate penalty
                if (!current.getType().equalsIgnoreCase("charging_station")) {
                    totalTravelTime += 1000; // High penalty for battery violation
                }

                // If we're at a charging station, recharge
                if (current.getType().equalsIgnoreCase("charging_station")) {
                    currentBattery = drone.getMaxBattery();
                    totalTravelTime += drone.getRechargingTime();
                    usedRechargingStations.add(current);
                }
            }

            // Add travel time
            currentTime += travelTime;
            totalTravelTime += travelTime;

            // Store battery level at next location
            batteryLevels.add(currentBattery);

            // Check time windows for next location
            if (!next.getType().equalsIgnoreCase("depot") && !next.getType().equalsIgnoreCase("charging_station")) {
                double[] timeWindow = next.getTimeWindow();

                // If too early, wait
                if (currentTime < timeWindow[0]) {
                    double waitTime = timeWindow[0] - currentTime;
                    currentTime = timeWindow[0];
                    totalTravelTime += waitTime;
                }

                // If too late, add violation
                if (currentTime > timeWindow[1]) {
                    timeWindowViolations++;
                    totalTravelTime += (currentTime - timeWindow[1]) * 2; // Penalty for being late
                }

                // Add service time
                currentTime += next.getServiceTime();
                totalTravelTime += next.getServiceTime();
            }

            // If next is a charging station, add recharging time and mark as used
            if (next.getType().equalsIgnoreCase("charging_station")) {
                totalTravelTime += drone.getRechargingTime();
                currentBattery = drone.getMaxBattery(); // Fully recharge
                usedRechargingStations.add(next);
            }
        }

        return totalTravelTime;
    }

    /**
     * Calculate energy consumption for a route segment
     * @param distance Distance traveled
     * @param payload Current payload weight
     * @return Energy consumed
     */
    private double calculateEnergyConsumption(double distance, double payload) {
        // Base energy consumption plus payload-dependent consumption
        return distance * (drone.getBaseEnergyConsumption() +
                payload * drone.getPayloadEnergyCoefficient());
    }

    /**
     * Calculate total cost of the route, combining distance, time, and recharging costs
     * Modified to include battery violation penalties
     */
    public double getTotalCost() {
        if (totalCost >= 0) {
            return totalCost;
        }

        // Recompute cached values if needed
        double distance = getTotalDistance();
        double travelTime = getTotalTravelTime();

        // Calculate recharging costs
        double rechargingCost = usedRechargingStations.size() * drone.getRechargingCost();

        // Calculate violation penalties
        double timeWindowPenalty = getTimeWindowViolations() * 500;
        double capacityPenalty = getCapacityViolations() * 1000;
        double batteryPenalty = getBatteryViolations() * 2000; // Higher penalty for battery violations

        // Combine all costs
        totalCost = distance + travelTime + rechargingCost + timeWindowPenalty + capacityPenalty + batteryPenalty;

        return totalCost;
    }

    /**
     * Get total demand for this route
     */
    public double getTotalDemand() {
        if (totalDemand >= 0) {
            return totalDemand;
        }

        totalDemand = 0;
        capacityViolations = 0;

        for (Location location : locations) {
            if (!location.getType().equalsIgnoreCase("depot") &&
                    !location.getType().equalsIgnoreCase("charging_station")) {
                totalDemand += location.getDemand();
            }
        }

        // Check for capacity violations
        if (totalDemand > drone.getPayloadCapacity()) {
            capacityViolations = 1;
        }

        return totalDemand;
    }

    /**
     * Get number of time window violations in this route
     */
    public int getTimeWindowViolations() {
        if (timeWindowViolations < 0) {
            // Force recalculation
            getTotalTravelTime();
        }
        return timeWindowViolations;
    }

    /**
     * Get number of capacity violations in this route
     */
    public int getCapacityViolations() {
        if (capacityViolations < 0) {
            // Force recalculation
            getTotalDemand();
        }
        return capacityViolations;
    }

    /**
     * Get number of battery violations in this route
     */
    public int getBatteryViolations() {
        if (batteryViolations < 0) {
            // Force recalculation
            getTotalTravelTime();
        }
        return batteryViolations;
    }

    /**
     * Get recharging stations used in this route
     */
    public List<Location> getUsedRechargingStations() {
        if (totalTravelTime < 0) {
            // Force recalculation
            getTotalTravelTime();
        }
        return usedRechargingStations;
    }

    /**
     * Get battery levels at each location
     */
    public List<Double> getBatteryLevels() {
        if (batteryLevels.isEmpty()) {
            // Force recalculation
            getTotalTravelTime();
        }
        return batteryLevels;
    }

    /**
     * Calculate Euclidean distance between two locations
     */
    private double calculateDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Check if edge intersects with an obstacle
     * Implements Algorithm 4 from the project documentation
     */
    private boolean intersectsObstacle(Location a, Location b, Obstacle obstacle) {
        double cx = obstacle.getCenterX();
        double cy = obstacle.getCenterY();
        double r = obstacle.getRadius();

        // Edge line parameters
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();

        // Calculate coefficients
        double fx = a.getX() - cx;
        double fy = a.getY() - cy;

        double a_coef = dx * dx + dy * dy;
        double b_coef = 2 * (fx * dx + fy * dy);
        double c_coef = (fx * fx + fy * fy) - r * r;

        // Calculate discriminant
        double discriminant = b_coef * b_coef - 4 * a_coef * c_coef;

        // Check if there's an intersection
        return discriminant >= 0;
    }

    /**
     * Check if edge intersects with a weather zone
     * Implements Algorithm 2 from the project documentation
     */
    private boolean intersectsWeatherZone(Location a, Location b, WeatherZone zone) {
        // Get bounds
        double x1 = zone.getXMin();
        double x2 = zone.getXMax();
        double y1 = zone.getYMin();
        double y2 = zone.getYMax();

        // Check if edge is completely outside the zone
        double minX = Math.min(a.getX(), b.getX());
        double maxX = Math.max(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double maxY = Math.max(a.getY(), b.getY());

        if (maxX < x1 || minX > x2 || maxY < y1 || minY > y2) {
            return false; // Completely outside
        }

        // Check if edge is completely inside the zone
        if (minX >= x1 && maxX <= x2 && minY >= y1 && maxY <= y2) {
            return true; // Completely inside
        }

        // Check for intersection with zone boundaries
        // This is a simplification that assumes edge cuts zone boundary somewhere
        return true;
    }

    /**
     * Calculate weather penalty for an edge
     * Implements formula from Algorithm 2
     */
    private double calculateWeatherPenalty(Location a, Location b, WeatherZone zone) {
        double baseDistance = calculateDistance(a, b);
        double windSpeed = zone.getWindSpeed();
        double windAngle = zone.getWindAngle();
        double droneSpeed = drone.getSpeed();

        // Calculate penalty based on the formula in Algorithm 2
        return baseDistance * (1 + 0.1 * (windSpeed / droneSpeed) *
                Math.cos(Math.toRadians(windAngle)));
    }

    /**
     * Debugging method to validate route and identify potential issues
     */
    public boolean validateRoute() {
        // Check if the route starts and ends at the depot
        if (locations.isEmpty()) {
            System.out.println("ERROR: Empty route detected");
            return false;
        }

        Location first = locations.get(0);
        Location last = locations.get(locations.size() - 1);

        if (!first.getType().equalsIgnoreCase("depot")) {
            System.out.println("ERROR: Route does not start at depot. Starts at: " + first.getId() + " (" + first.getType() + ")");
            return false;
        }

        if (!last.getType().equalsIgnoreCase("depot")) {
            System.out.println("ERROR: Route does not end at depot. Ends at: " + last.getId() + " (" + last.getType() + ")");
            return false;
        }

        // Check for duplicate locations (except depot)
        List<Integer> locationIds = new ArrayList<>();
        for (Location loc : locations) {
            if (!loc.getType().equalsIgnoreCase("depot") &&
                    !loc.getType().equalsIgnoreCase("charging_station")) {
                if (locationIds.contains(loc.getId())) {
                    System.out.println("ERROR: Duplicate location detected: " + loc.getId());
                    return false;
                }
                locationIds.add(loc.getId());
            }
        }

        // Check capacity constraint
        double totalDemand = getTotalDemand();
        if (totalDemand > drone.getPayloadCapacity()) {
            System.out.println("ERROR: Capacity exceeded: " + totalDemand + " > " + drone.getPayloadCapacity());
            return false;
        }

        // Check battery constraints
        List<Double> batteryLevels = getBatteryLevels();
        for (double level : batteryLevels) {
            if (level < drone.getMinBattery()) {
                System.out.println("ERROR: Battery level below minimum: " + level + " < " + drone.getMinBattery());
                return false;
            }
        }

        // Check for potentially problematic intersections with obstacles or weather zones
        int obstacleIntersections = 0;
        int weatherIntersections = 0;

        for (int i = 0; i < locations.size() - 1; i++) {
            Location current = locations.get(i);
            Location next = locations.get(i + 1);

            for (Obstacle obstacle : obstacles) {
                if (intersectsObstacle(current, next, obstacle)) {
                    obstacleIntersections++;
                }
            }

            for (WeatherZone zone : weatherZones) {
                if (intersectsWeatherZone(current, next, zone)) {
                    weatherIntersections++;
                }
            }
        }

        if (obstacleIntersections > 10) {
            System.out.println("WARNING: High number of obstacle intersections: " + obstacleIntersections);
        }

        if (weatherIntersections > 10) {
            System.out.println("WARNING: High number of weather zone intersections: " + weatherIntersections);
        }

        return true;
    }

    // Getters and setters

    public Drone getDrone() {
        return drone;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public List<Location> getRechargingStations() {
        return rechargingStations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = new ArrayList<>(locations);

        // Reset cached values
        totalDistance = -1;
        totalTravelTime = -1;
        totalCost = -1;
        totalDemand = -1;
        timeWindowViolations = -1;
        capacityViolations = -1;
        batteryViolations = -1;
        usedRechargingStations.clear();
        batteryLevels.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Route{drone=").append(drone.getId()).append(", locations=[");

        for (Location loc : locations) {
            sb.append(loc.getId()).append("(").append(loc.getType()).append("), ");
        }

        if (!locations.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove trailing comma and space
        }

        sb.append("], cost=").append(getTotalCost())
                .append(", distance=").append(getTotalDistance())
                .append(", demand=").append(getTotalDemand())
                .append(", batteryViolations=").append(getBatteryViolations())
                .append("}");

        return sb.toString();
    }
}