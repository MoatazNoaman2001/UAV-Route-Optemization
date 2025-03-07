package org.example;

import java.util.ArrayList;
import java.util.List;

public class Route {
    private Drone drone;
    private List<Location> locations;
    private double totalDistance;
    private double totalCost;
    private double totalTravelTime;
    private List<Location> rechargingStations;
    private List<WeatherZone> weatherZones;
    private List<Obstacle> obstacles;
    private int timeWindowViolations;
    private int capacityViolations;

//    public Route(Drone drone, List<Location> locations) {
//        this.drone = drone;
//        this.locations = new ArrayList<>(locations);
//        this.rechargingStations = new ArrayList<>();
//        this.weatherZones = new ArrayList<>();
//        this.obstacles = new ArrayList<>();
//
//        // Extract recharging stations from locations
//        for (Location location : locations) {
//            if (location.getType().equalsIgnoreCase("charging station")) {
//                rechargingStations.add(location);
//            }
//        }
//
//        calculateTotalDistanceAndCost();
//    }

    public Route(Drone drone, List<Location> locations, List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        this.drone = drone;
        this.locations = new ArrayList<>(locations);
        this.weatherZones = weatherZones;
        this.obstacles = obstacles;
        this.rechargingStations = new ArrayList<>();

        // Extract recharging stations from locations
        for (Location location : locations) {
            if (location.getType().equalsIgnoreCase("charging station")) {
                rechargingStations.add(location);
            }
        }

        calculateTotalDistanceAndCost();
    }

    private void calculateTotalDistanceAndCost() {
        if (locations.isEmpty()) {
            return;
        }

        timeWindowViolations = 0;
        capacityViolations = 0;
        totalDistance = 0;
        totalCost = 0;
        totalTravelTime = 0;

        Location depot = getDepot();
        Location currentLocation = depot;
        double batteryLevel = drone.getMaxBattery();
        double remainingCapacity = drone.getPayloadCapacity();
        double currentTime = 0;

        List<Location> routeSequence = new ArrayList<>(locations);
        if (!routeSequence.getFirst().equals(depot)) {
            routeSequence.addFirst(depot);
        }
        if (!routeSequence.getLast().equals(depot)) {
            routeSequence.add(depot);
        }

        for (Location nextLocation : locations) {
            // Skip if current and next location are the same
            if (currentLocation.getId() == nextLocation.getId()) {
                continue;
            }

            // Calculate distance between current and next location
            double distanceToNext = calculateDistance(currentLocation, nextLocation);

            // Check for intersections with weather zones and add penalties
            distanceToNext += calculateWeatherPenalty(currentLocation, nextLocation);

            // Check for intersections with obstacles and add penalties
            distanceToNext += calculateObstaclePenalty(currentLocation, nextLocation);

            // Calculate energy required for this leg
            double energyRequired = calculateEnergyRequirement(distanceToNext, nextLocation.getDemand());

            // Check if we need to recharge
            if (batteryLevel - energyRequired < drone.getMinBattery()) {
                Location nearestStation = findNearestRechargeStation(currentLocation);
                if (nearestStation != null) {
                    // Calculate distance to station
                    double distanceToStation = calculateDistance(currentLocation, nearestStation);
                    double energyToStation = calculateEnergyRequirement(distanceToStation, nextLocation.getDemand());

                    // Calculate distance from station to next location
                    double distanceFromStation = calculateDistance(nearestStation, nextLocation);
                    double energyFromStation = calculateEnergyRequirement(distanceFromStation, nextLocation.getDemand());

                    // Update total distance
                    totalDistance += distanceToStation + distanceFromStation - distanceToNext;

                    // Update battery level after reaching station
                    batteryLevel -= energyToStation;

                    // Recharge
                    batteryLevel = drone.getMaxBattery();
                    totalCost += drone.getRechargingCost();
                    totalTravelTime += drone.getRechargingTime();

                    // Continue to destination
                    batteryLevel -= energyFromStation;
                } else {
                    // No station available, we'll just continue and deplete the battery
                    // In a real system, this would be an error condition
                    batteryLevel -= energyRequired;
                }
            } else {
                // We have enough battery, just continue
                batteryLevel -= energyRequired;
                totalDistance += distanceToNext;
            }

            // In capacity constraint check
            if (remainingCapacity < nextLocation.getDemand()) {
                totalCost += 1000;
                capacityViolations++;  // Add this line
            }

            // Calculate arrival time
            double travelTime = distanceToNext / drone.getSpeed();
            totalTravelTime += travelTime;
            currentTime += travelTime;

            // Check time window constraints
            double[] timeWindow = nextLocation.getTimeWindow();
            if (currentTime < timeWindow[0]) {
                // We arrived too early, wait until time window opens
                double waitingTime = timeWindow[0] - currentTime;
                currentTime = timeWindow[0];
                totalTravelTime += waitingTime;
            } else if (currentTime > timeWindow[1]) {
                // We arrived too late, add a penalty
                totalCost += 500; // Add a late arrival penalty
                timeWindowViolations++;
            }

            // Add service time
            currentTime += nextLocation.getServiceTime();
            totalTravelTime += nextLocation.getServiceTime();

            // Update current location
            currentLocation = nextLocation;
        }

        // Add return to depot if needed
        if (currentLocation.getId() != depot.getId()) {
            double distanceToDepot = calculateDistance(currentLocation, depot);
            double energyToDepot = calculateEnergyRequirement(distanceToDepot, 0); // No more payload

            if (batteryLevel - energyToDepot < 0) {
                // Need to recharge to get back to depot
                Location nearestStation = findNearestRechargeStation(currentLocation);
                if (nearestStation != null) {
                    double distanceToStation = calculateDistance(currentLocation, nearestStation);
                    double energyToStation = calculateEnergyRequirement(distanceToStation, 0);

                    double distanceFromStation = calculateDistance(nearestStation, depot);

                    totalDistance += distanceToStation + distanceFromStation;
                    totalCost += drone.getRechargingCost();
                    totalTravelTime += drone.getRechargingTime();
                } else {
                    totalDistance += distanceToDepot;
                }
            } else {
                totalDistance += distanceToDepot;
            }
        }

        // Calculate the total cost based on distance and other factors
        totalCost += totalDistance * 0.1; // Basic cost per distance
    }

