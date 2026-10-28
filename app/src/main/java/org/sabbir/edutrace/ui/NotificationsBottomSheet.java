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

        loadNotifications();
    }

    private void loadNotifications() {
        if (getContext() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        CloudDbManager.fetchNotifications(getContext(), false, new CloudDbManager.NotificationsCallback() {
            @Override
            public void onSuccess(List<CloudNotification> notifications, int unreadCount) {
                progressBar.setVisibility(View.GONE);
                currentList = notifications != null ? notifications : new ArrayList<>();
                adapter.setItems(currentList);

                if (currentList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                }

                if (unreadCount > 0) {
                    tvUnreadBadge.setText(unreadCount + " New");
                    tvUnreadBadge.setVisibility(View.VISIBLE);
                    btnMarkAllRead.setVisibility(View.VISIBLE);
                } else {
                    tvUnreadBadge.setVisibility(View.GONE);
                    btnMarkAllRead.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                if (currentList.isEmpty()) layoutEmpty.setVisibility(View.VISIBLE);
                if (getContext() != null) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void markSingleRead(CloudNotification notification) {
        if (getContext() == null || notification == null) return;
        CloudDbManager.markNotificationRead(getContext(), notification.id, new CloudDbManager.ActionCallback() {
            @Override
            public void onSuccess(String message) {
                loadNotifications();
            }

            @Override
            public void onError(String message) {
                if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void markAllRead() {
        if (getContext() == null) return;
        btnMarkAllRead.setEnabled(false);
        CloudDbManager.markAllNotificationsRead(getContext(), new CloudDbManager.ActionCallback() {
            @Override
            public void onSuccess(String message) {
                btnMarkAllRead.setEnabled(true);
                loadNotifications();
            }

            @Override
            public void onError(String message) {
                btnMarkAllRead.setEnabled(true);
                if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteSingle(CloudNotification notification) {
        if (getContext() == null || notification == null) return;
        CloudDbManager.deleteNotification(getContext(), notification.id, new CloudDbManager.ActionCallback() {
            @Override
            public void onSuccess(String message) {
                loadNotifications();
            }

            @Override
            public void onError(String message) {
                if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dismissListener != null) {
            dismissListener.onDismissed();
        }
    }
}
