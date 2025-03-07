package org.example;

public class Obstacle {
    private double centerX, centerY;
    private double radius;
    private double penalty;

    public Obstacle(double centerX, double centerY, double radius, double penalty) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.penalty = penalty;
    }

    // Getters
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getRadius() { return radius; }
    public double getPenalty() { return penalty; }
}