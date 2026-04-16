package org.sabbir.edutrace.ui.activities;
import org.sabbir.edutrace.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            org.sabbir.edutrace.utils.SessionManager sessionManager = new org.sabbir.edutrace.utils.SessionManager(SplashActivity.this);
            if (sessionManager.isActive()) {
                Intent intent = new Intent(SplashActivity.this, TimerActivity.class);
                intent.putExtra("SUBJECT_ID", sessionManager.getSubjectId());
                intent.putExtra("SUBJECT_NAME", sessionManager.getSubjectName());
                startActivity(intent);
            } else {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            finish();
        }, 1500);
    }
}
