package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LoadingActivity extends AppCompatActivity {

    private Handler delayHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_page);

        View loadingCard = findViewById(R.id.loadingCard);
        TextView loadingLogo = findViewById(R.id.loading_logo);

        // 1. Entrance Animation for the card
        Animation entranceAnim = AnimationUtils.loadAnimation(this, R.anim.card_entrance);
        loadingCard.startAnimation(entranceAnim);

        // 2. Pulsing Animation for the logo
        Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);
        loadingLogo.startAnimation(pulseAnim);

        // Delay for 2.5 seconds then transition to StartingActivity
        delayHandler = new Handler(Looper.getMainLooper());
        delayHandler.postDelayed(() -> {
            Intent intent = new Intent(LoadingActivity.this, StartingActivity.class);
            startActivity(intent);
            finish(); // Close LoadingActivity
            // Add a smooth transition out
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 2500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the handler to prevent memory leaks
        if (delayHandler != null) {
            delayHandler.removeCallbacksAndMessages(null);
            delayHandler = null;
        }
    }
}
