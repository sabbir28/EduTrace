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
