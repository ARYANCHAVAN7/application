package com.example.myapplication;

import com.example.application.R;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.application.R;

import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private CountDownTimer holdTimer;
    private final long holdDurationMs = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dashboard);

        FrameLayout sosButton = findViewById(R.id.sosButton);
        TextView callNowBtn = findViewById(R.id.callNowBtn);

        // Press-and-hold for 2 seconds to trigger SOS
        sosButton.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startHoldCountdown();
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHoldCountdown();
                    return true;
                default:
                    return false;
            }
        });

        // Call Now button dials the emergency contact number
        callNowBtn.setOnClickListener(v -> {
            String phoneNumber = "6491050867";
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
            startActivity(intent);
        });
    }

    private void startHoldCountdown() {
        holdTimer = new CountDownTimer(holdDurationMs, holdDurationMs) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                triggerSos();
            }
        }.start();
    }

    private void cancelHoldCountdown() {
        if (holdTimer != null) {
            holdTimer.cancel();
            holdTimer = null;
        }
    }

    private void triggerSos() {
        Toast.makeText(this, "SOS Triggered! Connecting to nearest hospital…", Toast.LENGTH_LONG).show();
    }
}