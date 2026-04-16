package org.sabbir.edutrace.ui.adapters;
import org.sabbir.edutrace.R;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.databinding.ItemDegreeBinding;

public class DegreeAdapter extends RecyclerView.Adapter<DegreeAdapter.DegreeViewHolder> {
    private List<Degree> degrees = new ArrayList<>();
    private java.util.Map<Integer, Integer> degreePercentages = new java.util.HashMap<>();
    private OnDegreeClickListener listener;
    private OnDegreeLongClickListener longClickListener;

    public interface OnDegreeClickListener {
        void onDegreeClick(Degree degree);
    }

    public interface OnDegreeLongClickListener {
        void onDegreeLongClick(Degree degree);
    }

    public void setOnDegreeClickListener(OnDegreeClickListener listener) {
        this.listener = listener;
    }

    public void setOnDegreeLongClickListener(OnDegreeLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setDegrees(List<Degree> degrees) {
        this.degrees = degrees;
        notifyDataSetChanged();
    }

    public void setDegreePercentages(java.util.Map<Integer, Integer> percentages) {
        this.degreePercentages = percentages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DegreeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDegreeBinding binding = ItemDegreeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new DegreeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DegreeViewHolder holder, int position) {
        Degree degree = degrees.get(position);
        
        String displayName = degree.name;
        if (degreePercentages.containsKey(degree.id)) {
            displayName += " (" + degreePercentages.get(degree.id) + "%)";
        }
        holder.binding.tvDegreeName.setText(displayName);
        try {
            holder.binding.degreeColor.setBackgroundColor(Color.parseColor(degree.colorHex));
        } catch (Exception e) {
            // Default color if parse fails
        }
        // Stats can be calculated and passed here later
        holder.binding.tvDegreeStats.setText("Tracking Active");
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDegreeClick(degree);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onDegreeLongClick(degree);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return degrees.size();
    }

    static class DegreeViewHolder extends RecyclerView.ViewHolder {
        ItemDegreeBinding binding;
        DegreeViewHolder(ItemDegreeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
