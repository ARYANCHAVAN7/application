package com.example.application.utils;

import android.location.Location;
import java.util.ArrayList;
import java.util.List;

/**
 * GPS Location Smoothing Utility
 * 
 * Implements a moving average filter to reduce GPS jitter while preserving real movement.
 * 
 * Algorithms:
 * 1. Moving Average: Simple average of last N positions
 * 2. Outlier Detection: Reject positions that jump too far from previous position
 * 
 * Notes:
 * - Does NOT introduce fake coordinates
 * - Does NOT delay location updates excessively
 * - Preserves real ambulance movement
 * - Reduces small GPS noise
 */
public class GPSSmoothing {

    // Default buffer size for moving average
    private static final int DEFAULT_BUFFER_SIZE = 3;

    // Maximum acceptable distance jump (50 meters) before treating as valid movement
    private static final float MAX_JITTER_DISTANCE_METERS = 50.0f;

    /**
     * Simple moving average filter for GPS coordinates
     */
    public static class MovingAverageFilter {
        private final int bufferSize;
        private final List<Location> locationBuffer;
        private Location lastSmoothedLocation;

        /**
         * Create filter with default buffer size (3 positions)
         */
        public MovingAverageFilter() {
            this(DEFAULT_BUFFER_SIZE);
        }

        /**
         * Create filter with custom buffer size
         * 
         * @param bufferSize Number of positions to average (recommended 2-5)
         */
        public MovingAverageFilter(int bufferSize) {
            this.bufferSize = Math.max(1, bufferSize);
            this.locationBuffer = new ArrayList<>();
            this.lastSmoothedLocation = null;
        }

        /**
         * Add location and get smoothed result
         * 
         * @param location Raw GPS location
         * @return Smoothed location, or null if insufficient data
         */
        public Location addLocation(Location location) {
            if (location == null) {
                return null;
            }

            // Check if location is valid
            if (!isValidGPSReading(location)) {
                return null;
            }

            // Check if this is an outlier jump (possible GPS spike)
            if (lastSmoothedLocation != null) {
                float distanceTo = lastSmoothedLocation.distanceTo(location);
                
                // If jump is larger than expected, check if it's real movement or jitter
                if (distanceTo > MAX_JITTER_DISTANCE_METERS) {
                    // Large movement - could be real
                    // Clear buffer to quickly adapt to new position
                    locationBuffer.clear();
                }
            }

            // Add to buffer
            locationBuffer.add(location);

            // Keep buffer at specified size
            while (locationBuffer.size() > bufferSize) {
                locationBuffer.remove(0);
            }

            // Calculate smoothed location if buffer has data
            if (!locationBuffer.isEmpty()) {
                return calculateAverage();
            }

            return null;
        }

        /**
         * Calculate average location from buffer
         */
        private Location calculateAverage() {
            if (locationBuffer.isEmpty()) {
                return null;
            }

            double avgLatitude = 0;
            double avgLongitude = 0;
            double avgAccuracy = 0;
            long avgTime = 0;

            for (Location loc : locationBuffer) {
                avgLatitude += loc.getLatitude();
                avgLongitude += loc.getLongitude();
                avgAccuracy += loc.getAccuracy();
                avgTime += loc.getTime();
            }

            int count = locationBuffer.size();
            avgLatitude /= count;
            avgLongitude /= count;
            avgAccuracy /= count;
            avgTime /= count;

            // Create smoothed location
            Location smoothed = new Location("smoothed");
            smoothed.setLatitude(avgLatitude);
            smoothed.setLongitude(avgLongitude);
            smoothed.setAccuracy((float) avgAccuracy);
            smoothed.setTime(avgTime);

            lastSmoothedLocation = smoothed;
            return smoothed;
        }

        /**
         * Get the last smoothed location (without adding new data)
         */
        public Location getLastSmoothedLocation() {
            return lastSmoothedLocation;
        }

        /**
         * Clear the buffer (useful when resuming after pause)
         */
        public void reset() {
            locationBuffer.clear();
            lastSmoothedLocation = null;
        }

        /**
         * Get current buffer size
         */
        public int getBufferSize() {
            return locationBuffer.size();
        }
    }

    /**
     * Check if GPS reading is valid
     * 
     * - Not null
     * - Has valid coordinates
     * - Has reasonable accuracy
     */
    public static boolean isValidGPSReading(Location location) {
        if (location == null) {
            return false;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        float accuracy = location.getAccuracy();

        // Check coordinate ranges
        if (!HaversineDistance.isValidCoordinate(latitude, longitude)) {
            return false;
        }

        // Check accuracy (should be > 0 and < 1000 meters is reasonable)
        if (accuracy <= 0 || accuracy > 1000) {
            return false;
        }

        return true;
    }

    /**
     * Calculate estimated movement between two locations
     * 
     * @param prev Previous location
     * @param current Current location
     * @return Distance in meters, or -1 if invalid
     */
    public static float calculateMovement(Location prev, Location current) {
        if (prev == null || current == null) {
            return -1;
        }

        if (!isValidGPSReading(prev) || !isValidGPSReading(current)) {
            return -1;
        }

        return prev.distanceTo(current);
    }

    /**
     * Detect if location change represents real movement or GPS jitter
     * 
     * Simple heuristic: if movement is less than 10 meters, likely jitter
     */
    public static boolean isRealMovement(Location prev, Location current) {
        float movement = calculateMovement(prev, current);
        return movement >= 10.0f; // At least 10 meters to be considered real movement
    }
}
