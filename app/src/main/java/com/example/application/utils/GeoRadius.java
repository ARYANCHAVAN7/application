package com.example.application.utils;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Geo/Radius Filtering Utility
 * 
 * Filters locations (emergencies, ambulances, hospitals) by radius/distance.
 * 
 * Uses Haversine distance calculation for great-circle distances on Earth.
 * 
 * No fake coordinates or hardcoded locations.
 * All radius values are configurable.
 */
public class GeoRadius {

    // Default search radius in meters (5 km)
    public static final double DEFAULT_SEARCH_RADIUS_METERS = 5000.0;

    // Common radius presets
    public static final double RADIUS_1KM = 1000.0;
    public static final double RADIUS_2KM = 2000.0;
    public static final double RADIUS_5KM = 5000.0;
    public static final double RADIUS_10KM = 10000.0;
    public static final double RADIUS_15KM = 15000.0;
    public static final double RADIUS_20KM = 20000.0;

    /**
     * Check if a point is within radius of a center point
     * 
     * @param centerLatitude Center point latitude
     * @param centerLongitude Center point longitude
     * @param pointLatitude Point latitude
     * @param pointLongitude Point longitude
     * @param radiusMeters Search radius in meters
     * @return true if point is within radius, false otherwise
     */
    public static boolean isWithinRadius(double centerLatitude, double centerLongitude,
                                        double pointLatitude, double pointLongitude,
                                        double radiusMeters) {
        if (!HaversineDistance.isValidCoordinate(centerLatitude, centerLongitude) ||
            !HaversineDistance.isValidCoordinate(pointLatitude, pointLongitude)) {
            return false;
        }

        double distance = HaversineDistance.distanceInMeters(
            centerLatitude, centerLongitude,
            pointLatitude, pointLongitude
        );

        return distance <= radiusMeters;
    }

    /**
     * Get distance to a point from center
     * Returns -1 if coordinates are invalid
     */
    public static double getDistance(double centerLatitude, double centerLongitude,
                                     double pointLatitude, double pointLongitude) {
        if (!HaversineDistance.isValidCoordinate(centerLatitude, centerLongitude) ||
            !HaversineDistance.isValidCoordinate(pointLatitude, pointLongitude)) {
            return -1.0;
        }

        return HaversineDistance.distanceInMeters(
            centerLatitude, centerLongitude,
            pointLatitude, pointLongitude
        );
    }

    /**
     * Filter Firestore documents by distance from center point
     * Expects documents to have "latitude" and "longitude" fields
     * 
     * @param centerLatitude Center point latitude
     * @param centerLongitude Center point longitude
     * @param documents List of Firestore documents
     * @param radiusMeters Search radius in meters
     * @return Filtered list of documents within radius
     */
    public static java.util.List<DocumentSnapshot> filterByRadius(
            double centerLatitude, double centerLongitude,
            java.util.List<DocumentSnapshot> documents,
            double radiusMeters) {
        
        java.util.List<DocumentSnapshot> filtered = new java.util.ArrayList<>();
        
        for (DocumentSnapshot doc : documents) {
            Double latitude = doc.getDouble("latitude");
            Double longitude = doc.getDouble("longitude");
            
            if (HaversineDistance.isValidCoordinate(latitude, longitude)) {
                if (isWithinRadius(centerLatitude, centerLongitude,
                                  latitude, longitude, radiusMeters)) {
                    filtered.add(doc);
                }
            }
        }
        
        return filtered;
    }

    /**
     * Sort documents by distance from center point (nearest first)
     * 
     * @param centerLatitude Center point latitude
     * @param centerLongitude Center point longitude
     * @param documents List of Firestore documents
     * @return Sorted list (nearest first)
     */
    public static java.util.List<DocumentSnapshot> sortByDistance(
            double centerLatitude, double centerLongitude,
            java.util.List<DocumentSnapshot> documents) {
        
        java.util.List<DocumentSnapshot> sorted = new java.util.ArrayList<>(documents);
        
        sorted.sort((doc1, doc2) -> {
            Double lat1 = doc1.getDouble("latitude");
            Double lon1 = doc1.getDouble("longitude");
            Double lat2 = doc2.getDouble("latitude");
            Double lon2 = doc2.getDouble("longitude");
            
            if (!HaversineDistance.isValidCoordinate(lat1, lon1) ||
                !HaversineDistance.isValidCoordinate(lat2, lon2)) {
                return 0;
            }
            
            double dist1 = HaversineDistance.distanceInMeters(
                centerLatitude, centerLongitude, lat1, lon1
            );
            double dist2 = HaversineDistance.distanceInMeters(
                centerLatitude, centerLongitude, lat2, lon2
            );
            
            return Double.compare(dist1, dist2);
        });
        
        return sorted;
    }

    /**
     * Create a bounding box for a circular radius
     * Useful for optimizing Firestore queries (though Firestore doesn't support
     * native radius queries, this can help reduce returned documents)
     * 
     * Returns [minLat, maxLat, minLon, maxLon]
     * Note: This is approximate and doesn't account for Earth's curvature perfectly
     */
    public static double[] getBoundingBox(double centerLatitude, double centerLongitude, 
                                         double radiusMeters) {
        // Rough approximation: 1 degree ≈ 111 km at equator
        double radiusKm = radiusMeters / 1000.0;
        double degreesPerKm = 1.0 / 111.0;
        double latDelta = radiusKm * degreesPerKm;
        
        // Longitude delta varies by latitude
        double lonDelta = radiusKm * degreesPerKm / Math.cos(Math.toRadians(centerLatitude));
        
        return new double[] {
            centerLatitude - latDelta,  // minLat
            centerLatitude + latDelta,  // maxLat
            centerLongitude - lonDelta, // minLon
            centerLongitude + lonDelta  // maxLon
        };
    }

    /**
     * Format radius for display
     * 
     * @param radiusMeters Radius in meters
     * @return Formatted string (e.g., "5 km", "500 m")
     */
    public static String formatRadius(double radiusMeters) {
        return HaversineDistance.formatDistance(radiusMeters);
    }
}
