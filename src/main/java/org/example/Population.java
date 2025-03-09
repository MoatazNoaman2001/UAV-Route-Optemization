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

public class Population {
    private List<List<Route>> individuals;
    private int populationSize;

    public Population(int populationSize) {
        this.populationSize = populationSize;
        this.individuals = new ArrayList<>(populationSize);
    }

    public void initialize(List<Drone> drones, List<Location> locations,
                           List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        for (int i = 0; i < populationSize; i++) {
            List<Route> solution = new ArrayList<>();
            List<Location> rechargingPlaces= locations.stream().filter(loc ->  loc.getType().equalsIgnoreCase("charging_station")).toList();
            Location depot= locations.stream().filter(loc ->loc.getType().equalsIgnoreCase("depot")).findFirst().get();

            List<Location> remainingLocations = new ArrayList<>(locations){{
                removeAll(locations.stream().filter(loc -> loc.getType().equalsIgnoreCase("charging_station")).toList());
                removeAll(locations.stream().filter(loc -> loc.getType().equalsIgnoreCase("depot")).toList());
            }};
            Collections.shuffle(remainingLocations);

            int numDrones = drones.size();
            int locationsPerDrone = (int) Math.ceil((double) remainingLocations.size() / numDrones);
            for (Drone drone : drones) {
                List<Location> group = new ArrayList<>();
                double currentLoad = 0;

                Iterator<Location> iterator = remainingLocations.iterator();
                while (iterator.hasNext() && group.size() < locationsPerDrone) {
                    Location loc = iterator.next();
                    if (currentLoad + loc.getDemand() <= drone.getPayloadCapacity()) {
                        group.add(loc);
                        currentLoad += loc.getDemand();
                        iterator.remove();
                    }
                }

                if (!group.isEmpty()) {
                    group.add(depot);
                    solution.add(new Route(drone, group, weatherZones, obstacles, rechargingPlaces));
                }

                if (remainingLocations.isEmpty()) break;
            }

            individuals.add(solution);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(individuals.stream()
                .min(Comparator.comparingDouble(o -> o.stream()
                        .mapToDouble(Route::getTotalDemand).sum())).get());
        System.out.println(json);
    }
    public List<List<Route>> getIndividuals() {
        return individuals;
    }
}
