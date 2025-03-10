package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HybridAlgorithm class implements the hybrid genetic algorithm with simulated annealing
 * approach described in Algorithm 7 in the project documentation.
 */
public class HybridAlgorithm {
    // Population parameters
    private final int populationSize;
    private final int maxIterations;
    private final int noImprovementThreshold;

    // Problem data
    private final List<Drone> drones;
    private final List<Location> locations;
    private final List<Location> rechargingStations;
    private final List<WeatherZone> weatherZones;
    private final List<Obstacle> obstacles;

    // Simulated Annealing parameters
    private final double initialTemperature;
    private final double coolingRate;
    private final double minTemperature;

    /**
     * Constructor for HybridAlgorithm
     *
     * @param drones List of available drones
     * @param locations List of all locations including depot, customers, and charging stations
     * @param weatherZones List of weather zones that affect route costs
     * @param obstacles List of obstacles to avoid
     * @param initialTemp Initial temperature for simulated annealing
     * @param coolingRate Cooling rate for simulated annealing
     * @param minTemp Minimum temperature for simulated annealing
     */
    public HybridAlgorithm(
            List<Drone> drones,
            List<Location> locations,
            List<WeatherZone> weatherZones,
            List<Obstacle> obstacles,
            double initialTemp,
            double coolingRate,
            double minTemp) {

        this.drones = drones;
        this.locations = locations;
        this.weatherZones = weatherZones;
        this.obstacles = obstacles;
        this.initialTemperature = initialTemp;
        this.coolingRate = coolingRate;
        this.minTemperature = minTemp;

        // Extract charging stations from locations
        this.rechargingStations = locations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("charging_station"))
                .collect(Collectors.toList());

