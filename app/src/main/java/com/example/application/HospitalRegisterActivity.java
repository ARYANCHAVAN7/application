package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class HospitalRegisterActivity extends AppCompatActivity {

    private EditText etHospitalName, etEmail, etPhone, etAddress, etLicenseNumber,
            etTotalBeds, etTotalAmbulances, etPassword, etConfirmPassword;
    private android.widget.ProgressBar progressBar;
    private Button btnRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();

        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> handleRegistration());
        }

        TextView tvGoToLogin = findViewById(R.id.tvGoToLogin);
        if (tvGoToLogin != null) {
            tvGoToLogin.setOnClickListener(v -> finish());
        }
    }

    private void bindViews() {
        etHospitalName = findViewById(R.id.etHospitalName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etLicenseNumber = findViewById(R.id.etLicenseNumber);
        etTotalBeds = findViewById(R.id.etTotalBeds);
        etTotalAmbulances = findViewById(R.id.etTotalAmbulances);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
    }

    private void handleRegistration() {
        String name = etHospitalName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String license = etLicenseNumber.getText().toString().trim();
        String totalBeds = etTotalBeds.getText().toString().trim();
        String totalAmbulances = etTotalAmbulances.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String userId = mAuth.getCurrentUser().getUid();
                        // Approval Request Change: Passing false for setPending to allow direct registration
                        saveHospitalToFirestore(userId, name, email, phone, address, license, totalBeds, totalAmbulances, false);
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, "Hospital email already registered. Checking profile...", Toast.LENGTH_SHORT).show();
                            repairHospitalAccount(email, password, name, phone, address, license, totalBeds, totalAmbulances);
                        } else {
                            setLoading(false);
                            String error = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                            Toast.makeText(HospitalRegisterActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (btnRegister != null) btnRegister.setEnabled(!isLoading);
        if (progressBar != null) progressBar.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void repairHospitalAccount(String email, String password, String name, String phone, 
                                        String address, String license, String beds, String ambulances) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String userId = mAuth.getCurrentUser().getUid();
                        // For repairs, check if isVerified exists before resetting it
                        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
                            boolean currentStatus = false;
                            if (doc.exists() && doc.contains("isVerified")) {
                                Boolean status = doc.getBoolean("isVerified");
                                if (status != null) currentStatus = status;
                            }
                            saveHospitalToFirestore(userId, name, email, phone, address, license, beds, ambulances, !currentStatus && !doc.exists());
                        });
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Email is in use, but password doesn't match. Please use a different email.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveHospitalToFirestore(String userId, String name, String email, String phone,
                                         String address, String license, String beds, String ambulances,
                                         boolean setPending) {
        Map<String, Object> hospital = new HashMap<>();
        hospital.put("hospitalName", name);
        hospital.put("email", email);
        hospital.put("phone", phone);
        hospital.put("address", address);
        hospital.put("licenseNumber", license);
        hospital.put("totalBeds", beds);
        hospital.put("totalAmbulances", ambulances);
        hospital.put("availableBeds", beds);
        hospital.put("availableAmbulances", ambulances);
        hospital.put("role", "hospital");
        hospital.put("approvalStatus", setPending ? "pending" : "verified");
        hospital.put("isVerified", setPending ? false : true);

        db.collection("users").document(userId)
                .set(hospital, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(HospitalRegisterActivity.this, setPending ? "Hospital registration submitted for admin approval." : "Hospital registration successful.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(HospitalRegisterActivity.this, StartingActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(HospitalRegisterActivity.this, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
