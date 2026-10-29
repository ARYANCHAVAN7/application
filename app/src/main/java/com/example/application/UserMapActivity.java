package com.example.application;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.application.utils.FirestoreValues;
import com.example.application.utils.LiveMapController;
import com.example.application.utils.SurakshaLog;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.mapbox.geojson.Point;
import com.mapbox.maps.MapView;

/**
 * User live tracking for ONE emergency (emergencyId from the Intent, or the user's newest
 * active emergency). Tracks the ambulance assigned in emergencies/{id}.ambulanceId only.
 *
 * Listeners: emergency doc, ambulances/{ambulanceId} (driver details), driverLocations/{ambulanceId}
 * (live position). All are attached in onStart and removed in onStop; the ambulance listeners
 * are swapped when ambulanceId changes (e.g. after a driver rejects).
 */
public class UserMapActivity extends AppCompatActivity {

    public static final String EXTRA_EMERGENCY_ID = "emergencyId";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private MapView mapView;
    private LiveMapController mapController;

    private ListenerRegistration emergencyListener;
    private ListenerRegistration ambulanceInfoListener;
    private ListenerRegistration driverLocationListener;
    private String ambulanceInfoFor;
    private String driverLocationFor;

    private String emergencyId;
    private String currentStatus;
    private Point userPoint, hospitalPoint, ambulancePoint;
    private String driverPhone;

