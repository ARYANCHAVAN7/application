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

import com.google.firebase.auth.FirebaseAuth;
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

    private void checkExistingSession() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String role = documentSnapshot.getString("role");
                            if ("hospital".equals(role)) {
                                startActivity(new Intent(this, HospitalActivity.class));
                            } else {
                                startActivity(new Intent(this, DashboardActivity.class));
                            }
                            finish();
                        }
                    });
        }
    }
}