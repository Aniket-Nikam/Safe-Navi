package com.safenavi.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.safenavi.app.safety.demo.DemoSession;
import de.hdodenhof.circleimageview.CircleImageView;

public class SettingsFragment extends Fragment {

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private SharedPreferences sharedPreferences;

    private CircleImageView profileImageView;
    private TextView usernameTextView, emailTextView, verificationStatusTextView;
    private SwitchCompat notificationsSwitch, darkModeSwitch;
    private LinearLayout changePasswordLayout, deleteAccountLayout, logoutLayout;
    private MaterialCardView verificationCard;
    private boolean firebaseEnabled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        firebaseEnabled = !DemoSession.isActive() && BuildConfig.HAS_FIREBASE_CONFIG
                && !BuildConfig.FIREBASE_DATABASE_URL.trim().isEmpty();
        if (firebaseEnabled) {
            mAuth = FirebaseAuth.getInstance();
            usersRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
                    .getReference("users");
        }
        sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", 0);

        initializeViews(view);
        loadUserData();
        loadPreferences();
        setupClickListeners();

        return view;
    }

    private void initializeViews(View view) {
        profileImageView = view.findViewById(R.id.profileImageView);
        usernameTextView = view.findViewById(R.id.usernameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);
        verificationStatusTextView = view.findViewById(R.id.verificationStatusTextView);
        verificationCard = view.findViewById(R.id.verificationCard);
        notificationsSwitch = view.findViewById(R.id.notificationsSwitch);
        darkModeSwitch = view.findViewById(R.id.darkModeSwitch);
        changePasswordLayout = view.findViewById(R.id.changePasswordLayout);
        deleteAccountLayout = view.findViewById(R.id.deleteAccountLayout);
        logoutLayout = view.findViewById(R.id.logoutLayout);
    }

    private void loadUserData() {
        if (DemoSession.isActive()) {
            usernameTextView.setText("Citizen demo");
            emailTextView.setText("Synthetic classroom profile");
            verificationCard.setVisibility(View.GONE);
            return;
        }
        if (!firebaseEnabled || mAuth == null) {
            usernameTextView.setText("Local prototype");
            emailTextView.setText("Connect Firebase in a later phase");
            verificationCard.setVisibility(View.GONE);
            return;
        }
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            usernameTextView.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
            emailTextView.setText(user.getEmail());

            if (user.isEmailVerified()) {
                verificationCard.setVisibility(View.GONE);
            } else {
                verificationCard.setVisibility(View.VISIBLE);
                verificationStatusTextView.setText("Your email is not verified. Click to verify.");
            }
        }
    }

    private void loadPreferences() {
        boolean notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true);
        notificationsSwitch.setChecked(notificationsEnabled);

        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        darkModeSwitch.setChecked(currentNightMode == Configuration.UI_MODE_NIGHT_YES);
    }

    private void setupClickListeners() {
        verificationCard.setOnClickListener(v -> sendVerificationEmail());

        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                saveNotificationPreference(isChecked);
            }
        });

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                toggleDarkMode(isChecked);
            }
        });

        changePasswordLayout.setOnClickListener(v -> changePassword());
        deleteAccountLayout.setOnClickListener(v -> showDeleteAccountDialog());
        logoutLayout.setOnClickListener(v -> logout());
    }

    private void sendVerificationEmail() {
        if (!firebaseEnabled || mAuth == null) {
            showConnectedFeatureMessage();
            return;
        }
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isEmailVerified()) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Snackbar.make(requireView(), "Verification email sent", Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(requireView(), "Failed to send verification email", Snackbar.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void saveNotificationPreference(boolean enabled) {
        sharedPreferences.edit().putBoolean("notifications_enabled", enabled).apply();

        FirebaseUser user = mAuth == null ? null : mAuth.getCurrentUser();
        if (user != null && usersRef != null) {
            usersRef.child(user.getUid())
                    .child("notificationsEnabled").setValue(enabled)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Notification preference updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        // Fail silently - local preference is saved
                    });
        }
    }

    private void toggleDarkMode(boolean enabled) {
        sharedPreferences.edit().putBoolean("dark_mode_enabled", enabled).apply();

        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // Recreate activity to apply theme
        requireActivity().recreate();
    }

    private void changePassword() {
        if (!firebaseEnabled || mAuth == null) {
            showConnectedFeatureMessage();
            return;
        }
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            mAuth.sendPasswordResetEmail(user.getEmail())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Snackbar.make(requireView(),
                                    "Password reset email sent to " + user.getEmail(),
                                    Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(requireView(),
                                    "Failed to send reset email",
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void showDeleteAccountDialog() {
        if (!firebaseEnabled || mAuth == null) {
            showConnectedFeatureMessage();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAccount() {
        if (mAuth == null || usersRef == null) return;
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();

            // Show loading
            Snackbar.make(requireView(), "Deleting account...", Snackbar.LENGTH_SHORT).show();

            // Delete user data from Realtime Database
            usersRef.child(userId)
                    .removeValue()
                    .addOnSuccessListener(aVoid -> {
                        // Delete Firebase Auth account
                        user.delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(getContext(),
                                                "Account deleted successfully",
                                                Toast.LENGTH_SHORT).show();
                                        navigateToLogin();
                                    } else {
                                        Snackbar.make(requireView(),
                                                "Failed to delete account. Please re-login and try again.",
                                                Snackbar.LENGTH_LONG).show();
                                    }
                                });
                    })
                    .addOnFailureListener(e -> {
                        Snackbar.make(requireView(),
                                "Failed to delete user data",
                                Snackbar.LENGTH_LONG).show();
                    });
        }
    }

    private void logout() {
        new AlertDialog.Builder(requireContext())
                .setTitle(DemoSession.isActive() ? "Exit demo" : "Logout")
                .setMessage(DemoSession.isActive()
                        ? "Return to the Safe-Navi role selection screen?"
                        : "Are you sure you want to logout?")
                .setPositiveButton(DemoSession.isActive() ? "Exit demo" : "Logout", (dialog, which) -> {
                    if (DemoSession.isActive()) DemoSession.stop();
                    if (mAuth != null) mAuth.signOut();
                    navigateToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showConnectedFeatureMessage() {
        Snackbar.make(requireView(),
                "Connected account management is planned for a later phase. The 30% demo remains local and synthetic.",
                Snackbar.LENGTH_LONG).show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(getActivity(), LoginTypeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
