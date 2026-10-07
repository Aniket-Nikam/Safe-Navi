package com.safenavi.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.view.MenuItem;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.safenavi.app.product.SafetySyncWorker;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FrameLayout fragmentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupBottomNavigation();
        SafetySyncWorker.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 902);

        // Load home fragment by default
        if (savedInstanceState == null) {
            if (getIntent().hasExtra("reportLatitude") && getIntent().hasExtra("reportLongitude")) {
                ReportFragment report = new ReportFragment();
                Bundle arguments = new Bundle();
                arguments.putDouble("latitude", getIntent().getDoubleExtra("reportLatitude", 0));
                arguments.putDouble("longitude", getIntent().getDoubleExtra("reportLongitude", 0));
                report.setArguments(arguments);
                loadFragment(report);
                bottomNavigationView.setSelectedItemId(R.id.nav_report);
            } else {
                loadFragment(new HomeFragment());
            }
        }
    }

    private void initializeViews() {
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        fragmentContainer = findViewById(R.id.fragmentContainer);
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                int itemId = item.getItemId();

                if (itemId == R.id.nav_home) {
                    selectedFragment = new HomeFragment();
                } else if (itemId == R.id.nav_map) {
                    startActivity(new Intent(MainActivity.this, MapsActivity.class));
                    return false;
                } else if (itemId == R.id.nav_report) {
                    selectedFragment = new ReportFragment();
                } else if (itemId == R.id.nav_community) {
                    selectedFragment = new CommunityFragment();
                } else if (itemId == R.id.nav_profile) {
                    selectedFragment = new SettingsFragment();
                }

                if (selectedFragment != null) {
                    loadFragment(selectedFragment);
                    return true;
                }
                return false;
            }
        });
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    public void openReport() {
        bottomNavigationView.setSelectedItemId(R.id.nav_report);
    }

    public void openCommunity() {
        bottomNavigationView.setSelectedItemId(R.id.nav_community);
    }

    public void openAssistant() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new ChatbotFragment())
                .addToBackStack("assistant")
                .commit();
    }
}
