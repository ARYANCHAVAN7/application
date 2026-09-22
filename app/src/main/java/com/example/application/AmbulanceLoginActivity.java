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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;

public class AmbulanceLoginActivity extends AppCompatActivity {

    private EditText inputHospitalName, inputSecurityNumber;
    private View loginCard;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulance_login);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        loginCard = findViewById(R.id.loginCard);
        inputHospitalName = findViewById(R.id.inputHospitalName);
        inputSecurityNumber = findViewById(R.id.inputSecurityNumber);

        TextView btnLogin = findViewById(R.id.btnAmbulanceLogin);

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String hospitalName = inputHospitalName.getText().toString().trim();
        String securityNumber = inputSecurityNumber.getText().toString().trim();

        if (auth.getCurrentUser() != null) {
            loadAuthenticatedDriver(auth.getCurrentUser().getUid());
            return;
        }

        if (TextUtils.isEmpty(hospitalName) || TextUtils.isEmpty(securityNumber)) {
            if (loginCard != null) {
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
            }
            return;
        }

        // Legacy ambulance records use hospital name and security number.
        db.collection("ambulances")
                .whereEqualTo("hospitalName", hospitalName)
                .whereEqualTo("securityNumber", securityNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot ambulance = task.getResult().getDocuments().get(0);
                        openDashboard(ambulance, hospitalName, securityNumber, null);
                    } else if (task.getException() != null) {
                        showLoginError(task.getException());
                    } else {
                        showInvalidCredentials();
                    }
                });
    }

    private void loadAuthenticatedDriver(String uid) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        Log.d("AMB_DEBUG", "AUTH USER = " + user);
        Log.d("AMB_DEBUG", "AUTH UID = " +
            (user != null ? user.getUid() : "NULL"));

        FirebaseApp app = FirebaseApp.getInstance();
        Log.d("AMB_DEBUG", "FIREBASE PROJECT = " +
            app.getOptions().getProjectId());

        db.collection("ambulance_drivers").document(uid).get()
                .addOnSuccessListener(driver -> {
                    if (!driver.exists()) {
                        Toast.makeText(this, "Ambulance driver profile was not found for this account.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String ambulanceId = firstNonBlank(driver.getString("ambulanceId"), driver.getString("ambulance_id"));
                    if (ambulanceId == null) {
                        Toast.makeText(this, "Driver profile has no assigned ambulance.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    db.collection("ambulances").document(ambulanceId).get()
                            .addOnSuccessListener(ambulance -> {
                                if (!ambulance.exists()) {
                                    Toast.makeText(this, "Assigned ambulance record was not found.", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                openDashboard(ambulance, ambulance.getString("hospitalName"), ambulance.getString("securityNumber"), uid);
                            })
                            .addOnFailureListener(this::showLoginError);
                })
                .addOnFailureListener(this::showLoginError);
    }

    private void openDashboard(DocumentSnapshot ambulance, String hospitalName, String securityNumber, String driverUid) {
        String ambulanceId = ambulance.getId();
        if (ambulanceId.trim().isEmpty()) {
            Toast.makeText(this, "Ambulance record is invalid.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Driver Login Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, AmbulanceActivity.class);
        intent.putExtra("ambulanceId", ambulanceId);
        intent.putExtra("hospitalName", hospitalName);
        intent.putExtra("securityNumber", securityNumber);
        intent.putExtra("driverUid", driverUid);
        startActivity(intent);
        finish();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return null;
    }

    private void showInvalidCredentials() {
        if (loginCard != null) {
            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
            loginCard.startAnimation(shake);
        }
        Toast.makeText(this, "Invalid ambulance credentials.", Toast.LENGTH_LONG).show();
    }

    private void showLoginError(Exception exception) {
        String message = exception instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) exception).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ? "Permission denied while loading ambulance profile."
                : "Unable to load ambulance profile. Check your network and try again.";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
