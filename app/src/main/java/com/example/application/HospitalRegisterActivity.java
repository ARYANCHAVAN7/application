package com.example.application;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.application.utils.HaversineDistance;
import com.example.application.utils.SurakshaLog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hospital registration. Creates users/{uid} with role "hospital", isVerified=false and
 * approvalStatus "pending". The hospital cannot log in until an admin approves it.
 * Existing emails are never "repaired" or converted between roles.
 */
public class HospitalRegisterActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 2101;

    private EditText etHospitalName, etEmail, etPhone, etAddress, etLicenseNumber,
            etTotalBeds, etTotalAmbulances, etPassword, etConfirmPassword, etLatitude, etLongitude;
    private android.widget.ProgressBar progressBar;
    private Button btnRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        bindViews();

        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> handleRegistration());
        findViewById(R.id.btnUseCurrentLocation).setOnClickListener(v -> fillCurrentLocation());

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
        etLatitude = findViewById(R.id.etLatitude);
        etLongitude = findViewById(R.id.etLongitude);
    }

    private void fillCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
            return;
        }
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            Toast.makeText(this, "Could not get location. Turn on GPS and retry.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        etLatitude.setText(String.format(Locale.US, "%.6f", location.getLatitude()));
                        etLongitude.setText(String.format(Locale.US, "%.6f", location.getLongitude()));
                    })
                    .addOnFailureListener(e -> {
                        SurakshaLog.error(SurakshaLog.HOSPITAL, "registration getCurrentLocation", e);
                        Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } catch (SecurityException e) {
            SurakshaLog.error(SurakshaLog.HOSPITAL, "registration location permission", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fillCurrentLocation();
        }
    }

    private static Long parseNonNegativeLong(String s) {
        try {
            long v = Long.parseLong(s.trim());
            return v >= 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void handleRegistration() {
        String name = etHospitalName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String license = etLicenseNumber.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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
        Long totalBeds = parseNonNegativeLong(etTotalBeds.getText().toString());
        if (totalBeds == null) {
            etTotalBeds.setError("Enter a valid number of beds");
            return;
        }
        Long totalAmbulances = parseNonNegativeLong(etTotalAmbulances.getText().toString());
        if (totalAmbulances == null) {
            etTotalAmbulances.setError("Enter a valid number of ambulances");
            return;
        }
        Double latitude = parseDouble(etLatitude.getText().toString());
        Double longitude = parseDouble(etLongitude.getText().toString());
        if (!HaversineDistance.isValidCoordinate(latitude, longitude)) {
            etLatitude.setError("Enter valid coordinates or tap 'Use my current location'");
            return;
        }

        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        saveHospitalToFirestore(mAuth.getCurrentUser(), name, email, phone, address, license,
                                totalBeds, totalAmbulances, latitude, longitude);
                        return;
                    }
                    setLoading(false);
                    Exception e = task.getException();
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        SurakshaLog.info(SurakshaLog.AUTH, "hospital registration blocked: email exists");
                        Toast.makeText(this, RegistrationActivity.MSG_EMAIL_EXISTS, Toast.LENGTH_LONG).show();
                    } else {
                        SurakshaLog.error(SurakshaLog.AUTH, "createUserWithEmailAndPassword(hospital)", e);
                        Toast.makeText(this, e != null ? e.getMessage() : "Registration failed", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (btnRegister != null) btnRegister.setEnabled(!isLoading);
        if (progressBar != null) progressBar.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void saveHospitalToFirestore(FirebaseUser firebaseUser, String name, String email, String phone,
                                         String address, String license, long beds, long ambulances,
                                         double latitude, double longitude) {
        String userId = firebaseUser.getUid();
        Map<String, Object> hospital = new HashMap<>();
        hospital.put("uid", userId);
        hospital.put("hospitalName", name);
        hospital.put("email", email);
        hospital.put("phone", phone);
        hospital.put("address", address);
        hospital.put("licenseNumber", license);
        hospital.put("totalBeds", beds);
        hospital.put("availableBeds", beds);
        hospital.put("totalAmbulances", ambulances);
        hospital.put("availableAmbulances", ambulances);
        hospital.put("latitude", latitude);
        hospital.put("longitude", longitude);
        hospital.put("role", "hospital");
        hospital.put("isVerified", true);
        hospital.put("approvalStatus", "approved");
        hospital.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(userId)
                .set(hospital)
                .addOnSuccessListener(aVoid -> {
                    SurakshaLog.info(SurakshaLog.AUTH, "hospital registered & approved", "hospitalId=" + userId);
                    setLoading(false);
                    Toast.makeText(this, "Hospital registered and verified! Logging in...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, HospitalActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AUTH, "create users/{uid} (hospital)", e, "hospitalId=" + userId);
                    firebaseUser.delete().addOnCompleteListener(t -> {
                        if (!t.isSuccessful()) {
                            SurakshaLog.error(SurakshaLog.AUTH, "rollback hospital auth user", t.getException(), "hospitalId=" + userId);
                        }
                        mAuth.signOut();
                    });
                    setLoading(false);
                    Toast.makeText(this, SurakshaLog.userMessage("Database error", e), Toast.LENGTH_LONG).show();
                });
    }
}
