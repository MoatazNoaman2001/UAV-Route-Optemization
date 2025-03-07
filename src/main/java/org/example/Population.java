package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Population {
    private List<Route> individuals;
    private int populationSize;

    public Population(int populationSize) {
        this.populationSize = populationSize;
        this.individuals = new ArrayList<>(populationSize);
    }

    public Population(List<Route> individuals) {
        this.individuals = new ArrayList<>(individuals);
        this.populationSize = individuals.size();
    }

    public void initialize(List<Drone> drones, List<Location> locations,
                           List<WeatherZone> weatherZones, List<Obstacle> obstacles) {
        Random random = new Random();
        for (int i = 0; i < populationSize; i++) {
            Drone drone = drones.get(random.nextInt(drones.size()));
            List<Location> randomLocations = new ArrayList<>(locations);
            Collections.shuffle(randomLocations);
            individuals.add(new Route(drone, randomLocations, weatherZones, obstacles));
        }
    }

    public Route getBestIndividual() {
        return individuals.stream()
                .min(Comparator.comparingDouble(Route::getTotalCost))
                .orElse(null);
    }

    public void sortByFitness() {
        individuals.sort(Comparator.comparingDouble(Route::getTotalCost));
    }

    public Route getIndividual(int index) {
        return individuals.get(index);
    }

    public void setIndividual(int index, Route route) {
        individuals.set(index, route);
    }

    public void addIndividual(Route route) {
        individuals.add(route);
    }

    public int size() {
        return individuals.size();
    }

    public List<Route> getIndividuals() {
        return new ArrayList<>(individuals);
    }
}
