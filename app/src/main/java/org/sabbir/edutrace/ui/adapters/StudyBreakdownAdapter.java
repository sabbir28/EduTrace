package org.sabbir.edutrace.ui.adapters;
import org.sabbir.edutrace.R;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.sabbir.edutrace.databinding.ItemBreakdownBinding;

public class StudyBreakdownAdapter extends RecyclerView.Adapter<StudyBreakdownAdapter.BreakdownViewHolder> {
    private List<BreakdownItem> items = new ArrayList<>();

    public static class BreakdownItem {
        public String subjectName;
        public String degreeName;
        public String colorHex;
        public long totalMillis;
        public int percentage;

        public BreakdownItem(String subjectName, String degreeName, String colorHex, long totalMillis, int percentage) {
            this.subjectName = subjectName;
            this.degreeName = degreeName;
            this.colorHex = colorHex;
            this.totalMillis = totalMillis;
            this.percentage = percentage;
        }
    }

    public void setItems(List<BreakdownItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BreakdownViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBreakdownBinding binding = ItemBreakdownBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new BreakdownViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BreakdownViewHolder holder, int position) {
        BreakdownItem item = items.get(position);
        holder.binding.tvSubjectName.setText(item.subjectName + " (" + item.percentage + "%)");
        holder.binding.tvDegreeName.setText(item.degreeName);
        
        int hours = (int) (item.totalMillis / (1000 * 60 * 60));
        int minutes = (int) ((item.totalMillis / (1000 * 60)) % 60);
        holder.binding.tvTotalTime.setText(String.format("%dh %dm", hours, minutes));
        
        try {
            holder.binding.subjectColor.setBackgroundColor(Color.parseColor(item.colorHex));
        } catch (Exception e) {
            holder.binding.subjectColor.setBackgroundColor(Color.parseColor("#FACC15"));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BreakdownViewHolder extends RecyclerView.ViewHolder {
        ItemBreakdownBinding binding;
        BreakdownViewHolder(ItemBreakdownBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
