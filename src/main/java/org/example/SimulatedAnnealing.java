package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.util.*;

/**
 * Enhanced SimulatedAnnealing class with battery constraint management
 */
public class SimulatedAnnealing {
    private final Random random = new Random();

    // Simulated Annealing Parameters
    private double initialTemperature;
    private double coolingRate;
    private double minTemperature;
    private int maxIterations;
    private int noImprovementLimit;

    // Problem Data
    private List<Location> locations;
    private List<Drone> drones;
    private List<Obstacle> obstacles;
    private List<WeatherZone> weatherZones;
    private List<Location> rechargingStations;
    private Location depot;

    /**
     * Constructor for the SimulatedAnnealing algorithm
     */
    public SimulatedAnnealing(List<Location> locations, List<Drone> drones,
                              List<Obstacle> obstacles, List<WeatherZone> weatherZones,
                              double initialTemperature, double coolingRate, double minTemperature) {
        this.locations = new ArrayList<>(locations);
        this.drones = new ArrayList<>(drones);
        this.obstacles = new ArrayList<>(obstacles);
        this.weatherZones = new ArrayList<>(weatherZones);
        this.initialTemperature = initialTemperature;
        this.coolingRate = coolingRate;
        this.minTemperature = minTemperature;
        this.maxIterations = 1000;
        this.noImprovementLimit = 100;

        // Extract charging stations from locations
        this.rechargingStations = locations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("charging_station"))
                .toList();

        // Find depot
        this.depot = locations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("depot"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No depot found in locations"));
    }

    /**
     * Main algorithm method to optimize UAV routes
     */
    public List<Route> optimize(List<Route> initialSolution) {
        // Start with the provided solution or generate a random one if none provided
        List<Route> currentSolution = initialSolution != null && !initialSolution.isEmpty() ?
                deepCopy(initialSolution) : generateInitialSolution();

        // Insert charging stations to make routes feasible
        currentSolution = insertChargingStations(currentSolution);

        List<Route> bestSolution = deepCopy(currentSolution);

        double currentCost = calculateTotalCost(currentSolution);
        double bestCost = currentCost;

        double temperature = initialTemperature;
        int iterations = 0;
        int noImprovements = 0;
        int totalIterations = 0; // Safety counter for total iterations

        System.out.println("Starting simulated annealing with initial cost: " + currentCost);

        // Main SA loop
        // Add hard safety limit for iterations to prevent infinite loops
        while (iterations < maxIterations && noImprovements < noImprovementLimit && temperature > minTemperature && totalIterations < 5000) {
            totalIterations++;

            // Apply perturbation to explore the search space
            List<Route> perturbedSolution = perturbSolution(currentSolution);

            // Apply mutation (Reverse Swap)
            List<Route> mutatedSolution = applyReverseSwap(perturbedSolution);

            // Insert charging stations where needed
            mutatedSolution = insertChargingStations(mutatedSolution);

            // Create neighbor solution
            double neighborCost = calculateTotalCost(mutatedSolution);

            // Only consider solutions that are not drastically worse than current
            if (neighborCost > currentCost * 2) {
                noImprovements++;
                iterations++;
                temperature *= (1 - coolingRate);
                continue;
            }

            // Decide whether to accept the new solution using SA probability function
            if (acceptanceProbability(currentCost, neighborCost, temperature) > random.nextDouble()) {
                currentSolution = deepCopy(mutatedSolution);
                currentCost = neighborCost;

                // Check if this is better than our best solution
                if (currentCost < bestCost) {
                    bestSolution = deepCopy(currentSolution);
                    bestCost = currentCost;
                    noImprovements = 0;
                    System.out.println("New best solution found with cost: " + bestCost);
                } else {
                    noImprovements++;
                }
            } else {
                noImprovements++;
            }

            // Apply 3-opt local search periodically
            if (iterations % 20 == 0) {
                List<Route> improvedSolution = applyLocalSearch(bestSolution);
                improvedSolution = insertChargingStations(improvedSolution); // Ensure battery feasibility
                double improvedCost = calculateTotalCost(improvedSolution);

                if (improvedCost < bestCost) {
                    bestSolution = deepCopy(improvedSolution);
                    bestCost = improvedCost;
                    noImprovements = 0;
                    System.out.println("Local search improved solution to: " + bestCost);
                }
            }

            // Cool down the temperature
            temperature *= (1 - coolingRate);
            iterations++;

            if (iterations % 100 == 0) {
                System.out.println("Iteration: " + iterations +
                        ", Temperature: " + temperature +
                        ", Current Cost: " + currentCost +
                        ", Best Cost: " + bestCost +
                        ", No Improvements: " + noImprovements);
            }

            // Force some cooldown if we're seeing many iterations without improvement
            if (noImprovements > 50) {
                temperature *= 0.9; // Accelerate cooling
            }
        }

        // Do one final battery feasibility check
        bestSolution = insertChargingStations(bestSolution);
        System.out.println("Simulated annealing completed with best cost: " + calculateTotalCost(bestSolution));
        return bestSolution;
    }

