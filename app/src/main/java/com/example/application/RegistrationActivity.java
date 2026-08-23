package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistrationActivity extends AppCompatActivity {

    private EditText inputFullName, inputEmail, inputPhone, inputPassword,
            inputConfirmPassword, inputBloodGroup, inputEmergencyName, inputEmergencyPhone;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();

        TextView btnRegister = findViewById(R.id.btnRegister);
        TextView goToLogin = findViewById(R.id.goToLogin);

        btnRegister.setOnClickListener(v -> handleRegister());

        goToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, UserLoginActivity.class));
            finish();
        });
    }

    private void bindViews() {
        inputFullName = findViewById(R.id.inputFullName);
        inputEmail = findViewById(R.id.inputEmail);
        inputPhone = findViewById(R.id.inputPhone);
        inputPassword = findViewById(R.id.inputPassword);
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword);
        inputBloodGroup = findViewById(R.id.inputBloodGroup);
        inputEmergencyName = findViewById(R.id.inputEmergencyName);
        inputEmergencyPhone = findViewById(R.id.inputEmergencyPhone);
    }

    private void handleRegister() {
        String fullName = inputFullName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String phone = inputPhone.getText().toString().trim();
        String password = inputPassword.getText().toString();
        String confirmPassword = inputConfirmPassword.getText().toString();
        String bloodGroup = inputBloodGroup.getText().toString().trim();
        String emergencyName = inputEmergencyName.getText().toString().trim();
        String emergencyPhone = inputEmergencyPhone.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            inputFullName.setError("Full name is required");
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputEmail.setError("Valid email is required");
            return;
        }
        if (TextUtils.isEmpty(phone) || phone.length() != 10) {
            inputPhone.setError("10-digit phone number is required");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            inputPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            inputConfirmPassword.setError("Passwords do not match");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        saveUserToFirestore(userId, fullName, email, phone, bloodGroup, emergencyName, emergencyPhone);
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            // Email already exists. Let's see if we can log in and fix the profile.
                            Toast.makeText(this, "Email already registered. Checking if profile needs fix...", Toast.LENGTH_SHORT).show();
                            repairExistingAccount(email, password, fullName, phone, bloodGroup, emergencyName, emergencyPhone);
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(RegistrationActivity.this, "Registration failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void repairExistingAccount(String email, String password, String name, String phone, 
                                        String blood, String eName, String ePhone) {
        // Try to sign in. If successful, it means the password is correct but the profile might be missing.
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        saveUserToFirestore(userId, name, email, phone, blood, eName, ePhone);
                    } else {
                        Toast.makeText(this, "Email is in use, but password doesn't match. Please use a different email.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String name, String email, String phone,
                                     String blood, String eName, String ePhone) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", name);
        user.put("email", email);
        user.put("phone", phone);
        user.put("bloodGroup", blood);
        user.put("emergencyName", eName);
        user.put("emergencyPhone", ePhone);
        user.put("role", "user");

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RegistrationActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegistrationActivity.this, DashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    String error = e.getMessage() != null ? e.getMessage() : "Unknown Firestore error";
                    Toast.makeText(RegistrationActivity.this, "Data save failed: " + error, Toast.LENGTH_LONG).show();
                });
    }
}
