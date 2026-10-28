package org.sabbir.edutrace.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.CloudNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationActionListener {
        void onMarkRead(CloudNotification notification);
        void onDelete(CloudNotification notification);
        void onClick(CloudNotification notification);
    }

    private List<CloudNotification> items = new ArrayList<>();
    private OnNotificationActionListener listener;

    public void setListener(OnNotificationActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CloudNotification> newItems) {
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        CloudNotification item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final View viewUnreadDot;
        private final TextView tvType;
        private final TextView tvTime;
        private final TextView tvTitle;
        private final TextView tvMessage;
        private final ImageButton btnMarkRead;
        private final ImageButton btnDelete;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            tvType = itemView.findViewById(R.id.tvNotificationType);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            btnMarkRead = itemView.findViewById(R.id.btnMarkRead);
            btnDelete = itemView.findViewById(R.id.btnDeleteNotification);
        }

        public void bind(CloudNotification item, OnNotificationActionListener listener) {
            tvTitle.setText(item.title);
            tvMessage.setText(item.message);
            tvType.setText(item.type.replace("_", " ").toUpperCase(Locale.ROOT));

            viewUnreadDot.setVisibility(item.isRead ? View.GONE : View.VISIBLE);
            btnMarkRead.setVisibility(item.isRead ? View.GONE : View.VISIBLE);

            tvTime.setText(formatRelativeTime(item.createdAtEpoch));

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(item);
            });

            btnMarkRead.setOnClickListener(v -> {
                if (listener != null) listener.onMarkRead(item);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(item);
            });
        }

        private String formatRelativeTime(long epochMs) {
            if (epochMs <= 0) return "Recently";
            long diffMs = System.currentTimeMillis() - epochMs;
            if (diffMs < 60_000) return "Just now";
            if (diffMs < 3600_000) return (diffMs / 60_000) + "m ago";
            if (diffMs < 86400_000) return (diffMs / 3600_000) + "h ago";
            if (diffMs < 172800_000) return "Yesterday";
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
            return sdf.format(new Date(epochMs));
        }
    }
}
