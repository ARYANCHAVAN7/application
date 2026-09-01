package com.example.application.utils;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Ambulance Selection Algorithm
 * 
 * Selects the best ambulance for an emergency based on:
 * 1. Distance (using Haversine)
 * 2. Availability status
 * 3. Valid GPS coordinates
 * 
 * Only considers ambulances that are:
 * - Active and available (isOnTrip = false or not set)
 * - Have valid GPS coordinates
 * - Belong to the same hospital or nearby hospitals
 */
public class AmbulanceSelection {

    /**
     * Represents an ambulance with its distance from an emergency
     */
    public static class AmbulanceCandidate {
        public String ambulanceDocId;           // Firestore document ID
        public String hospitalName;             // Hospital name
        public String driverName;               // Driver name
        public String vehicleNumber;            // Vehicle ID
        public String driverPhone;              // Driver phone
        public double latitude;                 // Current latitude
        public double longitude;                // Current longitude
        public double distanceMeters;           // Distance from emergency in meters
        public boolean isAvailable;             // Availability status

        @Override
        public String toString() {
            return String.format(
                "%s (%s) - %s away | Driver: %s",
                vehicleNumber,
                hospitalName,
                HaversineDistance.formatDistance(distanceMeters),
                driverName
            );
        }
    }

    /**
     * Evaluate if an ambulance document is eligible for assignment
     * 
     * @param doc Firestore ambulance document
     * @return true if ambulance is eligible, false otherwise
     */
    public static boolean isEligibleAmbulance(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return false;
        }

        // Check GPS coordinates
        Double latitude = doc.getDouble("latitude");
        Double longitude = doc.getDouble("longitude");
        if (!HaversineDistance.isValidCoordinate(latitude, longitude)) {
            return false;
        }

        // Check availability
        // isOnTrip = false means ambulance is available
        // If field doesn't exist, assume available
        Boolean isOnTrip = doc.getBoolean("isOnTrip");
        if (isOnTrip != null && isOnTrip) {
            return false; // Ambulance is currently on a trip
        }

        // Check that required fields exist
        String hospitalName = doc.getString("hospitalName");
        String vehicleNumber = doc.getString("vehicleNumber");
        if (hospitalName == null || hospitalName.trim().isEmpty() ||
            vehicleNumber == null || vehicleNumber.trim().isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Create a candidate from a Firestore ambulance document
     * 
     * @param doc Firestore document snapshot
     * @param emergencyLatitude Emergency location latitude
     * @param emergencyLongitude Emergency location longitude
     * @return AmbulanceCandidate with calculated distance, or null if ineligible
     */
    public static AmbulanceCandidate createCandidate(DocumentSnapshot doc, 
                                                       double emergencyLatitude,
                                                       double emergencyLongitude) {
        if (!isEligibleAmbulance(doc)) {
            return null;
        }

        AmbulanceCandidate candidate = new AmbulanceCandidate();
        candidate.ambulanceDocId = doc.getId();
        candidate.hospitalName = doc.getString("hospitalName");
        candidate.driverName = doc.getString("driverName");
        candidate.vehicleNumber = doc.getString("vehicleNumber");
        candidate.driverPhone = doc.getString("driverPhone");
        candidate.latitude = doc.getDouble("latitude");
        candidate.longitude = doc.getDouble("longitude");
        candidate.isAvailable = !Boolean.TRUE.equals(doc.getBoolean("isOnTrip"));

        // Calculate distance using Haversine
        candidate.distanceMeters = HaversineDistance.distanceInMeters(
            emergencyLatitude, emergencyLongitude,
            candidate.latitude, candidate.longitude
        );

        return candidate;
    }

    /**
     * Find the nearest available ambulance from a list of candidates
     * 
     * @param candidates List of AmbulanceCandidate objects
     * @return AmbulanceCandidate with minimum distance, or null if list is empty
     */
    public static AmbulanceCandidate selectNearest(java.util.List<AmbulanceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        AmbulanceCandidate nearest = candidates.get(0);
        for (AmbulanceCandidate candidate : candidates) {
            if (candidate.distanceMeters < nearest.distanceMeters) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * Filter ambulances by radius
     * 
     * @param candidates List of ambulance candidates
     * @param radiusMeters Maximum distance in meters
     * @return Filtered list of candidates within radius
     */
    public static java.util.List<AmbulanceCandidate> filterByRadius(
            java.util.List<AmbulanceCandidate> candidates, double radiusMeters) {
        java.util.List<AmbulanceCandidate> filtered = new java.util.ArrayList<>();
        for (AmbulanceCandidate candidate : candidates) {
            if (candidate.distanceMeters <= radiusMeters) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    /**
     * Sort ambulances by distance (nearest first)
     * 
     * @param candidates List of ambulance candidates
     * @return Sorted list (nearest first)
     */
    public static java.util.List<AmbulanceCandidate> sortByDistance(
            java.util.List<AmbulanceCandidate> candidates) {
        java.util.List<AmbulanceCandidate> sorted = new java.util.ArrayList<>(candidates);
        sorted.sort((a, b) -> Double.compare(a.distanceMeters, b.distanceMeters));
        return sorted;
    }
}
