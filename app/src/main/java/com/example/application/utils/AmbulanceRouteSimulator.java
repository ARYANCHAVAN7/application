package com.example.application.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.application.EmergencyStatus;
import com.example.application.utils.SurakshaLog;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.mapbox.geojson.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-level two-leg route simulation:
 *
 * Leg 1 (Hospital -> Patient):
 *   - Fetches road geometry from Mapbox.
 *   - Streams live coordinates to driverLocations/{ambulanceId} every 2 seconds.
 *   - On arrival: updates status to AMBULANCE_ARRIVED.
 *   - Pauses 5 seconds to simulate boarding the patient & triage.
 *
 * Leg 2 (Patient -> Hospital):
 *   - Updates status to PATIENT_PICKED_UP then EN_ROUTE_TO_HOSPITAL.
 *   - Patient's app dynamically redraws route to the hospital.
 *   - Streams coordinates back along the road towards the hospital.
 *   - On arrival: updates status to COMPLETED, frees the ambulance (isOnTrip=false),
 *     allowing it to immediately serve the next waiting emergency.
 */
public class AmbulanceRouteSimulator {

    private static final Map<String, AmbulanceRouteSimulator> ACTIVE_SIMULATIONS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;
    private final String ambulanceId;
    private final String emergencyId;
    private final double hospitalLat;
    private final double hospitalLng;
    private final double patientLat;
    private final double patientLng;

    private enum Phase {
        TO_PATIENT,
        BOARDING,
        TO_HOSPITAL,
        DONE
    }

    private Phase phase = Phase.TO_PATIENT;
    private final List<Point> waypoints = new ArrayList<>();
    private ScheduledFuture<?> future;
    private int currentStep = 0;
    private boolean stopped = false;

    public static synchronized void start(Context context,
                                          String ambulanceId,
                                          String emergencyId,
                                          double hospitalLat, double hospitalLng,
                                          double patientLat, double patientLng,
                                          String currentStatus) {
        if (ambulanceId == null || emergencyId == null) return;
        if (!HaversineDistance.isValidCoordinate(hospitalLat, hospitalLng)
                || !HaversineDistance.isValidCoordinate(patientLat, patientLng)) {
            return;
        }

        // Avoid duplicate simulator for the same ambulance
        if (ACTIVE_SIMULATIONS.containsKey(ambulanceId)) {
            AmbulanceRouteSimulator existing = ACTIVE_SIMULATIONS.get(ambulanceId);
            if (existing != null && !existing.stopped) {
                return;
            }
        }

        AmbulanceRouteSimulator sim = new AmbulanceRouteSimulator(
                context.getApplicationContext(), ambulanceId, emergencyId,
                hospitalLat, hospitalLng, patientLat, patientLng);
        ACTIVE_SIMULATIONS.put(ambulanceId, sim);

        if (EmergencyStatus.AMBULANCE_ARRIVED.equals(currentStatus)) {
            sim.phase = Phase.BOARDING;
            sim.scheduleBoardingTransition();
        } else if (EmergencyStatus.PATIENT_PICKED_UP.equals(currentStatus)
                || EmergencyStatus.EN_ROUTE_TO_HOSPITAL.equals(currentStatus)) {
            sim.phase = Phase.TO_HOSPITAL;
            sim.startLegToHospital();
        } else {
            sim.phase = Phase.TO_PATIENT;
            sim.startLegToPatient();
        }
    }

    public static synchronized void stop(String ambulanceId) {
        if (ambulanceId == null) return;
        AmbulanceRouteSimulator sim = ACTIVE_SIMULATIONS.remove(ambulanceId);
        if (sim != null) {
            sim.cancel();
        }
    }

    public static synchronized void stopAll() {
        for (AmbulanceRouteSimulator sim : ACTIVE_SIMULATIONS.values()) {
            sim.cancel();
        }
        ACTIVE_SIMULATIONS.clear();
    }

    private AmbulanceRouteSimulator(Context context,
                                    String ambulanceId,
                                    String emergencyId,
                                    double hospitalLat, double hospitalLng,
                                    double patientLat, double patientLng) {
        this.context = context;
        this.ambulanceId = ambulanceId;
        this.emergencyId = emergencyId;
        this.hospitalLat = hospitalLat;
        this.hospitalLng = hospitalLng;
        this.patientLat = patientLat;
        this.patientLng = patientLng;
    }

