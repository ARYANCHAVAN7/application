package com.example.application;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class AmbulanceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private GoogleMap mMap;
    private FirebaseFirestore db;
    private ListenerRegistration emergencyListener;
    
    private MaterialSwitch statusSwitch;
    private LinearLayout medicalInfoSection;
    private TextView btnToggleVitals;
    private TextView tvPatientName, tvEmergencyType, tvAddress, tvDriverName, tvAmbulanceId;
    private TextView tvPatientBlood, tvPatientAllergies, tvPatientPhone, tvEmergencyContact;
    
    private String hospitalName, securityNumber;
    private String currentEmergencyAddress = "";
    private boolean isVitalsExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulence_dashboard);

        db = FirebaseFirestore.getInstance();
        hospitalName = getIntent().getStringExtra("hospitalName");
        securityNumber = getIntent().getStringExtra("securityNumber");

        bindViews();
        setupMap();
        
        loadAmbulanceData();
        listenForEmergencies();

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
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void loadAmbulanceData() {
        if (hospitalName == null || securityNumber == null) return;

        db.collection("ambulances")
                .whereEqualTo("hospitalName", hospitalName)
                .whereEqualTo("securityNumber", securityNumber)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        String name = doc.getString("driverName");
                        String vehicle = doc.getString("vehicleNumber");
                        
                        if (name != null) {
                            tvDriverName.setText(String.format("%s (Driver)", name));
                        }
                        if (vehicle != null) {
                            tvAmbulanceId.setText(String.format("ID: %s", vehicle));
                        }
                    }
                });
    }

    private void listenForEmergencies() {
        emergencyListener = db.collection("emergencies")
                .whereEqualTo("status", "active")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((value, error) -> {
                    if (value != null && !value.isEmpty()) {
                        DocumentSnapshot doc = value.getDocuments().get(0);
                        updateEmergencyUI(doc);
                    } else {
                        clearEmergencyUI();
                    }
                });
    }

    private void updateEmergencyUI(DocumentSnapshot doc) {
        String patientName = doc.getString("userName");
        String address = doc.getString("location");
        currentEmergencyAddress = address;

        tvPatientName.setText(patientName != null ? patientName : "Emergency Request");
        tvAddress.setText(address != null ? address : "Location shared via GPS");
        tvEmergencyType.setText("CRITICAL EMERGENCY");

        String userId = doc.getString("userId");
        if (userId != null) {
            fetchPatientMedicalInfo(userId);
        }

        updateMapMarker();
    }

    private void fetchPatientMedicalInfo(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Refresh name from profile if available
                        String name = doc.getString("fullName");
                        if (name != null) tvPatientName.setText(name);

                        tvPatientBlood.setText(doc.getString("bloodGroup"));
                        tvPatientAllergies.setText(doc.getString("allergies"));
                        tvPatientPhone.setText(doc.getString("phone"));

                        String eName = doc.getString("emergencyName");
                        String ePhone = doc.getString("emergencyPhone");
                        tvEmergencyContact.setText(String.format("%s: %s", eName, ePhone));
                    }
                });
    }

    private void updateMapMarker() {
        if (mMap == null) return;
        
        // Example: Mumbai Central simulated for patients
        LatLng emergencyPos = new LatLng(18.9696, 72.8193); 
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(emergencyPos).title("Patient Location"));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyPos, 15f));
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
        if (mMap != null) mMap.clear();
    }

    private void updateStatusInFirestore(boolean isOnTrip) {
        if (hospitalName == null || securityNumber == null) return;

        db.collection("ambulances")
                .whereEqualTo("hospitalName", hospitalName)
                .whereEqualTo("securityNumber", securityNumber)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        queryDocumentSnapshots.getDocuments().get(0).getReference()
                                .update("isOnTrip", isOnTrip);
                    }
                });
    }

    private void completeEmergency() {
        if (currentEmergencyAddress == null || currentEmergencyAddress.isEmpty() || currentEmergencyAddress.contains("Standby")) {
            Toast.makeText(this, "No active emergency to complete", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("emergencies")
                .whereEqualTo("status", "active")
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        queryDocumentSnapshots.getDocuments().get(0).getReference()
                                .update("status", "completed")
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Mission Completed Successfully!", Toast.LENGTH_LONG).show();
                                    clearEmergencyUI();
                                    statusSwitch.setChecked(false);
                                });
                    }
                });
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
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(currentEmergencyAddress));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Google Maps not installed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        
        enableMyLocation();
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                mMap.setTrafficEnabled(true);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emergencyListener != null) {
            emergencyListener.remove();
        }
    }
}
