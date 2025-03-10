package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

        int maxAttempts = 100;

        for (int i = 0; i < populationSize; i++) {
            boolean valid = false;
            List<Route> individual = new ArrayList<>();
            int attempts = 0;

            while (!valid && attempts < maxAttempts) {
                individual.clear();

                // Shuffle customers for random assignment
                List<Location> customersToAssign = new ArrayList<>(customers);
                Collections.shuffle(customersToAssign);

                // Assign customers to drones
                for (Drone drone : drones) {
                    if (customersToAssign.isEmpty()) {
                        break;
                    }

                    List<Location> routeLocations = new ArrayList<>();
                    routeLocations.add(depot); // Start at depot

                    double remainingCapacity = drone.getPayloadCapacity();
                    List<Location> assignedToRoute = new ArrayList<>();

                    // Try to assign customers until capacity is full
                    // Use a separate list to track assignment to avoid concurrent modification
                    for (Location customer : customersToAssign) {
                        if (customer.getDemand() <= remainingCapacity) {
                            routeLocations.add(customer);
                            assignedToRoute.add(customer);
                            remainingCapacity -= customer.getDemand();

                            // Limit route size randomly
                            if (random.nextDouble() < 0.2 || routeLocations.size() > 5) {
                                break;
                            }
                        }
                    }

                    // Remove assigned customers
                    customersToAssign.removeAll(assignedToRoute);

                    // Only create route if there are customers assigned
                    if (routeLocations.size() > 1) {
                        routeLocations.add(depot); // End at depot
                        Route route = new Route(drone, routeLocations, weatherZones, obstacles, rechargingStations);
                        individual.add(route);
                    }
                }

                // Check if all customers are assigned
                if (customersToAssign.isEmpty()) {
                    valid = true;
                }

                attempts++;
            }

            // If we failed to create a valid individual after max attempts, create a fallback solution
            if (!valid) {
                System.out.println("Warning: Could not generate valid individual after " + maxAttempts +
                        " attempts. Creating fallback solution.");

                // Create a simple solution with all customers in a single route for the first drone
                if (!customers.isEmpty() && !drones.isEmpty()) {
                    List<Location> routeLocations = new ArrayList<>();
                    routeLocations.add(depot);
                    routeLocations.addAll(customers);
                    routeLocations.add(depot);

                    individual.add(new Route(drones.get(0), routeLocations, weatherZones, obstacles, rechargingStations));
                }
            }

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
