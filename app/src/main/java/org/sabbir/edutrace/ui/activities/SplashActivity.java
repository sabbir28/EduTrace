package org.sabbir.edutrace.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.cloud.CloudDbManager;
import org.sabbir.edutrace.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
        }

        setContentView(R.layout.activity_splash);

        ImageView ivLogo = findViewById(R.id.ivLogo);
        View glowView = findViewById(R.id.vSplashGlow);
        TextView tvAppName = findViewById(R.id.tvAppName);
        TextView tvTagline = findViewById(R.id.tvTagline);
        LinearProgressIndicator progressIndicator = findViewById(R.id.progressIndicator);
        TextView tvBadge = findViewById(R.id.tvBadge);

        if (ivLogo != null) {
            ivLogo.setAlpha(0f);
            ivLogo.setScaleX(0.88f);
            ivLogo.setScaleY(0.88f);
            ivLogo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(700)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (glowView != null) {
            glowView.setAlpha(0f);
            glowView.setScaleX(0.7f);
            glowView.setScaleY(0.7f);
            glowView.animate()
                    .alpha(0.8f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(900)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (tvAppName != null) {
            tvAppName.setAlpha(0f);
            tvAppName.setTranslationY(20f);
            tvAppName.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(200)
                    .setDuration(600)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (tvTagline != null) {
            tvTagline.setAlpha(0f);
            tvTagline.setTranslationY(14f);
            tvTagline.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(350)
                    .setDuration(550)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (progressIndicator != null) {
            progressIndicator.setAlpha(0f);
            progressIndicator.animate()
                    .alpha(1f)
                    .setStartDelay(500)
                    .setDuration(400)
                    .start();
        }

        if (tvBadge != null) {
            tvBadge.setAlpha(0f);
            tvBadge.animate()
                    .alpha(1f)
                    .setStartDelay(600)
                    .setDuration(400)
                    .start();
        }

        if (CloudDbManager.isLoggedIn(this)) {
            CloudDbManager.autoSyncOnStartup(this);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (!CloudDbManager.isLoggedIn(SplashActivity.this)) {
                    // User not logged in -> Prompt to Sign In or Register
                    Intent authIntent = new Intent(SplashActivity.this, AuthActivity.class);
                    authIntent.putExtra("FORCE_LOGIN_ON_START", true);
                    startActivity(authIntent);
                } else {
                    SessionManager sessionManager = new SessionManager(SplashActivity.this);
                    if (sessionManager.isActive()) {
                        Intent intent = new Intent(SplashActivity.this, TimerActivity.class);
                        intent.putExtra("SUBJECT_ID", sessionManager.getSubjectId());
                        intent.putExtra("SUBJECT_NAME", sessionManager.getSubjectName());
                        startActivity(intent);
                    } else {
                        startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } finally {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, 1400);
    }
}
