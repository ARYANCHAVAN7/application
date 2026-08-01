package com.example.application;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class HospitalRegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_registration);

        Button btnRegister = findViewById(R.id.btnRegister);
        if (btnRegister != null) {
            btnRegister.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Logic to handle registration (not implemented yet)
                    Toast.makeText(HospitalRegisterActivity.this, "Hospital Registered Successfully!", Toast.LENGTH_LONG).show();
                    
                    // Return to the previous screen (MainActivity)
                    finish();
                }
            });
        }

        TextView tvGoToLogin = findViewById(R.id.tvGoToLogin);
        if (tvGoToLogin != null) {
            tvGoToLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Return to the previous screen (MainActivity)
                    finish();
                }
            });
        }
    }
}
