package org.example;
import java.util.*;
import java.util.stream.Collectors;

public class Algorithm {
    private List<Drone> drones;
    private List<Location> locations;
    private List<WeatherZone> weatherZones;
    private List<Obstacle> obstacles;
    private int populationSize = 50; // Size of the population
    private int maxIterations = 1000; // Maximum number of iterations
    private int noImprovementThreshold = 100; // Threshold for stopping if no improvement

    private double initialTemperature = 1000; // SA parameters
    private double coolingRate = 0.005;
    private double minTemperature = 1.0;

    public Algorithm(List<Drone> drones, List<Location> locations,
                     List<WeatherZone> weatherZones, List<Obstacle> obstacles,
                     double initialTemp, double coolingRate, double minTemp) {
        this.drones = drones;
        this.locations = locations;
        this.weatherZones = weatherZones;
        this.obstacles = obstacles;
        this.initialTemperature = initialTemp;
        this.coolingRate = coolingRate;
        this.minTemperature = minTemp;

    }

    public Route findOptimalRoute() {
        // Step 1: Initialize a random population of routes
        List<Route> population = initializePopulation();

        Route bestRoute = population.getFirst();
        double bestFitness = calculateFitness(bestRoute);

        int noImprovements = 0;
        int iteration = 0;


        // Step 2: Main loop for the hybrid algorithm
        while (iteration < maxIterations && noImprovements < noImprovementThreshold) {
            population = evolvePopulation(population);

            // Step 3: Apply Genetic Algorithm operations
            // Replace Tabu Search with Simulated Annealing
            for (int i = 0; i < population.size(); i++) {
                Route improvedRoute = simulatedAnnealing(population.get(i));
                population.set(i, improvedRoute);
            }


            // Step 5: Sort the population by fitness
            population.sort(Comparator.comparingDouble(this::calculateFitness));

            // Step 6: Update the best route if a better one is found
            Route currentBestRoute = population.get(0);
            double currentBestFitness = calculateFitness(currentBestRoute);

            if (currentBestFitness < bestFitness) {
                bestRoute = currentBestRoute;
                bestFitness = currentBestFitness;
                noImprovements = 0; // Reset the no improvement counter
            } else {
                noImprovements++; // Increment the no improvement counter
            }

            iteration++;
        }

        // Step 7: Apply 3-opt local search to the best route
        bestRoute = threeOptLocalSearch(bestRoute);

        return bestRoute;
    }

