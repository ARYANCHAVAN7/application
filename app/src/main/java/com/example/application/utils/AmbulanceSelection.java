package com.example.application.utils;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Ambulance Selection Algorithm
 * 
 * Selects the best ambulance for an emergency based on:
 * 1. Availability status (isOnTrip = false)
 * 2. Distance from emergency (using Haversine)
 * 3. Priority to live online drivers, with fallback to hospital base location
 */
public class AmbulanceSelection {

    /**
     * Represents an ambulance with its distance from an emergency
     */
    public static class AmbulanceCandidate {
        public String ambulanceDocId;           // Firestore document ID (= ambulanceId)
        public String hospitalId;               // Owning hospital UID
        public String hospitalName;             // Hospital name
        public String driverName;               // Driver name
        public String vehicleNumber;            // Vehicle ID
        public String driverPhone;              // Driver phone
        public double latitude;                 // Current latitude
        public double longitude;                // Current longitude
        public double distanceMeters;           // Distance from emergency in meters
        public boolean isAvailable;             // Availability status
        public boolean isOnline;                // Driver live online status

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

        // Check availability
        // isOnTrip = true means ambulance is currently busy on a trip
        Boolean isOnTrip = doc.getBoolean("isOnTrip");
        if (Boolean.TRUE.equals(isOnTrip)) {
            return false;
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
     * Create a candidate from a Firestore ambulance document with fallback coordinates.
     */
    public static AmbulanceCandidate createCandidate(DocumentSnapshot doc,
                                                       double defaultLatitude,
                                                       double defaultLongitude,
                                                       double emergencyLatitude,
                                                       double emergencyLongitude) {
        if (!isEligibleAmbulance(doc)) {
            return null;
        }

        Double lat = FirestoreValues.getDouble(doc, "latitude");
        Double lng = FirestoreValues.getDouble(doc, "longitude");
        if (!HaversineDistance.isValidCoordinate(lat, lng)) {
            lat = defaultLatitude;
            lng = defaultLongitude;
        }
        if (!HaversineDistance.isValidCoordinate(lat, lng)) {
            return null;
        }

        AmbulanceCandidate candidate = new AmbulanceCandidate();
        candidate.ambulanceDocId = doc.getId();
        candidate.hospitalName = doc.getString("hospitalName");
        candidate.driverName = doc.getString("driverName");
        candidate.vehicleNumber = doc.getString("vehicleNumber");
        candidate.driverPhone = doc.getString("driverPhone");
        candidate.hospitalId = doc.getString("hospitalId");
        candidate.latitude = lat;
        candidate.longitude = lng;
        candidate.isAvailable = !Boolean.TRUE.equals(doc.getBoolean("isOnTrip"));
        candidate.isOnline = Boolean.TRUE.equals(doc.getBoolean("isOnline"));

        // Calculate distance using Haversine
        candidate.distanceMeters = HaversineDistance.distanceInMeters(
            emergencyLatitude, emergencyLongitude,
            candidate.latitude, candidate.longitude
        );

        return candidate;
    }

    /**
     * Create a candidate from a Firestore ambulance document
     */
    public static AmbulanceCandidate createCandidate(DocumentSnapshot doc, 
                                                       double emergencyLatitude,
                                                       double emergencyLongitude) {
        return createCandidate(doc, 0.0, 0.0, emergencyLatitude, emergencyLongitude);
    }

    /**
     * Find the nearest available ambulance from a list of candidates
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
     * Sort ambulances by distance (nearest first).
     * Online active drivers get slight precedence if at a similar distance.
     */
    public static java.util.List<AmbulanceCandidate> sortByDistance(
            java.util.List<AmbulanceCandidate> candidates) {
        java.util.List<AmbulanceCandidate> sorted = new java.util.ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            int cmp = Double.compare(a.distanceMeters, b.distanceMeters);
            if (cmp != 0) return cmp;
            return Boolean.compare(b.isOnline, a.isOnline);
        });
        return sorted;
    }

    /**
     * Builds candidates for one emergency from the hospital's ambulance documents and returns
     * them nearest-first. Ambulances in excludedIds and ambulances of other hospitals are skipped.
     */
    public static java.util.List<AmbulanceCandidate> rankForEmergency(
            java.util.List<DocumentSnapshot> ambulanceDocs,
            String hospitalId,
            double hospitalLatitude,
            double hospitalLongitude,
            double emergencyLatitude,
            double emergencyLongitude,
            java.util.Collection<String> excludedIds) {
        java.util.List<AmbulanceCandidate> candidates = new java.util.ArrayList<>();
        for (DocumentSnapshot doc : ambulanceDocs) {
            if (excludedIds != null && excludedIds.contains(doc.getId())) continue;
            if (hospitalId != null && !hospitalId.equals(doc.getString("hospitalId"))) continue;
            AmbulanceCandidate c = createCandidate(doc, hospitalLatitude, hospitalLongitude, emergencyLatitude, emergencyLongitude);
            if (c != null) candidates.add(c);
        }
        return sortByDistance(candidates);
    }

    public static java.util.List<AmbulanceCandidate> rankForEmergency(
            java.util.List<DocumentSnapshot> ambulanceDocs,
            String hospitalId,
            double emergencyLatitude,
            double emergencyLongitude,
            java.util.Collection<String> excludedIds) {
        return rankForEmergency(ambulanceDocs, hospitalId, 0.0, 0.0, emergencyLatitude, emergencyLongitude, excludedIds);
    }
}
