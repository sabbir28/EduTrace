package org.sabbir.edutrace.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.EduTraceMusicTrack;

import java.util.ArrayList;
import java.util.List;

public class EduTraceMusicAdapter extends RecyclerView.Adapter<EduTraceMusicAdapter.TrackViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(EduTraceMusicTrack track, int index);
    }

    private final List<EduTraceMusicTrack> tracks = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean isPlaying = false;
    private OnTrackClickListener listener;

    public void setTracks(List<EduTraceMusicTrack> newTracks, int currentSelected, boolean playing) {
        this.tracks.clear();
        if (newTracks != null) {
            this.tracks.addAll(newTracks);
        }
        this.selectedIndex = currentSelected;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    public void addTracks(List<EduTraceMusicTrack> moreTracks) {
        if (moreTracks == null || moreTracks.isEmpty()) return;
        int startPos = this.tracks.size();
        this.tracks.addAll(moreTracks);
        notifyItemRangeInserted(startPos, moreTracks.size());
    }

    public void setPlaybackState(int currentSelected, boolean playing) {
        this.selectedIndex = currentSelected;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    public void setOnTrackClickListener(OnTrackClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_study_music_track, parent, false);
        return new TrackViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        EduTraceMusicTrack track = tracks.get(position);
        boolean isCurrent = (position == selectedIndex);

        // Distraction-free title: Never shows raw song filename while playing
        holder.tvTitle.setText(track.getDisplayTitle(isCurrent && isPlaying, position));
        holder.tvDominantHz.setText(track.getDominantHzDisplay());
        holder.tvSha256.setText("SHA: " + track.getShortSha256());

        if (isCurrent) {
            holder.tvActiveBadge.setVisibility(View.VISIBLE);
            holder.tvActiveBadge.setText(isPlaying ? "NOW PLAYING" : "PAUSED");
            holder.ivPlayState.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            holder.itemView.setBackgroundResource(R.drawable.bg_glass_card);
        } else {
            holder.tvActiveBadge.setVisibility(View.GONE);
            holder.ivPlayState.setImageResource(R.drawable.ic_play_arrow);
            holder.itemView.setBackgroundResource(R.drawable.bg_glass_item);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPlayState;
        TextView tvTitle;
        TextView tvDominantHz;
        TextView tvSha256;
        TextView tvActiveBadge;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPlayState = itemView.findViewById(R.id.ivTrackPlayState);
            tvTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvDominantHz = itemView.findViewById(R.id.tvDominantHz);
            tvSha256 = itemView.findViewById(R.id.tvSha256);
            tvActiveBadge = itemView.findViewById(R.id.tvActiveBadge);
        }
    }
}

