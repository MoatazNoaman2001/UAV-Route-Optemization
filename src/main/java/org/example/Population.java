package org.example;

import org.example.models.Drone;
import org.example.models.Location;
import org.example.models.Obstacle;
import org.example.models.WeatherZone;

import java.util.*;
import java.util.stream.Collectors;

public class Population {
    private List<List<Route>> individuals;
    private final Random random = new Random();
    private int populationSize;

    public Population(int populationSize) {
        this.populationSize = populationSize;
        this.individuals = new ArrayList<>(populationSize);
    }

    /**
     * Initialize population with evenly distributed customer locations
     * Each drone gets approximately the same number of customers
     * If there's a remainder, it's distributed to the first drones
     */
    public void initialize(List<Drone> drones, List<Location> allLocations,
                           List<WeatherZone> weatherZones, List<Obstacle> obstacles) {

        Location depot = allLocations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("depot"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No depot found in locations"));

        List<Location> rechargingStations = allLocations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("charging_station"))
                .collect(Collectors.toList());

        List<Location> customers = allLocations.stream()
                .filter(loc -> !loc.getType().equalsIgnoreCase("depot") &&
                        !loc.getType().equalsIgnoreCase("charging_station"))
                .collect(Collectors.toList());

        System.out.println("Total customers: " + customers.size());
        System.out.println("Total drones: " + drones.size());

        // Generate individuals for the population
        for (int i = 0; i < populationSize; i++) {
            List<Route> individual = createEvenlyDistributedIndividual(
                    drones, customers, depot, rechargingStations, weatherZones, obstacles);
            individuals.add(individual);
        }
    }

    /**
     * Create an individual with evenly distributed customers across drones
     * For example, with 10 customers and 4 drones:
     * - First 3 drones get 3 customers each
     * - Last drone gets 1 customer
     */
    private List<Route> createEvenlyDistributedIndividual(List<Drone> drones, List<Location> customers,
                                                          Location depot, List<Location> rechargingStations,
                                                          List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        List<Route> individual = new ArrayList<>();

        // If there are no drones or customers, return empty individual
        if (drones.isEmpty() || customers.isEmpty()) {
            return individual;
        }

        // Calculate distribution: base number and remainder
        int totalCustomers = customers.size();
        int numDrones = drones.size();

        int baseCustomersPerDrone = totalCustomers / numDrones;
        int remainder = totalCustomers % numDrones;

        System.out.println("Base customers per drone: " + baseCustomersPerDrone);
        System.out.println("Remainder: " + remainder);

        // Shuffle customers to create different individuals in the population
        List<Location> shuffledCustomers = new ArrayList<>(customers);
        Collections.shuffle(shuffledCustomers);

        // Queue to hold customers for assignment
        Queue<Location> customerQueue = new LinkedList<>(shuffledCustomers);

        // Distribute customers to drones
        for (int i = 0; i < drones.size(); i++) {
            Drone drone = drones.get(i);

            // Calculate how many customers this drone gets
            int customersForThisDrone = baseCustomersPerDrone;
            if (i < remainder) {
                customersForThisDrone++; // First 'remainder' drones get one extra customer
            }

            System.out.println("Assigning " + customersForThisDrone + " customers to drone " + drone.getId());

            if (customersForThisDrone > 0) {
                List<Location> routeLocations = new ArrayList<>();
                routeLocations.add(depot); // Start at depot

                double remainingCapacity = drone.getPayloadCapacity();
                List<Location> unassignedForCapacity = new ArrayList<>();

                // Try to assign the calculated number of customers
                for (int j = 0; j < customersForThisDrone && !customerQueue.isEmpty(); j++) {
                    Location customer = customerQueue.poll();

                    // Check if customer fits the capacity
                    if (customer.getDemand() <= remainingCapacity) {
                        routeLocations.add(customer);
                        remainingCapacity -= customer.getDemand();
                    } else {
                        // Customer doesn't fit capacity, save for later
                        unassignedForCapacity.add(customer);
                    }
                }

                // Add unassigned customers back to the queue
                customerQueue.addAll(unassignedForCapacity);

                // Only create route if there are customers assigned
                if (routeLocations.size() > 1) {
                    routeLocations.add(depot); // End at depot
                    Route route = new Route(drone, routeLocations, weatherZones, obstacles, rechargingStations);
                    individual.add(route);
                }
            }
        }

        // Handle any remaining customers (due to capacity constraints)
        if (!customerQueue.isEmpty()) {
            System.out.println("Warning: " + customerQueue.size() +
                    " customers couldn't be assigned due to capacity constraints. Creating additional routes.");

            // Try to assign remaining customers by creating additional routes
            int droneIndex = 0;

            while (!customerQueue.isEmpty() && droneIndex < drones.size()) {
                Drone drone = drones.get(droneIndex);

                List<Location> routeLocations = new ArrayList<>();
                routeLocations.add(depot);

                double remainingCapacity = drone.getPayloadCapacity();

                // Fill this route with as many customers as possible
                while (!customerQueue.isEmpty()) {
                    Location customer = customerQueue.peek();

                    if (customer.getDemand() <= remainingCapacity) {
                        routeLocations.add(customerQueue.poll());
                        remainingCapacity -= customer.getDemand();
                    } else {
                        break; // Move to next drone if customer doesn't fit
                    }
                }

                if (routeLocations.size() > 1) {
                    routeLocations.add(depot);
                    Route route = new Route(drone, routeLocations, weatherZones, obstacles, rechargingStations);
                    individual.add(route);
                }

                droneIndex = (droneIndex + 1) % drones.size();
            }
        }

        // Final check - if there are still unassigned customers, report error
        if (!customerQueue.isEmpty()) {
            System.err.println("ERROR: Failed to assign " + customerQueue.size() +
                    " customers. Check drone capacities or problem constraints.");
        }

        return individual;
    }

