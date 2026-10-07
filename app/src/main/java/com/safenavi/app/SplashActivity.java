package com.safenavi.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.safenavi.app.product.ProductSession;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 1400;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

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
            Intent intent;
            if (ProductSession.isSignedIn(this)) {
                String role = ProductSession.role(this);
                intent = new Intent(this, role.equals("government") || role.equals("admin")
                        ? GovernmentSafetyDashboardActivity.class : MainActivity.class);
            } else {
                intent = new Intent(this, LoginTypeActivity.class);
            }

            startActivity(intent);
            finish();
        }, SPLASH_DURATION);
    }
}
