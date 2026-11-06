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

public class FocusCuratedAppAdapter extends RecyclerView.Adapter<FocusCuratedAppAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(FocusAppItem item);
    }

    public interface OnAppLongClickListener {
        void onAppLongClick(FocusAppItem item, View anchorView);
    }

    private final List<FocusAppItem> apps = new ArrayList<>();
    private OnAppClickListener listener;
    private OnAppLongClickListener longClickListener;

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

    public void setOnAppLongClickListener(OnAppLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_focus_curated_app, parent, false);
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

        holder.tvAiBadge.setVisibility(item.isAiTool() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).withEndAction(() -> {
                    if (listener != null) listener.onAppClick(item);
                });
            });
        });

        holder.itemView.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (longClickListener != null) {
                longClickListener.onAppLongClick(item, v);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
        TextView tvAiBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvLabel = itemView.findViewById(R.id.tvAppLabel);
            tvAiBadge = itemView.findViewById(R.id.tvAiBadge);
        }
    }
}

