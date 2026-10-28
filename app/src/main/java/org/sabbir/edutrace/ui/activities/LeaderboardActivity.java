package org.sabbir.edutrace.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.data.cloud.CloudUser;
import org.sabbir.edutrace.data.models.MyRankingSummary;
import org.sabbir.edutrace.data.models.RankingUser;
import org.sabbir.edutrace.ui.adapters.LeaderboardAdapter;

import java.util.List;
import java.util.Locale;

public class LeaderboardActivity extends AppCompatActivity {

    private String currentTimeframe = "all_time";
    private LeaderboardAdapter adapter;

    private ChipGroup chipGroupTimeframe;
    private RecyclerView rvLeaderboard;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextView tvHeader;

    // My Standing views
    private TextView tvMyAvatar;
    private TextView tvMyRankText;
    private TextView tvMyStudyHours;
    private TextView tvMyTierBadge;
    private TextView tvMyStreakBadge;

    // Podium views
    private View layoutPodium;
    private View podiumFirst, podiumSecond, podiumThird;
    private TextView tvFirstName, tvFirstHours, tvFirstAvatar;
    private TextView tvSecondName, tvSecondHours, tvSecondAvatar;
    private TextView tvThirdName, tvThirdHours, tvThirdAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbarLeaderboard);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        setupListeners();
        loadMyPersonalSummary();
        loadLeaderboard(currentTimeframe);
    }

    private void initViews() {
        chipGroupTimeframe = findViewById(R.id.chipGroupTimeframe);
        rvLeaderboard = findViewById(R.id.rvLeaderboard);
        progressBar = findViewById(R.id.progressLeaderboard);
        tvEmpty = findViewById(R.id.tvLeaderboardEmpty);
        tvHeader = findViewById(R.id.tvLeaderboardHeader);

        tvMyAvatar = findViewById(R.id.tvMyAvatar);
        tvMyRankText = findViewById(R.id.tvMyRankText);
        tvMyStudyHours = findViewById(R.id.tvMyStudyHours);
        tvMyTierBadge = findViewById(R.id.tvMyTierBadge);
        tvMyStreakBadge = findViewById(R.id.tvMyStreakBadge);

        layoutPodium = findViewById(R.id.layoutPodium);
        podiumFirst = findViewById(R.id.podiumFirst);
        podiumSecond = findViewById(R.id.podiumSecond);
        podiumThird = findViewById(R.id.podiumThird);

        tvFirstName = findViewById(R.id.tvFirstName);
        tvFirstHours = findViewById(R.id.tvFirstHours);
        tvFirstAvatar = findViewById(R.id.tvFirstAvatar);

        tvSecondName = findViewById(R.id.tvSecondName);
        tvSecondHours = findViewById(R.id.tvSecondHours);
        tvSecondAvatar = findViewById(R.id.tvSecondAvatar);

        tvThirdName = findViewById(R.id.tvThirdName);
        tvThirdHours = findViewById(R.id.tvThirdHours);
        tvThirdAvatar = findViewById(R.id.tvThirdAvatar);

        adapter = new LeaderboardAdapter();
        rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        rvLeaderboard.setAdapter(adapter);

        CloudUser currentUser = CloudDbManager.getCurrentUser(this);
        if (currentUser != null) {
            String initial = currentUser.getDisplayName().isEmpty() ? "ME" : currentUser.getDisplayName().substring(0, 1).toUpperCase(Locale.ROOT);
            tvMyAvatar.setText(initial);
        }
    }

    private void setupListeners() {
        chipGroupTimeframe.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipDaily) {
                currentTimeframe = "daily";
            } else if (checkedId == R.id.chipWeekly) {
                currentTimeframe = "weekly";
            } else if (checkedId == R.id.chipMonthly) {
                currentTimeframe = "monthly";
            } else {
                currentTimeframe = "all_time";
            }
            loadLeaderboard(currentTimeframe);
        });
    }

    private void loadMyPersonalSummary() {
        if (!CloudDbManager.isLoggedIn(this)) {
            tvMyRankText.setText("Sign In to Compete");
            tvMyStudyHours.setText("0h 0m");
            tvMyTierBadge.setText("Guest Scholar");
            tvMyStreakBadge.setText("0d");
            return;
        }

        CloudDbManager.fetchMyRanking(this, new CloudDbManager.MyRankingCallback() {
            @Override
            public void onSuccess(MyRankingSummary summary) {
                if (summary == null) return;
                int rank = summary.allTimeRank;
                tvMyRankText.setText(rank > 0 ? "Rank #" + rank + " Overall" : "Unranked");
                tvMyStudyHours.setText(summary.formattedTime);
                tvMyTierBadge.setText(summary.tier + " Scholar");
                tvMyStreakBadge.setText(summary.streakDays + " Day Streak");

                int tierColor = getTierColor(summary.tier);
                tvMyTierBadge.setTextColor(tierColor);
            }

            @Override
            public void onError(String message) {
                // Keep default or fallback
            }
        });
    }

    private void loadLeaderboard(String timeframe) {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        CloudDbManager.fetchLeaderboard(this, timeframe, new CloudDbManager.LeaderboardCallback() {
            @Override
            public void onSuccess(List<RankingUser> users, RankingUser currentUserRank, int totalParticipants) {
                progressBar.setVisibility(View.GONE);

                if (users == null || users.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    layoutPodium.setVisibility(View.GONE);
                    adapter.setUsers(null);
                    tvHeader.setText("SCHOLAR RANKINGS (0)");
                    return;
                }

                tvEmpty.setVisibility(View.GONE);
                tvHeader.setText("ALL RANKED SCHOLARS (" + totalParticipants + ")");
                adapter.setUsers(users);

                // Update my standing with timeframe-specific rank if available
                if (currentUserRank != null) {
                    tvMyRankText.setText("Rank #" + currentUserRank.rank + " (" + timeframe.replace("_", "-") + ")");
                    tvMyStudyHours.setText(currentUserRank.formattedTime);
                    tvMyTierBadge.setText(currentUserRank.tier + " Scholar");
                    tvMyStreakBadge.setText(currentUserRank.streakDays + " Day Streak");
                    tvMyTierBadge.setTextColor(getTierColor(currentUserRank.tier));
                }

                // Update podium if at least 1 user exists
                if (users.size() >= 1) {
                    layoutPodium.setVisibility(View.VISIBLE);

                    // 1st Place Scholar
                    RankingUser first = users.get(0);
                    tvFirstName.setText(first.displayName);
                    tvFirstHours.setText(first.formattedTime);
                    String firstInitial = first.displayName.isEmpty() ? "1" : first.displayName.substring(0, 1).toUpperCase(Locale.ROOT);
                    tvFirstAvatar.setText(firstInitial);

                    // 2nd Place Scholar
                    if (users.size() >= 2) {
                        podiumSecond.setVisibility(View.VISIBLE);
                        RankingUser second = users.get(1);
                        tvSecondName.setText(second.displayName);
                        tvSecondHours.setText(second.formattedTime);
                        String secondInitial = second.displayName.isEmpty() ? "2" : second.displayName.substring(0, 1).toUpperCase(Locale.ROOT);
                        tvSecondAvatar.setText(secondInitial);
                    } else {
                        podiumSecond.setVisibility(View.INVISIBLE);
                    }

                    // 3rd Place Scholar
                    if (users.size() >= 3) {
                        podiumThird.setVisibility(View.VISIBLE);
                        RankingUser third = users.get(2);
                        tvThirdName.setText(third.displayName);
                        tvThirdHours.setText(third.formattedTime);
                        String thirdInitial = third.displayName.isEmpty() ? "3" : third.displayName.substring(0, 1).toUpperCase(Locale.ROOT);
                        tvThirdAvatar.setText(thirdInitial);
                    } else {
                        podiumThird.setVisibility(View.INVISIBLE);
                    }
                } else {
                    layoutPodium.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setText("Unable to load leaderboard: " + message);
                tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(LeaderboardActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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
