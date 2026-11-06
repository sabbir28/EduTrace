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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.data.cloud.CloudUser;
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

    private enum ScreenMode { DASHBOARD, MINIMALIST_HOME, APP_DRAWER, REFLECT }
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
        setupAlphabetScrubber();
        setupDockActions();
        setupTaskActions();
        setupWeatherActions();
        setupModeSwitching();
        startEntranceAnimations();

        checkUsageStatsPermission();
        checkDefaultLauncherPrompt();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLauncherData();
        updateDefaultLauncherBanner();
    }

    @Override
    public void onBackPressed() {
        // As a Home launcher, Back returns to the clean Minimalist Home or Dashboard instead of closing
        if (currentMode == ScreenMode.APP_DRAWER || currentMode == ScreenMode.REFLECT) {
            switchMode(ScreenMode.DASHBOARD);
        } else if (currentMode == ScreenMode.MINIMALIST_HOME) {
            switchMode(ScreenMode.DASHBOARD);
        }
    }

    private void setupHeaderData() {
        // Date & Day format
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
        SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date now = new Date();

        String dayStr = dayFormat.format(now);
        String dateStr = dateFormat.format(now);

        binding.tvEditorialDay.setText(dayStr);
        binding.tvEditorialDate.setText(dateStr);
        binding.tvDrawerClock.setText(clockFormat.format(now));
        binding.tvDrawerDate.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));

        // Display user name if logged in, else Guest
        CloudUser user = CloudDbManager.getCurrentUser(this);
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            binding.tvGreetingName.setText(user.getDisplayName());
        } else {
            binding.tvGreetingName.setText("Guest");
        }

        int dayPct = manager.getDayInProgressPercent();
        binding.tvDayProgressLabel.setText("Day in progress " + dayPct + "%");
        binding.pbDayProgress.setProgress(dayPct);

        // Header Action Buttons
        binding.btnUserProfile.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(currentMode == ScreenMode.MINIMALIST_HOME ? ScreenMode.DASHBOARD : ScreenMode.MINIMALIST_HOME);
        });

        binding.btnModeDashboard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(ScreenMode.DASHBOARD);
        });

        binding.btnModeReflect.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(ScreenMode.REFLECT);
        });

        binding.btnOpenTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startActivity(new Intent(this, TimerActivity.class));
        });

        binding.btnQuickSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Default Launcher Banner
        binding.bannerDefaultLauncher.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FocusLauncherManager.requestSetDefaultLauncher(this);
        });

        binding.btnSetDefaultLauncherBanner.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FocusLauncherManager.requestSetDefaultLauncher(this);
        });
    }

    private void updateDefaultLauncherBanner() {
        boolean isDefault = FocusLauncherManager.isDefaultLauncher(this);
        if (isDefault) {
            binding.layoutDefaultLauncherBanner.setBackgroundResource(R.drawable.bg_default_active_pill);
            binding.ivDefaultShield.setColorFilter(0xFF10B981);
            binding.tvDefaultBannerTitle.setText("Default Focus Shield Active ✓");
            binding.tvDefaultBannerDesc.setText("Cognitive focus locked & 5-hour distraction vanishing active");
            binding.btnSetDefaultLauncherBanner.setVisibility(View.GONE);
        } else {
            binding.layoutDefaultLauncherBanner.setBackgroundResource(R.drawable.bg_default_launcher_pill);
            binding.ivDefaultShield.setColorFilter(0xFFF59E0B);
            binding.tvDefaultBannerTitle.setText("Set as Default Home Launcher");
            binding.tvDefaultBannerDesc.setText("Protect your focus with automatic 5-hour app vanishing");
            binding.btnSetDefaultLauncherBanner.setVisibility(View.VISIBLE);
        }
    }

    private void checkDefaultLauncherPrompt() {
        if (!FocusLauncherManager.isDefaultLauncher(this)) {
            // Delay slightly so activity renders smoothly before showing system chooser
            binding.getRoot().postDelayed(() -> {
                if (!isFinishing() && !FocusLauncherManager.isDefaultLauncher(this)) {
                    FocusLauncherManager.requestSetDefaultLauncher(this);
                }
            }, 800);
        }
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

        // Screen time analytics in Reflect screen
        binding.tvPeakHour.setText(manager.getFormattedPeakHour(this));
        binding.tvLateNightUsage.setText(manager.getLateNightAppsSummary(this));

        long totalMs = manager.getTotalScreenTimeMs(this);
        long hours = totalMs / (3600 * 1000);
        long mins = (totalMs % (3600 * 1000)) / (60 * 1000);
        binding.tvScreenTimeSummary.setText("Holding steady. Total screen time today: " + hours + "h " + mins + "m across allowed apps.");

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

        binding.layoutSpotlightCapsule.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(ScreenMode.APP_DRAWER);
        });
    }

    private void setupAlphabetScrubber() {
        binding.layoutAlphabetScrubber.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                int height = v.getHeight();
                if (height <= 0) return true;
                float y = Math.max(0, Math.min(event.getY(), height));
                int count = binding.layoutAlphabetScrubber.getChildCount();
                int index = (int) ((y / height) * count);
                if (index >= 0 && index < count) {
                    View child = binding.layoutAlphabetScrubber.getChildAt(index);
                    if (child instanceof TextView) {
                        String letter = ((TextView) child).getText().toString();
                        if (!letter.isEmpty()) {
                            int pos = verticalAdapter.getPositionForSection(letter.charAt(0));
                            if (pos >= 0) {
                                binding.rvVerticalApps.scrollToPosition(pos);
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            }
                        }
                    }
                }
                return true;
            }
            return false;
        });
    }

    private void setupTaskActions() {
        binding.btnAddTask.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAddTaskDialog();
        });

        binding.btnAddFocusTaskRow.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAddTaskDialog();
        });

        binding.btnEditTile.setOnClickListener(v -> {
            Toast.makeText(this, "Tile configuration saved", Toast.LENGTH_SHORT).show();
        });

        binding.btnAddTile.setOnClickListener(v -> {
            showAddTaskDialog();
        });
    }

    private void setupWeatherActions() {
        binding.btnWeatherRefresh.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            v.animate().rotationBy(360f).setDuration(600).start();
            Toast.makeText(this, "Weather forecast updated ☀️", Toast.LENGTH_SHORT).show();
        });

        binding.btnWeatherEdit.setOnClickListener(v -> {
            Toast.makeText(this, "Weather location: Cox Mill (Focus Mode)", Toast.LENGTH_SHORT).show();
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
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                startActivity(dialIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Dialer unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Messages
        binding.dockBtnMessages.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"));
                startActivity(webIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Browser unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Camera
        binding.dockBtnCamera.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent cameraIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                startActivity(cameraIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 5. Drawer Toggle
        binding.dockBtnModeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentMode == ScreenMode.APP_DRAWER) {
                switchMode(ScreenMode.DASHBOARD);
            } else {
                switchMode(ScreenMode.APP_DRAWER);
            }
        });
    }

    private void setupModeSwitching() {
        switchMode(ScreenMode.DASHBOARD);

        binding.tabNow.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(ScreenMode.DASHBOARD);
        });

        binding.tabReflect.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(ScreenMode.REFLECT);
        });
    }

    private void switchMode(ScreenMode newMode) {
        currentMode = newMode;

        binding.viewDashboard.setVisibility(newMode == ScreenMode.DASHBOARD ? View.VISIBLE : View.GONE);
        binding.viewMinimalistHome.setVisibility(newMode == ScreenMode.MINIMALIST_HOME ? View.VISIBLE : View.GONE);
        binding.viewAppDrawer.setVisibility(newMode == ScreenMode.APP_DRAWER ? View.VISIBLE : View.GONE);
        binding.viewReflect.setVisibility(newMode == ScreenMode.REFLECT ? View.VISIBLE : View.GONE);

        // Header Tab Highlight
        binding.btnModeDashboard.setColorFilter(
                (newMode == ScreenMode.DASHBOARD || newMode == ScreenMode.MINIMALIST_HOME)
                        ? getResources().getColor(R.color.elite_gold) : 0xFF94A3B8
        );
        binding.btnModeReflect.setColorFilter(
                newMode == ScreenMode.REFLECT ? getResources().getColor(R.color.elite_gold) : 0xFF94A3B8
        );

        if (newMode == ScreenMode.APP_DRAWER) {
            binding.ivDockModeToggle.setImageResource(R.drawable.ic_back);
            binding.ivDockModeToggle.setColorFilter(getResources().getColor(R.color.elite_gold));
            binding.etSpotlightSearch.requestFocus();
        } else {
            binding.ivDockModeToggle.setImageResource(R.drawable.ic_apps);
            binding.ivDockModeToggle.setColorFilter(getResources().getColor(R.color.elite_gold));
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
