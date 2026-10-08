package org.sabbir.edutrace.ui.activities;
import org.sabbir.edutrace.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import org.sabbir.edutrace.databinding.ActivitySettingsBinding;
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
        setupCloudSync();
        setupAppUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCloudUI();
    }

    private void setupCloudSync() {
        binding.btnCloudAuthAction.setOnClickListener(v -> {
            if (org.sabbir.edutrace.data.cloud.CloudDbManager.isLoggedIn(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Sign Out")
                        .setMessage("Are you sure you want to sign out from EduTrace Cloud?")
                        .setPositiveButton("Sign Out", (dialog, which) -> {
                            org.sabbir.edutrace.data.cloud.CloudDbManager.logout(this);
                            updateCloudUI();
                            android.widget.Toast.makeText(this, "Signed out from cloud.", android.widget.Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                startActivity(new Intent(this, AuthActivity.class));
            }
        });

        binding.btnBackupNow.setOnClickListener(v -> {
            binding.progressCloudSync.setVisibility(android.view.View.VISIBLE);
            binding.btnBackupNow.setEnabled(false);
            binding.btnRestoreCloud.setEnabled(false);

            org.sabbir.edutrace.data.cloud.CloudDbManager.backupData(this, new org.sabbir.edutrace.data.cloud.CloudDbManager.SyncCallback() {
                @Override
                public void onSuccess(String message) {
                    binding.progressCloudSync.setVisibility(android.view.View.GONE);
                    binding.btnBackupNow.setEnabled(true);
                    binding.btnRestoreCloud.setEnabled(true);
                    binding.tvLastBackupTime.setText("Last Cloud Backup: " + org.sabbir.edutrace.data.cloud.CloudDbManager.getLastBackupTime(SettingsActivity.this));
                    android.widget.Toast.makeText(SettingsActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    binding.progressCloudSync.setVisibility(android.view.View.GONE);
                    binding.btnBackupNow.setEnabled(true);
                    binding.btnRestoreCloud.setEnabled(true);
                    android.widget.Toast.makeText(SettingsActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
                }
            });
        });

        binding.btnRestoreCloud.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Restore Cloud Data 📥")
                    .setMessage("This will download and merge your cloud-backed study sessions, subjects, and degrees into this device. Continue?")
                    .setPositiveButton("Restore", (dialog, which) -> {
                        binding.progressCloudSync.setVisibility(android.view.View.VISIBLE);
                        binding.btnBackupNow.setEnabled(false);
                        binding.btnRestoreCloud.setEnabled(false);

                        org.sabbir.edutrace.data.cloud.CloudDbManager.restoreData(this, new org.sabbir.edutrace.data.cloud.CloudDbManager.SyncCallback() {
                            @Override
                            public void onSuccess(String message) {
                                binding.progressCloudSync.setVisibility(android.view.View.GONE);
                                binding.btnBackupNow.setEnabled(true);
                                binding.btnRestoreCloud.setEnabled(true);
                                android.widget.Toast.makeText(SettingsActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String message) {
                                binding.progressCloudSync.setVisibility(android.view.View.GONE);
                                binding.btnBackupNow.setEnabled(true);
                                binding.btnRestoreCloud.setEnabled(true);
                                android.widget.Toast.makeText(SettingsActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                    })
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

    private void updateCloudUI() {
        if (org.sabbir.edutrace.data.cloud.CloudDbManager.isLoggedIn(this)) {
            org.sabbir.edutrace.data.cloud.CloudUser user = org.sabbir.edutrace.data.cloud.CloudDbManager.getCurrentUser(this);
            binding.tvCloudUser.setText(user != null ? user.getDisplayName() : "Connected");
            String sub = (user != null && user.getEmail() != null && !user.getEmail().isEmpty())
                    ? user.getEmail() : (user != null ? "@" + user.getUsername() : "Online backup enabled");
            binding.tvCloudStatus.setText(sub);
            binding.btnCloudAuthAction.setText("Sign Out");
            binding.btnCloudAuthAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
            binding.btnCloudAuthAction.setTextColor(android.graphics.Color.WHITE);
            binding.layoutCloudSyncActions.setVisibility(android.view.View.VISIBLE);
            binding.tvLastBackupTime.setText("Last Cloud Backup: " + org.sabbir.edutrace.data.cloud.CloudDbManager.getLastBackupTime(this));
        } else {
            binding.tvCloudUser.setText("Not Signed In");
            binding.tvCloudStatus.setText("Backup study time and history online");
            binding.btnCloudAuthAction.setText("Sign In or Register");
            binding.btnCloudAuthAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FACC15")));
            binding.btnCloudAuthAction.setTextColor(android.graphics.Color.parseColor("#0F172A"));
            binding.layoutCloudSyncActions.setVisibility(android.view.View.GONE);
        }
    }

    private void setupAppUpdates() {
        boolean autoUpdate = prefs.getBoolean("auto_check_updates", true);
        binding.switchAutoUpdate.setChecked(autoUpdate);

        binding.switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_check_updates", isChecked).apply();
        });

        binding.btnCheckUpdate.setOnClickListener(v -> {
            org.sabbir.edutrace.utils.UpdateManager.checkForUpdates(this, true);
        });

        String currentVer = org.sabbir.edutrace.utils.UpdateManager.getCurrentVersion(this);
        binding.tvVersionInfo.setText("EduTrace v" + currentVer + " (2026)");

        binding.tvObbStatus.setText("Asset Pack (OBB): " + org.sabbir.edutrace.utils.ObbManager.getInstalledObbVersion(this));
        binding.btnCheckObbUpdate.setOnClickListener(v -> {
            org.sabbir.edutrace.utils.ObbManager.checkForObbUpdates(this, true, (isAvailable, latestVersion, downloadUrl) -> {
                binding.tvObbStatus.setText("Asset Pack (OBB): " + org.sabbir.edutrace.utils.ObbManager.getInstalledObbVersion(this));
            });
        });
    }

    private void setupLibraryMode() {
        boolean isLibraryMode = prefs.getBoolean("library_mode", false);
        binding.switchLibraryMode.setChecked(isLibraryMode);

        binding.switchLibraryMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("library_mode", isChecked).apply();
            if (isChecked) {
                // Request phone state permissions here if toggled on, or leave for TimerActivity.
                // We'll leave the actual request to TimerActivity where it matters.
            }
        });
    }

    private void setupTheme() {
        boolean isDarkMode = prefs.getBoolean("dark_mode", true); // Default to dark as per UI
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
                
                // Restart app to apply language globally
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
