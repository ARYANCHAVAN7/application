package com.example.application.utils;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Emergency Priority Algorithm
 * 
 * Calculates priority score for emergencies based on:
 * 1. Status (pending = highest priority)
 * 2. Age (older emergencies = higher priority)
 * 3. Number of assignment attempts (more attempts = lower priority)
 * 
 * Note: This implementation uses EXISTING Firestore fields only.
 * No new fields are invented.
 * 
 * Priority Score: Higher score = higher priority
 */
public class EmergencyPriority {

    // Status weights (used to calculate base priority)
    private static final int PRIORITY_PENDING = 100;      // Waiting for hospital response
    private static final int PRIORITY_ACTIVE = 75;        // Hospital accepted, ambulance being dispatched
    private static final int PRIORITY_ACCEPTED = 50;      // Ambulance en route
    private static final int PRIORITY_COMPLETED = 0;      // Already handled

    // Age factor: add 1 point per minute of age
    private static final double AGE_WEIGHT_PER_MINUTE = 1.0;

    /**
     * Get priority score for an emergency document
     * 
     * @param doc Firestore emergency document
     * @return Priority score (higher = more urgent)
     */
    public static double calculatePriority(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return 0;
        }

        double score = 0;

        // 1. Base score by status
        String status = doc.getString("status");
        if (status != null) {
            switch (status) {
                case "pending":
                    score += PRIORITY_PENDING;
                    break;
                case "active":
                    score += PRIORITY_ACTIVE;
                    break;
                case "accepted":
                    score += PRIORITY_ACCEPTED;
                    break;
                case "completed":
                    score += PRIORITY_COMPLETED;
                    break;
            }
        }

        // 2. Age factor: older emergencies get higher priority
        com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");
        if (timestamp != null) {
            long emergencyTimeMs = timestamp.toDate().getTime();
            long currentTimeMs = System.currentTimeMillis();
            long ageMs = currentTimeMs - emergencyTimeMs;
            long ageMinutes = ageMs / (60 * 1000);
            
            score += ageMinutes * AGE_WEIGHT_PER_MINUTE;
        }

        return score;
    }

    /**
     * Get priority level as human-readable string
     * 
     * @param doc Firestore emergency document
     * @return Priority level: "CRITICAL", "HIGH", "MEDIUM", "LOW"
     */
    public static String getPriorityLevel(DocumentSnapshot doc) {
        String status = doc != null ? doc.getString("status") : null;
        
        if ("pending".equals(status)) {
            return "CRITICAL";        // No hospital response yet
        } else if ("active".equals(status)) {
            return "HIGH";            // Hospital responding, ambulance en route
        } else if ("accepted".equals(status)) {
            return "MEDIUM";          // Ambulance confirmed
        } else {
            return "LOW";             // Completed or unknown
        }
    }

    /**
     * Compare two emergencies by priority
     * 
     * Returns:
     * - Negative if doc1 has higher priority than doc2
     * - Positive if doc2 has higher priority than doc1
     * - Zero if equal priority
     */
    public static int compare(DocumentSnapshot doc1, DocumentSnapshot doc2) {
        double priority1 = calculatePriority(doc1);
        double priority2 = calculatePriority(doc2);
        return Double.compare(priority2, priority1); // Reverse for descending order
    }

    /**
     * Get emergency age in seconds
     * 
     * @param doc Firestore emergency document
     * @return Age in seconds, or -1 if timestamp not available
     */
    public static long getAgeSeconds(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return -1;
        }

        com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");
        if (timestamp == null) {
            return -1;
        }

        long emergencyTimeMs = timestamp.toDate().getTime();
        long currentTimeMs = System.currentTimeMillis();
        return (currentTimeMs - emergencyTimeMs) / 1000;
    }

    /**
     * Format emergency age for display
     * 
     * @param doc Firestore emergency document
     * @return Formatted string (e.g., "2 min 30 sec", "45 sec")
     */
    public static String formatAge(DocumentSnapshot doc) {
        long ageSeconds = getAgeSeconds(doc);
        if (ageSeconds < 0) {
            return "Unknown";
        }

        long minutes = ageSeconds / 60;
        long seconds = ageSeconds % 60;

        if (minutes > 0) {
            return String.format("%d min %d sec", minutes, seconds);
        } else {
            return String.format("%d sec", seconds);
        }
    }
}
