package org.example.models;

public class WeatherZone {
    private double xMin, xMax, yMin, yMax;
    private double windSpeed;
    private double windAngle; // in degrees

    public WeatherZone(double xMin, double xMax, double yMin, double yMax, double windSpeed, double windAngle) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.windSpeed = windSpeed;
        this.windAngle = windAngle;
    }

    @Override
    public String toString() {
        return "WeatherZone{" +
                "xMin=" + xMin +
                ", xMax=" + xMax +
                ", yMin=" + yMin +
                ", yMax=" + yMax +
                ", windSpeed=" + windSpeed +
                ", windAngle=" + windAngle +
                '}';
    }

    // Getters
    public double getXMin() { return xMin; }
    public double getXMax() { return xMax; }
    public double getYMin() { return yMin; }
    public double getYMax() { return yMax; }
    public double getWindSpeed() { return windSpeed; }
    public double getWindAngle() { return windAngle; }
}
