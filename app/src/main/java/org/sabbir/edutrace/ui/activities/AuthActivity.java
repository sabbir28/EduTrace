package org.sabbir.edutrace.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.data.cloud.CloudUser;
import org.sabbir.edutrace.databinding.ActivityAuthBinding;
import org.sabbir.edutrace.utils.EmailManager;

public class AuthActivity extends AppCompatActivity {
    private ActivityAuthBinding binding;
    private boolean isRegisterMode = false;
    private boolean isFromStart = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFromStart = getIntent().getBooleanExtra("FORCE_LOGIN_ON_START", false);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> handleBackOrExit());
        binding.tvSkipOffline.setOnClickListener(v -> handleBackOrExit());

        setupListeners();
        updateModeUI();
    }

    private void handleBackOrExit() {
        if (isFromStart) {
            Intent intent = new Intent(AuthActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBackOrExit();
    }

    private void onAuthSuccess(CloudUser user) {
        setLoading(false);
        Toast.makeText(AuthActivity.this, "Welcome, " + user.getDisplayName() + "! ☁️", Toast.LENGTH_SHORT).show();
        if (isFromStart) {
            Intent intent = new Intent(AuthActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            setResult(RESULT_OK);
        }
        finish();
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

        binding.tvForgotPassword.setOnClickListener(v -> handleForgotPassword());

        binding.btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    private void updateModeUI() {
        if (isRegisterMode) {
            binding.tvAuthTitle.setText("Create Cloud Account");
            binding.tilUsername.setHint("Username");
            binding.tilDisplayName.setVisibility(View.VISIBLE);
            binding.tilEmail.setVisibility(View.VISIBLE);
            binding.tvForgotPassword.setVisibility(View.GONE);
            binding.btnSubmit.setText("Verify Email & Register");
            binding.tvToggleModeHint.setText("Already have an account? Sign In");
        } else {
            binding.tvAuthTitle.setText("Sign In to Cloud");
            binding.tilUsername.setHint("Username or Email");
            binding.tilDisplayName.setVisibility(View.GONE);
            binding.tilEmail.setVisibility(View.GONE);
            binding.tvForgotPassword.setVisibility(View.VISIBLE);
            binding.btnSubmit.setText("Sign In");
            binding.tvToggleModeHint.setText("Don't have an account? Register here");
        }
    }

    private void handleSubmit() {
        String username = binding.etUsername.getText() != null ? binding.etUsername.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

        if (username.isEmpty()) {
            binding.tilUsername.setError(isRegisterMode ? "Username is required" : "Username or email is required");
            return;
        } else {
            binding.tilUsername.setError(null);
        }

        if (password.length() < 8) {
            binding.tilPassword.setError("Password must be at least 8 characters");
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        if (isRegisterMode) {
            String displayName = binding.etDisplayName.getText() != null ? binding.etDisplayName.getText().toString().trim() : "";
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.setError("Valid email address is required for registration");
                return;
            } else {
                binding.tilEmail.setError(null);
            }

            setLoading(true);
            // 1. Check if user already exists
            CloudDbManager.checkUserAvailability(username, email, new CloudDbManager.AvailabilityCallback() {
                @Override
                public void onAvailable() {
                    // 2. Dispatch OTP to email
                    String otpCode = EmailManager.generateOtp(email);
                    EmailManager.sendOtpEmail(email, username, otpCode, "Account Registration", new EmailManager.EmailCallback() {
                        @Override
                        public void onSuccess() {
                            setLoading(false);
                            showRegistrationOtpDialog(username, email, password, displayName);
                        }

                        @Override
                        public void onError(String error) {
                            setLoading(false);
                            // Fallback dialog for direct registration if SMTP email dispatch fails
                            new AlertDialog.Builder(AuthActivity.this)
                                    .setTitle("Direct Registration Option")
                                    .setMessage("Could not dispatch OTP email (" + error + "). Would you like to proceed with direct cloud registration?")
                                    .setPositiveButton("Register Directly", (dialog, which) -> {
                                        setLoading(true);
                                        CloudDbManager.register(AuthActivity.this, username, email, password, displayName, new CloudDbManager.AuthCallback() {
                                            @Override
                                            public void onSuccess(CloudUser user) {
                                                onAuthSuccess(user);
                                            }

                                            @Override
                                            public void onError(String message) {
                                                setLoading(false);
                                                Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });

        } else {
            setLoading(true);
            CloudDbManager.login(this, username, password, new CloudDbManager.AuthCallback() {
                @Override
                public void onSuccess(CloudUser user) {
                    onAuthSuccess(user);
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void showRegistrationOtpDialog(String username, String email, String password, String displayName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_otp_verify, null);
        TextView tvMsg = dialogView.findViewById(R.id.tvOtpDialogMessage);
        EditText etCode = dialogView.findViewById(R.id.etOtpCode);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnOtpCancel);
        MaterialButton btnVerify = dialogView.findViewById(R.id.btnOtpVerify);
        ProgressBar progress = dialogView.findViewById(R.id.progressOtp);

        tvMsg.setText("A 6-digit OTP code has been sent to " + email + ". Enter it to complete your registration:");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnVerify.setOnClickListener(v -> {
            String enteredCode = etCode.getText().toString().trim();
            if (enteredCode.length() != 6) {
                Toast.makeText(this, "Please enter the 6-digit code", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!EmailManager.verifyOtp(email, enteredCode)) {
                Toast.makeText(this, "Invalid or expired OTP code. Please check and try again.", Toast.LENGTH_LONG).show();
                return;
            }

            progress.setVisibility(View.VISIBLE);
            btnVerify.setEnabled(false);

            CloudDbManager.register(this, username, email, password, displayName, new CloudDbManager.AuthCallback() {
                @Override
                public void onSuccess(CloudUser user) {
                    dialog.dismiss();
                    onAuthSuccess(user);
                }

                @Override
                public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void handleForgotPassword() {
        String enteredUser = binding.etUsername.getText() != null ? binding.etUsername.getText().toString().trim() : "";

        android.widget.EditText inputEt = new android.widget.EditText(this);
        inputEt.setHint("Enter username or registered email");
        inputEt.setTextColor(android.graphics.Color.WHITE);
        inputEt.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        if (!enteredUser.isEmpty()) inputEt.setText(enteredUser);

        new AlertDialog.Builder(this)
                .setTitle("Reset Password via OTP 🔐")
                .setMessage("Enter your username or email address. We will send a verification code to your registered email.")
                .setView(inputEt)
                .setPositiveButton("Send Code", (d, which) -> {
                    String target = inputEt.getText().toString().trim();
                    if (target.isEmpty()) {
                        Toast.makeText(this, "Please enter your username or email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setLoading(true);
                    CloudDbManager.findUserEmail(target, new CloudDbManager.EmailLookupCallback() {
                        @Override
                        public void onFound(String username, String email) {
                            String otp = EmailManager.generateOtp(email);
                            EmailManager.sendOtpEmail(email, username, otp, "Password Reset", new EmailManager.EmailCallback() {
                                @Override
                                public void onSuccess() {
                                    setLoading(false);
                                    showPasswordResetDialog(username, email);
                                }

                                @Override
                                public void onError(String error) {
                                    setLoading(false);
                                    Toast.makeText(AuthActivity.this, "Failed to send reset code: " + error, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPasswordResetDialog(String username, String email) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_otp_verify, null);
        TextView tvTitle = dialogView.findViewById(R.id.tvOtpDialogTitle);
        TextView tvMsg = dialogView.findViewById(R.id.tvOtpDialogMessage);
        EditText etCode = dialogView.findViewById(R.id.etOtpCode);
        TextInputLayout tilNewPass = dialogView.findViewById(R.id.tilNewPassword);
        EditText etNewPass = dialogView.findViewById(R.id.etNewPassword);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnOtpCancel);
        MaterialButton btnVerify = dialogView.findViewById(R.id.btnOtpVerify);
        ProgressBar progress = dialogView.findViewById(R.id.progressOtp);

        tvTitle.setText("Reset Password 🔐");
        tvMsg.setText("A 6-digit code has been sent to " + email + ". Enter the code and choose a new password:");
        tilNewPass.setVisibility(View.VISIBLE);
        btnVerify.setText("Reset Password");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnVerify.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            String newPassword = etNewPass.getText().toString();

            if (code.length() != 6) {
                Toast.makeText(this, "Please enter the 6-digit code", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPassword.length() < 8) {
                tilNewPass.setError("Password must be at least 8 characters");
                return;
            } else {
                tilNewPass.setError(null);
            }

            if (!EmailManager.verifyOtp(email, code)) {
                Toast.makeText(this, "Invalid or expired OTP code.", Toast.LENGTH_LONG).show();
                return;
            }

            progress.setVisibility(View.VISIBLE);
            btnVerify.setEnabled(false);

            CloudDbManager.resetPassword(username, newPassword, new CloudDbManager.AuthCallback() {
                @Override
                public void onSuccess(CloudUser user) {
                    dialog.dismiss();
                    Toast.makeText(AuthActivity.this, "Password reset successfully! Please sign in with your new password.", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void setLoading(boolean isLoading) {
        binding.progressAuth.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSubmit.setEnabled(!isLoading);
        binding.btnTabLogin.setEnabled(!isLoading);
        binding.btnTabRegister.setEnabled(!isLoading);
    }
}
