package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AmbulanceLoginActivity extends AppCompatActivity {

    private static final String TAG = "AmbulanceLogin";

    private EditText inputEmail, inputPassword;
    private View loginCard;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulance_login);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loginCard = findViewById(R.id.loginCard);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);

        TextView btnLogin = findViewById(R.id.btnAmbulanceLogin);

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            if (loginCard != null) {
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
            }
            return;
        }

        mAuth.signOut();
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> loadAmbulanceProfile(authResult.getUser()))
                .addOnFailureListener(error -> {
                    Log.e(TAG, error.getClass().getName() + ": " + error.getMessage(), error);
                    showLoginError("Invalid email or password.");
                });
    }

    private void loadAmbulanceProfile(FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            showLoginError("Unable to identify the ambulance account.");
            return;
        }

        db.collection("ambulances").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        showLoginError("Ambulance profile not found.");
                        return;
                    }

                    String role = document.getString("role");
                    if (!"ambulance".equals(role)) {
                        showLoginError("This account is not registered as an ambulance.");
                        return;
                    }

                    Boolean isApproved = document.getBoolean("isApproved");
                    if (!Boolean.TRUE.equals(isApproved)) {
                        showLoginError("Ambulance account is awaiting approval.");
                        return;
                    }

                    Boolean isActive = document.getBoolean("isActive");
                    if (Boolean.FALSE.equals(isActive)) {
                        showLoginError("Ambulance account is inactive.");
                        return;
                    }

                    String hospitalName = document.getString("hospitalName");
                    String securityNumber = document.getString("securityNumber");
                    if (TextUtils.isEmpty(hospitalName) || TextUtils.isEmpty(securityNumber)) {
                        showLoginError("Ambulance profile is incomplete.");
                        return;
                    }

                    Intent intent = new Intent(this, AmbulanceActivity.class);
                    intent.putExtra("hospitalName", hospitalName);
                    intent.putExtra("securityNumber", securityNumber);
                    intent.putExtra("hospitalId", document.getString("hospitalId"));
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, error.getClass().getName() + ": " + error.getMessage(), error);
                    if (error instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) error).getCode()
                            == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        showLoginError("Ambulance profile access is not permitted.");
                    } else {
                        showLoginError("Unable to load the ambulance profile.");
                    }
                });
    }

    private void showLoginError(String message) {
        mAuth.signOut();
        if (loginCard != null) {
            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
            loginCard.startAnimation(shake);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