    /**
     * Initialize diverse population with different assignment strategies
     */
    public void initializeDiverse(List<Drone> drones, List<Location> allLocations,
                                  List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        Location depot = allLocations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("depot"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No depot found in locations"));

        List<Location> rechargingStations = allLocations.stream()
                .filter(loc -> loc.getType().equalsIgnoreCase("charging_station"))
                .collect(Collectors.toList());

        List<Location> customers = allLocations.stream()
                .filter(loc -> !loc.getType().equalsIgnoreCase("depot") &&
                        !loc.getType().equalsIgnoreCase("charging_station"))
                .collect(Collectors.toList());

        for (int i = 0; i < populationSize; i++) {
            List<Location> shuffledCustomers = new ArrayList<>(customers);

            // Use different sorting/shuffling strategies for diversity
            if (i % 4 == 0) {
                // Random assignment
                Collections.shuffle(shuffledCustomers);
            } else if (i % 4 == 1) {
                // Sort by demand
                shuffledCustomers.sort(Comparator.comparing(Location::getDemand).reversed());
            } else if (i % 4 == 2) {
                // Sort by distance from depot
                final Location finalDepot = depot;
                shuffledCustomers.sort(Comparator.comparing(customer -> {
                    double dx = customer.getX() - finalDepot.getX();
                    double dy = customer.getY() - finalDepot.getY();
                    return Math.sqrt(dx * dx + dy * dy);
                }));
            } else {
                // Sort by time windows
                shuffledCustomers.sort(Comparator.comparing(loc -> loc.getTimeWindow()[0]));
            }

            // Create individual with the specific distribution pattern
            List<Route> individual = createEvenlyDistributedIndividual(
                    drones, shuffledCustomers, depot, rechargingStations, weatherZones, obstacles);
            individuals.add(individual);
        }
    }

    public List<List<Route>> getIndividuals() {
        return individuals;
    }

    public void setIndividuals(List<List<Route>> individuals) {
        this.individuals = individuals;
    }
}