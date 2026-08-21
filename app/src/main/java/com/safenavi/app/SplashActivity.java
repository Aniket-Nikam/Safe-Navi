package com.safenavi.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2000; // 2 seconds
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (BuildConfig.HAS_FIREBASE_CONFIG) {
            mAuth = FirebaseAuth.getInstance();
        }
        
        android.view.View contentLayout = findViewById(R.id.splashContentLayout);
        if (contentLayout != null) {
            contentLayout.setTranslationY(50f);
            contentLayout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(800)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        new Handler().postDelayed(() -> {
            FirebaseUser currentUser = mAuth == null ? null : mAuth.getCurrentUser();
            Intent intent;

            if (currentUser != null) {
                // User is signed in, go to MainActivity
                intent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                // No user signed in, go to LoginActivity
                intent = new Intent(SplashActivity.this, LoginTypeActivity.class);
            }

            startActivity(intent);
            finish();
        }, SPLASH_DURATION);
    }
}
