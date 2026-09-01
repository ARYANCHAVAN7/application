package com.example.application;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;

import java.util.Locale;

public class UserMapActivity extends AppCompatActivity {

    private static final String TAG = "UserMapActivity";

    private MapboxMap mMap;
    private MapView mapView;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration emergencyListener;
    private ListenerRegistration ambulanceListener;

    private String currentEmergencyId;
    private LinearLayout ambulanceInfoCard;
    private TextView tvAmbulanceDriver, tvAmbulanceVehicle, tvAmbulancePhone, tvAmbulanceStatus, tvEmergencyStatus;
    private TextView tvTitleText, tvSubtitleText;

    // PHASE 5: Fields for route and ETA tracking
    private Double emergencyLatitude;
    private Double emergencyLongitude;
    private Double ambulanceLatitude;
    private Double ambulanceLongitude;
    private TextView tvETA;  // For displaying ETA

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_emergency_map);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupMap();
        listenForUserEmergency();

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
    }

    private void bindViews() {
        mapView = findViewById(R.id.map);
        ambulanceInfoCard = findViewById(R.id.ambulanceInfoCard);
        tvAmbulanceDriver = findViewById(R.id.tvAmbulanceDriver);
        tvAmbulanceVehicle = findViewById(R.id.tvAmbulanceVehicle);
        tvAmbulancePhone = findViewById(R.id.tvAmbulancePhone);
        tvAmbulanceStatus = findViewById(R.id.tvAmbulanceStatus);
        tvEmergencyStatus = findViewById(R.id.tvEmergencyStatus);
        tvTitleText = findViewById(R.id.tvTitleText);
        tvSubtitleText = findViewById(R.id.tvSubtitleText);
    }

    private void setupMap() {
        if (mapView == null) return;

        mapView.getMapboxMap().loadStyle(Style.MAPBOX_STREETS, style -> {
            mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                    .zoom(12.0)
                    .build());
        });
        mMap = mapView.getMapboxMap();
    }

    private void listenForUserEmergency() {
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            finish();
            return;
        }

        // Listen for the most recent emergency for this user with status pending/active/accepted
        emergencyListener = db.collection("emergencies")
                .whereEqualTo("userId", userId)
                .whereIn("status", java.util.Arrays.asList("pending", "active", "accepted"))
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to emergency", error);
                        Toast.makeText(this, "Error loading emergency: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null && !value.isEmpty()) {
                        DocumentSnapshot emergencyDoc = value.getDocuments().get(0);
                        currentEmergencyId = emergencyDoc.getId();
                        updateEmergencyDisplay(emergencyDoc);
                        listenForAmbulance(emergencyDoc);
                    } else {
                        // No active emergency
                        tvTitleText.setText("No Active Emergency");
                        tvSubtitleText.setText("Your SOS will appear here");
                        ambulanceInfoCard.setVisibility(View.GONE);
                    }
                });
    }

    private void updateEmergencyDisplay(DocumentSnapshot emergencyDoc) {
        Double latitude = emergencyDoc.getDouble("latitude");
        Double longitude = emergencyDoc.getDouble("longitude");
        String status = emergencyDoc.getString("status");

        // PHASE 5: Store emergency location for route calculation
        this.emergencyLatitude = latitude;
        this.emergencyLongitude = longitude;

        if (latitude != null && longitude != null && mMap != null) {
            Point emergencyPoint = Point.fromLngLat(longitude, latitude);
            try {
                mMap.setCamera(new CameraOptions.Builder()
                        .center(emergencyPoint)
                        .zoom(15.0)
                        .build());
            } catch (Exception e) {
                Log.e(TAG, "Error setting camera", e);
            }
        }

        if (status != null) {
            tvEmergencyStatus.setText("Status: " + status.toUpperCase());
            switch (status) {
                case "pending":
                    tvTitleText.setText("🚨 Emergency Sent");
                    tvSubtitleText.setText("Waiting for hospital response...");
                    tvEmergencyStatus.setTextColor(ContextCompat.getColor(this, R.color.red_primary));
                    break;
                case "active":
                    tvTitleText.setText("✓ Emergency Accepted");
                    tvSubtitleText.setText("Ambulance is dispatched");
                    tvEmergencyStatus.setTextColor(ContextCompat.getColor(this, R.color.red_primary));
                    break;
                case "accepted":
                    tvTitleText.setText("✓ Ambulance En Route");
                    tvSubtitleText.setText("Ambulance location is tracked below");
                    tvEmergencyStatus.setTextColor(ContextCompat.getColor(this, R.color.green_safe));
                    break;
            }
        }
    }

    private void listenForAmbulance(DocumentSnapshot emergencyDoc) {
        String hospitalId = emergencyDoc.getString("hospitalId");
        String acceptedBy = emergencyDoc.getString("acceptedBy");

        // If accepted, show ambulance info
        if (acceptedBy != null) {
            ambulanceInfoCard.setVisibility(View.VISIBLE);

            // Find the ambulance assigned to this hospital
            db.collection("ambulances")
                    .whereEqualTo("hospitalId", acceptedBy)
                    .limit(1)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error loading ambulance", error);
                            return;
                        }

                        if (value != null && !value.isEmpty()) {
                            DocumentSnapshot ambulanceDoc = value.getDocuments().get(0);
                            displayAmbulanceInfo(ambulanceDoc);
                            listenForAmbulanceLocation(ambulanceDoc.getId());
                        }
                    });
        } else {
            ambulanceInfoCard.setVisibility(View.GONE);
        }
    }

    private void listenForAmbulanceLocation(String ambulanceDocId) {
        ambulanceListener = db.collection("ambulances")
                .document(ambulanceDocId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to ambulance location", error);
                        return;
                    }

                    if (doc != null && doc.exists()) {
                        Double latitude = doc.getDouble("latitude");
                        Double longitude = doc.getDouble("longitude");

                        if (latitude != null && longitude != null) {
                            updateAmbulanceLocation(latitude, longitude);
                            tvAmbulanceStatus.setText(String.format(Locale.US, "📍 %.4f, %.4f", latitude, longitude));
                        }
                    }
                });
    }

    private void updateAmbulanceLocation(Double latitude, Double longitude) {
        if (mMap == null) return;

        // PHASE 5: Store ambulance location for route calculation
        this.ambulanceLatitude = latitude;
        this.ambulanceLongitude = longitude;

        Point ambulancePoint = Point.fromLngLat(longitude, latitude);
        try {
            mMap.setCamera(new CameraOptions.Builder()
                    .center(ambulancePoint)
                    .zoom(14.0)
                    .build());
        } catch (Exception e) {
            Log.e(TAG, "Error updating ambulance location", e);
        }

        // PHASE 5: Calculate route if both locations are available
        if (emergencyLatitude != null && emergencyLongitude != null &&
            ambulanceLatitude != null && ambulanceLongitude != null) {
            calculateAndDisplayRoute();
        }
    }

    // PHASE 5: Calculate route using Mapbox Directions API
    private void calculateAndDisplayRoute() {
        com.example.application.utils.MapboxRouting.calculateRoute(
            this,
            ambulanceLatitude, ambulanceLongitude,
            emergencyLatitude, emergencyLongitude,
            new com.example.application.utils.MapboxRouting.RouteCallback() {
                @Override
                public void onRouteSuccess(com.example.application.utils.MapboxRouting.RouteInfo route) {
                    Log.d(TAG, "Route calculated: " + route);
                    
                    // Display route information
                    String etaText = String.format(Locale.US,
                        "📍 Distance: %.1f km | ETA: %s",
                        route.distanceKilometers,
                        com.example.application.utils.MapboxRouting.formatDuration(route.durationSeconds));
                    
                    tvAmbulanceStatus.setText(etaText);
                    tvSubtitleText.setText(com.example.application.utils.MapboxRouting.getETADescription(route.durationSeconds));
                }

                @Override
                public void onRouteFailure(String errorMessage) {
                    Log.e(TAG, "Route calculation failed: " + errorMessage);
                    tvAmbulanceStatus.setText("📍 Location updating...");
                }
            }
        );
    }

    private void displayAmbulanceInfo(DocumentSnapshot ambulanceDoc) {
        String driverName = ambulanceDoc.getString("driverName");
        String vehicleNumber = ambulanceDoc.getString("vehicleNumber");
        String driverPhone = ambulanceDoc.getString("driverPhone");

        if (driverName != null) tvAmbulanceDriver.setText(driverName);
        if (vehicleNumber != null) tvAmbulanceVehicle.setText("Vehicle: " + vehicleNumber);
        if (driverPhone != null) tvAmbulancePhone.setText("Call: " + driverPhone);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (emergencyListener != null) emergencyListener.remove();
        if (ambulanceListener != null) ambulanceListener.remove();
    }
}
