package org.example.models;

import java.util.Arrays;

public class Location {
    private int id;
    private double x, y; // Coordinates
    private double demand; // Weight/quantity of packages
    private double[] timeWindow; // [ET, LT]
    private double serviceTime; // Time required to complete delivery
    private String type; // "depot", "customer", or "charging station"

    public Location(int id, double x, double y, double demand, double[] timeWindow, double serviceTime, String type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.timeWindow = timeWindow;
        this.serviceTime = serviceTime;
        this.type = type;
    }

    // Getters and setters
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getDemand() { return demand; }
    public double[] getTimeWindow() { return timeWindow; }
    public double getServiceTime() { return serviceTime; }
    public String getType() { return type; }


    @Override
    public String toString() {
        return "Location{" +
                "id=" + id +
                ", x=" + x +
                ", y=" + y +
                ", demand=" + demand +
                ", timeWindow=" + Arrays.toString(timeWindow) +
                ", serviceTime=" + serviceTime +
                ", type='" + type + '\'' +
                '}';
    }
}