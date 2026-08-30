package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class UserLoginActivity extends AppCompatActivity {

    private static final String TAG = "UserLoginActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

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
            btnGoogleLogin.setOnClickListener(v -> performGoogleSignIn());
        }

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegistrationActivity.class);
            startActivity(intent);
        });
    }

    private void performGoogleSignIn() {
        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (webClientId == null || webClientId.trim().isEmpty() || webClientId.contains("YOUR_")) {
            Toast.makeText(this,
                    "Google Web Client ID is missing. Add GOOGLE_WEB_CLIENT_ID from Firebase Console > Authentication > Sign-in method > Google and set it in local.properties.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(this, request, null, Runnable::run, new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse response) {
                if (response.getCredential() instanceof GoogleIdTokenCredential) {
                    GoogleIdTokenCredential googleIdTokenCredential = (GoogleIdTokenCredential) response.getCredential();
                    firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
                }
            }

            @Override
            public void onError(GetCredentialException e) {
                Log.e(TAG, "Credential Manager Error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(UserLoginActivity.this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String userId = mAuth.getCurrentUser().getUid();
                        // For Google login, if profile doesn't exist, create it automatically
                        db.collection("users").document(userId).get().addOnCompleteListener(dbTask -> {
                            if (dbTask.isSuccessful() && dbTask.getResult() != null && !dbTask.getResult().exists()) {
                                createBasicUserProfile(userId, mAuth.getCurrentUser().getDisplayName(), mAuth.getCurrentUser().getEmail());
                            } else {
                                checkUserRole(userId);
                            }
                        });
                    } else {
                        Toast.makeText(UserLoginActivity.this, "Firebase Auth failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createBasicUserProfile(String userId, String name, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", name != null ? name : "Google User");
        user.put("email", email);
        user.put("role", "user");
        user.put("phone", "");
        user.put("bloodGroup", "");
        user.put("emergencyName", "");
        user.put("emergencyPhone", "");

        db.collection("users").document(userId).set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile created for " + email, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(UserLoginActivity.this, DashboardActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