    // Helper method to initialize a random population of routes
    private List<Route> initializePopulation() {
        List<Route> population = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < populationSize; i++) {
            Drone drone = drones.get(random.nextInt(drones.size()));
            List<Location> randomLocations = new ArrayList<>(locations);
            Collections.shuffle(randomLocations);
            population.add(new Route(drone, randomLocations, weatherZones, obstacles));
        }
        return population;
    }

    // Helper method to calculate the fitness of a route (total cost)
    private double calculateFitness(Route route) {
        return route.getTotalCost();
    }

    // Helper method to evolve the population using Genetic Algorithm operations
    private List<Route> evolvePopulation(List<Route> population) {
        List<Route> newPopulation = new ArrayList<>();

        // Selection: Keep the top 10% of the population
        int eliteSize = populationSize / 10;
        newPopulation.addAll(population.subList(0, eliteSize));

        // Crossover and Mutation: Generate the rest of the population
        while (newPopulation.size() < populationSize) {
            Route parent1 = selectParent(population);
            Route parent2 = selectParent(population);

            Route offspring = crossover(parent1, parent2);
            mutate(offspring);

            newPopulation.add(offspring);
        }

        return newPopulation;
    }

    // Helper method to select a parent using tournament selection
    private Route selectParent(List<Route> population) {
        int tournamentSize = 5;
        List<Route> tournament = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < tournamentSize; i++) {
            int randomIndex = random.nextInt(population.size());
            tournament.add(population.get(randomIndex));
        }

        tournament.sort(Comparator.comparingDouble(this::calculateFitness));
        return tournament.get(0);
    }

    // Helper method to perform crossover between two parents
    private Route crossover(Route parent1, Route parent2) {
        List<Location> offspringLocations = new ArrayList<>();
        int crossoverPoint = new Random().nextInt(parent1.getLocations().size());

        // Take the first part from parent1
        offspringLocations.addAll(parent1.getLocations().subList(0, crossoverPoint));

        // Take the remaining locations from parent2, avoiding duplicates
        for (Location location : parent2.getLocations()) {
            if (!offspringLocations.contains(location)) {
                offspringLocations.add(location);
            }
        }

        return new Route(
                parent1.getDrone(),
                offspringLocations,
                weatherZones,
                obstacles
        );
    }

    // Helper method to perform mutation on a route
    private void mutate(Route route) {
        Random random = new Random();
        List<Location> locations = route.getLocations();

        // Swap two random locations in the route
        int index1 = random.nextInt(locations.size());
        int index2 = random.nextInt(locations.size());
        Collections.swap(locations, index1, index2);
    }

    // NEW: Simulated Annealing method
    private Route simulatedAnnealing(Route initialRoute) {
        Route currentRoute = new Route(initialRoute.getDrone(), initialRoute.getLocations(), weatherZones, obstacles);
        Route bestRoute = currentRoute;
        double currentTemp = initialTemperature;

        while (currentTemp > minTemperature) {
            Route adjacentRoute = getAdjacentRoute(currentRoute);
            double currentEnergy = calculateFitness(currentRoute);
            double adjacentEnergy = calculateFitness(adjacentRoute);

            if (acceptanceProbability(currentEnergy, adjacentEnergy, currentTemp) > Math.random()) {
                currentRoute = adjacentRoute;
                if (adjacentEnergy < calculateFitness(bestRoute)) {
                    bestRoute = adjacentRoute;
                }
            }
            currentTemp *= 1 - coolingRate;
        }
        return bestRoute;
    }

    // Generate adjacent route by swapping two random locations
    private Route getAdjacentRoute(Route route) {
        List<Location> newLocations = new ArrayList<>(route.getLocations());
        int x1 = (int) (newLocations.size() * Math.random());
        int x2 = (int) (newLocations.size() * Math.random());
        Collections.swap(newLocations, x1, x2);
        return new Route(
                route.getDrone(),
                newLocations,
                weatherZones,
                obstacles
        );
    }

    // Calculate acceptance probability
    private double acceptanceProbability(double currentEnergy, double newEnergy, double temp) {
        if (newEnergy < currentEnergy) return 1.0;
        return Math.exp((currentEnergy - newEnergy) / temp);
    }

    // Helper method to perform 3-opt local search on a route
    private Route threeOptLocalSearch(Route route) {
        Route bestRoute = route;
        double bestFitness = calculateFitness(bestRoute);

        List<Location> locations = route.getLocations();

        for (int i = 1; i < locations.size() - 2; i++) {
            for (int j = i + 1; j < locations.size() - 1; j++) {
                for (int k = j + 1; k < locations.size(); k++) {
                    Route newRoute = threeOptSwap(route, i, j, k);
                    double newFitness = calculateFitness(newRoute);

                    if (newFitness < bestFitness) {
                        bestRoute = newRoute;
                        bestFitness = newFitness;
                    }
                }
            }
        }

        return bestRoute;
    }

    // Helper method to perform a 3-opt swap on a route
    private Route threeOptSwap(Route route, int i, int j, int k) {
        List<Location> locations = route.getLocations();
        List<Location> newLocations = new ArrayList<>();

        // Take the segments and reverse them as needed
        newLocations.addAll(locations.subList(0, i));
        newLocations.addAll(reverseList(locations.subList(i, j)));
        newLocations.addAll(reverseList(locations.subList(j, k)));
        newLocations.addAll(locations.subList(k, locations.size()));

        return new Route(
                route.getDrone(),
                newLocations,
                weatherZones,
                obstacles
        );
    }

    // Helper method to reverse a list
    private List<Location> reverseList(List<Location> list) {
        List<Location> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }
}