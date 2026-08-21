package com.safenavi.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class ManageUsersActivity extends AppCompatActivity {

    private static final String TAG = "ManageUsersActivity";

    private TextInputEditText emailEditText;
    private Spinner roleSpinner;
    private MaterialButton assignButton;
    private ProgressBar progressBar;
    private DatabaseReference usersRef;
    private DatabaseReference invitationsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Manage Users");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL);
        usersRef = database.getReference("users");
        invitationsRef = database.getReference("invitations");

        initializeViews();
        setupSpinner();
        setupClickListeners();
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        roleSpinner = findViewById(R.id.roleSpinner);
        assignButton = findViewById(R.id.assignButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupSpinner() {
        String[] roles = {"government", "admin", "user"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roles);
        roleSpinner.setAdapter(adapter);
    }

    private void setupClickListeners() {
        assignButton.setOnClickListener(v -> handleAssignRole());
    }

    private void handleAssignRole() {
        String email = emailEditText.getText().toString().trim();
        String selectedRole = roleSpinner.getSelectedItem().toString();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email");
            return;
        }

        showLoading(true);

        // 1. Search if user exists in Realtime Database
        usersRef.orderByChild("email").equalTo(email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        // User exists! Update their role.
                        DataSnapshot snapshot = task.getResult().getChildren().iterator().next();
                        String userId = snapshot.getKey();
                        
                        usersRef.child(userId)
                                .child("role").setValue(selectedRole)
                                .addOnSuccessListener(aVoid -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Success: Role updated to " + selectedRole, Toast.LENGTH_LONG).show();
                                    emailEditText.setText("");
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Log.e(TAG, "Error updating role", e);
                                    Toast.makeText(this, "Failed to update role.", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // User does not exist. Save an invitation.
                        saveInvitation(email, selectedRole);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error searching users", e);
                    Toast.makeText(this, "Error connecting to database.", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveInvitation(String email, String role) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("email", email);
        invitation.put("role", role);
        invitation.put("createdAt", System.currentTimeMillis());

        String encodedEmail = email.replace(".", ",");
        invitationsRef.child(encodedEmail)
                .setValue(invitation)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "User not found. Invitation saved! They will receive the " + role + " role when they sign up.", Toast.LENGTH_LONG).show();
                    emailEditText.setText("");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error saving invitation", e);
                    Toast.makeText(this, "Failed to save invitation.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        assignButton.setEnabled(!show);
        emailEditText.setEnabled(!show);
        roleSpinner.setEnabled(!show);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
