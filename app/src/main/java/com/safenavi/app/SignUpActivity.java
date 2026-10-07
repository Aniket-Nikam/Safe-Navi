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

public class SignUpActivity extends AppCompatActivity {
    private EditText nameInput, emailInput, passwordInput, confirmationInput;
    private Button signUpButton;
    private ProgressBar progressBar;
    private View root;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        root = findViewById(android.R.id.content);
        nameInput = findViewById(R.id.usernameEditText);
        emailInput = findViewById(R.id.emailEditText);
        passwordInput = findViewById(R.id.passwordEditText);
        confirmationInput = findViewById(R.id.confirmPasswordEditText);
        signUpButton = findViewById(R.id.signUpButton);
        progressBar = findViewById(R.id.progressBar);
        signUpButton.setOnClickListener(v -> register());
        ((TextView) findViewById(R.id.loginTextView)).setOnClickListener(v -> finish());
    }

    private void register() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (name.length() < 2) { nameInput.setError("Enter your name"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailInput.setError("Enter a valid email"); return; }
        if (password.length() < 10) { passwordInput.setError("Use at least 10 characters"); return; }
        if (!TextUtils.equals(password, confirmationInput.getText().toString())) { confirmationInput.setError("Passwords do not match"); return; }
        showLoading(true);
        new ProductApiClient(this).register(name, email, password, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject response) { runOnUiThread(() -> complete(response)); }
            @Override public void onError(String message) { runOnUiThread(() -> { showLoading(false); Snackbar.make(root, message, Snackbar.LENGTH_LONG).show(); }); }
        });
    }

    private void complete(JSONObject response) {
        JSONObject user = response.optJSONObject("user");
        if (user == null) { showLoading(false); Snackbar.make(root, "Account response is incomplete.", Snackbar.LENGTH_LONG).show(); return; }
        ProductSession.save(this, response.optString("access_token"), user.optString("id"),
                user.optString("name"), user.optString("email"), user.optString("role", "citizen"));
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!loading);
        nameInput.setEnabled(!loading); emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading); confirmationInput.setEnabled(!loading);
    }
}
