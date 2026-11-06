package org.sabbir.edutrace.ui.activities;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.FocusAppItem;
import org.sabbir.edutrace.data.models.FocusTaskItem;
import org.sabbir.edutrace.databinding.ActivityFocusLauncherBinding;
import org.sabbir.edutrace.ui.adapters.FocusCuratedAppAdapter;
import org.sabbir.edutrace.ui.adapters.FocusTaskAdapter;
import org.sabbir.edutrace.ui.adapters.FocusVerticalAppAdapter;
import org.sabbir.edutrace.utils.FocusLauncherManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Minimalist Focus Home Launcher for EduTrace.
 * Replaces distracting phone home screens with an education-first environment.
 * Features AI & daily utility curation, interactive study checklist, and the
 * 5-Hour Dynamic App Ghosting / Vanishing Engine.
 */
public class FocusLauncherActivity extends AppCompatActivity {

    private ActivityFocusLauncherBinding binding;
    private FocusLauncherManager manager;

    private FocusCuratedAppAdapter curatedAdapter;
    private FocusVerticalAppAdapter verticalAdapter;
    private FocusVerticalAppAdapter vanishedAdapter;
    private FocusTaskAdapter taskAdapter;

    private enum ScreenMode { DASHBOARD, APP_DRAWER, REFLECT }
    private ScreenMode currentMode = ScreenMode.DASHBOARD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFocusLauncherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        manager = FocusLauncherManager.getInstance();

        setupHeaderData();
        setupAdapters();
        setupSpotlightSearch();
        setupDockActions();
        setupTaskActions();
        setupModeSwitching();
        startEntranceAnimations();

        checkUsageStatsPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLauncherData();
    }

    @Override
    public void onBackPressed() {
        // As a Home launcher, Back returns to the clean Dashboard instead of closing
        if (currentMode != ScreenMode.DASHBOARD) {
            switchMode(ScreenMode.DASHBOARD);
        }
    }

    private void setupHeaderData() {
        // Date & Day format (e.g. "Thursday • Apr 23")
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE \u2022 MMM d", Locale.getDefault());
        binding.tvDayDate.setText(sdf.format(new Date()));

        int dayPct = manager.getDayInProgressPercent();
        binding.tvDayProgressLabel.setText("Day in progress " + dayPct + "%");
        binding.pbDayProgress.setProgress(dayPct);

        binding.btnOpenEduTrace.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(currentMode == ScreenMode.REFLECT ? ScreenMode.DASHBOARD : ScreenMode.REFLECT);
        });

        binding.btnOpenTimer.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            startActivity(new Intent(this, TimerActivity.class));
        });
    }

    private void setupAdapters() {
        // Curated Top Row
        curatedAdapter = new FocusCuratedAppAdapter();
        curatedAdapter.setOnAppClickListener(this::launchApp);
        binding.rvCuratedApps.setAdapter(curatedAdapter);

        // Vertical A-Z Drawer
        verticalAdapter = new FocusVerticalAppAdapter();
        verticalAdapter.setOnAppClickListener(this::launchApp);
        binding.rvVerticalApps.setAdapter(verticalAdapter);

        // Vanished Distractions
        vanishedAdapter = new FocusVerticalAppAdapter();
        binding.rvVanishedApps.setAdapter(vanishedAdapter);

        // Tasks Checklist
        taskAdapter = new FocusTaskAdapter();
        taskAdapter.setOnTaskActionListener(new FocusTaskAdapter.OnTaskActionListener() {
            @Override
            public void onToggleTask(FocusTaskItem task, int position) {
                manager.toggleTask(FocusLauncherActivity.this, task.getId());
            }

            @Override
            public void onDeleteTask(FocusTaskItem task, int position) {
                manager.deleteTask(FocusLauncherActivity.this, task.getId());
                taskAdapter.setTasks(manager.getFocusTasks(FocusLauncherActivity.this));
            }
        });
        binding.rvFocusTasks.setAdapter(taskAdapter);
    }

    private void refreshLauncherData() {
        manager.reloadApps(this);

        List<FocusAppItem> curated = manager.getCuratedApps();
        curatedAdapter.setApps(curated);

        List<FocusAppItem> allowed = manager.getAllowedApps(binding.etSpotlightSearch.getText().toString());
        verticalAdapter.setApps(allowed);
        binding.tvDrawerCount.setText("ALLOWED APPS (" + allowed.size() + ")");

        List<FocusAppItem> vanished = manager.getVanishedApps();
        vanishedAdapter.setApps(vanished);
        binding.tvVanishedCount.setText(vanished.size() + " Hidden");
        binding.tvEmptyVanished.setVisibility(vanished.isEmpty() ? View.VISIBLE : View.GONE);

        taskAdapter.setTasks(manager.getFocusTasks(this));

        int dayPct = manager.getDayInProgressPercent();
        binding.tvDayProgressLabel.setText("Day in progress " + dayPct + "%");
        binding.pbDayProgress.setProgress(dayPct);
    }

    private void setupSpotlightSearch() {
        binding.etSpotlightSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString();
                binding.btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                List<FocusAppItem> filtered = manager.getAllowedApps(q);
                verticalAdapter.setApps(filtered);
                binding.tvDrawerCount.setText("SEARCH RESULTS (" + filtered.size() + ")");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSpotlightSearch.setText("");
        });
    }

    private void setupTaskActions() {
        binding.btnAddTask.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            showAddTaskDialog();
        });
    }

    private void showAddTaskDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. Read 15 pages of physics");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF64748B);
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Add Focus Task")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        manager.addTask(this, text);
                        taskAdapter.setTasks(manager.getFocusTasks(this));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupDockActions() {
        // 1. Phone
        binding.dockBtnPhone.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                startActivity(dialIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Dialer unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Messages
        binding.dockBtnMessages.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent smsIntent = new Intent(Intent.ACTION_MAIN);
                smsIntent.addCategory(Intent.CATEGORY_APP_MESSAGING);
                startActivity(smsIntent);
            } catch (Exception e) {
                try {
                    Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"));
                    startActivity(fallback);
                } catch (Exception ignored) {}
            }
        });

        // 3. Web Browser
        binding.dockBtnWeb.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"));
                startActivity(webIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Browser unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Camera
        binding.dockBtnCamera.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent cameraIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                startActivity(cameraIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 5. Drawer Toggle
        binding.dockBtnModeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentMode == ScreenMode.APP_DRAWER) {
                switchMode(ScreenMode.DASHBOARD);
            } else {
                switchMode(ScreenMode.APP_DRAWER);
            }
        });

        binding.btnViewAllApps.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(ScreenMode.APP_DRAWER);
        });

        binding.btnSetDefaultLauncher.setOnClickListener(v -> {
            openHomeLauncherSettings();
        });
    }

    private void setupModeSwitching() {
        // Initial state is Dashboard
        switchMode(ScreenMode.DASHBOARD);
    }

    private void switchMode(ScreenMode newMode) {
        currentMode = newMode;

        binding.viewDashboard.setVisibility(newMode == ScreenMode.DASHBOARD ? View.VISIBLE : View.GONE);
        binding.viewAppDrawer.setVisibility(newMode == ScreenMode.APP_DRAWER ? View.VISIBLE : View.GONE);
        binding.viewReflect.setVisibility(newMode == ScreenMode.REFLECT ? View.VISIBLE : View.GONE);

        if (newMode == ScreenMode.APP_DRAWER) {
            binding.dockBtnModeToggle.setImageResource(R.drawable.ic_back);
            binding.dockBtnModeToggle.setColorFilter(getResources().getColor(R.color.elite_gold));
            binding.etSpotlightSearch.requestFocus();
        } else {
            binding.dockBtnModeToggle.setImageResource(R.drawable.ic_apps);
            binding.dockBtnModeToggle.setColorFilter(getResources().getColor(R.color.elite_gold));
        }
    }

    private void launchApp(FocusAppItem item) {
        if (item == null || item.getPackageName() == null) return;
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(item.getPackageName());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "Cannot launch " + item.getAppLabel(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openHomeLauncherSettings() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            } else {
                intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            }
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void startEntranceAnimations() {
        binding.topBar.setTranslationY(-80f);
        binding.topBar.setAlpha(0f);
        binding.topBar.animate().translationY(0f).alpha(1f).setDuration(700).setInterpolator(new DecelerateInterpolator()).start();

        binding.contentFrame.setScaleX(0.92f);
        binding.contentFrame.setScaleY(0.92f);
        binding.contentFrame.setAlpha(0f);
        binding.contentFrame.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800).setStartDelay(150).setInterpolator(new OvershootInterpolator(1.1f)).start();

        binding.bottomDockCapsule.setTranslationY(80f);
        binding.bottomDockCapsule.setAlpha(0f);
        binding.bottomDockCapsule.animate().translationY(0f).alpha(1f).setDuration(700).setStartDelay(250).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void checkUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
            if (mode != AppOpsManager.MODE_ALLOWED) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable Dynamic App Ghosting")
                        .setMessage("EduTrace needs Usage Access to automatically detect and vanish apps that exceed 5 hours today. Please grant this permission.")
                        .setPositiveButton("Grant Access", (d, w) -> {
                            try {
                                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                            } catch (Exception ignored) {}
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }
}

