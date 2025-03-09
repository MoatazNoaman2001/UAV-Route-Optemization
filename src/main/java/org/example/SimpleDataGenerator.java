package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

public class SimpleDataGenerator {
    private static final Random random = new Random();

    public static void main(String[] args) {
        // Generate weather zones
        generateWeatherZones(1, "src/main/java/org/example/resource/weather_zone.txt");

        // Generate obstacles
        generateObstacles(2, "src/main/java/org/example/resource/obstacles.txt");

        System.out.println("Weather zones and obstacles generated successfully!");
    }

    private static void generateWeatherZones(int count, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header
            writer.println("# Weather Zones");
            writer.println("# xMin,xMax,yMin,yMax,windSpeed,windAngle");

            // Generate random weather zones
            for (int i = 0; i < count; i++) {
                double xMin = random.nextInt(80);
                double xMax = xMin + 10 + random.nextInt(30);
                double yMin = random.nextInt(80);
                double yMax = yMin + 10 + random.nextInt(30);
                double windSpeed = 5 + random.nextInt(20);  // 5-25 units
                double windAngle = random.nextInt(360);     // 0-359 degrees

                writer.printf("%.1f,%.1f,%.1f,%.1f,%.1f,%.1f%n",
                        xMin, xMax, yMin, yMax, windSpeed, windAngle);
            }
        } catch (IOException e) {
            System.err.println("Error writing weather zone file: " + e.getMessage());
        }
    }

    private static void generateObstacles(int count, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header
            writer.println("# Obstacles");
            writer.println("# centerX,centerY,radius,penalty");

            // Generate random obstacles
            for (int i = 0; i < count; i++) {
                double centerX = 10 + random.nextInt(80);
                double centerY = 10 + random.nextInt(80);
                double radius = 2 + random.nextInt(5);      // 2-7 units
                double penalty = 50 + random.nextInt(100);  // 50-150 penalty

                writer.printf("%.1f,%.1f,%.1f,%.1f%n",
                        centerX, centerY, radius, penalty);
            }
        } catch (IOException e) {
            System.err.println("Error writing obstacle file: " + e.getMessage());
        }
    }
}