    private void startLegToPatient() {
        if (stopped) return;

        FirebaseFirestore.getInstance().collection("emergencies").document(emergencyId)
                .update("status", EmergencyStatus.DRIVER_ACCEPTED,
                        "driverAcceptedAt", FieldValue.serverTimestamp(),
                        "updatedAt", FieldValue.serverTimestamp());

        MapboxRouting.calculateRoute(context, hospitalLat, hospitalLng, patientLat, patientLng, new MapboxRouting.RouteCallback() {
            @Override
            public void onRouteSuccess(MapboxRouting.RouteInfo route) {
                if (stopped) return;
                List<Point> raw = route.geometry != null ? route.geometry.coordinates() : null;
                if (raw != null && raw.size() >= 2) {
                    setupWaypointsFromRoute(raw, 25);
                } else {
                    setupDirectWaypoints(hospitalLat, hospitalLng, patientLat, patientLng, 25);
                }
                beginTicking();
            }

            @Override
            public void onRouteFailure(String errorMessage) {
                if (stopped) return;
                setupDirectWaypoints(hospitalLat, hospitalLng, patientLat, patientLng, 25);
                beginTicking();
            }
        });
    }

    private void startLegToHospital() {
        if (stopped) return;
        phase = Phase.TO_HOSPITAL;
        MapboxRouting.calculateRoute(context, patientLat, patientLng, hospitalLat, hospitalLng, new MapboxRouting.RouteCallback() {
            @Override
            public void onRouteSuccess(MapboxRouting.RouteInfo route) {
                if (stopped) return;
                List<Point> raw = route.geometry != null ? route.geometry.coordinates() : null;
                if (raw != null && raw.size() >= 2) {
                    setupWaypointsFromRoute(raw, 25);
                } else {
                    setupDirectWaypoints(patientLat, patientLng, hospitalLat, hospitalLng, 25);
                }
                beginTicking();
            }

            @Override
            public void onRouteFailure(String errorMessage) {
                if (stopped) return;
                setupDirectWaypoints(patientLat, patientLng, hospitalLat, hospitalLng, 25);
                beginTicking();
            }
        });
    }

