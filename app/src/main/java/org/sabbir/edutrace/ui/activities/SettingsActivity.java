package org.sabbir.edutrace.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.data.cloud.CloudUser;
import org.sabbir.edutrace.databinding.ActivitySettingsBinding;
import org.sabbir.edutrace.utils.LocalVaultManager;
import org.sabbir.edutrace.utils.ObbManager;
import org.sabbir.edutrace.utils.UpdateManager;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences("Settings", MODE_PRIVATE);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupTheme();
        setupLanguage();
        setupLibraryMode();
        setupLocalVault();
        setupCloudSync();
        setupAppUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVaultUI();
        updateCloudUI();
    }

    private void setupLocalVault() {
        updateVaultUI();

        binding.btnCreateVaultSnapshot.setOnClickListener(v -> {
            binding.btnCreateVaultSnapshot.setEnabled(false);
            binding.tvVaultStatus.setText("Securing snapshot in safe vault...");

            LocalVaultManager.saveVaultAsync(this, new LocalVaultManager.VaultCallback() {
                @Override
                public void onSuccess(String message) {
                    binding.btnCreateVaultSnapshot.setEnabled(true);
                    updateVaultUI();
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    binding.btnCreateVaultSnapshot.setEnabled(true);
                    updateVaultUI();
                    Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        binding.btnRestoreVaultSnapshot.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Restore Safe Vault 🛡️")
                    .setMessage("This will recover study sessions, subjects, and degrees preserved in your local safe vault snapshot. Continue?")
                    .setPositiveButton("Restore Now", (dialog, which) -> {
                        LocalVaultManager.restoreFromVault(this, new LocalVaultManager.VaultCallback() {
                            @Override
                            public void onSuccess(String message) {
                                updateVaultUI();
                                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateVaultUI() {
        String status = LocalVaultManager.getVaultStatus(this);
        binding.tvVaultStatus.setText(status);
    }

    private void setupCloudSync() {
        binding.btnCloudAuthAction.setOnClickListener(v -> {
            if (CloudDbManager.isLoggedIn(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Sign Out")
                        .setMessage("Are you sure you want to sign out from EduTrace Cloud?")
                        .setPositiveButton("Sign Out", (dialog, which) -> {
                            CloudDbManager.logout(this);
                            updateCloudUI();
                            Toast.makeText(this, "Signed out from cloud.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                startActivity(new Intent(this, AuthActivity.class));
            }
        });

        binding.btnBackupNow.setOnClickListener(v -> executeCloudBackup());

        binding.btnRestoreCloud.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Restore Cloud Data 📥")
                    .setMessage("This will download and merge your cloud-backed study sessions, subjects, and degrees into this device. Continue?")
                    .setPositiveButton("Restore", (dialog, which) -> executeCloudRestore())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        boolean autoBackup = prefs.getBoolean("cloud_auto_backup_enabled", true);
        binding.switchAutoCloudBackup.setChecked(autoBackup);
        binding.switchAutoCloudBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("cloud_auto_backup_enabled", isChecked).apply();
        });

        updateCloudUI();
    }

    private static class CloudProgressViewHolder {
        AlertDialog dialog;
        TextView tvTitle;
        TextView tvStep;
        TextView tvDetail;
        ProgressBar progressBar;
    }

    private CloudProgressViewHolder showCloudProgressDialog(String title, String initialStep) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_cloud_progress, null);
        CloudProgressViewHolder holder = new CloudProgressViewHolder();
        holder.tvTitle = view.findViewById(R.id.tvCloudProgressTitle);
        holder.tvStep = view.findViewById(R.id.tvCloudProgressStep);
        holder.tvDetail = view.findViewById(R.id.tvCloudProgressDetail);
        holder.progressBar = view.findViewById(R.id.progressBarCloud);

        holder.tvTitle.setText(title);
        holder.tvStep.setText(initialStep);
        holder.tvDetail.setText("Securing SSL connection to PostgreSQL database...");

        holder.dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();
        holder.dialog.show();
        return holder;
    }

    private void showSyncErrorDialog(String title, String errorMessage, Runnable retryAction) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(errorMessage + "\n\nPlease ensure your device is connected to the internet. Note: Your local data is still safely preserved in your Local Safe Vault.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Retry", (dialog, which) -> {
                    if (retryAction != null) retryAction.run();
                })
                .setNegativeButton("Dismiss", null)
                .show();
    }

    private void executeCloudBackup() {
        CloudProgressViewHolder progress = showCloudProgressDialog("Backing Up to Cloud ☁️", "Connecting to PostgreSQL cloud database...");
        binding.btnBackupNow.setEnabled(false);
        binding.btnRestoreCloud.setEnabled(false);

        CloudDbManager.backupData(this, new CloudDbManager.SyncProgressCallback() {
            @Override
            public void onProgress(String status) {
                if (progress.dialog != null && progress.dialog.isShowing()) {
                    progress.tvStep.setText(status);
                    progress.tvDetail.setText(status);
                }
            }

            @Override
            public void onSuccess(String message) {
                if (progress.dialog != null && progress.dialog.isShowing()) {
                    progress.dialog.dismiss();
                }
                binding.btnBackupNow.setEnabled(true);
                binding.btnRestoreCloud.setEnabled(true);
                binding.tvLastBackupTime.setText("Last Cloud Backup: " + CloudDbManager.getLastBackupTime(SettingsActivity.this));
                updateVaultUI();

                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Backup Successful ☁️")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                if (progress.dialog != null && progress.dialog.isShowing()) {
                    progress.dialog.dismiss();
                }
                binding.btnBackupNow.setEnabled(true);
                binding.btnRestoreCloud.setEnabled(true);
                showSyncErrorDialog("Cloud Backup Failed", message, () -> executeCloudBackup());
            }
        });
    }

    private void executeCloudRestore() {
        CloudProgressViewHolder progress = showCloudProgressDialog("Restoring Cloud Data 📥", "Connecting to PostgreSQL cloud database...");
        binding.btnBackupNow.setEnabled(false);
        binding.btnRestoreCloud.setEnabled(false);

        CloudDbManager.restoreData(this, new CloudDbManager.SyncProgressCallback() {
            @Override
            public void onProgress(String status) {
                if (progress.dialog != null && progress.dialog.isShowing()) {
                    progress.tvStep.setText(status);
                    progress.tvDetail.setText(status);
                }
            }

            @Override
            public void onSuccess(String message) {
                if (progress.dialog != null && progress.dialog.isShowing()) {
                    progress.dialog.dismiss();
                }
                binding.btnBackupNow.setEnabled(true);
                binding.btnRestoreCloud.setEnabled(true);
                updateVaultUI();

                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Restore Complete 📥")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                if (progress.dialog != null && progress.dialog.isShowing()) {
                    progress.dialog.dismiss();
                }
                binding.btnBackupNow.setEnabled(true);
                binding.btnRestoreCloud.setEnabled(true);
                showSyncErrorDialog("Cloud Restore Failed", message, () -> executeCloudRestore());
            }
        });
    }

    private void updateCloudUI() {
        if (CloudDbManager.isLoggedIn(this)) {
            CloudUser user = CloudDbManager.getCurrentUser(this);
            binding.tvCloudUser.setText(user != null ? user.getDisplayName() : "Connected");
            String sub = (user != null && user.getEmail() != null && !user.getEmail().isEmpty())
                    ? user.getEmail() : (user != null ? "@" + user.getUsername() : "Online backup enabled");
            binding.tvCloudStatus.setText(sub);
            binding.btnCloudAuthAction.setText("Sign Out");
            binding.btnCloudAuthAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
            binding.btnCloudAuthAction.setTextColor(Color.WHITE);
            binding.layoutCloudSyncActions.setVisibility(View.VISIBLE);
            binding.tvLastBackupTime.setText("Last Cloud Backup: " + CloudDbManager.getLastBackupTime(this));
        } else {
            binding.tvCloudUser.setText("Not Signed In");
            binding.tvCloudStatus.setText("Backup study time and history online");
            binding.btnCloudAuthAction.setText("Sign In or Register");
            binding.btnCloudAuthAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FACC15")));
            binding.btnCloudAuthAction.setTextColor(Color.parseColor("#0F172A"));
            binding.layoutCloudSyncActions.setVisibility(View.GONE);
        }
    }

    private void setupAppUpdates() {
        boolean autoUpdate = prefs.getBoolean("auto_check_updates", true);
        binding.switchAutoUpdate.setChecked(autoUpdate);

        binding.switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_check_updates", isChecked).apply();
        });

        binding.btnCheckUpdate.setOnClickListener(v -> {
            UpdateManager.checkForUpdates(this, true);
        });

        String currentVer = UpdateManager.getCurrentVersion(this);
        binding.tvVersionInfo.setText("EduTrace v" + currentVer + " (2026)");

        binding.tvObbStatus.setText("Asset Pack (OBB): " + ObbManager.getInstalledObbVersion(this));
        binding.btnCheckObbUpdate.setOnClickListener(v -> {
            ObbManager.checkForObbUpdates(this, true, (isAvailable, latestVersion, downloadUrl) -> {
                binding.tvObbStatus.setText("Asset Pack (OBB): " + ObbManager.getInstalledObbVersion(this));
            });
        });
    }

    private void setupLibraryMode() {
        boolean isLibraryMode = prefs.getBoolean("library_mode", false);
        binding.switchLibraryMode.setChecked(isLibraryMode);

        binding.switchLibraryMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("library_mode", isChecked).apply();
        });
    }

    private void setupTheme() {
        boolean isDarkMode = prefs.getBoolean("dark_mode", true);
        binding.switchDarkMode.setChecked(isDarkMode);

        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    private void setupLanguage() {
        String lang = prefs.getString("language", "en");
        if (lang.equals("bn")) {
            binding.rbBangla.setChecked(true);
        } else {
            binding.rbEnglish.setChecked(true);
        }

        binding.rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String newLang = (checkedId == R.id.rbBangla) ? "bn" : "en";
            if (!newLang.equals(lang)) {
                prefs.edit().putString("language", newLang).apply();
                updateLocale(newLang);

                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private void updateLocale(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}
