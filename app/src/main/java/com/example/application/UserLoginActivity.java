package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class UserLoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        CardView loginCard = findViewById(R.id.loginCard);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn = findViewById(R.id.btnSignIn);
        Button btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        // Entrance Animation
        Animation entrance = AnimationUtils.loadAnimation(this, R.anim.card_entrance);
        loginCard.startAnimation(entrance);

        btnSignIn.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                            String userId = mAuth.getCurrentUser().getUid();
                            checkUserRole(userId);
                        } else {
                            Animation shake = AnimationUtils.loadAnimation(UserLoginActivity.this, R.anim.shake);
                            loginCard.startAnimation(shake);
                            
                            String errorMsg = "Login failed";
                            if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                                errorMsg = "Account not found. Please register first.";
                            } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                errorMsg = "Incorrect password. Please try again.";
                            } else if (task.getException() != null) {
                                errorMsg = task.getException().getMessage();
                            }
                            Toast.makeText(UserLoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> {
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
                Toast.makeText(this, "Google Sign-In coming soon!", Toast.LENGTH_SHORT).show();
            });
        }

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegistrationActivity.class);
            startActivity(intent);
        });
    }

    private void checkUserRole(String userId) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        
                        if (!document.exists()) {
                            mAuth.signOut();
                            Toast.makeText(UserLoginActivity.this, "Profile not found in database. Try registering again with this email.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String role = document.getString("role");
                        if (role != null) role = role.trim();
                        if ("user".equals(role) || "admin".equals(role)) {
                            Toast.makeText(UserLoginActivity.this, getString(R.string.msg_login_success), Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(UserLoginActivity.this, DashboardActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            mAuth.signOut();
                            Toast.makeText(UserLoginActivity.this, "Access Denied: This is a User-only portal. You are registered as: " + role, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        mAuth.signOut();
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown verification error";
                        Toast.makeText(UserLoginActivity.this, "Verification failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
