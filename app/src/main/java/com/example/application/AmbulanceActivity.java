package com.example.application;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class AmbulanceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Switch statusSwitch;
    private LinearLayout medicalInfoSection;
    private TextView btnToggleVitals;
    private boolean isVitalsExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulence_dashboard);

        // Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        statusSwitch = findViewById(R.id.statusSwitch);
        Button btnNavigate = findViewById(R.id.btnNavigate);
        Button btnArrived = findViewById(R.id.btnArrived);
        medicalInfoSection = findViewById(R.id.medicalInfoSection);
        btnToggleVitals = findViewById(R.id.btnToggleVitals);

        statusSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                statusSwitch.setText(R.string.status_on_trip);
                statusSwitch.setTextColor(ContextCompat.getColor(this, R.color.red_primary));
            } else {
                statusSwitch.setText(R.string.status_available);
                statusSwitch.setTextColor(ContextCompat.getColor(this, R.color.green_safe));
            }
        });

        btnToggleVitals.setOnClickListener(v -> {
            isVitalsExpanded = !isVitalsExpanded;
            if (isVitalsExpanded) {
                medicalInfoSection.setVisibility(View.VISIBLE);
                btnToggleVitals.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_up_float, 0);
            } else {
                medicalInfoSection.setVisibility(View.GONE);
                btnToggleVitals.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
            }
        });

        btnNavigate.setOnClickListener(v -> {
            Toast.makeText(this, "Opening navigation...", Toast.LENGTH_SHORT).show();
        });

        btnArrived.setOnClickListener(v -> {
            Toast.makeText(this, "Status updated: Arrived at destination", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable live traffic
        try {
            mMap.setTrafficEnabled(true);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // Add a placeholder marker and move camera
        LatLng emergencyLocation = new LatLng(19.0760, 72.8777); // Example: Mumbai
        mMap.addMarker(new MarkerOptions().position(emergencyLocation).title("Emergency Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 15f));
    }
}