    private void setupDirectWaypoints(double startLat, double startLng, double endLat, double endLng, int steps) {
        waypoints.clear();
        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double lat = startLat + (endLat - startLat) * fraction;
            double lng = startLng + (endLng - startLng) * fraction;
            waypoints.add(Point.fromLngLat(lng, lat));
        }
    }

    private void setupWaypointsFromRoute(List<Point> routePoints, int totalSteps) {
        waypoints.clear();
        if (routePoints == null || routePoints.size() < 2) return;

        double[] segLengths = new double[routePoints.size() - 1];
        double totalDist = 0.0;
        for (int i = 0; i < routePoints.size() - 1; i++) {
            Point a = routePoints.get(i);
            Point b = routePoints.get(i + 1);
            segLengths[i] = HaversineDistance.distanceInMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude());
            totalDist += segLengths[i];
        }

        if (totalDist <= 0.0) {
            waypoints.addAll(routePoints);
            return;
        }

        waypoints.add(routePoints.get(0));
        for (int s = 1; s < totalSteps; s++) {
            double targetDist = (totalDist * s) / totalSteps;
            double accumulated = 0.0;
            boolean added = false;
            for (int i = 0; i < segLengths.length; i++) {
                if (accumulated + segLengths[i] >= targetDist) {
                    double remain = targetDist - accumulated;
                    double frac = segLengths[i] > 0 ? remain / segLengths[i] : 0.0;
                    Point p1 = routePoints.get(i);
                    Point p2 = routePoints.get(i + 1);
                    double lat = p1.latitude() + (p2.latitude() - p1.latitude()) * frac;
                    double lng = p1.longitude() + (p2.longitude() - p1.longitude()) * frac;
                    waypoints.add(Point.fromLngLat(lng, lat));
                    added = true;
                    break;
                }
                accumulated += segLengths[i];
            }
            if (!added && !waypoints.isEmpty()) {
                waypoints.add(routePoints.get(routePoints.size() - 1));
            }
        }
        waypoints.add(routePoints.get(routePoints.size() - 1));
    }

    private synchronized void beginTicking() {
        if (stopped || waypoints.isEmpty()) return;
        currentStep = 0;
        if (future != null) future.cancel(true);
        future = EXECUTOR.scheduleWithFixedDelay(this::tickSafe, 1000, 2000, TimeUnit.MILLISECONDS);
        SurakshaLog.info(SurakshaLog.AMBULANCE, "route simulation ticking",
                "phase=" + phase.name(), "waypoints=" + waypoints.size());
    }

    private void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            SurakshaLog.error(SurakshaLog.AMBULANCE, "simulation tick exception", e);
        }
    }

    private void tick() {
        if (stopped) return;
        if (currentStep >= waypoints.size()) {
            if (phase == Phase.TO_PATIENT) {
                onArrivedAtPatient();
            } else if (phase == Phase.TO_HOSPITAL) {
                onArrivedAtHospital();
            }
            return;
        }

        Point p = waypoints.get(currentStep);
        currentStep++;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> loc = new HashMap<>();
        loc.put("ambulanceId", ambulanceId);
        loc.put("emergencyId", emergencyId);
        loc.put("latitude", p.latitude());
        loc.put("longitude", p.longitude());
        loc.put("timestamp", FieldValue.serverTimestamp());

        db.collection("driverLocations").document(ambulanceId)
                .set(loc, SetOptions.merge())
                .addOnFailureListener(e -> SurakshaLog.warn(SurakshaLog.MAP, "sim publish loc", e.getMessage()));

        db.collection("ambulances").document(ambulanceId)
                .update("latitude", p.latitude(), "longitude", p.longitude(), "isOnline", true)
                .addOnFailureListener(e -> SurakshaLog.warn(SurakshaLog.MAP, "sim update amb", e.getMessage()));

        Map<String, Object> emLoc = new HashMap<>();
        emLoc.put("ambulanceLatitude", p.latitude());
        emLoc.put("ambulanceLongitude", p.longitude());
        emLoc.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("emergencies").document(emergencyId)
                .update(emLoc)
                .addOnFailureListener(e -> SurakshaLog.warn(SurakshaLog.MAP, "sim update em loc", e.getMessage()));
    }

    private void onArrivedAtPatient() {
        if (future != null) {
            future.cancel(true);
            future = null;
        }
        SurakshaLog.info(SurakshaLog.AMBULANCE, "ambulance arrived at patient location",
                "ambulanceId=" + ambulanceId, "emergencyId=" + emergencyId);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> update = new HashMap<>();
        update.put("status", EmergencyStatus.AMBULANCE_ARRIVED);
        update.put("arrivedAt", FieldValue.serverTimestamp());
        update.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("emergencies").document(emergencyId)
                .update(update)
                .addOnSuccessListener(v -> scheduleBoardingTransition())
                .addOnFailureListener(e -> scheduleBoardingTransition());
    }

    private void scheduleBoardingTransition() {
        phase = Phase.BOARDING;
        SurakshaLog.info(SurakshaLog.AMBULANCE, "simulating patient boarding (5 seconds pause)...");
        MAIN.postDelayed(() -> {
            if (stopped) return;
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> update = new HashMap<>();
            update.put("status", EmergencyStatus.EN_ROUTE_TO_HOSPITAL);
            update.put("pickedUpAt", FieldValue.serverTimestamp());
            update.put("enRouteAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());

            db.collection("emergencies").document(emergencyId)
                    .update(update)
                    .addOnSuccessListener(v -> {
                        SurakshaLog.info(SurakshaLog.AMBULANCE, "patient onboarded -> starting transport to hospital");
                        startLegToHospital();
                    })
                    .addOnFailureListener(e -> startLegToHospital());
        }, 5000);
    }

    private void onArrivedAtHospital() {
        phase = Phase.DONE;
        SurakshaLog.info(SurakshaLog.AMBULANCE, "ambulance arrived at hospital emergency bay - completing trip",
                "ambulanceId=" + ambulanceId, "emergencyId=" + emergencyId);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> update = new HashMap<>();
        update.put("status", EmergencyStatus.COMPLETED);
        update.put("completedAt", FieldValue.serverTimestamp());
        update.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("emergencies").document(emergencyId)
                .update(update)
                .addOnSuccessListener(v -> SurakshaLog.info(SurakshaLog.AMBULANCE, "marked COMPLETED in emergencies"))
                .addOnFailureListener(e -> SurakshaLog.warn(SurakshaLog.AMBULANCE, "mark completed failed", e.getMessage()));

        // Release the ambulance back to the hospital fleet
        Map<String, Object> ambUpdate = new HashMap<>();
        ambUpdate.put("isOnTrip", false);
        ambUpdate.put("currentEmergencyId", null);
        ambUpdate.put("latitude", hospitalLat);
        ambUpdate.put("longitude", hospitalLng);

        db.collection("ambulances").document(ambulanceId)
                .update(ambUpdate)
                .addOnSuccessListener(v -> SurakshaLog.info(SurakshaLog.AMBULANCE, "ambulance released for next dispatch"))
                .addOnFailureListener(e -> SurakshaLog.warn(SurakshaLog.AMBULANCE, "ambulance release failed", e.getMessage()));

        // Unlink driver location from emergency
        Map<String, Object> loc = new HashMap<>();
        loc.put("emergencyId", null);
        loc.put("latitude", hospitalLat);
        loc.put("longitude", hospitalLng);
        loc.put("timestamp", FieldValue.serverTimestamp());

        db.collection("driverLocations").document(ambulanceId)
                .set(loc, SetOptions.merge());

        cancel();
    }

    private synchronized void cancel() {
        stopped = true;
        if (future != null) {
            future.cancel(true);
            future = null;
        }
        ACTIVE_SIMULATIONS.remove(ambulanceId);
    }
}
