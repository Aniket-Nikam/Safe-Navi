package com.example.xavierproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
// removed firestore imports
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private TextView forgotPasswordTextView, titleTextView;
    private ProgressBar progressBar;
    private View rootView;
    private String loginType;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Get login type from intent
        loginType = getIntent().getStringExtra("LOGIN_TYPE");
        if (loginType == null) {
            loginType = "user";
        }

        initializeViews();
        updateUIForLoginType();
        setupFirebase();
        setupClickListeners();
    }

    private void initializeViews() {
        rootView = findViewById(android.R.id.content);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);
        titleTextView = findViewById(R.id.loginTitleTextView);
        progressBar = findViewById(R.id.progressBar);
    }

    private void updateUIForLoginType() {
        switch (loginType) {
            case "admin":
                titleTextView.setText("Admin Login");
                break;
            case "government":
                titleTextView.setText("Government Official Login");
                break;
            default:
                titleTextView.setText("User Login");
                break;
        }
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("users");
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> loginWithEmail());
        forgotPasswordTextView.setOnClickListener(v -> resetPassword());
    }

    private void loginWithEmail() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (!validateInput(email, password)) {
            return;
        }

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // SAVE EMAIL TO REALTIME DATABASE
                            saveUserEmailToDatabase(user);

                            if (!user.isEmailVerified()) {
                                Snackbar.make(rootView,
                                                "Please verify your email for full access",
                                                Snackbar.LENGTH_LONG)
                                        .setAction("Resend", v -> resendVerificationEmail(user))
                                        .show();
                            }
                            
                            // Check Role in Realtime Database
                            usersRef.child(user.getUid()).get()
                                .addOnCompleteListener(roleTask -> {
                                    if (roleTask.isSuccessful()) {
                                        String role = "user"; // default
                                        if (roleTask.getResult().exists() && roleTask.getResult().child("role").exists()) {
                                            role = roleTask.getResult().child("role").getValue(String.class);
                                        }
                                        
                                        verifyRoleAndNavigate(role);
                                    } else {
                                        showLoading(false);
                                        mAuth.signOut();
                                        Snackbar.make(rootView, "Failed to verify account role.", Snackbar.LENGTH_LONG).show();
                                    }
                                });
                        }
                    } else {
                        showLoading(false);
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        handleLoginError(task.getException());
                    }
                });
    }

    private void verifyRoleAndNavigate(String role) {
        if ("admin".equals(loginType)) {
            if ("admin".equals(role)) {
                Intent intent = new Intent(LoginActivity.this, AdminMainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                showLoading(false);
                mAuth.signOut();
                Snackbar.make(rootView, "Access Denied: You are not an Admin.", Snackbar.LENGTH_LONG).show();
            }
        } else if ("government".equals(loginType)) {
            if ("government".equals(role)) {
                Intent intent = new Intent(LoginActivity.this, GovMainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                showLoading(false);
                mAuth.signOut();
                Snackbar.make(rootView, "Access Denied: You are not a Government Official.", Snackbar.LENGTH_LONG).show();
            }
        } else {
            // Normal user login
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    /**
     * âœ… NEW METHOD - Saves user email to Realtime Database
     */
    private void saveUserEmailToDatabase(FirebaseUser user) {
        String userId = user.getUid();
        String email = user.getEmail();
        String name = user.getDisplayName() != null ? user.getDisplayName() : "User";

        // Create user data map
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("email", email);
        userData.put("name", name);
        userData.put("lastLogin", System.currentTimeMillis());

        // Save to database
        usersRef.child(userId).updateChildren(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User email saved to database: " + email);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user email: " + e.getMessage());
                    // Don't show error to user - this is background operation
                });
    }

    private void handleLoginError(Exception exception) {
        String errorMessage = "Login failed. Please check your credentials.";

        if (exception instanceof FirebaseAuthInvalidUserException) {
            errorMessage = "No account found with this email. Please sign up.";
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Incorrect password. Please try again.";
        }

        Snackbar.make(rootView, errorMessage, Snackbar.LENGTH_LONG).show();
    }

    private void resendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void resetPassword() {
        String email = emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter your email");
            emailEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter valid email");
            emailEditText.requestFocus();
            return;
        }

        showLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Snackbar.make(rootView, "Password reset email sent to " + email,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        Snackbar.make(rootView, "Failed to send reset email. Check if email exists.",
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateInput(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter valid email");
            emailEditText.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return false;
        }

        return true;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
        emailEditText.setEnabled(!show);
        passwordEditText.setEnabled(!show);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
