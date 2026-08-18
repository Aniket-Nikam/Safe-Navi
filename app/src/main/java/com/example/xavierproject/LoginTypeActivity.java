package com.example.xavierproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.example.xavierproject.safety.demo.DemoSession;
import com.example.xavierproject.safety.model.UserRole;

public class LoginTypeActivity extends AppCompatActivity {

    private MaterialCardView userLoginCard, adminLoginCard, govLoginCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_type);

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        userLoginCard = findViewById(R.id.userLoginCard);
        adminLoginCard = findViewById(R.id.adminLoginCard);
        govLoginCard = findViewById(R.id.govLoginCard);
    }

    private void setupClickListeners() {
        userLoginCard.setOnClickListener(v -> openLogin("user"));
        adminLoginCard.setOnClickListener(v -> openLogin("admin"));
        govLoginCard.setOnClickListener(v -> openLogin("government"));
        findViewById(R.id.citizenDemoButton).setOnClickListener(v -> {
            DemoSession.start(UserRole.CITIZEN);
            startActivity(new Intent(this, MainActivity.class));
        });
        findViewById(R.id.governmentDemoButton).setOnClickListener(v -> {
            DemoSession.start(UserRole.GOVERNMENT);
            startActivity(new Intent(this, GovernmentSafetyDashboardActivity.class));
        });
    }

    private void openLogin(String loginType) {
        Intent intent = new Intent(LoginTypeActivity.this, LoginActivity.class);
        intent.putExtra("LOGIN_TYPE", loginType);
        startActivity(intent);
    }
}
