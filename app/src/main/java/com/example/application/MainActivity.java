package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CardView loginCard = findViewById(R.id.loginCard);
        final EditText etEmail = findViewById(R.id.etEmail);
        final EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.button2);
        Button btnGoogleLogin = findViewById(R.id.button3);
        Button btnSignUp = findViewById(R.id.button);

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                String email = etEmail != null ? etEmail.getText().toString() : "";
                String password = etPassword != null ? etPassword.getText().toString() : "";

                // For demonstration, we'll allow login if fields are not empty
                if (!email.isEmpty() && !password.isEmpty()) {
                    Intent intent = new Intent(MainActivity.this, HospitalActivity.class);
                    startActivity(intent);
                } else {
                    if (loginCard != null) {
                        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                        loginCard.startAnimation(shake);
                    }
                    Toast.makeText(MainActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> {
                // Shake effect for feedback
                if (loginCard != null) {
                    Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                    loginCard.startAnimation(shake);
                }

                // Simulated Account Selection Options
                String[] accounts = {"hospital_admin@suraksha.com", "sevenstar_staff@gmail.com", "Add another account"};
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Select Hospital Account");
                builder.setItems(accounts, (dialog, which) -> {
                    if (which < 2) {
                        Toast.makeText(MainActivity.this, "Signing in with " + accounts[which], Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(MainActivity.this, HospitalActivity.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "Redirecting to Google Sign-In...", Toast.LENGTH_SHORT).show();
                    }
                });
                builder.show();
            });
        }

        if (btnSignUp != null) {
            btnSignUp.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HospitalRegisterActivity.class);
                startActivity(intent);
            });
        }
    }
}