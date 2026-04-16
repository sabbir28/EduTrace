package org.sabbir.edutrace.ui.activities;
import org.sabbir.edutrace.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.textfield.TextInputEditText;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.databinding.ActivitySubjectBinding;
import org.sabbir.edutrace.ui.adapters.SubjectAdapter;
import org.sabbir.edutrace.viewmodels.StudyViewModel;

public class SubjectActivity extends AppCompatActivity implements SubjectAdapter.OnSubjectClickListener, SubjectAdapter.OnSubjectLongClickListener {
    private ActivitySubjectBinding binding;
    private StudyViewModel viewModel;
    private SubjectAdapter adapter;
    private int degreeId;
    private String degreeName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        degreeId = getIntent().getIntExtra("DEGREE_ID", -1);
        degreeName = getIntent().getStringExtra("DEGREE_NAME");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(degreeName + " Subjects");
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        adapter = new SubjectAdapter();
        adapter.setOnSubjectClickListener(this);
        adapter.setOnSubjectLongClickListener(this);
        binding.rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSubjects.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(StudyViewModel.class);
        viewModel.getSubjectsForDegree(degreeId).observe(this, subjects -> {
            adapter.setSubjects(subjects);
        });

        // Track sessions to show today's subject stats
        viewModel.getAllSessions().observe(this, sessions -> {
            java.util.HashMap<Integer, Long> timeMap = new java.util.HashMap<>();
            java.util.Calendar today = java.util.Calendar.getInstance();
            int year = today.get(java.util.Calendar.YEAR);
            int dayOfYear = today.get(java.util.Calendar.DAY_OF_YEAR);

            for (org.sabbir.edutrace.data.models.StudySession session : sessions) {
                java.util.Calendar sessionDate = java.util.Calendar.getInstance();
                sessionDate.setTimeInMillis(session.startTimestamp);
                
                if (sessionDate.get(java.util.Calendar.YEAR) == year && 
                    sessionDate.get(java.util.Calendar.DAY_OF_YEAR) == dayOfYear) {
                    
                    long duration = session.endTimestamp - session.startTimestamp - session.breakDurationMillis;
                    timeMap.put(session.subjectId, timeMap.getOrDefault(session.subjectId, 0L) + duration);
                }
            }
            adapter.setSubjectTimes(timeMap);
        });

        binding.fabAddSubject.setOnClickListener(v -> showAddSubjectDialog());
    }

    private void showAddSubjectDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null);
        TextInputEditText etSubjectName = view.findViewById(R.id.etSubjectName);

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etSubjectName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Please enter subject name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // For now, use a default color or same as degree
                    // In a more advanced version, we could pick a sub-color
                    Subject subject = new Subject(degreeId, name, "#FACC15");
                    viewModel.insertSubject(subject);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onSubjectClick(Subject subject) {
        Intent intent = new Intent(this, TimerActivity.class);
        intent.putExtra("SUBJECT_ID", subject.id);
        intent.putExtra("SUBJECT_NAME", subject.name);
        startActivity(intent);
    }

    @Override
    public void onSubjectLongClick(Subject subject) {
        String[] options = {"Rename Subject", "Delete Subject"};
        new AlertDialog.Builder(this)
                .setTitle(subject.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditSubjectDialog(subject);
                    else if (which == 1) showDeleteSubjectConfirmDialog(subject);
                })
                .show();
    }

    private void showEditSubjectDialog(Subject subject) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null);
        TextInputEditText etSubjectName = view.findViewById(R.id.etSubjectName);
        etSubjectName.setText(subject.name);

        new AlertDialog.Builder(this)
                .setTitle("Rename Subject")
                .setView(view)
                .setPositiveButton("Update", (dialog, which) -> {
                    String name = etSubjectName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        subject.name = name;
                        viewModel.updateSubject(subject);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteSubjectConfirmDialog(Subject subject) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Subject?")
                .setMessage("All study sessions for " + subject.name + " will be deleted.")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteSubject(subject))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