    private double calculateEnergyRequirement(double distance, double payload) {
        return distance * (drone.getBaseEnergyConsumption() + payload * drone.getPayloadEnergyCoefficient());
    }

    private Location findNearestRechargeStation(Location currentLocation) {
        if (rechargingStations.isEmpty()) {
            return null;
        }

        Location nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Location station : rechargingStations) {
            double distance = calculateDistance(currentLocation, station);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = station;
            }
        }

        return nearest;
    }

    private double calculateWeatherPenalty(Location from, Location to) {
        double penalty = 0;

        for (WeatherZone zone : weatherZones) {
            if (doesIntersectWeatherZone(from, to, zone)) {
                // Apply penalty based on the weather zone's parameters
                double baseDistance = calculateDistance(from, to);
                penalty += baseDistance * (1 + 0.1 * (zone.getWindSpeed() / drone.getSpeed())
                        * Math.cos(Math.toRadians(zone.getWindAngle())));
            }
        }

        return penalty;
    }

    private boolean doesIntersectWeatherZone(Location from, Location to, WeatherZone zone) {
        // Check if the line from->to intersects with the weather zone boundaries
        double x1 = from.getX();
        double y1 = from.getY();
        double x2 = to.getX();
        double y2 = to.getY();

        double xMin = zone.getXMin();
        double xMax = zone.getXMax();
        double yMin = zone.getYMin();
        double yMax = zone.getYMax();

        // Check if the line intersects with any of the four boundaries
        return lineIntersectsRectangle(x1, y1, x2, y2, xMin, yMin, xMax, yMax);
    }

    private boolean lineIntersectsRectangle(double x1, double y1, double x2, double y2,
                                            double xMin, double yMin, double xMax, double yMax) {
        // Check if line intersects with any of the four sides of the rectangle
        return (lineIntersect(x1, y1, x2, y2, xMin, yMin, xMax, yMin) ||  // Bottom edge
                lineIntersect(x1, y1, x2, y2, xMin, yMax, xMax, yMax) ||  // Top edge
                lineIntersect(x1, y1, x2, y2, xMin, yMin, xMin, yMax) ||  // Left edge
                lineIntersect(x1, y1, x2, y2, xMax, yMin, xMax, yMax));   // Right edge
    }

    private boolean lineIntersect(double x1, double y1, double x2, double y2,
                                  double x3, double y3, double x4, double y4) {
        // Calculate the direction vectors
        double dx1 = x2 - x1;
        double dy1 = y2 - y1;
        double dx2 = x4 - x3;
        double dy2 = y4 - y3;

        // Calculate the determinant
        double det = dx1 * dy2 - dy1 * dx2;

        // If det is zero, the lines are parallel
        if (Math.abs(det) < 1e-10) {
            return false;
        }

        // Calculate the parameters for the intersection point
        double t1 = ((x3 - x1) * dy2 - (y3 - y1) * dx2) / det;
        double t2 = ((x3 - x1) * dy1 - (y3 - y1) * dx1) / det;

        // Check if the intersection point lies on both line segments
        return (t1 >= 0 && t1 <= 1 && t2 >= 0 && t2 <= 1);
    }

    private double calculateObstaclePenalty(Location from, Location to) {
        double penalty = 0;

        for (Obstacle obstacle : obstacles) {
            if (doesIntersectObstacle(from, to, obstacle)) {
                penalty += obstacle.getPenalty();
            }
        }

        return penalty;
    }

    private boolean doesIntersectObstacle(Location from, Location to, Obstacle obstacle) {
        // Extract parameters from the obstacle
        double cx = obstacle.getCenterX();
        double cy = obstacle.getCenterY();
        double r = obstacle.getRadius();

        // Line parameters
        double x1 = from.getX();
        double y1 = from.getY();
        double x2 = to.getX();
        double y2 = to.getY();

        // Vector from line start to circle center
        double dx = x2 - x1;
        double dy = y2 - y1;
        double fx = x1 - cx;
        double fy = y1 - cy;

        // Quadratic equation coefficients
        double a = dx * dx + dy * dy;
        double b = 2 * (fx * dx + fy * dy);
        double c = (fx * fx + fy * fy) - r * r;

        // Calculate the discriminant
        double discriminant = b * b - 4 * a * c;

        // Check if the line intersects the circle
        if (discriminant >= 0) {
            // Calculate the parameters for the intersection points
            double t1 = (-b + Math.sqrt(discriminant)) / (2 * a);
            double t2 = (-b - Math.sqrt(discriminant)) / (2 * a);

            // Check if at least one intersection point lies on the line segment
            return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
        }

        return false;
    }

    private double calculateDistance(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private Location getDepot() {
        for (Location location : locations) {
            if (location.getType().equalsIgnoreCase("depot")) {
                return location;
            }
        }

        // If no depot is found, return the first location (should not happen in a valid dataset)
        return locations.getFirst();
    }

    // Getters and setters
    public Drone getDrone() {
        return drone;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public double getTotalTravelTime() {
        return totalTravelTime;
    }

    public List<Location> getRechargingStations() {
        return rechargingStations;
    }
    public int getTimeWindowViolations() { return timeWindowViolations; }
    public int getCapacityViolations() { return capacityViolations; }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Route route = (Route) o;

        if (drone.getId() != route.drone.getId()) return false;
        if (locations.size() != route.locations.size()) return false;

        // Check if locations are the same (ignoring order)
        return new ArrayList<>(locations).containsAll(route.locations);
    }

    @Override
    public int hashCode() {
        int result = drone.getId().hashCode();
        for (Location location : locations) {
            result = 31 * result + location.getId();
        }
        return result;
    }
}