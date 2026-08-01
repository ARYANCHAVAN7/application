package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AmbulanceLoginActivity extends AppCompatActivity {

    private EditText inputHospitalName, inputSecurityNumber;
    private View loginCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulance_login);

        loginCard = findViewById(R.id.loginCard);
        inputHospitalName = findViewById(R.id.inputHospitalName);
        inputSecurityNumber = findViewById(R.id.inputSecurityNumber);

        TextView btnLogin = findViewById(R.id.btnAmbulanceLogin);
        View btnGoogleLogin = findViewById(R.id.btnAmbulanceGoogleLogin);

        btnLogin.setOnClickListener(v -> handleLogin());

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> {
                // Shake effect for feedback
                if (loginCard != null) {
                    Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                    loginCard.startAnimation(shake);
                }

                // Simulated Account Selection Options
                String[] accounts = {"ambulance_alpha@suraksha.com", "driver_77@gmail.com", "Add another account"};
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Select Ambulance Account")
                        .setItems(accounts, (dialog, which) -> {
                            if (which < 2) {
                                Toast.makeText(this, "Signing in with " + accounts[which], Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, HospitalActivity.class));
                            } else {
                                Toast.makeText(this, "Redirecting to Google Sign-In...", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            });
        }
    }

    /**
     * Frontend-only validation for now — no backend exists yet.
     * Once a backend is ready, replace this with an actual API call
     * that verifies the hospital name + security number combination.
     */
    private void handleLogin() {
        String hospitalName = inputHospitalName.getText().toString().trim();
        String securityNumber = inputSecurityNumber.getText().toString().trim();

        if (TextUtils.isEmpty(hospitalName) || TextUtils.isEmpty(securityNumber)) {
            if (loginCard != null) {
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
            }
            
            if (TextUtils.isEmpty(hospitalName)) {
                inputHospitalName.setError("Hospital name is required");
            }
            if (TextUtils.isEmpty(securityNumber)) {
                inputSecurityNumber.setError("Security number is required");
            }
            return;
        }

        // TODO: Replace with real authentication once backend exists.
        Toast.makeText(this, "Login successful! (frontend only)", Toast.LENGTH_LONG).show();

        // Navigate to the Hospital dashboard your teammate built
        startActivity(new Intent(this, HospitalActivity.class));
        finish();
    }
}