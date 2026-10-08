package org.sabbir.edutrace.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

import org.sabbir.edutrace.R;
import org.sabbir.edutrace.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                SessionManager sessionManager = new SessionManager(SplashActivity.this);
                if (sessionManager.isActive()) {
                    Intent intent = new Intent(SplashActivity.this, TimerActivity.class);
                    intent.putExtra("SUBJECT_ID", sessionManager.getSubjectId());
                    intent.putExtra("SUBJECT_NAME", sessionManager.getSubjectName());
                    startActivity(intent);
                } else {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                }
            } catch (Throwable t) {
                t.printStackTrace();
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } finally {
                finish();
            }
        }, 1200);
    }
}

