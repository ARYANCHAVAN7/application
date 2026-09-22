package com.example.application;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private CountDownTimer holdTimer;
    private final long holdDurationMs = 2000;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private String userEmergencyPhone = "6491050867";
    private FrameLayout sosButton;
    private android.widget.LinearLayout adminPanelBtn;
    private boolean isSosTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sosButton = findViewById(R.id.sosButton);
        TextView callNowBtn = findViewById(R.id.callNowBtn);
        adminPanelBtn = findViewById(R.id.adminPanelBtn);

        sosButton.setOnTouchListener((view, event) -> {
            if (isSosTriggered) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startHoldCountdown();
                    startScalingAnimation();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHoldCountdown();
                    stopScalingAnimation();
                    view.performClick();
                    return true;
                default:
                    return false;
            }
        });

        callNowBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + userEmergencyPhone));
            startActivity(intent);
        });

        findViewById(R.id.logoutBtn).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(DashboardActivity.this, StartingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        if (adminPanelBtn != null) {
            adminPanelBtn.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AdminApprovalActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
        } else {
            loadUserProfile();
        }
    }

    private void loadUserProfile() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        if (role != null) role = role.trim();

                        if (!"user".equals(role) && !"admin".equals(role)) {
                            Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show();
                            redirectToLogin();
                            return;
                        }

                        ((TextView) findViewById(R.id.userNameDisplay)).setText(documentSnapshot.getString("fullName"));
                        ((TextView) findViewById(R.id.bloodGroupDisplay)).setText(documentSnapshot.getString("bloodGroup"));
                        ((TextView) findViewById(R.id.emergencyContactName)).setText(documentSnapshot.getString("emergencyName"));

                        userEmergencyPhone = documentSnapshot.getString("emergencyPhone");
                        ((TextView) findViewById(R.id.emergencyContactPhone)).setText(userEmergencyPhone);

                        if ("admin".equals(role) && adminPanelBtn != null) {
                            adminPanelBtn.setVisibility(android.view.View.VISIBLE);
                        }
                    }
                });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, StartingActivity.class);
        startActivity(intent);
        finish();
    }

    private void startHoldCountdown() {
        isSosTriggered = false;
        holdTimer = new CountDownTimer(holdDurationMs, holdDurationMs) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                isSosTriggered = true;
                triggerSos();
                stopScalingAnimation();
            }
        }.start();
    }

    private void cancelHoldCountdown() {
        if (holdTimer != null) {
            holdTimer.cancel();
            holdTimer = null;
        }
    }

    private void startScalingAnimation() {
        ScaleAnimation scaleUp = new ScaleAnimation(1f, 1.3f, 1f, 1.3f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleUp.setDuration(holdDurationMs);
        scaleUp.setFillAfter(true);
        sosButton.startAnimation(scaleUp);
        vibrate(50);
    }

    private void stopScalingAnimation() {
        sosButton.clearAnimation();
        ScaleAnimation scaleDown = new ScaleAnimation(1.3f, 1f, 1.3f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleDown.setDuration(200);
        sosButton.startAnimation(scaleDown);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return false;
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @SuppressLint("MissingPermission")
    private void triggerSos() {
        if (mAuth.getCurrentUser() == null) return;
        if (!hasLocationPermission()) {
            requestLocationPermission();
            Toast.makeText(this, "Location permission is required to send a real emergency SOS.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Turn on device location to send an emergency SOS.", Toast.LENGTH_LONG).show();
            return;
        }

        vibrate(500);

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        isSosTriggered = false;
                        Toast.makeText(this, "Unable to get your current location. Please try again.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String uid = mAuth.getCurrentUser().getUid();
                    Map<String, Object> emergency = new HashMap<>();
                    emergency.put("userId", uid);
                    emergency.put("userName", ((TextView) findViewById(R.id.userNameDisplay)).getText().toString());
                    emergency.put("status", "pending");
                    emergency.put("timestamp", FieldValue.serverTimestamp());
                    emergency.put("latitude", location.getLatitude());
                    emergency.put("longitude", location.getLongitude());
                    emergency.put("location", String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
                    emergency.put("locationUpdatedAt", FieldValue.serverTimestamp());

                    db.collection("emergencies").add(emergency)
                            .addOnSuccessListener(documentReference -> {
                                Toast.makeText(this, "🚨 SOS SENT! Help is on the way.", Toast.LENGTH_LONG).show();
                                // Navigate to map for real-time emergency tracking
                                Intent intent = new Intent(DashboardActivity.this, UserMapActivity.class);
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                isSosTriggered = false;
                                Toast.makeText(this, "Failed to send SOS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    isSosTriggered = false;
                    Log.e(TAG, "Failed to fetch current location", e);
                    Toast.makeText(this, "Failed to get current GPS location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (hasLocationPermission()) {
                triggerSos();
            } else {
                Toast.makeText(this, "Emergency SOS cannot be sent without location permission.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void vibrate(long duration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(duration);
        }
    }
}
