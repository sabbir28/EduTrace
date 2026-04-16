package org.sabbir.edutrace.ui.adapters;
import org.sabbir.edutrace.R;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.sabbir.edutrace.data.models.StudySession;
import org.sabbir.edutrace.databinding.ItemSessionBinding;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<StudySession> sessions = new ArrayList<>();
    private Map<Integer, String> subjectNames;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());

    public void setSessions(List<StudySession> sessions, Map<Integer, String> subjectNames) {
        this.sessions = sessions;
        this.subjectNames = subjectNames;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSessionBinding binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SessionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        StudySession session = sessions.get(position);
        
        holder.binding.tvDate.setText(dateFormat.format(new Date(session.startTimestamp)));
        
        String subjectName = subjectNames != null ? subjectNames.get(session.subjectId) : "Unknown Subject";
        holder.binding.tvSubjectName.setText(subjectName);
        
        long duration = session.endTimestamp - session.startTimestamp - session.breakDurationMillis;
        int seconds = (int) (duration / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        holder.binding.tvDuration.setText(String.format("%dh %dm", hours, minutes % 60));
        
        holder.binding.tvDistractions.setText(session.distractionCount + " Distractions");
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        ItemSessionBinding binding;
        SessionViewHolder(ItemSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