    /**
     * Insert charging stations into routes where battery would run out
     * This is the key method to ensure drones use charging stations
     */
    private List<Route> insertChargingStations(List<Route> solution) {
        List<Route> result = new ArrayList<>();

        for (Route route : solution) {
            // Skip empty routes
            if (route.getLocations().size() <= 2) {
                result.add(route);
                continue;
            }

            // Check if route has battery violations
            Route checkRoute = new Route(route.getDrone(), route.getLocations(), weatherZones, obstacles, rechargingStations);
            if (checkRoute.getBatteryViolations() == 0) {
                // Route is already feasible - no battery violations
                result.add(route);
                continue;
            }

            // Route has battery violations - needs charging stations
            List<Location> modifiedRoute = new ArrayList<>();
            Drone drone = route.getDrone();
            List<Location> originalLocations = route.getLocations();

            // Start with depot
            modifiedRoute.add(originalLocations.get(0));

            // Track current battery level
            double currentBattery = drone.getMaxBattery();

            // Process each segment
            for (int i = 0; i < originalLocations.size() - 1; i++) {
                Location current = originalLocations.get(i);
                Location next = originalLocations.get(i + 1);

                // Calculate energy needed for this segment
                double distance = calculateDistance(current, next);
                double payload = route.getTotalDemand(); // Simplification: use route total demand
                double energyNeeded = calculateEnergyConsumption(distance, payload, drone);

                // Check if we need to recharge before continuing
                if (currentBattery - energyNeeded < drone.getMinBattery()) {
                    // Find nearest charging station
                    Location nearest = findNearestChargingStation(current);

                    if (nearest != null) {
                        // Calculate energy to reach charging station
                        double distanceToCharger = calculateDistance(current, nearest);
                        double energyToCharger = calculateEnergyConsumption(distanceToCharger, payload, drone);

                        // If we can reach the charging station
                        if (currentBattery - energyToCharger >= drone.getMinBattery()) {
                            // Go to charging station
                            modifiedRoute.add(nearest);

                            // Recharge
                            currentBattery = drone.getMaxBattery();

                            // Calculate energy from charger to next location
                            double distanceFromCharger = calculateDistance(nearest, next);
                            double energyFromCharger = calculateEnergyConsumption(distanceFromCharger, payload, drone);
                            currentBattery -= energyFromCharger;
                        } else {
                            // Can't even reach the charging station, just continue and accept violation
                            currentBattery -= energyNeeded;
                        }
                    } else {
                        // No charging station available, just continue and accept violation
                        currentBattery -= energyNeeded;
                    }
                } else {
                    // No need to recharge, just update battery
                    currentBattery -= energyNeeded;
                }

                // Add next location
                if (i < originalLocations.size() - 2) { // Don't add the last location yet
                    modifiedRoute.add(next);
                }
            }

            // Add the last location (depot)
            modifiedRoute.add(originalLocations.get(originalLocations.size() - 1));

            // Create new route with modified locations
            Route modifiedRouteObj = new Route(drone, modifiedRoute, weatherZones, obstacles, rechargingStations);
            result.add(modifiedRouteObj);
        }

        return result;
    }

