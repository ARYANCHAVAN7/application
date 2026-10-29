package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.application.utils.SurakshaLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistrationActivity extends AppCompatActivity {

    static final String MSG_EMAIL_EXISTS = "Email already registered. Please login or use another email.";

    private EditText inputFullName, inputEmail, inputPhone, inputPassword,
            inputConfirmPassword, inputBloodGroup, inputEmergencyName, inputEmergencyPhone;
    private TextView btnRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean submitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();

        btnRegister = findViewById(R.id.btnRegister);
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

    private void setSubmitting(boolean value) {
        submitting = value;
        btnRegister.setEnabled(!value);
        btnRegister.setAlpha(value ? 0.5f : 1f);
    }

    private void handleRegister() {
        if (submitting) return;

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

        setSubmitting(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        saveUserToFirestore(mAuth.getCurrentUser(), fullName, email, phone,
                                bloodGroup, emergencyName, emergencyPhone);
                        return;
                    }
                    setSubmitting(false);
                    Exception e = task.getException();
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        // Never sign in and overwrite an existing account's profile/role.
                        SurakshaLog.info(SurakshaLog.AUTH, "user registration blocked: email exists");
                        Toast.makeText(this, MSG_EMAIL_EXISTS, Toast.LENGTH_LONG).show();
                    } else {
                        SurakshaLog.error(SurakshaLog.AUTH, "createUserWithEmailAndPassword(user)", e);
                        Toast.makeText(this, "Registration failed: "
                                + (e != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser firebaseUser, String name, String email, String phone,
                                     String blood, String eName, String ePhone) {
        String userId = firebaseUser.getUid();
        Map<String, Object> user = new HashMap<>();
        user.put("uid", userId);
        user.put("name", name);
        user.put("fullName", name); // existing readers use fullName
        user.put("email", email);
        user.put("phone", phone);
        user.put("bloodGroup", blood);
        user.put("emergencyName", eName);
        user.put("emergencyPhone", ePhone);
        user.put("role", "user");
        user.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    SurakshaLog.info(SurakshaLog.AUTH, "user profile created", "uid=" + userId);
                    Toast.makeText(RegistrationActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegistrationActivity.this, DashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AUTH, "create users/{uid} (user)", e, "uid=" + userId);
                    // Roll back the Auth account so the email can be registered again cleanly.
                    firebaseUser.delete().addOnCompleteListener(t -> {
                        if (!t.isSuccessful()) {
                            SurakshaLog.error(SurakshaLog.AUTH, "rollback auth user", t.getException(), "uid=" + userId);
                        }
                        mAuth.signOut();
                    });
                    setSubmitting(false);
                    Toast.makeText(RegistrationActivity.this,
                            SurakshaLog.userMessage("Could not save your profile. Please try again", e),
                            Toast.LENGTH_LONG).show();
                });
    }
}