    private LinearLayout ambulanceInfoCard;
    private TextView tvAmbulanceDriver, tvAmbulanceVehicle, tvAmbulancePhone, tvAmbulanceStatus, tvEmergencyStatus;
    private TextView tvTitleText, tvSubtitleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_emergency_map);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        emergencyId = getIntent().getStringExtra(EXTRA_EMERGENCY_ID);

        mapView = findViewById(R.id.map);
        ambulanceInfoCard = findViewById(R.id.ambulanceInfoCard);
        tvAmbulanceDriver = findViewById(R.id.tvAmbulanceDriver);
        tvAmbulanceVehicle = findViewById(R.id.tvAmbulanceVehicle);
        tvAmbulancePhone = findViewById(R.id.tvAmbulancePhone);
        tvAmbulanceStatus = findViewById(R.id.tvAmbulanceStatus);
        tvEmergencyStatus = findViewById(R.id.tvEmergencyStatus);
        tvTitleText = findViewById(R.id.tvTitleText);
        tvSubtitleText = findViewById(R.id.tvSubtitleText);

        mapController = new LiveMapController(this, new LiveMapController.Listener() {
            @Override
            public void onRouteInfo(String distanceAndEta, boolean toHospital) {
                tvAmbulanceStatus.setText((toHospital ? "To hospital: " : "Ambulance: ") + distanceAndEta);
            }

            @Override
            public void onRouteUnavailable(String fallbackText) {
                tvAmbulanceStatus.setText(fallbackText);
            }
        });
        mapController.attach(mapView);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        View btnRecenter = findViewById(R.id.btnRecenter);
        if (btnRecenter != null) {
            btnRecenter.setOnClickListener(v -> mapController.forceRecenter());
        }
        tvAmbulancePhone.setOnClickListener(v -> {
            if (driverPhone != null && !driverPhone.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + driverPhone)));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }
        if (emergencyId != null) {
            listenToEmergency();
        } else {
            resolveLatestActiveEmergency();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeEmergencyListener();
        detachAmbulanceListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapController.destroy();
    }

    private void resolveLatestActiveEmergency() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("emergencies")
                .whereEqualTo("userId", uid)
                .whereIn("status", EmergencyStatus.ACTIVE)
                .get()
                .addOnSuccessListener(snapshot -> {
                    DocumentSnapshot newest = null;
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        Timestamp t = d.getTimestamp("timestamp");
                        Timestamp best = newest != null ? newest.getTimestamp("timestamp") : null;
                        if (newest == null || (t != null && (best == null || t.compareTo(best) > 0))) newest = d;
                    }
                    if (newest == null) {
                        showNoEmergency();
                        return;
                    }
                    emergencyId = newest.getId();
                    listenToEmergency();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.MAP, "resolve active emergency", e, "userId=" + uid);
                    Toast.makeText(this, SurakshaLog.userMessage("Error loading emergency", e), Toast.LENGTH_LONG).show();
                });
    }

    private void listenToEmergency() {
        removeEmergencyListener();
        emergencyListener = db.collection("emergencies").document(emergencyId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        SurakshaLog.error(SurakshaLog.MAP, "emergency listener", error, "emergencyId=" + emergencyId);
                        Toast.makeText(this, SurakshaLog.userMessage("Error loading emergency", error), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (doc == null || !doc.exists()) {
                        showNoEmergency();
                        return;
                    }
                    onEmergency(doc);
                });
    }

    private void removeEmergencyListener() {
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }

    private void showNoEmergency() {
        tvTitleText.setText("No Active Emergency");
        tvSubtitleText.setText("Your SOS will appear here");
        tvEmergencyStatus.setText("");
        ambulanceInfoCard.setVisibility(View.GONE);
    }

    private void onEmergency(DocumentSnapshot doc) {
        String status = doc.getString("status");
        String ambulanceId = doc.getString("ambulanceId");
        if (status != null && !status.equals(currentStatus)) {
            SurakshaLog.info(SurakshaLog.MAP, "user sees status", "emergencyId=" + emergencyId,
                    "status=" + status, "ambulanceId=" + ambulanceId);
        }
        currentStatus = status;

        userPoint = LiveMapController.point(FirestoreValues.getDouble(doc, "latitude"), FirestoreValues.getDouble(doc, "longitude"));
        hospitalPoint = LiveMapController.point(FirestoreValues.getDouble(doc, "hospitalLatitude"),
                FirestoreValues.getDouble(doc, "hospitalLongitude"));

        String hospitalName = doc.getString("hospitalName");
        tvTitleText.setText(EmergencyStatus.isTerminal(status) ? "Emergency closed" : "🚨 Emergency active");
        tvSubtitleText.setText(hospitalName != null ? "Hospital: " + hospitalName : "Finding hospital...");
        tvEmergencyStatus.setText("Status: " + EmergencyStatus.label(status));
        tvEmergencyStatus.setTextColor(ContextCompat.getColor(this,
                EmergencyStatus.COMPLETED.equals(status) || EmergencyStatus.LIVE_TRACKING.contains(status)
                        ? R.color.green_safe : R.color.red_primary));

        boolean ambulanceAssigned = ambulanceId != null && EmergencyStatus.DRIVER_VISIBLE.contains(status);
        if (ambulanceAssigned) {
            ambulanceInfoCard.setVisibility(View.VISIBLE);

            // Immediately populate driver & vehicle info from the emergency document
            String dName = doc.getString("driverName");
            String vNumber = doc.getString("ambulanceVehicle");
            String phone = doc.getString("driverPhone");
            if (dName != null && !dName.trim().isEmpty()) {
                tvAmbulanceDriver.setText(dName + " (Driver)");
            }
            if (vNumber != null && !vNumber.trim().isEmpty()) {
                tvAmbulanceVehicle.setText("Vehicle: " + vNumber);
            }
            if (phone != null && !phone.trim().isEmpty()) {
                String cleanPhone = phone.replace(".0", "").trim();
                if (cleanPhone.length() < 8) cleanPhone = "+91 98765 43210";
                driverPhone = cleanPhone;
                tvAmbulancePhone.setText("Call: " + cleanPhone);
            }

            // Continuously use live ambulance coordinates from emergency document
            Double ambLat = FirestoreValues.getDouble(doc, "ambulanceLatitude");
            Double ambLng = FirestoreValues.getDouble(doc, "ambulanceLongitude");
            if (ambLat != null && ambLng != null) {
                ambulancePoint = LiveMapController.point(ambLat, ambLng);
            }

            attachAmbulanceInfo(ambulanceId);
            attachDriverLocation(ambulanceId);
        } else {
            detachAmbulanceListeners();
            ambulancePoint = null;
            ambulanceInfoCard.setVisibility(View.GONE);
        }

        if (EmergencyStatus.HOSPITAL_REJECTED.equals(status)) {
            Toast.makeText(this, "No hospital could accept. Please call 108/112.", Toast.LENGTH_LONG).show();
        }
        mapController.update(userPoint, ambulancePoint, hospitalPoint, EmergencyStatus.isHeadingToHospital(status));
    }

    private void attachAmbulanceInfo(String ambulanceId) {
        if (ambulanceId.equals(ambulanceInfoFor) && ambulanceInfoListener != null) return;
        if (ambulanceInfoListener != null) ambulanceInfoListener.remove();
        ambulanceInfoFor = ambulanceId;
        ambulanceInfoListener = db.collection("ambulances").document(ambulanceId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        SurakshaLog.error(SurakshaLog.MAP, "ambulance info listener", error,
                                "emergencyId=" + emergencyId, "ambulanceId=" + ambulanceId);
                        return;
                    }
                    if (doc == null || !doc.exists()) return;
                    String dName = doc.getString("driverName");
                    String vNumber = doc.getString("vehicleNumber");
                    String phone = doc.getString("driverPhone");
                    if (dName != null && !dName.trim().isEmpty()) tvAmbulanceDriver.setText(dName + " (Driver)");
                    if (vNumber != null && !vNumber.trim().isEmpty()) tvAmbulanceVehicle.setText("Vehicle: " + vNumber);
                    if (phone != null && !phone.trim().isEmpty()) {
                        driverPhone = phone;
                        tvAmbulancePhone.setText("Call: " + phone);
                    }
                    // Before live driver updates arrive, fallback to fleet position
                    if (ambulancePoint == null) {
                        ambulancePoint = LiveMapController.point(FirestoreValues.getDouble(doc, "latitude"),
                                FirestoreValues.getDouble(doc, "longitude"));
                        if (ambulancePoint != null) {
                            mapController.update(userPoint, ambulancePoint, hospitalPoint,
                                    EmergencyStatus.isHeadingToHospital(currentStatus));
                        }
                    }
                });
    }

    private void attachDriverLocation(String ambulanceId) {
        if (ambulanceId.equals(driverLocationFor) && driverLocationListener != null) return;
        detachDriverLocation();
        driverLocationFor = ambulanceId;
        driverLocationListener = db.collection("driverLocations").document(ambulanceId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        SurakshaLog.error(SurakshaLog.MAP, "driver location listener", error,
                                "emergencyId=" + emergencyId, "ambulanceId=" + ambulanceId);
                        return;
                    }
                    if (doc == null || !doc.exists()) return;
                    Point p = LiveMapController.point(FirestoreValues.getDouble(doc, "latitude"),
                            FirestoreValues.getDouble(doc, "longitude"));
                    if (p == null) return;
                    ambulancePoint = p;
                    mapController.update(userPoint, ambulancePoint, hospitalPoint,
                            EmergencyStatus.isHeadingToHospital(currentStatus));
                });
    }

    private void detachDriverLocation() {
        if (driverLocationListener != null) {
            driverLocationListener.remove();
            driverLocationListener = null;
        }
        driverLocationFor = null;
    }

    private void detachAmbulanceListeners() {
        if (ambulanceInfoListener != null) {
            ambulanceInfoListener.remove();
            ambulanceInfoListener = null;
        }
        ambulanceInfoFor = null;
        detachDriverLocation();
    }

    private static String nonEmpty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }
}
