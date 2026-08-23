package com.example.application;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private CountDownTimer holdTimer;
    private final long holdDurationMs = 2000;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userEmergencyPhone = "6491050867"; // Default
    private FrameLayout sosButton;
    private android.widget.LinearLayout adminPanelBtn;
    private boolean isSosTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        sosButton = findViewById(R.id.sosButton);
        TextView callNowBtn = findViewById(R.id.callNowBtn);
        adminPanelBtn = findViewById(R.id.adminPanelBtn);

        // Press-and-hold for 2 seconds to trigger SOS
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

        // Call Now button dials the emergency contact number
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

                        // Update UI with real data
                        ((TextView) findViewById(R.id.userNameDisplay)).setText(documentSnapshot.getString("fullName"));
                        ((TextView) findViewById(R.id.bloodGroupDisplay)).setText(documentSnapshot.getString("bloodGroup"));
                        ((TextView) findViewById(R.id.emergencyContactName)).setText(documentSnapshot.getString("emergencyName"));
                        
                        userEmergencyPhone = documentSnapshot.getString("emergencyPhone");
                        ((TextView) findViewById(R.id.emergencyContactPhone)).setText(userEmergencyPhone);

                        // Check for admin role
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
        
        // Short vibration to signal start
        vibrate(50);
    }

    private void stopScalingAnimation() {
        sosButton.clearAnimation();
        ScaleAnimation scaleDown = new ScaleAnimation(1.3f, 1f, 1.3f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleDown.setDuration(200);
        sosButton.startAnimation(scaleDown);
    }

    private void triggerSos() {
        if (mAuth.getCurrentUser() == null) return;

        // Strong vibration for success
        vibrate(500);

        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> emergency = new HashMap<>();
        emergency.put("userId", uid);
        emergency.put("userName", ((TextView) findViewById(R.id.userNameDisplay)).getText().toString());
        emergency.put("status", "active");
        emergency.put("timestamp", FieldValue.serverTimestamp());
        emergency.put("location", "Static Location (GPS Disabled)");

        db.collection("emergencies").add(emergency)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "🚨 SOS SENT! Help is on the way.", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    isSosTriggered = false;
                    Toast.makeText(this, "Failed to send SOS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void vibrate(long duration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(duration);
        }
    }
}
