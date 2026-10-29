package com.example.application;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for emergency status strings stored in
 * emergencies/{emergencyId}.status. firestore.rules uses the same literals.
 *
 * Flow:
 * HOSPITAL_REQUESTED -> HOSPITAL_ACCEPTED -> AMBULANCE_DISPATCHED -> DRIVER_ACCEPTED
 *   -> AMBULANCE_ARRIVED -> PATIENT_PICKED_UP -> EN_ROUTE_TO_HOSPITAL -> COMPLETED
 * HOSPITAL_REQUESTED -(reject, next hospital)-> HOSPITAL_REQUESTED (new hospitalId)
 * HOSPITAL_REQUESTED -(reject, none left)-> HOSPITAL_REJECTED (terminal)
 * AMBULANCE_DISPATCHED -(driver reject)-> DRIVER_REJECTED -(re-dispatch)-> AMBULANCE_DISPATCHED
 */
public final class EmergencyStatus {

    public static final String HOSPITAL_REQUESTED = "HOSPITAL_REQUESTED";
    public static final String HOSPITAL_ACCEPTED = "HOSPITAL_ACCEPTED";
    public static final String HOSPITAL_REJECTED = "HOSPITAL_REJECTED";
    public static final String AMBULANCE_DISPATCHED = "AMBULANCE_DISPATCHED";
    public static final String DRIVER_ACCEPTED = "DRIVER_ACCEPTED";
    public static final String DRIVER_REJECTED = "DRIVER_REJECTED";
    public static final String AMBULANCE_ARRIVED = "AMBULANCE_ARRIVED";
    public static final String PATIENT_PICKED_UP = "PATIENT_PICKED_UP";
    public static final String EN_ROUTE_TO_HOSPITAL = "EN_ROUTE_TO_HOSPITAL";
    public static final String COMPLETED = "COMPLETED";

    /** Statuses shown on the assigned hospital's dashboard. */
    public static final List<String> HOSPITAL_VISIBLE = Collections.unmodifiableList(Arrays.asList(
            HOSPITAL_REQUESTED, HOSPITAL_ACCEPTED, DRIVER_REJECTED, AMBULANCE_DISPATCHED,
            DRIVER_ACCEPTED, AMBULANCE_ARRIVED, PATIENT_PICKED_UP, EN_ROUTE_TO_HOSPITAL));

    /** Statuses in which the assigned ambulance must see the request. */
    public static final List<String> DRIVER_VISIBLE = Collections.unmodifiableList(Arrays.asList(
            AMBULANCE_DISPATCHED, DRIVER_ACCEPTED, AMBULANCE_ARRIVED, PATIENT_PICKED_UP, EN_ROUTE_TO_HOSPITAL));

    /** Non-terminal statuses (an emergency in one of these blocks a new SOS). */
    public static final List<String> ACTIVE = HOSPITAL_VISIBLE;

    /** Statuses where the ambulance is driving and its live location matters to the user. */
    public static final List<String> LIVE_TRACKING = Collections.unmodifiableList(Arrays.asList(
            DRIVER_ACCEPTED, AMBULANCE_ARRIVED, PATIENT_PICKED_UP, EN_ROUTE_TO_HOSPITAL));

    private EmergencyStatus() {}

    public static boolean isActive(String status) {
        return status != null && ACTIVE.contains(status);
    }

    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status) || HOSPITAL_REJECTED.equals(status);
    }

    public static boolean needsDispatch(String status) {
        return HOSPITAL_ACCEPTED.equals(status) || DRIVER_REJECTED.equals(status);
    }

    /** True once the patient is on board: routes go to the hospital instead of the user. */
    public static boolean isHeadingToHospital(String status) {
        return PATIENT_PICKED_UP.equals(status) || EN_ROUTE_TO_HOSPITAL.equals(status);
    }

    /** Next status for the driver's primary action button, or null if none. */
    public static String nextDriverStatus(String status) {
        if (DRIVER_ACCEPTED.equals(status)) return AMBULANCE_ARRIVED;
        if (AMBULANCE_ARRIVED.equals(status)) return PATIENT_PICKED_UP;
        if (PATIENT_PICKED_UP.equals(status)) return EN_ROUTE_TO_HOSPITAL;
        if (EN_ROUTE_TO_HOSPITAL.equals(status)) return COMPLETED;
        return null;
    }

    /** Timestamp field written together with a driver transition. */
    public static String timestampFieldFor(String newStatus) {
        if (AMBULANCE_ARRIVED.equals(newStatus)) return "arrivedAt";
        if (PATIENT_PICKED_UP.equals(newStatus)) return "pickedUpAt";
        if (EN_ROUTE_TO_HOSPITAL.equals(newStatus)) return "enRouteAt";
        if (COMPLETED.equals(newStatus)) return "completedAt";
        return "updatedAt";
    }

    public static String driverActionLabel(String status) {
        if (DRIVER_ACCEPTED.equals(status)) return "ARRIVED";
        if (AMBULANCE_ARRIVED.equals(status)) return "PATIENT PICKED UP";
        if (PATIENT_PICKED_UP.equals(status)) return "START TO HOSPITAL";
        if (EN_ROUTE_TO_HOSPITAL.equals(status)) return "COMPLETE";
        return null;
    }

    /** Human-readable label for users/hospitals/drivers. */
    public static String label(String status) {
        if (status == null) return "Unknown";
        switch (status) {
            case HOSPITAL_REQUESTED: return "Waiting for hospital";
            case HOSPITAL_ACCEPTED: return "Hospital accepted - assigning ambulance";
            case HOSPITAL_REJECTED: return "No hospital could accept";
            case AMBULANCE_DISPATCHED: return "Ambulance assigned - waiting for driver";
            case DRIVER_ACCEPTED: return "Ambulance on the way";
            case DRIVER_REJECTED: return "Driver unavailable - reassigning";
            case AMBULANCE_ARRIVED: return "Ambulance arrived";
            case PATIENT_PICKED_UP: return "Patient picked up";
            case EN_ROUTE_TO_HOSPITAL: return "On the way to hospital";
            case COMPLETED: return "Completed";
            default: return status;
        }
    }
}
