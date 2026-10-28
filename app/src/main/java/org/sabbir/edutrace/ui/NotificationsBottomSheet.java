package org.sabbir.edutrace.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.data.models.CloudNotification;
import org.sabbir.edutrace.ui.adapters.NotificationAdapter;
import org.sabbir.edutrace.utils.NotificationCacheManager;

import java.util.ArrayList;
import java.util.List;

public class NotificationsBottomSheet extends BottomSheetDialogFragment {

    public interface OnNotificationDismissListener {
        void onDismissed();
    }

    private RecyclerView rvNotifications;
    private ProgressBar progressBar;
    private View layoutEmpty;
    private TextView tvUnreadBadge;
    private MaterialButton btnMarkAllRead;
    private NotificationAdapter adapter;
    private List<CloudNotification> currentList = new ArrayList<>();
    private OnNotificationDismissListener dismissListener;

    public void setOnDismissListener(OnNotificationDismissListener listener) {
        this.dismissListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvNotifications = view.findViewById(R.id.rvNotifications);
        progressBar = view.findViewById(R.id.progressNotifications);
        layoutEmpty = view.findViewById(R.id.layoutEmptyNotifications);
        tvUnreadBadge = view.findViewById(R.id.tvHeaderUnreadBadge);
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead);

        adapter = new NotificationAdapter();
        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        rvNotifications.setAdapter(adapter);

        adapter.setListener(new NotificationAdapter.OnNotificationActionListener() {
            @Override
            public void onMarkRead(CloudNotification notification) {
                markSingleRead(notification);
            }

            @Override
            public void onDelete(CloudNotification notification) {
                deleteSingle(notification);
            }

            @Override
            public void onClick(CloudNotification notification) {
                if (!notification.isRead) {
                    markSingleRead(notification);
                }
            }
        });

        btnMarkAllRead.setOnClickListener(v -> markAllRead());

        // Fast cache-first loading + background sync
        loadNotificationsFast();
    }

    private void loadNotificationsFast() {
        if (getContext() == null) return;

        // 1. Instant Cache Layer (0ms retrieval)
        List<CloudNotification> cached = NotificationCacheManager.getCachedNotifications(getContext());
        int cachedUnread = NotificationCacheManager.getCachedUnreadCount(getContext());

        if (!cached.isEmpty()) {
            currentList = new ArrayList<>(cached);
            adapter.setItems(currentList);
            layoutEmpty.setVisibility(View.GONE);
            updateBadgeUI(cachedUnread);
            // Hide progress spinner because cached data is already visible
            progressBar.setVisibility(View.GONE);
        } else {
            // Check if user is guest/logged out
            if (!CloudDbManager.isLoggedIn(getContext())) {
                currentList = NotificationCacheManager.getFallbackGuestNotifications(getContext());
                adapter.setItems(currentList);
                layoutEmpty.setVisibility(View.GONE);
                updateBadgeUI(1);
                progressBar.setVisibility(View.GONE);
                return;
            }
            // If cache was genuinely empty and user is logged in, show spinner while initial fetch runs
            progressBar.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }

        // 2. Background Revalidation (Network Sync)
        if (!CloudDbManager.isLoggedIn(getContext())) {
            progressBar.setVisibility(View.GONE);
            return;
        }

        CloudDbManager.fetchNotifications(getContext(), false, new CloudDbManager.NotificationsCallback() {
            @Override
            public void onSuccess(List<CloudNotification> notifications, int unreadCount) {
                if (getContext() == null) return;
                progressBar.setVisibility(View.GONE);
                currentList = notifications != null ? new ArrayList<>(notifications) : new ArrayList<>();
                adapter.setItems(currentList);

                // Update cache
                NotificationCacheManager.saveNotifications(getContext(), currentList, unreadCount);

                if (currentList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                }

                updateBadgeUI(unreadCount);
            }

            @Override
            public void onError(String message) {
                if (getContext() == null) return;
                progressBar.setVisibility(View.GONE);
                // If we already had cached data displayed, don't blank the screen!
                if (currentList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void updateBadgeUI(int unreadCount) {
        if (unreadCount > 0) {
            tvUnreadBadge.setText(unreadCount + " New");
            tvUnreadBadge.setVisibility(View.VISIBLE);
            btnMarkAllRead.setVisibility(View.VISIBLE);
        } else {
            tvUnreadBadge.setVisibility(View.GONE);
            btnMarkAllRead.setVisibility(View.GONE);
        }
    }

    private void markSingleRead(CloudNotification notification) {
        if (getContext() == null || notification == null) return;

        // Optimistic UI update: instantly update UI & local cache
        notification.setRead(true);
        NotificationCacheManager.markItemRead(getContext(), notification.id);
        adapter.notifyDataSetChanged();
        updateBadgeUI(NotificationCacheManager.getCachedUnreadCount(getContext()));

        // Silent background server sync
        CloudDbManager.markNotificationRead(getContext(), notification.id, null);
    }

    private void markAllRead() {
        if (getContext() == null) return;

        // Optimistic UI update: instantly mark all items read
        for (CloudNotification n : currentList) {
            n.setRead(true);
        }
        NotificationCacheManager.markAllRead(getContext());
        adapter.notifyDataSetChanged();
        updateBadgeUI(0);

        // Silent background server sync
        btnMarkAllRead.setEnabled(false);
        CloudDbManager.markAllNotificationsRead(getContext(), new CloudDbManager.ActionCallback() {
            @Override
            public void onSuccess(String message) {
                if (getContext() != null) btnMarkAllRead.setEnabled(true);
            }

            @Override
            public void onError(String message) {
                if (getContext() != null) btnMarkAllRead.setEnabled(true);
            }
        });
    }

    private void deleteSingle(CloudNotification notification) {
        if (getContext() == null || notification == null) return;

        // Optimistic UI update: instantly remove from list & local cache
        int index = currentList.indexOf(notification);
        if (index >= 0) {
            currentList.remove(index);
            NotificationCacheManager.deleteItem(getContext(), notification.id);
            adapter.notifyItemRemoved(index);
            if (currentList.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
            }
            updateBadgeUI(NotificationCacheManager.getCachedUnreadCount(getContext()));
        }

        // Silent background server sync
        CloudDbManager.deleteNotification(getContext(), notification.id, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dismissListener != null) {
            dismissListener.onDismissed();
        }
    }
}
