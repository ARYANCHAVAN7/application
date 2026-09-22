package com.example.application;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;

import java.util.Locale;

public class AmbulanceActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MapboxMap mMap;
    private MapView mapView;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration emergencyListener;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // PHASE 8: GPS Smoothing filter to reduce jitter
    private com.example.application.utils.GPSSmoothing.MovingAverageFilter gpsFilter;

    private MaterialSwitch statusSwitch;
    private LinearLayout medicalInfoSection;
    private TextView btnToggleVitals;
    private TextView tvPatientName, tvEmergencyType, tvAddress, tvDriverName, tvAmbulanceId;
    private TextView tvPatientBlood, tvPatientAllergies, tvPatientPhone, tvEmergencyContact;

    private String hospitalName, securityNumber;
    private String driverUid;
    private String currentEmergencyId;
    private String currentAmbulanceId;
    private String currentEmergencyAddress = "";
    private boolean isVitalsExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulence_dashboard);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        hospitalName = getIntent().getStringExtra("hospitalName");
        securityNumber = getIntent().getStringExtra("securityNumber");
        currentAmbulanceId = getIntent().getStringExtra("ambulanceId");
        driverUid = getIntent().getStringExtra("driverUid");

        if (driverUid != null && (auth.getCurrentUser() == null
                || !driverUid.equals(auth.getCurrentUser().getUid()))) {
            Toast.makeText(this, "Your ambulance login session has expired. Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // PHASE 8: Initialize GPS smoothing filter with buffer size 3
        gpsFilter = new com.example.application.utils.GPSSmoothing.MovingAverageFilter(3);

        bindViews();
        if (mapView != null) {
            mMap = mapView.getMapboxMap();
        }
        
        try {
            setupMap();
        } catch (Exception e) {
            Log.e("AmbulanceActivity", "Error during map setup in onCreate", e);
            Toast.makeText(this, "Error initializing ambulance dashboard: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        if (statusSwitch == null || btnToggleVitals == null || mapView == null) {
            Toast.makeText(this, "Ambulance dashboard is incomplete. Please update the app.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        statusSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateStatusInFirestore(isChecked);
            if (isChecked) {
                statusSwitch.setText(R.string.status_on_trip);
                statusSwitch.setTextColor(ContextCompat.getColor(this, R.color.red_primary));
            } else {
                statusSwitch.setText(R.string.status_available);
                statusSwitch.setTextColor(ContextCompat.getColor(this, R.color.green_safe));
            }
        });

        btnToggleVitals.setOnClickListener(v -> toggleVitals());
        findViewById(R.id.btnNavigate).setOnClickListener(v -> openNavigation());
        findViewById(R.id.btnArrived).setOnClickListener(v -> completeEmergency());
    }

    private void bindViews() {
        statusSwitch = findViewById(R.id.statusSwitch);
        medicalInfoSection = findViewById(R.id.medicalInfoSection);
        btnToggleVitals = findViewById(R.id.btnToggleVitals);
        tvPatientName = findViewById(R.id.tvPatientName);
        tvEmergencyType = findViewById(R.id.tvEmergencyType);
        tvAddress = findViewById(R.id.tvAddress);
        tvDriverName = findViewById(R.id.tvDriverName);
        tvAmbulanceId = findViewById(R.id.tvAmbulanceId);

        tvPatientBlood = findViewById(R.id.tvPatientBlood);
        tvPatientAllergies = findViewById(R.id.tvPatientAllergies);
        tvPatientPhone = findViewById(R.id.tvPatientPhone);
        tvEmergencyContact = findViewById(R.id.tvEmergencyContact);
        
        mapView = findViewById(R.id.map);
    }

    private void setupMap() {
        if (mapView == null) return;
        
        try {
            mapView.getMapboxMap().loadStyle(Style.MAPBOX_STREETS, style -> {
                try {
                    mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                            .zoom(12.0)
                            .build());
                    loadAmbulanceData();
                } catch (Exception e) {
                    Log.e("AmbulanceActivity", "Error in map initialization", e);
                    Toast.makeText(AmbulanceActivity.this, "Error loading emergency data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e("AmbulanceActivity", "Error loading map style", e);
            Toast.makeText(this, "Error loading map: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAmbulanceData() {
        if (currentAmbulanceId != null && !currentAmbulanceId.trim().isEmpty()) {
            db.collection("ambulances").document(currentAmbulanceId).get()
                    .addOnSuccessListener(this::applyAmbulanceData)
                    .addOnFailureListener(e -> showDashboardError("Unable to load ambulance profile."));
            return;
        }
        if (hospitalName == null || securityNumber == null) {
            showDashboardError("Ambulance credentials are missing. Please log in again.");
            return;
        }

        db.collection("ambulances")
                .whereEqualTo("hospitalName", hospitalName)
                .whereEqualTo("securityNumber", securityNumber)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        applyAmbulanceData(queryDocumentSnapshots.getDocuments().get(0));
                    } else {
                        showDashboardError("No ambulance record is assigned to this login.");
                    }
                })
                .addOnFailureListener(e -> showDashboardError("Unable to load ambulance profile."));
    }

    private void applyAmbulanceData(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            showDashboardError("Ambulance record was not found.");
            return;
        }
        currentAmbulanceId = doc.getId();
        String name = doc.getString("driverName");
        String vehicle = doc.getString("vehicleNumber");
        if (name != null && !name.trim().isEmpty()) tvDriverName.setText(String.format(Locale.US, "%s (Driver)", name));
        if (vehicle != null && !vehicle.trim().isEmpty()) tvAmbulanceId.setText(String.format(Locale.US, "ID: %s", vehicle));
        if (hospitalName == null) hospitalName = doc.getString("hospitalName");
        if (securityNumber == null) securityNumber = doc.getString("securityNumber");
        listenForEmergencies();
        if (hasPermission()) {
            startLiveAmbulanceTracking();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void showDashboardError(String message) {
        Log.e("AmbulanceActivity", message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void listenForEmergencies() {
        if (currentAmbulanceId == null || currentAmbulanceId.trim().isEmpty()) {
            return;
        }

        try {
            emergencyListener = db.collection("emergencies")
                    .whereEqualTo("assignedAmbulanceId", currentAmbulanceId)
                    .whereIn("status", java.util.Arrays.asList("pending", "active", "accepted"))
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) {
                            Log.e("AmbulanceActivity", "Firestore listener error", error);
                            Toast.makeText(AmbulanceActivity.this, "Unable to load emergency updates: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (value != null && !value.isEmpty()) {
                            updateEmergencyUI(value.getDocuments().get(0));
                        } else {
                            clearEmergencyUI();
                        }
                    });
        } catch (Exception e) {
            Log.e("AmbulanceActivity", "Error setting up emergency listener", e);
            // If there's an error setting up the listener (e.g., missing index), show a message
            Toast.makeText(this, "Could not set up emergency listener: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEmergencyUI(DocumentSnapshot doc) {
        currentEmergencyId = doc.getId();
        String patientName = doc.getString("userName");
        String address = doc.getString("location");
        currentEmergencyAddress = address != null ? address : "";

        tvPatientName.setText(patientName != null ? patientName : "Emergency Request");
        tvAddress.setText(address != null ? address : "Location shared via GPS");
        tvEmergencyType.setText("CRITICAL EMERGENCY");

        String userId = doc.getString("userId");
        if (userId != null) {
            fetchPatientMedicalInfo(userId);
        }

        updateMapMarker(doc);
    }

    private void fetchPatientMedicalInfo(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("fullName");
                        if (name != null) tvPatientName.setText(name);

                        tvPatientBlood.setText(doc.getString("bloodGroup"));
                        tvPatientAllergies.setText(doc.getString("allergies"));
                        tvPatientPhone.setText(doc.getString("phone"));

                        String eName = doc.getString("emergencyName");
                        String ePhone = doc.getString("emergencyPhone");
                        tvEmergencyContact.setText(String.format(Locale.US, "%s: %s", eName, ePhone));
                    }
                });
    }

    private void updateMapMarker(DocumentSnapshot doc) {
        if (mMap == null) return;

        Double latitude = doc.getDouble("latitude");
        Double longitude = doc.getDouble("longitude");
        if (latitude == null || longitude == null) {
            clearEmergencyUI();
            return;
        }

        // Mapbox v11 no longer exposes the legacy annotation plugin used here.
        // Keep the map centered on the emergency location without relying on removed APIs.
        Point emergencyPoint = Point.fromLngLat(longitude, latitude);
        try {
            mMap.setCamera(new CameraOptions.Builder()
                    .center(emergencyPoint)
                    .zoom(15.0)
                    .build());
        } catch (Exception e) {
            Log.e("AmbulanceActivity", "Error updating map marker", e);
        }

        if (hasPermission()) {
            requestCurrentAmbulanceLocation();
        }
    }

    private void clearEmergencyUI() {
        tvPatientName.setText("No Active Task");
        tvAddress.setText("Standby for incoming requests");
        tvEmergencyType.setText("System Ready");
        tvPatientBlood.setText("--");
        tvPatientAllergies.setText("--");
        tvPatientPhone.setText("--");
        tvEmergencyContact.setText("--");
        currentEmergencyAddress = "";
    }

    private void updateStatusInFirestore(boolean isOnTrip) {
        if (currentAmbulanceId == null || currentAmbulanceId.trim().isEmpty()) return;
        db.collection("ambulances").document(currentAmbulanceId).update("isOnTrip", isOnTrip);
    }

    private void completeEmergency() {
        if (currentEmergencyAddress == null || currentEmergencyAddress.isEmpty() || currentEmergencyAddress.contains("Standby")) {
            Toast.makeText(this, "No active emergency to complete", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentEmergencyId == null) return;
        db.collection("emergencies").document(currentEmergencyId)
                .update("status", "completed")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Mission Completed Successfully!", Toast.LENGTH_LONG).show();
                    clearEmergencyUI();
                    statusSwitch.setChecked(false);
                    currentEmergencyId = null;
                    if (currentAmbulanceId != null) {
                        db.collection("ambulances").document(currentAmbulanceId).update("isOnTrip", false);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Unable to complete emergency: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void toggleVitals() {
        isVitalsExpanded = !isVitalsExpanded;
        medicalInfoSection.setVisibility(isVitalsExpanded ? View.VISIBLE : View.GONE);
        btnToggleVitals.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                isVitalsExpanded ? android.R.drawable.arrow_up_float : android.R.drawable.arrow_down_float, 0);
    }

    private void openNavigation() {
        if (currentEmergencyAddress.isEmpty()) {
            Toast.makeText(this, "No destination set", Toast.LENGTH_SHORT).show();
            return;
        }
        // Use generic Maps URI that works with any installed mapping app
        Uri mapIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(currentEmergencyAddress));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapIntentUri);
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "No mapping application found", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableMyLocation() {
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // The Mapbox v11 SDK no longer exposes the legacy LocationComponent plugin classes
        // used by earlier versions. GPS positioning continues via Google Play Services.
    }

    @SuppressLint("MissingPermission")
    private void requestCurrentAmbulanceLocation() {
        if (!hasPermission() || fusedLocationClient == null) return;

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        addAmbulanceMarker(location);
                        saveCurrentAmbulanceLocation(location);
                    }
                });
    }

    private void addAmbulanceMarker(Location location) {
        if (mMap == null) return;

        try {
            Point ambulancePoint = Point.fromLngLat(location.getLongitude(), location.getLatitude());
            mMap.setCamera(new CameraOptions.Builder()
                    .center(ambulancePoint)
                    .zoom(12.0)
                    .build());
        } catch (Exception e) {
            Log.e("AmbulanceActivity", "Error adding ambulance marker", e);
        }
    }

    private void saveCurrentAmbulanceLocation(Location location) {
        if (currentAmbulanceId == null || currentAmbulanceId.trim().isEmpty()) return;
        db.collection("ambulances").document(currentAmbulanceId).update(
                "latitude", location.getLatitude(),
                "longitude", location.getLongitude(),
                "lastUpdatedAt", FieldValue.serverTimestamp());
    }

    @SuppressLint("MissingPermission")
    private void startLiveAmbulanceTracking() {
        if (!hasPermission()) return;

        try {
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000L)
                    .setMinUpdateDistanceMeters(10f)
                    .build();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult.getLastLocation() != null) {
                        // PHASE 8: Apply GPS smoothing filter to reduce jitter
                        Location rawLocation = locationResult.getLastLocation();
                        Location smoothedLocation = gpsFilter.addLocation(rawLocation);

                        // Only save if we have a smoothed location (sufficient buffer data)
                        if (smoothedLocation != null) {
                            Log.d("AmbulanceActivity", String.format("GPS - Raw: (%.4f, %.4f), Smoothed: (%.4f, %.4f)",
                                    rawLocation.getLatitude(), rawLocation.getLongitude(),
                                    smoothedLocation.getLatitude(), smoothedLocation.getLongitude()));
                            saveCurrentAmbulanceLocation(smoothedLocation);
                        } else {
                            Log.d("AmbulanceActivity", "GPS filter buffering... (" + gpsFilter.getBufferSize() + "/3)");
                        }
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (Exception e) {
            Log.e("AmbulanceActivity", "Error starting location tracking", e);
            Toast.makeText(this, "Could not start location tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (hasPermission()) {
                enableMyLocation();
                startLiveAmbulanceTracking();
            } else {
                Toast.makeText(this, "Location permission is required for live ambulance GPS updates.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emergencyListener != null) {
            emergencyListener.remove();
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
