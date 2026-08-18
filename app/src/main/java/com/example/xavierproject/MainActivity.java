package com.example.xavierproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.example.xavierproject.safety.demo.DemoSession;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigationView;
    private FrameLayout fragmentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!DemoSession.isActive() && BuildConfig.HAS_FIREBASE_CONFIG) {
            mAuth = FirebaseAuth.getInstance();
        }

        initializeViews();
        setupBottomNavigation();

        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
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
                    selectedFragment = DemoSession.isActive() ? new DemoReportFragment() : new ReportFragment();
                } else if (itemId == R.id.nav_community) {
                    if (DemoSession.isActive()) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Community demo")
                                .setMessage("The full community feature was migrated from XavierProject and uses Firebase. Add google-services.json to connect live posts and comments.")
                                .setPositiveButton("Understood", null)
                                .show();
                    } else {
                        startActivity(new Intent(MainActivity.this, DiscussionActivity.class));
                    }
                    return false;
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
}
