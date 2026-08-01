package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

public class StartingActivity extends AppCompatActivity {

    private LinearLayout roleUser, roleHospital, roleAdmin;
    private CardView loginCard;
    private String selectedRole = "User"; // Default role

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.starting_page);

        loginCard = findViewById(R.id.loginCard);
        roleUser = findViewById(R.id.roleUser);
        roleHospital = findViewById(R.id.roleHospital);
        roleAdmin = findViewById(R.id.roleAdmin);

        // 1. Entrance Animation - Slides the whole card up on start
        Animation entrance = AnimationUtils.loadAnimation(this, R.anim.card_entrance);
        loginCard.startAnimation(entrance);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn = findViewById(R.id.btnSignIn);
        TextView tvRegister = findViewById(R.id.tvRegister);

        roleUser.setOnClickListener(v -> selectRole("User"));
        roleHospital.setOnClickListener(v -> selectRole("Hospital"));
        roleAdmin.setOnClickListener(v -> selectRole("Ambulance"));

        btnSignIn.setOnClickListener(v -> {
            String email = etEmail.getText().toString();
            String password = etPassword.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                // 2. Shake Animation - For error feedback
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedRole.equals("Hospital")) {
                Intent intent = new Intent(this, HospitalActivity.class);
                startActivity(intent);
            } else {
                // Placeholder for other roles
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            }
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegistrationActivity.class);
            startActivity(intent);
        });
    }

    private void selectRole(String role) {
        if (selectedRole.equals(role)) return;
        selectedRole = role;

        // 3. Selection Animation - Quick scale for the selected role
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);

        // Reset all to unselected state
        updateRoleView(roleUser, false);
        updateRoleView(roleHospital, false);
        updateRoleView(roleAdmin, false);

        // Highlight and animate the selected role
        if (role.equals("User")) {
            updateRoleView(roleUser, true);
            roleUser.startAnimation(scaleUp);
        } else if (role.equals("Hospital")) {
            updateRoleView(roleHospital, true);
            roleHospital.startAnimation(scaleUp);
        } else if (role.equals("Ambulance")) {
            updateRoleView(roleAdmin, true);
            roleAdmin.startAnimation(scaleUp);
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
}