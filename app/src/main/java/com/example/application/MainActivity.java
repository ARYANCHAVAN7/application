package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText etEmail = findViewById(R.id.etEmail);
        final EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.button2);
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
                    Toast.makeText(MainActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                }
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