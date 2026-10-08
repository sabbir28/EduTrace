package org.sabbir.edutrace.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.data.cloud.CloudUser;
import org.sabbir.edutrace.databinding.ActivityAuthBinding;

public class AuthActivity extends AppCompatActivity {
    private ActivityAuthBinding binding;
    private boolean isRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
        updateModeUI();
    }

    private void setupListeners() {
        binding.toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isRegisterMode = (checkedId == R.id.btnTabRegister);
                updateModeUI();
            }
        });

        binding.tvToggleModeHint.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            binding.toggleMode.check(isRegisterMode ? R.id.btnTabRegister : R.id.btnTabLogin);
        });

        binding.btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    private void updateModeUI() {
        if (isRegisterMode) {
            binding.tvAuthTitle.setText("Create Cloud Account");
            binding.tilDisplayName.setVisibility(View.VISIBLE);
            binding.tilEmail.setVisibility(View.VISIBLE);
            binding.btnSubmit.setText("Create Account");
            binding.tvToggleModeHint.setText("Already have an account? Sign In");
        } else {
            binding.tvAuthTitle.setText("Sign In to Cloud");
            binding.tilDisplayName.setVisibility(View.GONE);
            binding.tilEmail.setVisibility(View.GONE);
            binding.btnSubmit.setText("Sign In");
            binding.tvToggleModeHint.setText("Don't have an account? Register here");
        }
    }

    private void handleSubmit() {
        String username = binding.etUsername.getText() != null ? binding.etUsername.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

        if (username.isEmpty()) {
            binding.tilUsername.setError("Username is required");
            return;
        } else {
            binding.tilUsername.setError(null);
        }

        if (password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        setLoading(true);

        if (isRegisterMode) {
            String displayName = binding.etDisplayName.getText() != null ? binding.etDisplayName.getText().toString().trim() : "";
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";

            CloudDbManager.register(this, username, email, password, displayName, new CloudDbManager.AuthCallback() {
                @Override
                public void onSuccess(CloudUser user) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, "Welcome, " + user.getDisplayName() + "! 🎉 Account created.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            CloudDbManager.login(this, username, password, new CloudDbManager.AuthCallback() {
                @Override
                public void onSuccess(CloudUser user) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, "Welcome back, " + user.getDisplayName() + "! ☁️", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void setLoading(boolean isLoading) {
        binding.progressAuth.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSubmit.setEnabled(!isLoading);
        binding.btnTabLogin.setEnabled(!isLoading);
        binding.btnTabRegister.setEnabled(!isLoading);
    }
}
