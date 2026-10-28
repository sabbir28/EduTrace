package org.sabbir.edutrace.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.RankingUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder> {

    private List<RankingUser> users = new ArrayList<>();

    public void setUsers(List<RankingUser> newUsers) {
        this.users = newUsers != null ? new ArrayList<>(newUsers) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LeaderboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard_user, parent, false);
        return new LeaderboardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardViewHolder holder, int position) {
        RankingUser user = users.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class LeaderboardViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView tvRankNumber;
        private final TextView tvAvatar;
        private final TextView tvName;
        private final TextView tvYouBadge;
        private final TextView tvTier;
        private final TextView tvStreak;
        private final TextView tvHours;

        public LeaderboardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            tvRankNumber = itemView.findViewById(R.id.tvRankNumber);
            tvAvatar = itemView.findViewById(R.id.tvUserAvatar);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvYouBadge = itemView.findViewById(R.id.tvYouBadge);
            tvTier = itemView.findViewById(R.id.tvUserTier);
            tvStreak = itemView.findViewById(R.id.tvUserStreak);
            tvHours = itemView.findViewById(R.id.tvUserStudyHours);
        }

        public void bind(RankingUser user) {
            tvRankNumber.setText("#" + user.rank);
            tvName.setText(user.displayName != null && !user.displayName.isEmpty() ? user.displayName : user.username);
            tvHours.setText(user.formattedTime);
            tvStreak.setText(user.streakDays + "d");

            // Avatar initial
            String initial = "S";
            if (user.displayName != null && !user.displayName.isEmpty()) {
                initial = user.displayName.substring(0, 1).toUpperCase(Locale.ROOT);
            } else if (user.username != null && !user.username.isEmpty()) {
                initial = user.username.substring(0, 1).toUpperCase(Locale.ROOT);
            }
            tvAvatar.setText(initial);

            // Tier styling
            tvTier.setText(user.tier);
            int tierColor = getTierColor(user.tier);
            tvTier.setTextColor(tierColor);

            // Top 3 Rank colors
            if (user.rank == 1) {
                tvRankNumber.setTextColor(Color.parseColor("#FACC15")); // Gold
            } else if (user.rank == 2) {
                tvRankNumber.setTextColor(Color.parseColor("#E2E8F0")); // Silver
            } else if (user.rank == 3) {
                tvRankNumber.setTextColor(Color.parseColor("#CD7F32")); // Bronze
            } else {
                tvRankNumber.setTextColor(Color.parseColor("#94A3B8"));
            }

            // Current user highlight
            if (user.isCurrentUser) {
                tvYouBadge.setVisibility(View.VISIBLE);
                cardView.setStrokeColor(Color.parseColor("#38BDF8"));
                cardView.setStrokeWidth(4);
                cardView.setCardBackgroundColor(Color.parseColor("#0F2942"));
            } else {
                tvYouBadge.setVisibility(View.GONE);
                cardView.setStrokeColor(Color.parseColor("#334155"));
                cardView.setStrokeWidth(2);
                cardView.setCardBackgroundColor(Color.parseColor("#1E293B"));
            }
        }

        private int getTierColor(String tier) {
            if (tier == null) return Color.parseColor("#CD7F32");
            switch (tier.toLowerCase(Locale.ROOT)) {
                case "diamond":
                    return Color.parseColor("#38BDF8");
                case "platinum":
                    return Color.parseColor("#C084FC");
                case "gold":
                    return Color.parseColor("#FACC15");
                case "silver":
                    return Color.parseColor("#E2E8F0");
                case "bronze":
                default:
                    return Color.parseColor("#CD7F32");
            }
        }
    }
}
