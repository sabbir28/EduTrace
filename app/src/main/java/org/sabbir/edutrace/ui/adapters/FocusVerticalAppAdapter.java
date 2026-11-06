package org.sabbir.edutrace.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.FocusAppItem;

import java.util.ArrayList;
import java.util.List;

public class FocusVerticalAppAdapter extends RecyclerView.Adapter<FocusVerticalAppAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(FocusAppItem item);
    }

    private final List<FocusAppItem> apps = new ArrayList<>();
    private OnAppClickListener listener;

    public void setApps(List<FocusAppItem> newApps) {
        this.apps.clear();
        if (newApps != null) {
            this.apps.addAll(newApps);
        }
        notifyDataSetChanged();
    }

    public void setOnAppClickListener(OnAppClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_focus_vertical_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FocusAppItem item = apps.get(position);
        holder.tvLabel.setText(item.getAppLabel());

        if (item.getIcon() != null) {
            holder.ivIcon.setImageDrawable(item.getIcon());
        } else {
            holder.ivIcon.setImageResource(R.mipmap.ic_launcher);
        }

        holder.tvAiTag.setVisibility(item.isAiTool() ? View.VISIBLE : View.GONE);

        String usage = item.getFormattedUsage();
        if (usage != null && !usage.isEmpty()) {
            holder.tvUsage.setVisibility(View.VISIBLE);
            holder.tvUsage.setText(usage);
        } else {
            holder.tvUsage.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).withEndAction(() -> {
                    if (listener != null) listener.onAppClick(item);
                });
            });
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    /**
     * Finds first app index matching alphabetical section char (e.g. 'A', 'B', '#')
     */
    public int getPositionForSection(char section) {
        char target = Character.toUpperCase(section);
        for (int i = 0; i < apps.size(); i++) {
            String label = apps.get(i).getAppLabel();
            if (label != null && !label.isEmpty()) {
                char first = Character.toUpperCase(label.charAt(0));
                if (target == '#') {
                    if (!Character.isLetter(first)) return i;
                } else if (first >= target) {
                    return i;
                }
            }
        }
        return -1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
        TextView tvAiTag;
        TextView tvUsage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivVerticalIcon);
            tvLabel = itemView.findViewById(R.id.tvVerticalLabel);
            tvAiTag = itemView.findViewById(R.id.tvAiTag);
            tvUsage = itemView.findViewById(R.id.tvUsageDuration);
        }
    }
}

