package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.application.utils.FirestoreValues;
import com.example.application.utils.SurakshaLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class StartingActivity extends AppCompatActivity {

    private LinearLayout roleUser, roleHospital, roleAdmin;
    private String selectedRole = "User"; // Default role
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.starting_page);

        mAuth = FirebaseAuth.getInstance();
        checkExistingSession();

        View headerSection = findViewById(R.id.headerSection);
        CardView loginCard = findViewById(R.id.loginCard);
        roleUser = findViewById(R.id.roleUser);
        roleHospital = findViewById(R.id.roleHospital);
        roleAdmin = findViewById(R.id.roleAdmin);
        LinearLayout roleGrid = findViewById(R.id.roleGrid);
        Button btnContinue = findViewById(R.id.btnContinue);

        // 1. Entrance Animations
        Animation fadeInDown = AnimationUtils.loadAnimation(this, R.anim.card_entrance);
        headerSection.startAnimation(fadeInDown);

        Animation fadeInUp = AnimationUtils.loadAnimation(this, R.anim.card_entrance);
        loginCard.startAnimation(fadeInUp);

        // 2. Staggered Entrance for Roles
        Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.scale_up);
        LayoutAnimationController controller = new LayoutAnimationController(slideIn);
        controller.setDelay(0.2f);
        roleGrid.setLayoutAnimation(controller);

        roleUser.setOnClickListener(v -> selectRole("User"));
        roleHospital.setOnClickListener(v -> selectRole("Hospital"));
        roleAdmin.setOnClickListener(v -> selectRole("Ambulance"));

        btnContinue.setOnClickListener(v -> {
            Intent intent;
            if (selectedRole.equals("Hospital")) {
                intent = new Intent(this, MainActivity.class);
            } else if (selectedRole.equals("User")) {
                intent = new Intent(this, UserLoginActivity.class);
            } else if (selectedRole.equals("Ambulance")) {
                intent = new Intent(this, AmbulanceLoginActivity.class);
            } else {
                intent = new Intent(this, MainActivity.class);
            }
            startActivity(intent);
        });
    }

    private void selectRole(String role) {
        if (selectedRole.equals(role)) return;
        selectedRole = role;

        // Spring-like scale effect
        Animation spring = AnimationUtils.loadAnimation(this, R.anim.scale_up);

        updateRoleView(roleUser, false);
        updateRoleView(roleHospital, false);
        updateRoleView(roleAdmin, false);

        if (role.equals("User")) {
            updateRoleView(roleUser, true);
            roleUser.startAnimation(spring);
        } else if (role.equals("Hospital")) {
            updateRoleView(roleHospital, true);
            roleHospital.startAnimation(spring);
        } else if (role.equals("Ambulance")) {
            updateRoleView(roleAdmin, true);
            roleAdmin.startAnimation(spring);
        }
    }

    private void updateRoleView(LinearLayout layout, boolean isSelected) {
        if (isSelected) {
            layout.setBackgroundResource(R.drawable.bg_role_selected);
            ((TextView) layout.getChildAt(1)).setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            layout.setBackgroundResource(R.drawable.bg_role_unselected);
            ((TextView) layout.getChildAt(1)).setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    /**
     * Restores a session only when FirebaseAuth has a current user AND the Firestore profile
     * confirms the role. Anything inconsistent is signed out (prevents stale-role redirect loops).
     */
    private void checkExistingSession() {
        FirebaseUser current = mAuth.getCurrentUser();
        if (current == null) return;
        String uid = current.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (current.isAnonymous()) {
            // Ambulance driver session (anonymous auth + driverSessions/{uid}).
            db.collection("driverSessions").document(uid).get()
                    .addOnSuccessListener(session -> {
                        String ambulanceId = session.exists() ? session.getString("ambulanceId") : null;
                        if (ambulanceId == null) {
                            SurakshaLog.info(SurakshaLog.AUTH, "anonymous session without driver session; signing out");
                            mAuth.signOut();
                            return;
                        }
                        SurakshaLog.info(SurakshaLog.AUTH, "restoring driver session", "ambulanceId=" + ambulanceId);
                        Intent intent = new Intent(this, AmbulanceActivity.class);
                        intent.putExtra(AmbulanceActivity.EXTRA_AMBULANCE_ID, ambulanceId);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        SurakshaLog.error(SurakshaLog.AUTH, "restore driver session", e);
                        mAuth.signOut();
                    });
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // Check if an ambulance driver session exists for this account
                        db.collection("driverSessions").document(uid).get()
                                .addOnSuccessListener(driverSession -> {
                                    String ambId = driverSession.exists() ? driverSession.getString("ambulanceId") : null;
                                    if (ambId != null && !ambId.trim().isEmpty()) {
                                        SurakshaLog.info(SurakshaLog.AUTH, "restoring driver session from driverSessions", "ambulanceId=" + ambId);
                                        Intent intent = new Intent(this, AmbulanceActivity.class);
                                        intent.putExtra(AmbulanceActivity.EXTRA_AMBULANCE_ID, ambId);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        SurakshaLog.warn(SurakshaLog.AUTH, "session without profile; signing out", "uid=" + uid);
                                        mAuth.signOut();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    SurakshaLog.warn(SurakshaLog.AUTH, "session without profile; signing out", "uid=" + uid);
                                    mAuth.signOut();
                                });
                        return;
                    }
                    String role = documentSnapshot.getString("role");
                    if (role != null) role = role.trim();
                    if ("hospital".equals(role)) {
                        if (FirestoreValues.getBoolean(documentSnapshot, "isVerified")) {
                            startActivity(new Intent(this, HospitalActivity.class));
                            finish();
                        } else {
                            SurakshaLog.info(SurakshaLog.AUTH, "unverified hospital session; signing out", "uid=" + uid);
                            mAuth.signOut();
                        }
                    } else if ("user".equals(role) || "admin".equals(role)) {
                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();
                    } else if ("ambulance".equals(role)) {
                        String ambId = documentSnapshot.getString("ambulanceId");
                        if (ambId != null && !ambId.trim().isEmpty()) {
                            SurakshaLog.info(SurakshaLog.AUTH, "restoring ambulance session from user profile", "ambulanceId=" + ambId);
                            Intent intent = new Intent(this, AmbulanceActivity.class);
                            intent.putExtra(AmbulanceActivity.EXTRA_AMBULANCE_ID, ambId);
                            startActivity(intent);
                            finish();
                        } else {
                            db.collection("driverSessions").document(uid).get()
                                    .addOnSuccessListener(sess -> {
                                        String aId = sess.exists() ? sess.getString("ambulanceId") : null;
                                        if (aId != null && !aId.trim().isEmpty()) {
                                            Intent intent = new Intent(this, AmbulanceActivity.class);
                                            intent.putExtra(AmbulanceActivity.EXTRA_AMBULANCE_ID, aId);
                                            startActivity(intent);
                                            finish();
                                        } else {
                                            mAuth.signOut();
                                        }
                                    }).addOnFailureListener(e -> mAuth.signOut());
                        }
                    } else {
                        SurakshaLog.warn(SurakshaLog.AUTH, "unknown role; signing out", "uid=" + uid, "role=" + role);
                        mAuth.signOut();
                    }
                })
                .addOnFailureListener(e -> SurakshaLog.error(SurakshaLog.AUTH, "restore session profile", e, "uid=" + uid));
    }
}
