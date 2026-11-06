package org.sabbir.edutrace.ui.adapters;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.FocusTaskItem;

import java.util.ArrayList;
import java.util.List;

public class FocusTaskAdapter extends RecyclerView.Adapter<FocusTaskAdapter.ViewHolder> {

    public interface OnTaskActionListener {
        void onToggleTask(FocusTaskItem task, int position);
        void onDeleteTask(FocusTaskItem task, int position);
    }

    private final List<FocusTaskItem> tasks = new ArrayList<>();
    private OnTaskActionListener listener;

    public void setTasks(List<FocusTaskItem> newTasks) {
        this.tasks.clear();
        if (newTasks != null) {
            this.tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    public void setOnTaskActionListener(OnTaskActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_focus_task, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FocusTaskItem item = tasks.get(position);
        holder.tvTitle.setText(item.getTitle());

        // Remove listener temporarily while binding checked state
        holder.cbCheck.setOnCheckedChangeListener(null);
        holder.cbCheck.setChecked(item.isCompleted());

        updateStrikeThrough(holder.tvTitle, item.isCompleted());

        holder.cbCheck.setOnCheckedChangeListener((btn, isChecked) -> {
            item.setCompleted(isChecked);
            updateStrikeThrough(holder.tvTitle, isChecked);
            if (listener != null) {
                listener.onToggleTask(item, holder.getAdapterPosition());
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteTask(item, holder.getAdapterPosition());
            }
        });
    }

    private void updateStrikeThrough(TextView tv, boolean completed) {
        if (completed) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(0xFF64748B); // subtle muted slate
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tv.setTextColor(0xFFE2E8F0); // crisp light
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbCheck;
        TextView tvTitle;
        ImageView btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbCheck = itemView.findViewById(R.id.cbTaskCompleted);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            btnDelete = itemView.findViewById(R.id.btnDeleteTask);
        }
    }
}
