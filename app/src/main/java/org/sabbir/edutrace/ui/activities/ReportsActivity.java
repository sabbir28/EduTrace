package org.sabbir.edutrace.ui.activities;
import org.sabbir.edutrace.R;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.databinding.ActivityReportsBinding;
import org.sabbir.edutrace.ui.adapters.StudyBreakdownAdapter;
import org.sabbir.edutrace.viewmodels.StudyViewModel;

public class ReportsActivity extends AppCompatActivity {
    private ActivityReportsBinding binding;
    private StudyViewModel viewModel;
    private StudyBreakdownAdapter breakdownAdapter;
    private int weekOffset = 0;
    private int selectedDayIndex = -1;
    private List<StudySession> allSessions = new ArrayList<>();
    private List<Subject> allSubjects = new ArrayList<>();
    private List<Degree> allDegrees = new ArrayList<>();
    
    private Calendar currentWeekStart;
    private SimpleDateFormat weekRangeFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
    private SimpleDateFormat dayFullFormat = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        breakdownAdapter = new StudyBreakdownAdapter();
        binding.rvDayBreakdown.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDayBreakdown.setAdapter(breakdownAdapter);

        viewModel = new ViewModelProvider(this).get(StudyViewModel.class);

        Calendar today = Calendar.getInstance();
        selectedDayIndex = today.get(Calendar.DAY_OF_WEEK) - 1;

        binding.btnPrevWeek.setOnClickListener(v -> {
            weekOffset--;
            updateContent();
        });

        binding.btnNextWeek.setOnClickListener(v -> {
            weekOffset++;
            updateContent();
        });

        binding.studyGraph.setOnDaySelectedListener(index -> {
            selectedDayIndex = index;
            updateDailyDetail();
        });

        viewModel.getAllDegrees().observe(this, degrees -> {
            allDegrees = degrees;
            viewModel.getAllSubjects().observe(this, subjects -> {
                allSubjects = subjects;
                viewModel.getAllSessions().observe(this, sessions -> {
                    allSessions = sessions;
                    updateContent();
                });
            });
        });
    }

    private void updateContent() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        currentWeekStart = (Calendar) cal.clone();

        String startStr = weekRangeFormat.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 6);
        String endStr = weekRangeFormat.format(cal.getTime());
        binding.tvWeekRange.setText(startStr + " - " + endStr);

        List<Float> weeklyHours = new ArrayList<>();
        Calendar dayCal = (Calendar) currentWeekStart.clone();
        for (int i = 0; i < 7; i++) {
            long dayStart = dayCal.getTimeInMillis();
            dayCal.add(Calendar.DAY_OF_YEAR, 1);
            long dayEnd = dayCal.getTimeInMillis();
            
            long totalMillis = 0;
            for (StudySession session : allSessions) {
                if (session.startTimestamp >= dayStart && session.startTimestamp < dayEnd) {
                    totalMillis += (session.endTimestamp - session.startTimestamp - session.breakDurationMillis);
                }
            }
            weeklyHours.add((float) totalMillis / (1000 * 60 * 60));
        }
        binding.studyGraph.setData(weeklyHours, selectedDayIndex);
        
        updateDailyDetail();
    }

    private void updateDailyDetail() {
        Calendar cal = (Calendar) currentWeekStart.clone();
        cal.add(Calendar.DAY_OF_YEAR, selectedDayIndex);
        long dayStart = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long dayEnd = cal.getTimeInMillis();

        binding.tvSelectedDay.setText(dayFullFormat.format(new Date(dayStart)).toUpperCase());

        Map<Integer, Long> subjectTimeMap = new HashMap<>();
        long totalMillis = 0;
        for (StudySession session : allSessions) {
            if (session.startTimestamp >= dayStart && session.startTimestamp < dayEnd) {
                long duration = session.endTimestamp - session.startTimestamp - session.breakDurationMillis;
                subjectTimeMap.put(session.subjectId, subjectTimeMap.getOrDefault(session.subjectId, 0L) + duration);
                totalMillis += duration;
            }
        }

        int seconds = (int) (totalMillis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        binding.tvDayTotalTime.setText(String.format("%dh %dm", hours, minutes % 60));

        // Create breakdown items
        List<StudyBreakdownAdapter.BreakdownItem> breakdownItems = new ArrayList<>();
        Map<Integer, Degree> degreeMap = new HashMap<>();
        for (Degree d : allDegrees) degreeMap.put(d.id, d);
        
        for (Map.Entry<Integer, Long> entry : subjectTimeMap.entrySet()) {
            Subject subject = null;
            for (Subject s : allSubjects) if (s.id == entry.getKey()) subject = s;
            
            if (subject != null) {
                Degree degree = degreeMap.get(subject.degreeId);
                String degreeName = (degree != null) ? degree.name : "Unknown Degree";
                String colorHex = (degree != null) ? degree.colorHex : "#FACC15";
                
                int percentage = (totalMillis > 0) ? (int) (entry.getValue() * 100 / totalMillis) : 0;
                breakdownItems.add(new StudyBreakdownAdapter.BreakdownItem(subject.name, degreeName, colorHex, entry.getValue(), percentage));
            }
        }

        if (breakdownItems.isEmpty()) {
            binding.rvDayBreakdown.setVisibility(View.GONE);
        } else {
            binding.rvDayBreakdown.setVisibility(View.VISIBLE);
            breakdownAdapter.setItems(breakdownItems);
        }
    }
}
