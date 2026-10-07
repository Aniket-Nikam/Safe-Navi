package com.safenavi.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.safenavi.app.product.ProductApiClient;
import com.safenavi.app.product.ProductSession;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;
    private View rootView;
    private String expectedRole;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        rootView = findViewById(android.R.id.content);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);
        expectedRole = getIntent().getStringExtra("LOGIN_TYPE");
        if (expectedRole == null || expectedRole.equals("user")) expectedRole = "citizen";
        ((TextView) findViewById(R.id.loginTitleTextView)).setText(expectedRole.equals("government")
                ? "Official sign in" : expectedRole.equals("admin") ? "Admin sign in" : "Welcome back");
        TextView secondaryAction = findViewById(R.id.forgotPasswordTextView);
        secondaryAction.setText("Create citizen account");
        secondaryAction.setVisibility(expectedRole.equals("citizen") ? View.VISIBLE : View.GONE);
        secondaryAction.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        loginButton.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailEditText.setError("Enter a valid email"); return; }
        if (TextUtils.isEmpty(password)) { passwordEditText.setError("Password is required"); return; }
        showLoading(true);
        new ProductApiClient(this).login(email, password, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject response) { runOnUiThread(() -> completeLogin(response)); }
            @Override public void onError(String message) { runOnUiThread(() -> { showLoading(false); Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show(); }); }
        });
    }

    private void completeLogin(JSONObject response) {
        JSONObject user = response.optJSONObject("user");
        if (user == null) { showLoading(false); Snackbar.make(rootView, "Account response is incomplete.", Snackbar.LENGTH_LONG).show(); return; }
        String role = user.optString("role", "citizen");
        if (!role.equals(expectedRole)) {
            showLoading(false);
            Snackbar.make(rootView, "This account does not have the selected " + expectedRole + " role.", Snackbar.LENGTH_LONG).show();
            return;
        }
        ProductSession.save(this, response.optString("access_token"), user.optString("id"),
                user.optString("name"), user.optString("email"), role);
        Intent destination = new Intent(this, role.equals("government") || role.equals("admin")
                ? GovernmentSafetyDashboardActivity.class : MainActivity.class);
        destination.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(destination);
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        emailEditText.setEnabled(!loading);
        passwordEditText.setEnabled(!loading);
    }
}
