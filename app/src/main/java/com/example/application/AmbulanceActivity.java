package com.example.application;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.application.utils.FirestoreValues;
import com.example.application.utils.LiveMapController;
import com.example.application.utils.SurakshaLog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.mapbox.geojson.Point;
import com.mapbox.maps.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ambulance dashboard + live map for ONE ambulance (ambulanceId from login / session restore).
 *
 * - Assigned request: emergencies where ambulanceId == this ambulance and status in DRIVER_VISIBLE.
 * - AMBULANCE_DISPATCHED: ACCEPT / REJECT.
 * - Primary action walks DRIVER_ACCEPTED -> AMBULANCE_ARRIVED -> PATIENT_PICKED_UP
 *   -> EN_ROUTE_TO_HOSPITAL -> COMPLETED (each a transaction on the SAME document).
 * - Map: ambulance marker (from driverLocations), user and hospital markers, route + distance + ETA.
 * - Location is produced only by DriverLocationService (no tracker in this Activity).
 */
public class AmbulanceActivity extends AppCompatActivity {

    public static final String EXTRA_AMBULANCE_ID = "ambulanceId";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String ambulanceId;

    private ListenerRegistration ambulanceListener;
    private ListenerRegistration emergencyListener;
    private ListenerRegistration ownLocationListener;

    private MapView mapView;
    private LiveMapController mapController;
    private Point ambulancePoint, userPoint, hospitalPoint;

    private DocumentSnapshot currentEmergency;
    private boolean actionInProgress = false;
    private boolean suppressSwitchCallback = false;
    private boolean onTrip = false;

    private MaterialSwitch statusSwitch;
    private LinearLayout medicalInfoSection, jobDecisionRow;
    private TextView btnToggleVitals, tvJobStatus, tvRouteInfo;
    private TextView tvPatientName, tvEmergencyType, tvAddress, tvDriverName, tvAmbulanceId;
    private TextView tvPatientBlood, tvPatientAllergies, tvPatientPhone, tvEmergencyContact;
    private Button btnPrimaryAction, btnNavigate, btnAcceptJob, btnRejectJob;
    private boolean isVitalsExpanded = false;