        // Default population parameters
        this.populationSize = 50;
        this.maxIterations = 1000;
        this.noImprovementThreshold = 100;
    }

    /**
     * Main method to find optimal routes using the hybrid algorithm
     * This implements Algorithm 7 from the project documentation
     *
     * @return A list of optimized routes
     */
    public List<Route> findOptimalRoutes() {
        // Step 1: Initial random population (PS)
        Population population = new Population(populationSize);
        population.initialize(drones, locations, weatherZones, obstacles);

        List<List<Route>> individuals = population.getIndividuals();
        List<Route> bestSolution = getBestIndividual(individuals);
        double bestFitness = calculateFitness(bestSolution);
        double previousBestFitness = bestFitness;

        int iterations = 0;
        int noImprovements = 0;
        int totalIterations = 0;
        long startTime = System.currentTimeMillis();

        System.out.println("Starting hybrid algorithm optimization...");
        System.out.println("Initial best solution fitness: " + bestFitness);

        // Main loop (while loop in Algorithm 7)
        while (iterations < maxIterations && noImprovements < noImprovementThreshold && totalIterations < 1000) {
            totalIterations++;

            // Check for timeout (5 minutes)
            long currentTime = System.currentTimeMillis();
            if ((currentTime - startTime) > 5 * 60 * 1000) {
                System.out.println("Time limit reached (5 minutes). Terminating optimization.");
                break;
            }

            // Genetic algorithm steps
            List<List<Route>> newPopulation = evolvePopulation(individuals);

            // Create a new SA instance with adjusted parameters for better convergence
            SimulatedAnnealing simulatedAnnealing = new SimulatedAnnealing(
                    locations, drones, obstacles, weatherZones,
                    initialTemperature, coolingRate, minTemperature);

            // Set lower limits to prevent SA from taking too long
            simulatedAnnealing.setMaxIterations(200);  // Limit SA iterations
            simulatedAnnealing.setNoImprovementThreshold(50);  // Lower no improvement threshold

            for (int i = 0; i < newPopulation.size(); i++) {
                List<Route> individual = newPopulation.get(i);

                // Skip empty individuals
                if (individual.isEmpty()) {
                    continue;
                }

                // Apply SA with timeout protection
                List<Route> improvedIndividual;
                try {
                    improvedIndividual = simulatedAnnealing.optimize(individual);
                    newPopulation.set(i, improvedIndividual);
                } catch (Exception e) {
                    System.err.println("Error in SA optimization for individual " + i + ": " + e.getMessage());
                    // Keep the original individual if SA fails
                }
            }

            // Find current best solution
            List<Route> currentBest = getBestIndividual(newPopulation);
            double currentFitness = calculateFitness(currentBest);

            // Apply 3-opt local search to the best solution (lines 22-32 in Algorithm 7)
            if (currentFitness < bestFitness) {
                double improvement = bestFitness - currentFitness;
                double relativeImprovement = improvement / bestFitness * 100;

                bestSolution = currentBest;
                bestFitness = currentFitness;
                noImprovements = 0;
                System.out.println("New best solution found with fitness: " + bestFitness +
                        " (Improvement: " + relativeImprovement + "%)");

                // Reset previous best for tracking significant improvements
                previousBestFitness = bestFitness;
            } else {
                noImprovements++;

                // Check if we're making minimal progress
                if (Math.abs(previousBestFitness - bestFitness) < 0.001 && noImprovements > 20) {
                    System.out.println("Minimal progress detected. Accelerating termination.");
                    noImprovements = noImprovementThreshold;  // Force termination
                }
            }

            // Update population for next iteration
            individuals = newPopulation;
            iterations++;

            if (iterations % 10 == 0) {
                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                System.out.println("Iteration " + iterations +
                        ", Best fitness: " + bestFitness +
                        ", No improvements: " + noImprovements +
                        ", Time elapsed: " + elapsedTime + " seconds");
            }
        }

        // Print termination reason
        if (iterations >= maxIterations) {
            System.out.println("Optimization terminated: maximum iterations reached");
        } else if (noImprovements >= noImprovementThreshold) {
            System.out.println("Optimization terminated: no improvement threshold reached");
        } else if (totalIterations >= 1000) {
            System.out.println("Optimization terminated: safety iteration limit reached");
        } else {
            System.out.println("Optimization terminated: time limit reached");
        }

        System.out.println("Optimization completed after " + iterations + " iterations");
        System.out.println("Best solution fitness: " + bestFitness);

        return bestSolution;
    }

    /**
     * Get the best individual from a population based on fitness
     *
     * @param population The population to evaluate
     * @return The best individual (solution) in the population
     */
    private List<Route> getBestIndividual(List<List<Route>> population) {
        return population.stream()
                .min(Comparator.comparingDouble(this::calculateFitness))
                .orElse(population.get(0));
    }

    /**
     * Calculate fitness (total cost) of a solution
     *
     * @param solution The solution to evaluate
     * @return The fitness (total cost) of the solution
     */
    private double calculateFitness(List<Route> solution) {
        double totalCost = solution.stream()
                .mapToDouble(Route::getTotalCost)
                .sum();
        return Math.min(totalCost, Double.MAX_VALUE);
    }

    /**
     * Evolve the population using genetic operators
     * This implements the genetic algorithm portion of lines 10-12 in Algorithm 7
     *
     * @param population The current population
     * @return A new evolved population
     */
    private List<List<Route>> evolvePopulation(List<List<Route>> population) {
        List<List<Route>> newPopulation = new ArrayList<>();

        // Keep top 10% elite individuals
        int eliteSize = populationSize / 10;
        population.stream()
                .sorted(Comparator.comparingDouble(this::calculateFitness))
                .limit(eliteSize)
                .forEach(individual -> newPopulation.add(deepCopy(individual)));

        // Perform crossover and mutation to generate the rest of the population
        while (newPopulation.size() < populationSize) {
            // Tournament selection
            List<Route> parent1 = selectParent(population);
            List<Route> parent2 = selectParent(population);

            // Crossover
            List<Route> offspring = crossover(parent1, parent2);

            // Mutation
            mutate(offspring);

            newPopulation.add(offspring);
        }

        return newPopulation;
    }

    /**
     * Select a parent using tournament selection
     *
     * @param population The population to select from
     * @return The selected parent
     */
    private List<Route> selectParent(List<List<Route>> population) {
        // Tournament selection
        int tournamentSize = 5;
        List<List<Route>> tournament = new ArrayList<>();
        Random rand = new Random();

        for (int i = 0; i < tournamentSize; i++) {
            tournament.add(population.get(rand.nextInt(population.size())));
        }

        return deepCopy(getBestIndividual(tournament));
    }

    /**
     * Perform crossover between two parents
     * This implements part of line 11 in Algorithm 7
     *
     * @param parent1 First parent
     * @param parent2 Second parent
     * @return Offspring resulting from crossover
     */
    private List<Route> crossover(List<Route> parent1, List<Route> parent2) {
        List<Route> offspring = new ArrayList<>();
        Set<Location> usedLocations = new HashSet<>();

        // Find depot
        Location depot = locations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("depot"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No depot found in locations"));

        // Add depot to used locations to avoid duplicates
        usedLocations.add(depot);

        // Take routes from parent1 where possible
        for (Route route : parent1) {
            Drone drone = route.getDrone();
            List<Location> newLocations = new ArrayList<>();
            double totalDemand = 0;

            // Always include depot in each route
            newLocations.add(depot);

            // Add locations that respect capacity and aren't already used
            for (Location loc : route.getLocations()) {
                if (!usedLocations.contains(loc) &&
                        !loc.getType().equalsIgnoreCase("depot") &&
                        !loc.getType().equalsIgnoreCase("charging_station")) {

                    if (totalDemand + loc.getDemand() <= drone.getPayloadCapacity()) {
                        newLocations.add(loc);
                        usedLocations.add(loc);
                        totalDemand += loc.getDemand();
                    }
                }
            }

            // Add depot at the end
            if (!newLocations.isEmpty() && !newLocations.get(newLocations.size() - 1).equals(depot)) {
                newLocations.add(depot);
            }

            if (newLocations.size() > 2) { // At least depot + one location + depot
                offspring.add(new Route(drone, newLocations, weatherZones, obstacles, rechargingStations));
            }
        }

        // Add remaining locations from parent2
        List<Location> remainingLocations = new ArrayList<>();
        for (Route route : parent2) {
            for (Location loc : route.getLocations()) {
                if (!usedLocations.contains(loc) &&
                        !loc.getType().equalsIgnoreCase("depot") &&
                        !loc.getType().equalsIgnoreCase("charging_station")) {
                    remainingLocations.add(loc);
                    usedLocations.add(loc);
                }
            }
        }

        // Group remaining locations into new routes
        if (!remainingLocations.isEmpty()) {
            for (Drone drone : drones) {
                List<Location> droneLocations = new ArrayList<>();
                droneLocations.add(depot);

                double availableCapacity = drone.getPayloadCapacity();
                Iterator<Location> iterator = remainingLocations.iterator();

                while (iterator.hasNext()) {
                    Location loc = iterator.next();
                    if (loc.getDemand() <= availableCapacity) {
                        droneLocations.add(loc);
                        availableCapacity -= loc.getDemand();
                        iterator.remove();
                    }
                }

                // Add depot at the end
                if (!droneLocations.isEmpty() && !droneLocations.get(droneLocations.size() - 1).equals(depot)) {
                    droneLocations.add(depot);
                }

                if (droneLocations.size() > 2) { // At least depot + one location + depot
                    offspring.add(new Route(drone, droneLocations, weatherZones, obstacles, rechargingStations));
                }

                if (remainingLocations.isEmpty()) {
                    break;
                }
            }
        }

        return offspring;
    }

    /**
     * Perform mutation on a solution
     * This implements part of line 10 in Algorithm 7
     *
     * @param solution The solution to mutate
     */
    private void mutate(List<Route> solution) {
        Random rand = new Random();

        // Skip if there aren't enough routes to mutate
        if (solution.size() < 2) {
            return;
        }

        // Select two random routes
        int routeIdx1 = rand.nextInt(solution.size());
        int routeIdx2;
        do {
            routeIdx2 = rand.nextInt(solution.size());
        } while (routeIdx1 == routeIdx2);

        Route route1 = solution.get(routeIdx1);
        Route route2 = solution.get(routeIdx2);

        // Skip if either route is too small
        if (route1.getLocations().size() < 3 || route2.getLocations().size() < 3) {
            return;
        }

        // Choose random locations to swap (skip depot at start and end)
        int locIdx1 = 1 + rand.nextInt(route1.getLocations().size() - 2);
        int locIdx2 = 1 + rand.nextInt(route2.getLocations().size() - 2);

        Location loc1 = route1.getLocations().get(locIdx1);
        Location loc2 = route2.getLocations().get(locIdx2);

        // Check capacity constraints
        double newDemand1 = route1.getTotalDemand() - loc1.getDemand() + loc2.getDemand();
        double newDemand2 = route2.getTotalDemand() - loc2.getDemand() + loc1.getDemand();

        if (route1.getDrone().getPayloadCapacity() >= newDemand1 &&
                route2.getDrone().getPayloadCapacity() >= newDemand2) {
            // Swap locations
            route1.getLocations().set(locIdx1, loc2);
            route2.getLocations().set(locIdx2, loc1);
        }
    }

    /**
     * Create a deep copy of a solution
     *
     * @param solution The solution to copy
     * @return A deep copy of the solution
     */
    private List<Route> deepCopy(List<Route> solution) {
        List<Route> copy = new ArrayList<>();

        for (Route route : solution) {
            copy.add(new Route(
                    route.getDrone(),
                    new ArrayList<>(route.getLocations()),
                    weatherZones,
                    obstacles,
                    rechargingStations
            ));
        }

        return copy;
    }
}