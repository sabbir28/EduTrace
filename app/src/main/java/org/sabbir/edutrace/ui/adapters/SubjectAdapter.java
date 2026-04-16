package org.sabbir.edutrace.ui.adapters;
import org.sabbir.edutrace.R;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.databinding.ItemSubjectBinding;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {
    private List<Subject> subjects = new ArrayList<>();
    private java.util.Map<Integer, Long> subjectTimes = new java.util.HashMap<>();
    private OnSubjectClickListener listener;
    private OnSubjectLongClickListener longClickListener;

    public interface OnSubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public interface OnSubjectLongClickListener {
        void onSubjectLongClick(Subject subject);
    }

    public void setOnSubjectClickListener(OnSubjectClickListener listener) {
        this.listener = listener;
    }

    public void setOnSubjectLongClickListener(OnSubjectLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
        notifyDataSetChanged();
    }

    public void setSubjectTimes(java.util.Map<Integer, Long> times) {
        this.subjectTimes = times;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubjectBinding binding = ItemSubjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SubjectViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        holder.binding.tvSubjectName.setText(subject.name);
        
        long totalMillis = subjectTimes.containsKey(subject.id) ? subjectTimes.get(subject.id) : 0L;
        int totalSeconds = (int) (totalMillis / 1000);
        int totalMinutes = totalSeconds / 60;
        int totalHours = totalMinutes / 60;
        holder.binding.tvSubjectStats.setText(String.format("Today: %dh %dm", totalHours, totalMinutes % 60));

        try {
            holder.binding.subjectColor.setBackgroundColor(Color.parseColor(subject.colorHex));
        } catch (Exception e) {
            // Default
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSubjectClick(subject);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onSubjectLongClick(subject);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        ItemSubjectBinding binding;
        SubjectViewHolder(ItemSubjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
