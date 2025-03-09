package org.example.models;

public class Drone {
    private int id;
    private double speed; // km/h or m/s
    private double maxBattery; // Maximum battery capacity (kWh)
    private double minBattery; // Minimum battery level before recharge (kWh)
    private double batteryLevel; // Current battery charge
    private double payloadCapacity; // Maximum weight the drone can carry (kg)
    private double baseEnergyConsumption; // Energy used per km without load (kWh/km)
    private double payloadEnergyCoefficient; // Additional energy consumption per kg payload
    private double rechargingTime; // Time to fully recharge the battery
    private double rechargingCost; // Cost of each recharge operation

    public Drone(int id, double speed, double maxBattery, double minBattery, double payloadCapacity,
                 double baseEnergyConsumption, double payloadEnergyCoefficient, double rechargingTime, double rechargingCost) {
        this.id = id;
        this.speed = speed;
        this.maxBattery = maxBattery;
        this.minBattery = minBattery;
        this.batteryLevel = maxBattery;
        this.payloadCapacity = payloadCapacity;
        this.baseEnergyConsumption = baseEnergyConsumption;
        this.payloadEnergyCoefficient = payloadEnergyCoefficient;
        this.rechargingTime = rechargingTime;
        this.rechargingCost = rechargingCost;
    }

    // Getters and setters
    public Integer   getId() { return id; }
    public double getSpeed() { return speed; }
    public double getMaxBattery() { return maxBattery; }
    public double getMinBattery() { return minBattery; }
    public double getBatteryLevel() { return batteryLevel; }
    public double getPayloadCapacity() { return payloadCapacity; }
    public double getBaseEnergyConsumption() { return baseEnergyConsumption; }
    public double getPayloadEnergyCoefficient() { return payloadEnergyCoefficient; }
    public double getRechargingTime() { return rechargingTime; }
    public double getRechargingCost() { return rechargingCost; }

    public void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    @Override
    public String toString() {
        return "Drone{" +
                "id=" + id +
                ", speed=" + speed +
                ", maxBattery=" + maxBattery +
                ", minBattery=" + minBattery +
                ", batteryLevel=" + batteryLevel +
                ", payloadCapacity=" + payloadCapacity +
                ", baseEnergyConsumption=" + baseEnergyConsumption +
                ", payloadEnergyCoefficient=" + payloadEnergyCoefficient +
                ", rechargingTime=" + rechargingTime +
                ", rechargingCost=" + rechargingCost +
                '}';
    }
}