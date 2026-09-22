package com.example.application.utils;

/**
 * Haversine Distance Calculator
 * 
 * Calculates the great-circle distance between two points on Earth
 * given their latitude and longitude coordinates.
 * 
 * Formula reference: https://en.wikipedia.org/wiki/Haversine_formula
 */
public class HaversineDistance {

    // Earth's radius in meters
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    
    // Earth's radius in kilometers
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calculate distance between two points in METERS
     * 
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Distance in meters
     */
    public static double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        return distance(lat1, lon1, lat2, lon2, EARTH_RADIUS_METERS);
    }

    /**
     * Calculate distance between two points in KILOMETERS
     * 
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Distance in kilometers
     */
    public static double distanceInKilometers(double lat1, double lon1, double lat2, double lon2) {
        return distance(lat1, lon1, lat2, lon2, EARTH_RADIUS_KM);
    }

    /**
     * Core Haversine formula implementation
     * 
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @param radius Earth's radius (in meters or km)
     * @return Distance in same units as radius
     */
    private static double distance(double lat1, double lon1, double lat2, double lon2, double radius) {
        // Convert degrees to radians
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        // Haversine formula
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        
        return radius * c;
    }

    /**
     * Check if two coordinates are valid (non-null, within valid range)
     * 
     * @param latitude Latitude in degrees (-90 to +90)
     * @param longitude Longitude in degrees (-180 to +180)
     * @return true if coordinates are valid, false otherwise
     */
    public static boolean isValidCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return latitude >= -90.0 && latitude <= 90.0 &&
               longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * Format distance for display
     * 
     * @param distanceMeters Distance in meters
     * @return Formatted string (e.g., "2.5 km" or "450 m")
     */
    public static String formatDistance(double distanceMeters) {
        if (distanceMeters >= 1000) {
            return String.format("%.1f km", distanceMeters / 1000.0);
        } else {
            return String.format("%.0f m", distanceMeters);
        }
    }
}
