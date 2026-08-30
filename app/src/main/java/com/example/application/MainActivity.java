package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

        CardView loginCard = findViewById(R.id.loginCard);
        final EditText etEmail = findViewById(R.id.etEmail);
        final EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.button2);
        Button btnGoogleLogin = findViewById(R.id.button3);
        Button btnSignUp = findViewById(R.id.button);

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                String email = etEmail != null ? etEmail.getText().toString().trim() : "";
                String password = etPassword != null ? etPassword.getText().toString() : "";

                if (!email.isEmpty() && !password.isEmpty()) {
                    mAuth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, task -> {
                                if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                                    checkHospitalRole(mAuth.getCurrentUser().getUid());
                                } else {
                                    if (loginCard != null) {
                                        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                                        loginCard.startAnimation(shake);
                                    }
                                    
                                    String errorMsg = "Login failed";
                                    if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                                        errorMsg = "Hospital account not found. Please register.";
                                    } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                        errorMsg = "Incorrect password. Please try again.";
                                    } else if (task.getException() != null) {
                                        errorMsg = task.getException().getMessage();
                                    }
                                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                }
                            });
                } else {
                    if (loginCard != null) {
                        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                        loginCard.startAnimation(shake);
                    }
                    Toast.makeText(MainActivity.this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> performGoogleSignIn());
        }

        if (btnSignUp != null) {
            btnSignUp.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HospitalRegisterActivity.class);
                startActivity(intent);
            });
        }
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
                    String idToken = googleIdTokenCredential.getIdToken();
                    firebaseAuthWithGoogle(idToken);
                }
            }

            @Override
            public void onError(GetCredentialException e) {
                Log.e(TAG, "Credential Manager Error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        checkHospitalRole(mAuth.getCurrentUser().getUid());
                    } else {
                        Toast.makeText(MainActivity.this, "Firebase Auth failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkHospitalRole(String userId) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        
                        if (!document.exists()) {
                            mAuth.signOut();
                            Toast.makeText(MainActivity.this, "Hospital profile not found. Try registering again.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String role = document.getString("role");
                        if (role != null) role = role.trim();
                        
                        boolean isVerified = false;
                        if (document.contains("isVerified")) {
                            Object val = document.get("isVerified");
                            if (val instanceof Boolean) isVerified = (Boolean) val;
                            else if (val instanceof String) isVerified = "true".equalsIgnoreCase((String) val);
                        }
                        
                        if ("hospital".equals(role)) {
                            if (isVerified) {
                                Toast.makeText(MainActivity.this, "Hospital Login Successful!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(MainActivity.this, HospitalActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                mAuth.signOut();
                                Toast.makeText(MainActivity.this, "Account pending verification. Please contact admin to verify License: " + document.getString("licenseNumber"), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            mAuth.signOut();
                            Toast.makeText(MainActivity.this, "Access Denied: Registered as " + role + ". Use the User portal.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        mAuth.signOut();
                        Toast.makeText(MainActivity.this, "Failed to verify account role", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
