package org.sabbir.edutrace.ui.activities;
import org.sabbir.edutrace.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.textfield.TextInputEditText;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.databinding.ActivityMainBinding;
import org.sabbir.edutrace.ui.adapters.DegreeAdapter;
import org.sabbir.edutrace.viewmodels.StudyViewModel;

public class MainActivity extends AppCompatActivity implements DegreeAdapter.OnDegreeClickListener, DegreeAdapter.OnDegreeLongClickListener {
    private ActivityMainBinding binding;
    private StudyViewModel viewModel;
    private DegreeAdapter adapter;
    private org.sabbir.edutrace.utils.SessionManager sessionManager;
    private android.os.Handler updateHandler = new android.os.Handler();
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionManager.isActive()) {
                refreshStats();
            }
            updateHandler.postDelayed(this, 30000); // Update every 30 seconds
        }
    };

    private java.util.List<org.sabbir.edutrace.data.models.Subject> lastSubjects = new java.util.ArrayList<>();
    private java.util.List<org.sabbir.edutrace.data.models.StudySession> lastSessions = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        sessionManager = new org.sabbir.edutrace.utils.SessionManager(this);

        setSupportActionBar(binding.toolbar);

        adapter = new DegreeAdapter();
        adapter.setOnDegreeClickListener(this);
        adapter.setOnDegreeLongClickListener(this);
        binding.rvDegrees.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDegrees.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(StudyViewModel.class);
        viewModel.getAllDegrees().observe(this, degrees -> {
            adapter.setDegrees(degrees);
        });

        // Live update for today's study stats and degree distribution
        viewModel.getAllSubjects().observe(this, allSubjects -> {
            this.lastSubjects = allSubjects;
            refreshStats();
        });

        viewModel.getAllSessions().observe(this, sessions -> {
            this.lastSessions = sessions;
            refreshStats();
        });

        binding.btnViewHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportsActivity.class);
            startActivity(intent);
        });

        binding.fabStart.setText("Add Degree");
        binding.fabStart.setOnClickListener(v -> showAddDegreeDialog());

        startAppUsageService();
        scheduleStudyReminder();
        requestNotificationPermission();
        checkForAppUpdates();
    }

    private void checkForAppUpdates() {
        try {
            boolean autoCheck = getSharedPreferences("Settings", MODE_PRIVATE).getBoolean("auto_check_updates", true);
            if (autoCheck) {
                org.sabbir.edutrace.utils.UpdateManager.checkForUpdates(this, false);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 103);
            }
        }
    }

    private void scheduleStudyReminder() {
        try {
            androidx.work.PeriodicWorkRequest reminderRequest =
                    new androidx.work.PeriodicWorkRequest.Builder(org.sabbir.edutrace.services.StudyReminderWorker.class,
                            24, java.util.concurrent.TimeUnit.HOURS)
                            .setInitialDelay(12, java.util.concurrent.TimeUnit.HOURS) // Start roughly in the evening
                            .build();

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "StudyReminder",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    reminderRequest
            );
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void startAppUsageService() {
        try {
            Intent serviceIntent = new Intent(this, org.sabbir.edutrace.services.AppBlockService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        
        try {
            checkUsagePermissions();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void checkUsagePermissions() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("To block apps after 5 hours of use, EduTrace needs to draw over other apps. Please grant this permission.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
        }
        
        // Usage Stats permission is also needed, but harder to check in a simple way without just trying to query or sending to settings
        // I'll add a check for it too.
        if (!hasUsageStatsPermission()) {
            new AlertDialog.Builder(this)
                .setTitle("Usage Stats Permission Required")
                .setMessage("To track app usage, EduTrace needs access to usage statistics. Please enable it in Settings.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
                })
                .setNegativeButton("Later", null)
                .show();
        }
    }

    private boolean hasUsageStatsPermission() {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now);
            return stats != null && !stats.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshStats() {
        if (lastSubjects == null || lastSessions == null) return;

        long now = System.currentTimeMillis();
        long todayStart = getStartOfDay(now);
        long yesterdayStart = todayStart - (24 * 60 * 60 * 1000);
        
        long totalMillisToday = 0;
        long totalMillisYesterday = 0;
        int distractionsToday = 0;
        int sessionsTodayCount = 0;
        
        java.util.HashMap<Integer, Long> degreeTimeMap = new java.util.HashMap<>();
        long totalOverallMillis = 0;

        // Create subject to degree mapping
        java.util.Map<Integer, Integer> subjectToDegree = new java.util.HashMap<>();
        for (org.sabbir.edutrace.data.models.Subject subject : lastSubjects) {
            subjectToDegree.put(subject.id, subject.degreeId);
        }
        
        for (org.sabbir.edutrace.data.models.StudySession session : lastSessions) {
            long duration = session.endTimestamp - session.startTimestamp - session.breakDurationMillis;
            
            // Daily comparisons
            if (session.startTimestamp >= todayStart) {
                totalMillisToday += duration;
                distractionsToday += session.distractionCount;
                sessionsTodayCount++;
            } else if (session.startTimestamp >= yesterdayStart && session.startTimestamp < todayStart) {
                totalMillisYesterday += duration;
            }

            // Total distribution by degree
            Integer degId = subjectToDegree.get(session.subjectId);
            if (degId != null) {
                degreeTimeMap.put(degId, degreeTimeMap.getOrDefault(degId, 0L) + duration);
                totalOverallMillis += duration;
            }
        }
        
        updateTodayStats(totalMillisToday, distractionsToday, sessionsTodayCount, totalMillisYesterday);
        updateDegreeDistribution(degreeTimeMap, totalOverallMillis);
        updateWeeklyGraph(lastSessions);
    }

    private void updateWeeklyGraph(java.util.List<org.sabbir.edutrace.data.models.StudySession> sessions) {
        long now = System.currentTimeMillis();
        long todayStart = getStartOfDay(now);
        java.util.List<Float> weeklyHours = new java.util.ArrayList<>();
        
        for (int i = 6; i >= 0; i--) {
            long dayStart = todayStart - (i * 24 * 60 * 60 * 1000L);
            long dayEnd = dayStart + (24 * 60 * 60 * 1000L);
            long totalMillis = 0;
            
            for (org.sabbir.edutrace.data.models.StudySession session : sessions) {
                if (session.startTimestamp >= dayStart && session.startTimestamp < dayEnd) {
                    totalMillis += (session.endTimestamp - session.startTimestamp - session.breakDurationMillis);
                }
            }
            weeklyHours.add((float) totalMillis / (1000 * 60 * 60));
        }
        binding.studyGraph.setData(weeklyHours, 6);
    }

    private long getStartOfDay(long time) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void updateTodayStats(long totalMillis, int distractions, int sessionsCount, long yesterdayMillis) {
        if (sessionManager.isActive()) {
            totalMillis += sessionManager.getActiveSessionElapsed();
            distractions += sessionManager.getDistractionCount();
            // We don't increment sessionsCount as it's not finished
        }

        int seconds = (int) (totalMillis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        binding.tvTotalTime.setText(String.format("%dh %dm", hours, minutes % 60));
        
        long goalMillis = 8L * 60 * 60 * 1000;
        int progress = (int) ((totalMillis * 100) / goalMillis);
        binding.progressToday.setProgress(Math.min(progress, 100));
        
        binding.tvSessionsToday.setText(sessionsCount + " Sessions Today");
        binding.tvDistractionsToday.setText(distractions + " Distractions");

        // Comparison logic
        if (yesterdayMillis > 0) {
            binding.tvComparison.setVisibility(View.VISIBLE);
            long diff = totalMillis - yesterdayMillis;
            double percent = (Math.abs(diff) * 100.0) / yesterdayMillis;
            if (diff >= 0) {
                binding.tvComparison.setText(String.format("+%.1f%% vs yesterday", percent));
                binding.tvComparison.setTextColor(android.graphics.Color.parseColor("#10B981")); // Green
            } else {
                binding.tvComparison.setText(String.format("-%.1f%% vs yesterday", percent));
                binding.tvComparison.setTextColor(android.graphics.Color.parseColor("#F43F5E")); // Red
            }
        } else {
            binding.tvComparison.setVisibility(View.GONE);
        }
    }

    private void updateDegreeDistribution(java.util.HashMap<Integer, Long> degreeTimeMap, long totalOverallMillis) {
        java.util.HashMap<Integer, Integer> degreePercentMap = new java.util.HashMap<>();
        if (totalOverallMillis > 0) {
            for (java.util.Map.Entry<Integer, Long> entry : degreeTimeMap.entrySet()) {
                int percent = (int) ((entry.getValue() * 100) / totalOverallMillis);
                degreePercentMap.put(entry.getKey(), percent);
            }
        }
        adapter.setDegreePercentages(degreePercentMap);
    }

    private void showAddDegreeDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_degree, null);
        TextInputEditText etDegreeName = view.findViewById(R.id.etDegreeName);
        RadioGroup rgColors = view.findViewById(R.id.rgColors);

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etDegreeName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Please enter degree name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String colorHex = "#FACC15"; // Default Yellow
                    int checkedId = rgColors.getCheckedRadioButtonId();
                    if (checkedId == R.id.colorBlue) colorHex = "#3B82F6";
                    else if (checkedId == R.id.colorGreen) colorHex = "#22C55E";
                    else if (checkedId == R.id.colorRed) colorHex = "#EF4444";
                    else if (checkedId == R.id.colorPurple) colorHex = "#A855F7";

                    Degree degree = new Degree(name, colorHex);
                    viewModel.insertDegree(degree);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDegreeClick(Degree degree) {
        Intent intent = new Intent(this, SubjectActivity.class);
        intent.putExtra("DEGREE_ID", degree.id);
        intent.putExtra("DEGREE_NAME", degree.name);
        startActivity(intent);
    }

    @Override
    public void onDegreeLongClick(Degree degree) {
        String[] options = {"Edit Name", "Delete Degree"};
        new AlertDialog.Builder(this)
                .setTitle(degree.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditDegreeDialog(degree);
                    else if (which == 1) showDeleteConfirmDialog(degree);
                })
                .show();
    }

    private void showEditDegreeDialog(Degree degree) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_degree, null);
        TextInputEditText etDegreeName = view.findViewById(R.id.etDegreeName);
        RadioGroup rgColors = view.findViewById(R.id.rgColors);
        
        etDegreeName.setText(degree.name);
        // Map current color back to radio button if needed, or just let them pick a new one
        
        new AlertDialog.Builder(this)
                .setTitle("Edit Degree")
                .setView(view)
                .setPositiveButton("Update", (dialog, which) -> {
                    String name = etDegreeName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        degree.name = name;
                        // Update color based on selection
                        int checkedId = rgColors.getCheckedRadioButtonId();
                        if (checkedId == R.id.colorBlue) degree.colorHex = "#3B82F6";
                        else if (checkedId == R.id.colorGreen) degree.colorHex = "#22C55E";
                        else if (checkedId == R.id.colorRed) degree.colorHex = "#EF4444";
                        else if (checkedId == R.id.colorPurple) degree.colorHex = "#A855F7";
                        else degree.colorHex = "#FACC15";

                        viewModel.updateDegree(degree);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmDialog(Degree degree) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Degree?")
                .setMessage("All subjects and sessions for " + degree.name + " will be deleted.")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteDegree(degree))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reports) {
            startActivity(new Intent(this, ReportsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
        updateHandler.postDelayed(updateRunnable, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }
}
