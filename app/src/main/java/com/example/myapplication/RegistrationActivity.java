package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import com.example.application.MainActivity;
import com.example.application.R;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

public class RegistrationActivity extends AppCompatActivity {

    private EditText inputFullName, inputEmail, inputPhone, inputPassword,
            inputConfirmPassword, inputBloodGroup, inputEmergencyName, inputEmergencyPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_registration);

        bindViews();

        TextView btnRegister = findViewById(R.id.btnRegister);
        TextView goToLogin = findViewById(R.id.goToLogin);

        btnRegister.setOnClickListener(v -> handleRegister());

        goToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
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

    private void handleRegister() {
        String fullName = inputFullName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String phone = inputPhone.getText().toString().trim();
        String password = inputPassword.getText().toString();
        String confirmPassword = inputConfirmPassword.getText().toString();
        String bloodGroup = inputBloodGroup.getText().toString().trim();
        String emergencyName = inputEmergencyName.getText().toString().trim();
        String emergencyPhone = inputEmergencyPhone.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            inputFullName.setError(getString(R.string.error_full_name_required));
            return;
        }
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputEmail.setError(getString(R.string.error_invalid_email));
            return;
        }
        if (TextUtils.isEmpty(phone) || phone.length() != 10) {
            inputPhone.setError(getString(R.string.error_invalid_phone));
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            inputPassword.setError(getString(R.string.error_password_length));
            return;
        }
        if (!password.equals(confirmPassword)) {
            inputConfirmPassword.setError(getString(R.string.error_password_mismatch));
            return;
        }
        if (TextUtils.isEmpty(bloodGroup)) {
            inputBloodGroup.setError(getString(R.string.error_blood_group_required));
            return;
        }
        if (TextUtils.isEmpty(emergencyName)) {
            inputEmergencyName.setError(getString(R.string.error_emergency_name_required));
            return;
        }
        if (TextUtils.isEmpty(emergencyPhone) || emergencyPhone.length() != 10) {
            inputEmergencyPhone.setError(getString(R.string.error_emergency_phone_invalid));
            return;
        }

        Toast.makeText(this, getString(R.string.msg_registration_success), Toast.LENGTH_LONG).show();

        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}