    private static FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.CANCELLED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulence_dashboard);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        ambulanceId = getIntent().getStringExtra(EXTRA_AMBULANCE_ID);
        if (ambulanceId == null || mAuth.getCurrentUser() == null) {
            SurakshaLog.warn(SurakshaLog.DRIVER, "ambulance dashboard without active session");
            goToStart();
            return;
        }

        bindViews();
        mapController = new LiveMapController(this, new LiveMapController.Listener() {
            @Override
            public void onRouteInfo(String distanceAndEta, boolean toHospital) {
                tvRouteInfo.setText((toHospital ? "To hospital: " : "To patient: ") + distanceAndEta);
            }

            @Override
            public void onRouteUnavailable(String fallbackText) {
                tvRouteInfo.setText(fallbackText);
            }
        });
        mapController.attach(mapView); // map loading no longer gates data or GPS

        statusSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallback) return;
            setOnDuty(isChecked);
        });
        btnToggleVitals.setOnClickListener(v -> toggleVitals());
        btnNavigate.setOnClickListener(v -> openNavigation());
        btnPrimaryAction.setOnClickListener(v -> advanceStatus());
        btnAcceptJob.setOnClickListener(v -> acceptJob());
        btnRejectJob.setOnClickListener(v -> rejectJob());
        findViewById(R.id.btnDriverLogout).setOnClickListener(v -> logout(false));
        View btnRecenter = findViewById(R.id.btnRecenterAmbulanceMap);
        if (btnRecenter != null) {
            btnRecenter.setOnClickListener(v -> {
                if (mapController != null) mapController.forceRecenter();
            });
        }

        tvAmbulanceId.setText("ID: " + ambulanceId);
        clearEmergencyUI();
        ensurePermissionsThenTrack();
    }

    private void bindViews() {
        statusSwitch = findViewById(R.id.statusSwitch);
        medicalInfoSection = findViewById(R.id.medicalInfoSection);
        jobDecisionRow = findViewById(R.id.jobDecisionRow);
        btnToggleVitals = findViewById(R.id.btnToggleVitals);
        tvJobStatus = findViewById(R.id.tvJobStatus);
        tvRouteInfo = findViewById(R.id.tvRouteInfo);
        tvPatientName = findViewById(R.id.tvPatientName);
        tvEmergencyType = findViewById(R.id.tvEmergencyType);
        tvAddress = findViewById(R.id.tvAddress);
        tvDriverName = findViewById(R.id.tvDriverName);
        tvAmbulanceId = findViewById(R.id.tvAmbulanceId);
        tvPatientBlood = findViewById(R.id.tvPatientBlood);
        tvPatientAllergies = findViewById(R.id.tvPatientAllergies);
        tvPatientPhone = findViewById(R.id.tvPatientPhone);
        tvEmergencyContact = findViewById(R.id.tvEmergencyContact);
        btnPrimaryAction = findViewById(R.id.btnArrived);
        btnNavigate = findViewById(R.id.btnNavigate);
        btnAcceptJob = findViewById(R.id.btnAcceptJob);
        btnRejectJob = findViewById(R.id.btnRejectJob);
        mapView = findViewById(R.id.map);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ambulanceId == null) return;
        attachListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachListeners(); // the foreground service keeps publishing location
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapController != null) mapController.destroy();
    }

    // ------------------------------------------------------------------ permissions / tracking

    private void ensurePermissionsThenTrack() {
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return;
        }
        startTracking();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startTracking() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission is required for live ambulance tracking.", Toast.LENGTH_LONG).show();
            setSwitch(false);
            return;
        }
        DriverLocationService.start(this, ambulanceId);
        setSwitch(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startTracking();
        }
    }

    private void setSwitch(boolean onDuty) {
        suppressSwitchCallback = true;
        statusSwitch.setChecked(onDuty);
        suppressSwitchCallback = false;
        statusSwitch.setText(onDuty ? "ON DUTY" : "OFF DUTY");
        statusSwitch.setTextColor(ContextCompat.getColor(this, onDuty ? R.color.green_safe : R.color.red_primary));
    }

    private void setOnDuty(boolean onDuty) {
        if (!onDuty && onTrip) {
            Toast.makeText(this, "Finish or reject the current emergency before going off duty.", Toast.LENGTH_LONG).show();
            setSwitch(true);
            return;
        }
        if (onDuty) {
            ensurePermissionsThenTrack();
        } else {
            DriverLocationService.stop(this);
            db.collection("ambulances").document(ambulanceId).update("isOnline", false)
                    .addOnFailureListener(e -> SurakshaLog.error(SurakshaLog.DRIVER, "set offline", e, "ambulanceId=" + ambulanceId));
            setSwitch(false);
        }
    }

    // ------------------------------------------------------------------ listeners

    private void attachListeners() {
        if (ambulanceListener == null) {
            ambulanceListener = db.collection("ambulances").document(ambulanceId)
                    .addSnapshotListener((doc, error) -> {
                        if (error != null) {
                            SurakshaLog.error(SurakshaLog.AMBULANCE, "own ambulance listener", error, "ambulanceId=" + ambulanceId);
                            if (error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Toast.makeText(this, "Driver session is no longer valid. Please log in again.", Toast.LENGTH_LONG).show();
                                logout(true);
                            }
                            return;
                        }
                        if (doc == null || !doc.exists()) return;
                        String name = doc.getString("driverName");
                        String vehicle = doc.getString("vehicleNumber");
                        tvDriverName.setText(String.format(Locale.US, "%s (Driver)", name != null ? name : "Driver"));
                        tvAmbulanceId.setText(String.format(Locale.US, "Vehicle: %s  •  %s",
                                vehicle != null ? vehicle : "--", nonEmpty(doc.getString("hospitalName"), "")));
                        onTrip = Boolean.TRUE.equals(doc.getBoolean("isOnTrip"));
                    });
        }
        if (emergencyListener == null) {
            emergencyListener = db.collection("emergencies")
                    .whereEqualTo("ambulanceId", ambulanceId)
                    .whereIn("status", EmergencyStatus.DRIVER_VISIBLE)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) {
                            SurakshaLog.error(SurakshaLog.DRIVER, "assigned emergency listener", error, "ambulanceId=" + ambulanceId);
                            Toast.makeText(this, SurakshaLog.userMessage("Unable to load assignments", error), Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (value == null || value.isEmpty()) {
                            currentEmergency = null;
                            clearEmergencyUI();
                            return;
                        }
                        // Normally at most one; pick the newest defensively.
                        DocumentSnapshot newest = value.getDocuments().get(0);
                        for (DocumentSnapshot d : value.getDocuments()) {
                            if (d.getTimestamp("timestamp") != null && newest.getTimestamp("timestamp") != null
                                    && d.getTimestamp("timestamp").compareTo(newest.getTimestamp("timestamp")) > 0) {
                                newest = d;
                            }
                        }
                        onEmergency(newest);
                    });
        }
        if (ownLocationListener == null) {
            ownLocationListener = db.collection("driverLocations").document(ambulanceId)
                    .addSnapshotListener((doc, error) -> {
                        if (error != null) {
                            SurakshaLog.error(SurakshaLog.MAP, "own location listener", error, "ambulanceId=" + ambulanceId);
                            return;
                        }
                        if (doc == null || !doc.exists()) return;
                        Point p = LiveMapController.point(FirestoreValues.getDouble(doc, "latitude"),
                                FirestoreValues.getDouble(doc, "longitude"));
                        if (p != null) {
                            ambulancePoint = p;
                            refreshMap();
                        }
                    });
        }
    }

    private void detachListeners() {
        if (ambulanceListener != null) { ambulanceListener.remove(); ambulanceListener = null; }
        if (emergencyListener != null) { emergencyListener.remove(); emergencyListener = null; }
        if (ownLocationListener != null) { ownLocationListener.remove(); ownLocationListener = null; }
    }

    // ------------------------------------------------------------------ UI

    private void onEmergency(DocumentSnapshot doc) {
        String previousStatus = currentEmergency != null ? currentEmergency.getString("status") : null;
        currentEmergency = doc;
        String status = doc.getString("status");
        if (status != null && !status.equals(previousStatus)) {
            SurakshaLog.info(SurakshaLog.DRIVER, "assignment update", "emergencyId=" + doc.getId(),
                    "ambulanceId=" + ambulanceId, "status=" + status);
        }

        tvPatientName.setText(nonEmpty(doc.getString("userName"), "Emergency Request"));
        tvEmergencyType.setText("PRIORITY: " + nonEmpty(doc.getString("priority"), "CRITICAL"));
        Double lat = FirestoreValues.getDouble(doc, "latitude");
        Double lng = FirestoreValues.getDouble(doc, "longitude");
        tvAddress.setText(lat != null && lng != null
                ? String.format(Locale.US, "Patient: %.5f, %.5f\nHospital: %s", lat, lng, nonEmpty(doc.getString("hospitalName"), "--"))
                : "Location unavailable");
        tvJobStatus.setText("Status: " + EmergencyStatus.label(status));

        tvPatientBlood.setText(nonEmpty(doc.getString("bloodGroup"), "--"));
        tvPatientAllergies.setText("--");
        tvPatientPhone.setText(nonEmpty(doc.getString("userPhone"), "--"));
        tvEmergencyContact.setText(String.format(Locale.US, "%s: %s",
                nonEmpty(doc.getString("emergencyContactName"), "--"), nonEmpty(doc.getString("emergencyContactPhone"), "--")));

        boolean dispatched = EmergencyStatus.AMBULANCE_DISPATCHED.equals(status);
        jobDecisionRow.setVisibility(dispatched ? View.VISIBLE : View.GONE);
        String actionLabel = EmergencyStatus.driverActionLabel(status);
        btnPrimaryAction.setVisibility(actionLabel != null ? View.VISIBLE : View.GONE);
        if (actionLabel != null) btnPrimaryAction.setText(actionLabel);
        btnNavigate.setVisibility(dispatched ? View.GONE : View.VISIBLE);
        setActionsEnabled(!actionInProgress);

        userPoint = LiveMapController.point(lat, lng);
        hospitalPoint = LiveMapController.point(FirestoreValues.getDouble(doc, "hospitalLatitude"),
                FirestoreValues.getDouble(doc, "hospitalLongitude"));
        refreshMap();
    }

    private void refreshMap() {
        if (mapController == null) return;
        String status = currentEmergency != null ? currentEmergency.getString("status") : null;
        boolean routing = status != null && !EmergencyStatus.AMBULANCE_DISPATCHED.equals(status);
        mapController.update(currentEmergency != null ? userPoint : null, ambulancePoint,
                currentEmergency != null ? hospitalPoint : null,
                EmergencyStatus.isHeadingToHospital(status));
        if (!routing && currentEmergency == null) tvRouteInfo.setText("");
    }

    private void clearEmergencyUI() {
        tvPatientName.setText("No Active Task");
        tvAddress.setText("Standby for incoming requests");
        tvEmergencyType.setText("System Ready");
        tvJobStatus.setText("Status: waiting for dispatch");
        tvRouteInfo.setText("");
        tvPatientBlood.setText("--");
        tvPatientAllergies.setText("--");
        tvPatientPhone.setText("--");
        tvEmergencyContact.setText("--");
        jobDecisionRow.setVisibility(View.GONE);
        btnPrimaryAction.setVisibility(View.GONE);
        btnNavigate.setVisibility(View.GONE);
        userPoint = null;
        hospitalPoint = null;
        refreshMap();
    }

    private void setActionsEnabled(boolean enabled) {
        btnPrimaryAction.setEnabled(enabled);
        btnAcceptJob.setEnabled(enabled);
        btnRejectJob.setEnabled(enabled);
    }

    private void setInProgress(boolean value) {
        actionInProgress = value;
        setActionsEnabled(!value);
    }

    // ------------------------------------------------------------------ transitions

    private void acceptJob() {
        if (currentEmergency == null || actionInProgress) return;
        String emergencyId = currentEmergency.getId();
        DocumentReference ref = db.collection("emergencies").document(emergencyId);
        DocumentReference locRef = db.collection("driverLocations").document(ambulanceId);
        setInProgress(true);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (!snap.exists() || !ambulanceId.equals(snap.getString("ambulanceId"))) throw abort("Request no longer assigned to you.");
            if (!EmergencyStatus.AMBULANCE_DISPATCHED.equals(snap.getString("status"))) throw abort("Request already handled.");
            Map<String, Object> u = new HashMap<>();
            u.put("status", EmergencyStatus.DRIVER_ACCEPTED);
            u.put("driverAcceptedAt", FieldValue.serverTimestamp());
            u.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(ref, u);
            // Links the live location to this emergency so the patient is allowed to read it.
            Map<String, Object> link = new HashMap<>();
            link.put("ambulanceId", ambulanceId);
            link.put("emergencyId", emergencyId);
            transaction.set(locRef, link, SetOptions.merge());
            return null;
        }).addOnSuccessListener(r -> {
            SurakshaLog.info(SurakshaLog.DRIVER, "driver accepted", "emergencyId=" + emergencyId,
                    "ambulanceId=" + ambulanceId, "status=" + EmergencyStatus.DRIVER_ACCEPTED);
            setInProgress(false);
            refreshMap();
            if (mapController != null) mapController.forceRecenter();
            Toast.makeText(this, "Accepted. Route to patient is on the map.", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            SurakshaLog.error(SurakshaLog.DRIVER, "driver accept", e, "emergencyId=" + emergencyId, "ambulanceId=" + ambulanceId);
            setInProgress(false);
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Accept failed", Toast.LENGTH_LONG).show();
        });
    }

    private void rejectJob() {
        if (currentEmergency == null || actionInProgress) return;
        String emergencyId = currentEmergency.getId();
        DocumentReference ref = db.collection("emergencies").document(emergencyId);
        DocumentReference ambRef = db.collection("ambulances").document(ambulanceId);
        setInProgress(true);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (!snap.exists() || !ambulanceId.equals(snap.getString("ambulanceId"))) throw abort("Request no longer assigned to you.");
            if (!EmergencyStatus.AMBULANCE_DISPATCHED.equals(snap.getString("status"))) throw abort("Request already handled.");
            Map<String, Object> u = new HashMap<>();
            u.put("status", EmergencyStatus.DRIVER_REJECTED);
            u.put("ambulanceId", null);
            u.put("rejectedAmbulanceIds", FieldValue.arrayUnion(ambulanceId));
            u.put("driverRejectedAt", FieldValue.serverTimestamp());
            u.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(ref, u);
            Map<String, Object> a = new HashMap<>();
            a.put("isOnTrip", false);
            a.put("currentEmergencyId", null);
            transaction.update(ambRef, a);
            return null;
        }).addOnSuccessListener(r -> {
            SurakshaLog.info(SurakshaLog.DRIVER, "driver rejected", "emergencyId=" + emergencyId,
                    "ambulanceId=" + ambulanceId, "status=" + EmergencyStatus.DRIVER_REJECTED);
            setInProgress(false);
            currentEmergency = null;
            clearEmergencyUI();
            Toast.makeText(this, "Rejected. The hospital will assign another ambulance.", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            SurakshaLog.error(SurakshaLog.DRIVER, "driver reject", e, "emergencyId=" + emergencyId, "ambulanceId=" + ambulanceId);
            setInProgress(false);
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Reject failed", Toast.LENGTH_LONG).show();
        });
    }

    /** ARRIVED -> PICKED UP -> EN ROUTE -> COMPLETED on the same emergency document. */
    private void advanceStatus() {
        if (currentEmergency == null || actionInProgress) return;
        String emergencyId = currentEmergency.getId();
        String expected = currentEmergency.getString("status");
        String next = EmergencyStatus.nextDriverStatus(expected);
        if (next == null) return;

        DocumentReference ref = db.collection("emergencies").document(emergencyId);
        DocumentReference ambRef = db.collection("ambulances").document(ambulanceId);
        DocumentReference locRef = db.collection("driverLocations").document(ambulanceId);
        setInProgress(true);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (!snap.exists() || !ambulanceId.equals(snap.getString("ambulanceId"))) throw abort("Request no longer assigned to you.");
            if (!expected.equals(snap.getString("status"))) throw abort("Status changed; refresh.");
            Map<String, Object> u = new HashMap<>();
            u.put("status", next);
            u.put(EmergencyStatus.timestampFieldFor(next), FieldValue.serverTimestamp());
            u.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(ref, u);
            if (EmergencyStatus.COMPLETED.equals(next)) {
                // Release the ambulance and unlink the patient from the live location.
                Map<String, Object> a = new HashMap<>();
                a.put("isOnTrip", false);
                a.put("currentEmergencyId", null);
                transaction.update(ambRef, a);
                Map<String, Object> unlink = new HashMap<>();
                unlink.put("emergencyId", null);
                transaction.set(locRef, unlink, SetOptions.merge());
            }
            return null;
        }).addOnSuccessListener(r -> {
            SurakshaLog.info(SurakshaLog.DRIVER, "status advanced", "emergencyId=" + emergencyId,
                    "ambulanceId=" + ambulanceId, "status=" + next);
            setInProgress(false);
            refreshMap();
            if (mapController != null) mapController.forceRecenter();
            if (EmergencyStatus.COMPLETED.equals(next)) {
                Toast.makeText(this, "Mission completed. Ambulance is available again.", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> {
            SurakshaLog.error(SurakshaLog.DRIVER, "advance status", e, "emergencyId=" + emergencyId,
                    "ambulanceId=" + ambulanceId, "status=" + next);
            setInProgress(false);
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Update failed", Toast.LENGTH_LONG).show();
        });
    }

    // ------------------------------------------------------------------ misc

    private void toggleVitals() {
        isVitalsExpanded = !isVitalsExpanded;
        medicalInfoSection.setVisibility(isVitalsExpanded ? View.VISIBLE : View.GONE);
        btnToggleVitals.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                isVitalsExpanded ? android.R.drawable.arrow_up_float : android.R.drawable.arrow_down_float, 0);
    }

    /** Opens turn-by-turn navigation directly: patient before pickup, hospital afterwards. */
    private void openNavigation() {
        if (currentEmergency == null) {
            Toast.makeText(this, "No destination set", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean toHospital = EmergencyStatus.isHeadingToHospital(currentEmergency.getString("status"));
        Point dest = toHospital ? hospitalPoint : userPoint;
        if (dest == null) {
            Toast.makeText(this, "Destination location unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        // Re-center in-app map right away
        if (mapController != null) mapController.forceRecenter();

        // Launch direct Google Maps turn-by-turn navigation
        Uri gmmIntentUri = Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f&mode=d",
                dest.latitude(), dest.longitude()));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Fallback for devices without Google Maps app installed
            String label = toHospital ? nonEmpty(currentEmergency.getString("hospitalName"), "Hospital") : "Patient";
            Uri uri = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                    dest.latitude(), dest.longitude(), dest.latitude(), dest.longitude(), Uri.encode(label)));
            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, uri);
            if (fallbackIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(fallbackIntent);
            } else {
                Toast.makeText(this, "No navigation app found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void logout(boolean force) {
        if (!force && onTrip && currentEmergency != null) {
            Toast.makeText(this, "Finish or reject the current emergency before logging out.", Toast.LENGTH_LONG).show();
            return;
        }
        DriverLocationService.stop(this);
        detachListeners();
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        db.collection("ambulances").document(ambulanceId).update("isOnline", false)
                .addOnCompleteListener(t -> {
                    if (uid != null) {
                        db.collection("driverSessions").document(uid).delete()
                                .addOnCompleteListener(t2 -> {
                                    SurakshaLog.info(SurakshaLog.DRIVER, "driver logged out", "ambulanceId=" + ambulanceId);
                                    mAuth.signOut();
                                    goToStart();
                                });
                    } else {
                        goToStart();
                    }
                });
    }

    private void goToStart() {
        Intent intent = new Intent(this, StartingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String nonEmpty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }
}
