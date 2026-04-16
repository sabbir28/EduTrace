package org.sabbir.edutrace.ui.activities;
import org.sabbir.edutrace.R;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.data.repository.StudyRepository;
import org.sabbir.edutrace.databinding.ActivityTimerBinding;
import org.sabbir.edutrace.utils.NotificationHelper;
import com.google.android.material.snackbar.Snackbar;

public class TimerActivity extends AppCompatActivity {
    private ActivityTimerBinding binding;
    private long startTime;
    private long breakStartTime;
    private long totalBreakTime = 0;
    private int distractionCount = 0;
    private boolean isPaused = false;
    private long lastResumeTime = 0;
    private long lastAppLeaveTime = 0;
    private Handler timerHandler = new Handler();
    private int subjectId = -1;
    private NotificationManager notificationManager;
    private String subjectName;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private org.sabbir.edutrace.utils.SessionManager sessionManager;
    private org.sabbir.edutrace.utils.SoundscapeManager soundManager;
    private org.sabbir.edutrace.utils.QuoteManager quoteManager;
    private long lastQuoteUpdateTime = 0;
    private Snackbar incomingCallSnackbar;

    private android.content.BroadcastReceiver inAppCallReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (org.sabbir.edutrace.receivers.CallReceiver.ACTION_INCOMING_CALL.equals(intent.getAction())) {
                String number = intent.getStringExtra("INCOMING_NUMBER");
                if (incomingCallSnackbar == null) {
                    incomingCallSnackbar = Snackbar.make(binding.getRoot(), 
                            "📞 Incoming Call: " + number, Snackbar.LENGTH_INDEFINITE);
                    incomingCallSnackbar.getView().setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));
                }
                incomingCallSnackbar.setText("📞 Incoming Call: " + number);
                incomingCallSnackbar.show();
            } else if (org.sabbir.edutrace.receivers.CallReceiver.ACTION_CALL_ENDED.equals(intent.getAction())) {
                if (incomingCallSnackbar != null) {
                    incomingCallSnackbar.dismiss();
                }
            }
        }
    };

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPaused) {
                long sessionMillis = sessionManager.getActiveSessionElapsed();
                long totalTodayMillis = sessionMillis + thisSubjectTodayBeforeNow;
                
                int seconds = (int) (totalTodayMillis / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;
                binding.tvTimer.setText(String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60));
                
                int progress = (int) ((totalTodayMillis % (60 * 60 * 1000)) * 100 / (60 * 60 * 1000));
                binding.timerProgress.setProgress(progress);
                
                // Notification is handled by StudyTimerService
                
                updateRealTimeComparison(sessionMillis);
                updateFlowAura(minutes);
                updateDynamicQuote(minutes);
            } else if (isPaused) {
                // Keep updating the timer text even when paused to show static time
                long sessionMillis = sessionManager.getActiveSessionElapsed();
                long totalTodayMillis = sessionMillis + thisSubjectTodayBeforeNow;
                int seconds = (int) (totalTodayMillis / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;
                binding.tvTimer.setText(String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60));
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    private void updateFlowAura(int minutes) {
        float alpha = 0;
        int color = android.graphics.Color.WHITE;

        if (minutes >= 45) {
            alpha = 0.6f;
            color = getResources().getColor(R.color.amber_glow);
        } else if (minutes >= 20) {
            alpha = 0.4f;
            color = android.graphics.Color.parseColor("#F59E0B"); // Deep Amber
        } else if (minutes >= 5) {
            alpha = 0.2f;
            color = getResources().getColor(R.color.elite_gold);
        }

        if (alpha > 0) {
            binding.auraView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            binding.auraView.animate().alpha(alpha).setDuration(1000);
        }
    }

    private void updateDynamicQuote(int minutes) {
        long now = System.currentTimeMillis();
        if (now - lastQuoteUpdateTime > 5 * 60 * 1000) { // Every 5 minutes
            String quote = (minutes >= 20) ? quoteManager.getFlowQuote() : quoteManager.getRandomFocusQuote();
            binding.tvQuoteText.setText(quote);
            lastQuoteUpdateTime = now;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTimerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bold the timer text
        binding.tvTimer.setTypeface(null, android.graphics.Typeface.BOLD);

        soundManager = new org.sabbir.edutrace.utils.SoundscapeManager(this);
        quoteManager = new org.sabbir.edutrace.utils.QuoteManager(this);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationHelper.createNotificationChannel(this);
        checkNotificationPolicyAccess();
        enableDND();
        checkPhoneStatePermission();

        subjectId = getIntent().getIntExtra("SUBJECT_ID", -1);
        subjectName = getIntent().getStringExtra("SUBJECT_NAME");
        binding.tvSubjectName.setText(subjectName);

        // Apply custom font to the quote text
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
            String lang = prefs.getString("language", "en");
            android.graphics.Typeface tf;
            if ("bn".equals(lang)) {
                tf = android.graphics.Typeface.createFromAsset(getAssets(), "font/ChhatrishJuly/Unicode/Li Chhatrish July Unicode.ttf");
            } else {
                tf = android.graphics.Typeface.createFromAsset(getAssets(), "font/Chillax_Complete/Fonts/TTF/Chillax-Variable.ttf");
            }
            binding.tvQuoteText.setTypeface(tf);
        } catch (Exception e) {
            e.printStackTrace();
        }

        sessionManager = new org.sabbir.edutrace.utils.SessionManager(this);
        
        if (sessionManager.isActive() && sessionManager.getSubjectId() == subjectId) {
            startTime = sessionManager.getStartTime();
            totalBreakTime = sessionManager.getTotalBreakTime();
            distractionCount = sessionManager.getDistractionCount();
            isPaused = sessionManager.isPaused();
            if (isPaused) {
                breakStartTime = sessionManager.getPauseStartTime();
                binding.btnBreak.setText("Resume");
                binding.tvStatus.setText("ON BREAK");
                binding.tvStatus.setTextColor(getResources().getColor(R.color.status_break));
            }
        } else {
            startTime = System.currentTimeMillis();
            sessionManager.startSession(subjectId, subjectName, startTime);
        }

        timerHandler.postDelayed(timerRunnable, 0);

        loadIntelligenceData();

        setupSoundscapeControls();
        startEntranceAnimations();

        // Start Foreground Service Distraction Warden
        Intent serviceIntent = new Intent(this, org.sabbir.edutrace.services.StudyTimerService.class);
        serviceIntent.putExtra("SUBJECT_NAME", subjectName);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        binding.btnBreak.setOnClickListener(v -> {
            performHapticFeedback(v);
            if (!isPaused) {
                isPaused = true;
                breakStartTime = System.currentTimeMillis();
                binding.btnBreak.setText("Resume");
                binding.tvStatus.setText("ON BREAK");
                binding.tvStatus.setTextColor(getResources().getColor(R.color.status_break));
                binding.tvStatus.animate().alpha(0.8f).scaleX(0.9f).scaleY(0.9f).setDuration(300);
                sessionManager.setPaused(true, breakStartTime, totalBreakTime);
            } else {
                isPaused = false;
                totalBreakTime += (System.currentTimeMillis() - breakStartTime);
                lastResumeTime = System.currentTimeMillis();
                binding.tvStatus.setText("FOCUSING");
                binding.tvStatus.setTextColor(getResources().getColor(R.color.status_focus));
                binding.btnBreak.setText("BREAK");
                binding.tvStatus.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(300);
                sessionManager.setPaused(false, 0, totalBreakTime);
            }
        });

        binding.btnDistracted.setOnClickListener(v -> {
            performHapticFeedback(v);
            distractionCount++;
            sessionManager.updateSession(totalBreakTime, distractionCount);
            binding.btnDistracted.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(() -> 
                binding.btnDistracted.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100));
        });

        binding.btnEnd.setOnClickListener(v -> {
            performHapticFeedback(v);
            saveSession();
            finish();
        });

        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void startEntranceAnimations() {
        binding.headerCard.setTranslationY(-100f);
        binding.headerCard.setAlpha(0f);
        binding.headerCard.animate().translationY(0f).alpha(1f).setDuration(800).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        binding.timerWrapper.setScaleX(0.8f);
        binding.timerWrapper.setScaleY(0.8f);
        binding.timerWrapper.setAlpha(0f);
        binding.timerWrapper.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(1000).setStartDelay(200).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();

        binding.intelligenceCard.setTranslationY(100f);
        binding.intelligenceCard.setAlpha(0f);
        binding.intelligenceCard.animate().translationY(0f).alpha(1f).setDuration(800).setStartDelay(300).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        binding.bottomBarCard.setTranslationY(100f);
        binding.bottomBarCard.setAlpha(0f);
        binding.bottomBarCard.animate().translationY(0f).alpha(1f).setDuration(800).setStartDelay(400).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void performHapticFeedback(android.view.View v) {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void setupSoundscapeControls() {
        binding.ivToggleSound.setOnClickListener(v -> {
            if (binding.soundscapePill.getVisibility() == android.view.View.GONE) {
                binding.soundscapePill.setVisibility(android.view.View.VISIBLE);
                binding.soundscapePill.setAlpha(0);
                binding.soundscapePill.animate().alpha(1).setDuration(300);
            } else {
                binding.soundscapePill.animate().alpha(0).setDuration(300).withEndAction(() -> 
                    binding.soundscapePill.setVisibility(android.view.View.GONE));
            }
        });

        binding.btnRain.setOnClickListener(v -> toggleSound("rain", binding.btnRain));
        binding.btnLofi.setOnClickListener(v -> toggleSound("lofi", binding.btnLofi));
        binding.btnNoise.setOnClickListener(v -> toggleSound("noise", binding.btnNoise));
    }

    private void toggleSound(String name, android.widget.ImageView view) {
        int resId = getResources().getIdentifier(name, "raw", getPackageName());
        if (resId != 0) {
            boolean wasPlaying = soundManager.isPlaying(resId);
            soundManager.playSound(resId);
            
            // Reset all icons
            binding.btnRain.setImageAlpha(100);
            binding.btnLofi.setImageAlpha(100);
            binding.btnNoise.setImageAlpha(100);
            
            if (!wasPlaying) {
                view.setImageAlpha(255); // Highlight selected
                view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).withEndAction(() -> 
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200));
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200);
            }
        }
    }

    private void loadIntelligenceData() {
        StudyRepository repo = new StudyRepository(getApplication());
        long todayStart = getStartOfDay(System.currentTimeMillis());
        
        repo.getAllSessions().observe(this, sessions -> {
            long otherSubjectsTimeToday = 0;
            long thisSubjectTimeToday = 0;

            for (StudySession session : sessions) {
                long duration = session.endTimestamp - session.startTimestamp - session.breakDurationMillis;
                if (session.startTimestamp >= todayStart) {
                    if (session.subjectId == subjectId) {
                        thisSubjectTimeToday += duration;
                    } else {
                        otherSubjectsTimeToday += duration;
                    }
                }
            }

            // Initial graph update
            updateComparisonGraph(thisSubjectTimeToday, otherSubjectsTimeToday);
        });
    }

    private void updateRealTimeComparison(long currentSessionMillis) {
        long totalThisSubjectNow = thisSubjectTodayBeforeNow + currentSessionMillis;
        float v1 = (float) totalThisSubjectNow / (1000 * 60 * 60);
        float v2 = (float) otherSubjectsToday / (1000 * 60 * 60);
        binding.comparisonBars.setValues(v1, v2, subjectName, "Other Subjects Today");
    }

    private long otherSubjectsToday = 0;
    private long thisSubjectTodayBeforeNow = 0;

    private void updateComparisonGraph(long thisSubToday, long othersToday) {
        this.thisSubjectTodayBeforeNow = thisSubToday;
        this.otherSubjectsToday = othersToday;
        
        float v1 = (float) thisSubToday / (1000 * 60 * 60);
        float v2 = (float) othersToday / (1000 * 60 * 60);
        binding.comparisonBars.setValues(v1, v2, subjectName, "Other Subjects");
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

    private void saveSession() {
        disableDND();
        NotificationHelper.clearFocusNotification(this);
        long endTime = System.currentTimeMillis();
        StudySession session = new StudySession(subjectId, startTime, endTime, totalBreakTime, distractionCount, "");
        new StudyRepository(getApplication()).insertSession(session);
        
        sessionManager.clearSession();

        // Stop Foreground Service when session is explicitly ended
        Intent serviceIntent = new Intent(this, org.sabbir.edutrace.services.StudyTimerService.class);
        stopService(serviceIntent);

        // Notify Widget
        Intent intent = new Intent(this, org.sabbir.edutrace.widget.StudyPulseWidget.class);
        intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        sendBroadcast(intent);
    }

    private void checkNotificationPolicyAccess() {
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            new AlertDialog.Builder(this)
                .setTitle("Distraction-Free Mode")
                .setMessage("To automatically block WhatsApp/FB notifications, EduTrace needs 'Do Not Disturb' access. Please enable it in the next screen.")
                .setPositiveButton("Grant Access", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Skip", null)
                .show();
        }
    }

    private void checkPhoneStatePermission() {
        android.content.SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        if (prefs.getBoolean("library_mode", false)) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_PHONE_STATE}, 102);
            }
        }
    }

    private void enableDND() {
        if (notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        }
    }

    private void disableDND() {
        if (notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        lastAppLeaveTime = System.currentTimeMillis();
        unregisterReceiver(inAppCallReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(org.sabbir.edutrace.receivers.CallReceiver.ACTION_INCOMING_CALL);
        filter.addAction(org.sabbir.edutrace.receivers.CallReceiver.ACTION_CALL_ENDED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inAppCallReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(inAppCallReceiver, filter);
        }
        
        if (lastAppLeaveTime > 0) {
            long awayTime = System.currentTimeMillis() - lastAppLeaveTime;
            if (awayTime > 5000 && !isPaused) { // If away for more than 5s
                long penalty = awayTime / 2; // Subtract 50% of the time they were away
                distractionCount++; // Automatically add a distraction point!
                totalBreakTime += penalty; // Effectively reduces study time
                
                android.widget.Toast.makeText(this, "Distraction applied! 50% penalty applied.", android.widget.Toast.LENGTH_LONG).show();
            }
            lastAppLeaveTime = 0;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (soundManager != null) soundManager.stop();
    }
}
