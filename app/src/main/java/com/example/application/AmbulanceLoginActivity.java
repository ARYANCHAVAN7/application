package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AmbulanceLoginActivity extends AppCompatActivity {

    private EditText inputHospitalName, inputSecurityNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulance_login);

        inputHospitalName = findViewById(R.id.inputHospitalName);
        inputSecurityNumber = findViewById(R.id.inputSecurityNumber);

        TextView btnLogin = findViewById(R.id.btnAmbulanceLogin);
        btnLogin.setOnClickListener(v -> handleLogin());
    }

    /**
     * Frontend-only validation for now — no backend exists yet.
     * Once a backend is ready, replace this with an actual API call
     * that verifies the hospital name + security number combination.
     */
    private void handleLogin() {
        String hospitalName = inputHospitalName.getText().toString().trim();
        String securityNumber = inputSecurityNumber.getText().toString().trim();

        if (TextUtils.isEmpty(hospitalName)) {
            inputHospitalName.setError("Hospital name is required");
            return;
        }
        if (TextUtils.isEmpty(securityNumber)) {
            inputSecurityNumber.setError("Security number is required");
            return;
        }

        // TODO: Replace with real authentication once backend exists.
        Toast.makeText(this, "Login successful! (frontend only)", Toast.LENGTH_LONG).show();

        // Navigate to the Hospital dashboard your teammate built
        startActivity(new Intent(this, HospitalActivity.class));
        finish();
    }
}