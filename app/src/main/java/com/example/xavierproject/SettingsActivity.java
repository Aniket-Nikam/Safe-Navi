package com.example.xavierproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import de.hdodenhof.circleimageview.CircleImageView;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    private CircleImageView profileImageView;
    private TextView usernameTextView, emailTextView;
    private SwitchCompat notificationsSwitch, darkModeSwitch;
    private Button changePasswordButton, deleteAccountButton, logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Settings");

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("users");

        initializeViews();
        loadUserData();
        setupClickListeners();
    }

    private void initializeViews() {
        profileImageView = findViewById(R.id.profileImageView);
        usernameTextView = findViewById(R.id.usernameTextView);
        emailTextView = findViewById(R.id.emailTextView);
        notificationsSwitch = findViewById(R.id.notificationsSwitch);
        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        deleteAccountButton = findViewById(R.id.deleteAccountButton);
        logoutButton = findViewById(R.id.logoutButton);
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            usernameTextView.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
            emailTextView.setText(user.getEmail());
        }
    }

    private void setupClickListeners() {
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveNotificationPreference(isChecked);
        });

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        changePasswordButton.setOnClickListener(v -> changePassword());
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());
        logoutButton.setOnClickListener(v -> logout());
    }

    private void saveNotificationPreference(boolean enabled) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            usersRef.child(user.getUid())
                    .child("notificationsEnabled").setValue(enabled)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this,
                                "Notification preference updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this,
                                "Failed to update preference", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void changePassword() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            mAuth.sendPasswordResetEmail(user.getEmail())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(SettingsActivity.this,
                                    "Password reset email sent", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(SettingsActivity.this,
                                    "Failed to send reset email", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();

            // Delete user data from Realtime Database
            usersRef.child(userId)
                    .removeValue()
                    .addOnSuccessListener(aVoid -> {
                        // Delete Firebase Auth account
                        user.delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(SettingsActivity.this,
                                                "Account deleted successfully", Toast.LENGTH_SHORT).show();
                                        navigateToLogin();
                                    } else {
                                        Toast.makeText(SettingsActivity.this,
                                                "Failed to delete account. Please re-login and try again",
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(SettingsActivity.this,
                                "Failed to delete user data", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void logout() {
        mAuth.signOut();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SettingsActivity.this, LoginTypeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
