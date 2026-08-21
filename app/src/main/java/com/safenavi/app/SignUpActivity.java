package com.safenavi.app;

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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
// removed Firestore import
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private Button signUpButton, googleSignUpButton;
    private TextView loginTextView;
    private ProgressBar progressBar;
    private View rootView;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private DatabaseReference invitationsRef;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initializeViews();
        setupFirebase();
        setupClickListeners();
    }

    private void initializeViews() {
        rootView = findViewById(android.R.id.content);
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        signUpButton = findViewById(R.id.signUpButton);
        googleSignUpButton = findViewById(R.id.googleSignUpButton);
        loginTextView = findViewById(R.id.loginTextView);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL);
        usersRef = database.getReference("users");
        invitationsRef = database.getReference("invitations");

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupClickListeners() {
        signUpButton.setOnClickListener(v -> registerWithEmail());
        googleSignUpButton.setOnClickListener(v -> signUpWithGoogle());
        loginTextView.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginTypeActivity.class));
            finish();
        });
    }

    private void registerWithEmail() {
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        if (!validateInput(username, email, password, confirmPassword)) {
            return;
        }

        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            updateUserProfileAndSave(user, username, email);
                        }
                    } else {
                        showLoading(false);
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());

                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Snackbar.make(rootView,
                                            "This email is already registered. Please login instead.",
                                            Snackbar.LENGTH_LONG)
                                    .setAction("Login", v -> {
                                        startActivity(new Intent(SignUpActivity.this, LoginTypeActivity.class));
                                        finish();
                                    })
                                    .show();
                        } else {
                            Snackbar.make(rootView,
                                    "Registration failed: " + task.getException().getMessage(),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void updateUserProfileAndSave(FirebaseUser user, String username, String email) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User profile updated.");
                    }
                    // Continue regardless of profile update success
                    saveUserData(user, username, email);
                });
    }

    /**
     * âœ… UPDATED METHOD - Saves to both Firestore AND Realtime Database
     */
    private void saveUserData(FirebaseUser user, String username, String email) {
        String userId = user.getUid();
        
        String encodedEmail = email.replace(".", ",");
        invitationsRef.child(encodedEmail).get()
            .addOnCompleteListener(task -> {
                String role = "user";
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    role = task.getResult().child("role").getValue(String.class);
                    if (role == null) role = "user";
                    invitationsRef.child(encodedEmail).removeValue(); // consume invite
                }
                finalizeSaveUserData(user, username, email, userId, role);
            });
    }

    private void finalizeSaveUserData(FirebaseUser user, String username, String email, String userId, String role) {
        // Save to Realtime Database
        Map<String, Object> realtimeData = new HashMap<>();
        realtimeData.put("userId", userId);
        realtimeData.put("email", email);
        realtimeData.put("name", username);
        realtimeData.put("username", username);
        realtimeData.put("createdAt", System.currentTimeMillis());
        realtimeData.put("emailVerified", false);
        realtimeData.put("role", role);

        usersRef.child(userId).setValue(realtimeData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User data saved to Realtime Database: " + email);
                    sendVerificationEmail(user);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save to Realtime Database", e);
                    // Continue anyway - send verification email
                    sendVerificationEmail(user);
                });
    }

    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Email verification sent.");
                        Snackbar.make(rootView,
                                "Account created! Verification email sent to " + user.getEmail(),
                                Snackbar.LENGTH_LONG).show();

                        navigateToMainActivity();
                    } else {
                        Log.w(TAG, "Failed to send verification email", task.getException());
                        Snackbar.make(rootView,
                                "Account created but failed to send verification email. You can resend it from settings.",
                                Snackbar.LENGTH_LONG).show();
                        navigateToMainActivity();
                    }
                });
    }

    private void signUpWithGoogle() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign up failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        showLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveGoogleUserData(user);
                        }
                    } else {
                        showLoading(false);
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Snackbar.make(rootView, "Authentication failed", Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * âœ… UPDATED METHOD - Saves Google user to both Firestore AND Realtime Database
     */
    private void saveGoogleUserData(FirebaseUser user) {
        String userId = user.getUid();
        String email = user.getEmail();
        String name = user.getDisplayName() != null ? user.getDisplayName() : "User";

        String encodedEmail = (email != null ? email : "").replace(".", ",");
        invitationsRef.child(encodedEmail).get()
            .addOnCompleteListener(task -> {
                String role = "user";
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    role = task.getResult().child("role").getValue(String.class);
                    if (role == null) role = "user";
                    invitationsRef.child(encodedEmail).removeValue(); // consume invite
                }
                finalizeSaveGoogleUserData(user, userId, email, name, role);
            });
    }

    private void finalizeSaveGoogleUserData(FirebaseUser user, String userId, String email, String name, String role) {
        usersRef.child(userId).get().addOnSuccessListener(dataSnapshot -> {
            if (!dataSnapshot.exists()) {
                // New user - save to Realtime Database
                Map<String, Object> realtimeData = new HashMap<>();
                realtimeData.put("userId", userId);
                realtimeData.put("email", email);
                realtimeData.put("name", name);
                realtimeData.put("username", name);
                realtimeData.put("createdAt", System.currentTimeMillis());
                realtimeData.put("emailVerified", user.isEmailVerified());
                realtimeData.put("role", role);
                realtimeData.put("lastLogin", System.currentTimeMillis());

                usersRef.child(userId).setValue(realtimeData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Google user saved to Realtime Database");
                            showLoading(false);
                            navigateToMainActivity();
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Failed to save Google user", e);
                            showLoading(false);
                            navigateToMainActivity();
                        });
            } else {
                // Existing user - update last login
                usersRef.child(userId).child("lastLogin").setValue(System.currentTimeMillis())
                        .addOnCompleteListener(task -> {
                            showLoading(false);
                            navigateToMainActivity();
                        });
            }
        }).addOnFailureListener(e -> {
            showLoading(false);
            Log.w(TAG, "Failed to check user existence", e);
            navigateToMainActivity();
        });
    }

    private boolean validateInput(String username, String email, String password, String confirmPassword) {
        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError("Username is required");
            usernameEditText.requestFocus();
            return false;
        }

        if (username.length() < 3) {
            usernameEditText.setError("Username must be at least 3 characters");
            usernameEditText.requestFocus();
            return false;
        }

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

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return false;
        }

        return true;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!show);
        googleSignUpButton.setEnabled(!show);
        usernameEditText.setEnabled(!show);
        emailEditText.setEnabled(!show);
        passwordEditText.setEnabled(!show);
        confirmPasswordEditText.setEnabled(!show);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
