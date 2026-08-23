package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class AmbulanceLoginActivity extends AppCompatActivity {

    private EditText inputHospitalName, inputSecurityNumber;
    private View loginCard;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulance_login);

        db = FirebaseFirestore.getInstance();

        loginCard = findViewById(R.id.loginCard);
        inputHospitalName = findViewById(R.id.inputHospitalName);
        inputSecurityNumber = findViewById(R.id.inputSecurityNumber);

        TextView btnLogin = findViewById(R.id.btnAmbulanceLogin);

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String hospitalName = inputHospitalName.getText().toString().trim();
        String securityNumber = inputSecurityNumber.getText().toString().trim();

        if (TextUtils.isEmpty(hospitalName) || TextUtils.isEmpty(securityNumber)) {
            if (loginCard != null) {
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                loginCard.startAnimation(shake);
            }
            return;
        }

        // Real Firestore Check
        db.collection("ambulances")
                .whereEqualTo("hospitalName", hospitalName)
                .whereEqualTo("securityNumber", securityNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        Toast.makeText(this, "Driver Login Successful!", Toast.LENGTH_SHORT).show();
                        
                        Intent intent = new Intent(this, AmbulanceActivity.class);
                        intent.putExtra("hospitalName", hospitalName);
                        intent.putExtra("securityNumber", securityNumber);
                        startActivity(intent);
                        finish();
                    } else {
                        if (loginCard != null) {
                            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                            loginCard.startAnimation(shake);
                        }
                        Toast.makeText(this, "Invalid credentials for this hospital", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