    /**
     * Find the nearest charging station to a given location
     */
    private Location findNearestChargingStation(Location location) {
        if (rechargingStations.isEmpty()) {
            return null;
        }

        Location nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Location station : rechargingStations) {
            double distance = calculateDistance(location, station);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = station;
            }
        }

        return nearest;
    }

    /**
     * Calculate energy consumption for a route segment
     */
    private double calculateEnergyConsumption(double distance, double payload, Drone drone) {
        return distance * (drone.getBaseEnergyConsumption() +
                payload * drone.getPayloadEnergyCoefficient());
    }

    /**
     * Calculate distance between two locations
     */
    private double calculateDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /* The remaining methods of the SimulatedAnnealing class follow,
   with adjustments to account for battery constraints */

    private double acceptanceProbability(double currentCost, double neighborCost, double temperature) {
        // If the new solution is better, accept it
        if (neighborCost < currentCost) {
            return 1.0;
        }

        // Otherwise, accept with a probability that decreases as the temperature decreases
        return Math.exp((currentCost - neighborCost) / temperature);
    }

    private List<Route> generateInitialSolution() {
        List<Route> solution = new ArrayList<>();

        // Separate locations by type for easier processing
        List<Location> customers = locations.stream()
                .filter(loc -> !loc.getType().equalsIgnoreCase("depot") &&
                        !loc.getType().equalsIgnoreCase("charging_station"))
                .toList();

        List<Location> customersToAssign = new ArrayList<>(customers);
        Collections.shuffle(customersToAssign); // Randomize initial assignment

        // Assign customers to drones
        for (Drone drone : drones) {
            if (customersToAssign.isEmpty()) {
                break;
            }

            List<Location> routeLocations = new ArrayList<>();
            routeLocations.add(depot); // Start at depot

            double remainingCapacity = drone.getPayloadCapacity();

            // Add customers until capacity is reached
            Iterator<Location> iterator = customersToAssign.iterator();
            while (iterator.hasNext()) {
                Location customer = iterator.next();

                if (customer.getDemand() <= remainingCapacity) {
                    routeLocations.add(customer);
                    remainingCapacity -= customer.getDemand();
                    iterator.remove();
                }
            }

            // Only create route if there are customers assigned
            if (routeLocations.size() > 1) {
                routeLocations.add(depot); // End at depot
                solution.add(new Route(drone, routeLocations, weatherZones, obstacles, rechargingStations));
            }
        }

        // If customers remain unassigned, create additional routes
        if (!customersToAssign.isEmpty()) {
            int droneIndex = 0;

            while (!customersToAssign.isEmpty()) {
                Drone drone = drones.get(droneIndex);
                List<Location> routeLocations = new ArrayList<>();
                routeLocations.add(depot);

                double remainingCapacity = drone.getPayloadCapacity();
                Iterator<Location> iterator = customersToAssign.iterator();

                while (iterator.hasNext()) {
                    Location customer = iterator.next();

                    if (customer.getDemand() <= remainingCapacity) {
                        routeLocations.add(customer);
                        remainingCapacity -= customer.getDemand();
                        iterator.remove();

                        // Limit route size for initial solution
                        if (routeLocations.size() > 5) {
                            break;
                        }
                    }
                }

                if (routeLocations.size() > 1) {
                    routeLocations.add(depot);
                    solution.add(new Route(drone, routeLocations, weatherZones, obstacles, rechargingStations));
                }

                droneIndex = (droneIndex + 1) % drones.size();
            }
        }

        // Apply battery feasibility by inserting charging stations
        solution = insertChargingStations(solution);
        return solution;
    }

    /**
     * Perturb a solution to generate a neighbor
     * This implements line 9 in Algorithm 7 from the project documentation
     *
     * @param currentSolution The solution to perturb
     * @return A perturbed solution
     */
    private List<Route> perturbSolution(List<Route> currentSolution) {
        List<Route> neighbor = deepCopy(currentSolution);

        if (neighbor.isEmpty()) return neighbor;

        Random rand = new Random();
        int maxAttempts = 5; // Limit the number of attempts to avoid getting stuck

        // Choose perturbation type
        int perturbationType = rand.nextInt(3);

        boolean successful = false;
        int attempts = 0;

        while (!successful && attempts < maxAttempts) {
            attempts++;

            switch (perturbationType) {
                case 0: // Swap locations between two routes
                    if (neighbor.size() >= 2) {
                        int routeIdx1 = rand.nextInt(neighbor.size());
                        int routeIdx2 = rand.nextInt(neighbor.size());

                        while (routeIdx1 == routeIdx2 && neighbor.size() > 1) {
                            routeIdx2 = rand.nextInt(neighbor.size());
                        }

                        Route route1 = neighbor.get(routeIdx1);
                        Route route2 = neighbor.get(routeIdx2);

                        if (route1.getLocations().size() > 2 && route2.getLocations().size() > 2) {
                            // Skip depot at first and last positions
                            int loc1Idx = 1 + rand.nextInt(route1.getLocations().size() - 2);
                            int loc2Idx = 1 + rand.nextInt(route2.getLocations().size() - 2);

                            Location loc1 = route1.getLocations().get(loc1Idx);
                            Location loc2 = route2.getLocations().get(loc2Idx);

                            // Validate that we're not working with depots or charging stations
                            if (!loc1.getType().equalsIgnoreCase("depot") && !loc1.getType().equalsIgnoreCase("charging_station") &&
                                    !loc2.getType().equalsIgnoreCase("depot") && !loc2.getType().equalsIgnoreCase("charging_station")) {

                                // Check capacity constraints
                                double newDemand1 = route1.getTotalDemand() - loc1.getDemand() + loc2.getDemand();
                                double newDemand2 = route2.getTotalDemand() - loc2.getDemand() + loc1.getDemand();

                                if (route1.getDrone().getPayloadCapacity() >= newDemand1 &&
                                        route2.getDrone().getPayloadCapacity() >= newDemand2) {

                                    // Swap locations
                                    route1.getLocations().set(loc1Idx, loc2);
                                    route2.getLocations().set(loc2Idx, loc1);
                                    successful = true;
                                }
                            }
                        }
                    }
                    break;

                case 1: // Reverse a random segment in a random route
                    List<Route> reversedSolution = applyReverseSwap(neighbor);
                    if (!reversedSolution.isEmpty()) {
                        neighbor = reversedSolution;
                        successful = true;
                    }
                    break;

                case 2: // Randomly insert a location within a route
                    int routeIndex = rand.nextInt(neighbor.size());
                    Route route = neighbor.get(routeIndex);
                    List<Location> locs = route.getLocations();

                    if (locs.size() > 3) {
                        // Choose a location to move (excluding depot at start and end)
                        int removeIdx = 1 + rand.nextInt(locs.size() - 2);
                        Location locToMove = locs.get(removeIdx);

                        // Skip if it's a depot or charging station
                        if (!locToMove.getType().equalsIgnoreCase("depot") &&
                                !locToMove.getType().equalsIgnoreCase("charging_station")) {

                            locs.remove(removeIdx);

                            // Choose a new position to insert (avoiding start and end positions)
                            int insertIdx = 1 + rand.nextInt(locs.size() - 1);
                            locs.add(insertIdx, locToMove);
                            successful = true;
                        }
                    }
                    break;
            }

            // If current type failed, try another type
            if (!successful) {
                perturbationType = (perturbationType + 1) % 3;
            }
        }

        // If all attempts failed, return original solution
        if (!successful) {
            System.out.println("Warning: Failed to perturb solution after " + maxAttempts + " attempts");
        }

        return neighbor;
    }

    /**
     * Reverse Swap mutation operator
     * This implements line 10 in Algorithm 7 from the project documentation
     *
     * @param solution The solution to apply the reverse swap to
     * @return The solution after applying reverse swap
     */
    private List<Route> applyReverseSwap(List<Route> solution) {
        // Choose a random route
        if (solution.isEmpty()) return solution;

        int routeIndex = random.nextInt(solution.size());
        Route route = solution.get(routeIndex);

        List<Location> locations = route.getLocations();
        if (locations.size() <= 3) {  // Not enough locations to swap (considering depot at start and end)
            return solution;
        }

        // Choose two random points in the route (excluding depot)
        int start = 1 + random.nextInt(locations.size() - 3);
        int end = start + 1 + random.nextInt(locations.size() - start - 2);

        // Reverse the order of locations between start and end
        List<Location> newLocations = new ArrayList<>(locations.subList(0, start));
        List<Location> segmentToReverse = new ArrayList<>(locations.subList(start, end + 1));
        Collections.reverse(segmentToReverse);
        newLocations.addAll(segmentToReverse);
        newLocations.addAll(locations.subList(end + 1, locations.size()));

        // Update the route with the new locations
        route.setLocations(newLocations);

        return solution;
    }

    /**
     * Single Point Crossover operator between routes
     * This implements line 11 in Algorithm 7 from the project documentation
     *
     * @param solution The current solution
     * @return A new solution after applying crossover
     */
    private List<Route> applyCrossover(List<Route> solution) {
        if (solution.size() < 2) return solution;

        // Choose two random routes for crossover
        int indexA = random.nextInt(solution.size());
        int indexB = random.nextInt(solution.size());
        int attempts = 0;

        // Try to find two different routes
        while (indexA == indexB && attempts < 5) {
            indexB = random.nextInt(solution.size());
            attempts++;
        }

        if (indexA == indexB) return solution; // Skip if couldn't find different routes

        Route routeA = solution.get(indexA);
        Route routeB = solution.get(indexB);

        List<Location> locationsA = routeA.getLocations();
        List<Location> locationsB = routeB.getLocations();

        // Need at least 3 locations (depot + customer + depot)
        if (locationsA.size() <= 3 || locationsB.size() <= 3) {
            return solution;
        }

        // Choose crossover points (excluding depot at start and end)
        int pointA = 1 + random.nextInt(locationsA.size() - 2);
        int pointB = 1 + random.nextInt(locationsB.size() - 2);

        // Create new route locations
        List<Location> newLocationsA = new ArrayList<>();
        List<Location> newLocationsB = new ArrayList<>();

        // First parts (include depot)
        for (int i = 0; i <= pointA; i++) {
            newLocationsA.add(locationsA.get(i));
        }

        for (int i = 0; i <= pointB; i++) {
            newLocationsB.add(locationsB.get(i));
        }

        // Exchange parts (excluding depot at end)
        for (int i = pointB + 1; i < locationsB.size() - 1; i++) {
            newLocationsA.add(locationsB.get(i));
        }

        for (int i = pointA + 1; i < locationsA.size() - 1; i++) {
            newLocationsB.add(locationsA.get(i));
        }

        // Add depot at the end
        newLocationsA.add(depot);
        newLocationsB.add(depot);

        // Check feasibility and update routes
        if (isRouteFeasible(newLocationsA, routeA.getDrone()) &&
                isRouteFeasible(newLocationsB, routeB.getDrone())) {
            routeA.setLocations(newLocationsA);
            routeB.setLocations(newLocationsB);
        }

        return solution;
    }

    /**
     * Apply 3-opt local search to improve a solution
     * This implements lines 22-32 in Algorithm 7 from the project documentation
     *
     * @param solution The solution to improve
     * @return An improved solution after applying 3-opt
     */
    /**
     * Apply 3-opt local search to improve a solution
     * This implements lines 22-32 in Algorithm 7 from the project documentation
     * Fixed version to prevent infinite loops
     *
     * @param solution The solution to improve
     * @return An improved solution after applying 3-opt
     */
    private List<Route> applyLocalSearch(List<Route> solution) {
        List<Route> improvedSolution = deepCopy(solution);
        System.out.println("Starting local search optimization...");

        for (int r = 0; r < improvedSolution.size(); r++) {
            Route route = improvedSolution.get(r);
            List<Location> locations = route.getLocations();

            if (locations.size() <= 4) {  // Not enough locations for 3-opt
                continue;
            }

            // Track the total iterations for this route to ensure termination
            int totalIterations = 0;
            int maxTotalIterations = 100; // Hard limit on iterations
            int consecutiveNoImprovements = 0;
            int maxConsecutiveNoImprovements = 10;

            while (totalIterations < maxTotalIterations && consecutiveNoImprovements < maxConsecutiveNoImprovements) {
                boolean foundImprovement = false;
                totalIterations++;

                // Try all possible 3-opt moves
                outerLoop:
                for (int i = 1; i < locations.size() - 3; i++) {
                    for (int j = i + 1; j < locations.size() - 2; j++) {
                        for (int k = j + 1; k < locations.size() - 1; k++) {
                            // Try all 7 possible combinations of 3-opt
                            List<List<Location>> options = generate3OptOptions(locations, i, j, k);

                            for (List<Location> option : options) {
                                if (isRouteFeasible(option, route.getDrone())) {
                                    // Use the existing Route class to compute costs
                                    Route originalRoute = new Route(route.getDrone(), new ArrayList<>(locations),
                                            weatherZones, obstacles, rechargingStations);
                                    Route optionRoute = new Route(route.getDrone(), option,
                                            weatherZones, obstacles, rechargingStations);

                                    if (optionRoute.getTotalCost() < originalRoute.getTotalCost()) {
                                        route.setLocations(new ArrayList<>(option));
                                        foundImprovement = true;
                                        break outerLoop; // Exit all nested loops once improvement is found
                                    }
                                }
                            }
                        }
                    }
                }

                // Update the consecutive no improvements counter
                if (foundImprovement) {
                    consecutiveNoImprovements = 0;
                    System.out.println("Route #" + r + " improved in iteration " + totalIterations);
                } else {
                    consecutiveNoImprovements++;
                }

                // Log progress every 10 iterations
                if (totalIterations % 10 == 0) {
                    System.out.println("Local search for route #" + r + ": " + totalIterations +
                            " iterations, " + consecutiveNoImprovements + " consecutive no improvements");
                }
            }

            System.out.println("Local search for route #" + r + " completed after " + totalIterations +
                    " iterations with " + consecutiveNoImprovements + " consecutive no improvements");
        }

        return improvedSolution;
    }
    /**
     * Generate all possible 3-opt options for a route
     * This generates the 7 possible reconnections for 3-opt local search
     *
     * @param route The current route
     * @param i First cutting point
     * @param j Second cutting point
     * @param k Third cutting point
     * @return List of possible route configurations
     */
    private List<List<Location>> generate3OptOptions(List<Location> route, int i, int j, int k) {
        List<List<Location>> options = new ArrayList<>();

        // Create segments
        List<Location> segment1 = new ArrayList<>(route.subList(0, i));
        List<Location> segment2 = new ArrayList<>(route.subList(i, j));
        List<Location> segment3 = new ArrayList<>(route.subList(j, k));
        List<Location> segment4 = new ArrayList<>(route.subList(k, route.size()));

        // Create reversed segments
        List<Location> segment2Reversed = new ArrayList<>(segment2);
        Collections.reverse(segment2Reversed);

        List<Location> segment3Reversed = new ArrayList<>(segment3);
        Collections.reverse(segment3Reversed);

        // Generate all 7 possible reconnections (excluding the original)

        // Option 2: 1 + 2 + 3R + 4
        List<Location> option2 = new ArrayList<>();
        option2.addAll(segment1);
        option2.addAll(segment2);
        option2.addAll(segment3Reversed);
        option2.addAll(segment4);

        // Option 3: 1 + 2R + 3 + 4
        List<Location> option3 = new ArrayList<>();
        option3.addAll(segment1);
        option3.addAll(segment2Reversed);
        option3.addAll(segment3);
        option3.addAll(segment4);

        // Option 4: 1 + 2R + 3R + 4
        List<Location> option4 = new ArrayList<>();
        option4.addAll(segment1);
        option4.addAll(segment2Reversed);
        option4.addAll(segment3Reversed);
        option4.addAll(segment4);

        // Option 5: 1 + 3 + 2 + 4
        List<Location> option5 = new ArrayList<>();
        option5.addAll(segment1);
        option5.addAll(segment3);
        option5.addAll(segment2);
        option5.addAll(segment4);

        // Option 6: 1 + 3 + 2R + 4
        List<Location> option6 = new ArrayList<>();
        option6.addAll(segment1);
        option6.addAll(segment3);
        option6.addAll(segment2Reversed);
        option6.addAll(segment4);

        // Option 7: 1 + 3R + 2 + 4
        List<Location> option7 = new ArrayList<>();
        option7.addAll(segment1);
        option7.addAll(segment3Reversed);
        option7.addAll(segment2);
        option7.addAll(segment4);

        // Option 8: 1 + 3R + 2R + 4
        List<Location> option8 = new ArrayList<>();
        option8.addAll(segment1);
        option8.addAll(segment3Reversed);
        option8.addAll(segment2Reversed);
        option8.addAll(segment4);

        options.add(option2);
        options.add(option3);
        options.add(option4);
        options.add(option5);
        options.add(option6);
        options.add(option7);
        options.add(option8);

        return options;
    }

    /**
     * Calculate the total cost of a solution
     * Uses the existing Route class to leverage its cost calculation
     *
     * @param solution The solution to calculate cost for
     * @return Total cost of the solution
     */
    private double calculateTotalCost(List<Route> solution) {
        double totalCost = 0.0;

        for (Route route : solution) {
            totalCost += route.getTotalCost();
        }

        return totalCost;
    }

    /**
     * Check if a route is feasible considering capacity and battery constraints
     *
     * @param locations List of locations in the route
     * @param drone The drone assigned to the route
     * @return True if the route is feasible, false otherwise
     */
    private boolean isRouteFeasible(List<Location> locations, Drone drone) {
        // Check capacity constraint
        double totalDemand = 0.0;
        for (Location loc : locations) {
            if (!loc.getType().equalsIgnoreCase("depot") &&
                    !loc.getType().equalsIgnoreCase("charging_station")) {
                totalDemand += loc.getDemand();
            }
        }

        if (totalDemand > drone.getPayloadCapacity()) {
            return false;
        }

        // Create a temporary route to check for other feasibility aspects
        Route tempRoute = new Route(drone, locations, weatherZones, obstacles, rechargingStations);

        // Return true if the route looks reasonable (not extremely high cost)
        return tempRoute.getTotalCost() < Double.MAX_VALUE / 2;
    }

    /**
     * Deep copy a list of routes to avoid modifying the original
     *
     * @param original The original list of routes
     * @return A deep copy of the routes
     */
    private List<Route> deepCopy(List<Route> original) {
        List<Route> copy = new ArrayList<>();

        for (Route route : original) {
            // Create a new Route object with copied locations
            Route routeCopy = new Route(
                    route.getDrone(),
                    new ArrayList<>(route.getLocations()),
                    weatherZones,
                    obstacles,
                    rechargingStations
            );
            copy.add(routeCopy);
        }

        return copy;
    }

    /**
     * Getter for initial temperature
     * @return The initial temperature
     */
    public double getInitialTemperature() {
        return initialTemperature;
    }

    /**
     * Setter for initial temperature
     * @param initialTemperature The new initial temperature
     */
    public void setInitialTemperature(double initialTemperature) {
        this.initialTemperature = initialTemperature;
    }

    /**
     * Getter for cooling rate
     * @return The cooling rate
     */
    public double getCoolingRate() {
        return coolingRate;
    }

    /**
     * Setter for cooling rate
     * @param coolingRate The new cooling rate
     */
    public void setCoolingRate(double coolingRate) {
        this.coolingRate = coolingRate;
    }

    /**
     * Getter for minimum temperature
     * @return The minimum temperature
     */
    public double getMinTemperature() {
        return minTemperature;
    }

    /**
     * Setter for minimum temperature
     * @param minTemperature The new minimum temperature
     */
    public void setMinTemperature(double minTemperature) {
        this.minTemperature = minTemperature;
    }

    /**
     * Setter for maximum iterations
     * @param maxIterations The new maximum iterations
     */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    /**
     * Setter for no improvement threshold
     * @param threshold The new threshold
     */
    public void setNoImprovementThreshold(int threshold) {
        this.noImprovementLimit = threshold;
    }
}
