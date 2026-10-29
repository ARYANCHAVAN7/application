package com.example.application.utils;

import com.example.application.EmergencyStatus;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

/**
 * Emergency priority scoring (existing algorithm, updated to EmergencyStatus constants).
 *
 * score = statusWeight + ageMinutes * AGE_WEIGHT_PER_MINUTE
 *
 * Emergencies still waiting for a hospital decision are CRITICAL, emergencies waiting for an
 * ambulance are HIGH, emergencies already being served are MEDIUM, terminal ones LOW.
 * Older emergencies climb the list. Used by the hospital dashboard to order its queue and
 * stored as the "priority" field on each emergency.
 */
public class EmergencyPriority {

    private static final int PRIORITY_AWAITING_HOSPITAL = 100;
    private static final int PRIORITY_AWAITING_AMBULANCE = 75;
    private static final int PRIORITY_IN_SERVICE = 50;
    private static final int PRIORITY_DONE = 0;

    private static final double AGE_WEIGHT_PER_MINUTE = 1.0;

    public static final String LEVEL_CRITICAL = "CRITICAL";
    public static final String LEVEL_HIGH = "HIGH";
    public static final String LEVEL_MEDIUM = "MEDIUM";
    public static final String LEVEL_LOW = "LOW";

    private static int statusWeight(String status) {
        if (status == null) return PRIORITY_DONE;
        switch (status) {
            case EmergencyStatus.HOSPITAL_REQUESTED:
                return PRIORITY_AWAITING_HOSPITAL;
            case EmergencyStatus.HOSPITAL_ACCEPTED:
            case EmergencyStatus.DRIVER_REJECTED:
                return PRIORITY_AWAITING_AMBULANCE;
            case EmergencyStatus.AMBULANCE_DISPATCHED:
            case EmergencyStatus.DRIVER_ACCEPTED:
            case EmergencyStatus.AMBULANCE_ARRIVED:
            case EmergencyStatus.PATIENT_PICKED_UP:
            case EmergencyStatus.EN_ROUTE_TO_HOSPITAL:
                return PRIORITY_IN_SERVICE;
            default:
                return PRIORITY_DONE;
        }
    }

    /** Priority level for a status; also used when the emergency document is first created. */
    public static String levelForStatus(String status) {
        int weight = statusWeight(status);
        if (weight >= PRIORITY_AWAITING_HOSPITAL) return LEVEL_CRITICAL;
        if (weight >= PRIORITY_AWAITING_AMBULANCE) return LEVEL_HIGH;
        if (weight >= PRIORITY_IN_SERVICE) return LEVEL_MEDIUM;
        return LEVEL_LOW;
    }

    public static double calculatePriority(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return 0;
        }
        double score = statusWeight(doc.getString("status"));
        long ageSeconds = getAgeSeconds(doc);
        if (ageSeconds > 0) {
            score += (ageSeconds / 60) * AGE_WEIGHT_PER_MINUTE;
        }
        return score;
    }

    public static String getPriorityLevel(DocumentSnapshot doc) {
        return levelForStatus(doc != null ? doc.getString("status") : null);
    }

    /** Descending priority comparator (highest first). */
    public static int compare(DocumentSnapshot doc1, DocumentSnapshot doc2) {
        return Double.compare(calculatePriority(doc2), calculatePriority(doc1));
    }

    public static long getAgeSeconds(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return -1;
        }
        // Pending server timestamps are estimated locally so a fresh SOS is not "unknown age".
        com.google.firebase.Timestamp timestamp =
                doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        if (timestamp == null) {
            return -1;
        }
        return Math.max(0, (System.currentTimeMillis() - timestamp.toDate().getTime()) / 1000);
    }

    public static String formatAge(DocumentSnapshot doc) {
        long ageSeconds = getAgeSeconds(doc);
        if (ageSeconds < 0) {
            return "Unknown";
        }
        long minutes = ageSeconds / 60;
        long seconds = ageSeconds % 60;
        if (minutes > 0) {
            return String.format(Locale.US, "%d min %d sec ago", minutes, seconds);
        }
        return String.format(Locale.US, "%d sec ago", seconds);
    }
}
