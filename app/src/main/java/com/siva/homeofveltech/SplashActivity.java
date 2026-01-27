package com.siva.homeofveltech;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Dashboard.DashboardActivity;
import com.siva.homeofveltech.UI.Login.LoginActivity;
import com.siva.homeofveltech.Utils.NetworkUtils;

public class SplashActivity extends AppCompatActivity {

    private ImageView ivMovingIcon;
    private ImageView ivFullLogo;
    private View viewCurtain;
    private boolean isAnimationDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ivMovingIcon = findViewById(R.id.iv_moving_icon);
        ivFullLogo = findViewById(R.id.iv_full_logo);
        viewCurtain = findViewById(R.id.view_curtain);

        startAnimations();
    }

    private void startAnimations() {
        // Initial State
        ivMovingIcon.setVisibility(View.VISIBLE);
        ivMovingIcon.setAlpha(1f);
        ivFullLogo.setVisibility(View.INVISIBLE);
        viewCurtain.clearAnimation();
        viewCurtain.setTranslationX(0f);
        viewCurtain.bringToFront();

        Animation scaleAnim = AnimationUtils.loadAnimation(this, R.anim.anim_scale_only);
        final Animation curtainSlideAnim = AnimationUtils.loadAnimation(this, R.anim.anim_curtain_slide);

        ivMovingIcon.startAnimation(scaleAnim);

        scaleAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                ivMovingIcon.clearAnimation();
                ivMovingIcon.setAlpha(0f);
                ivMovingIcon.setVisibility(View.GONE);
                ivFullLogo.setVisibility(View.VISIBLE);
                viewCurtain.post(() -> viewCurtain.startAnimation(curtainSlideAnim));
            }
            @Override public void onAnimationRepeat(Animation animation) {}
        });

        curtainSlideAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                // Animation finished, wait slightly then proceed to checks
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    isAnimationDone = true;
                    checkInternetAndProceed();
                }, 500);
            }
            @Override public void onAnimationRepeat(Animation animation) {}
        });
    }

    private void checkInternetAndProceed() {
        if (NetworkUtils.isNetworkAvailable(this)) {
            // Internet is available -> Check Login Session
            PrefsManager prefsManager = new PrefsManager(this);
            if (prefsManager.hasCredentials()) {
                navigateToDashboard();
            } else {
                navigateToLogin();
            }
        } else {
            // No Internet -> Show Dialog
            showNoInternetDialog();
        }
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Please check your internet settings to proceed.")
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Settings", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    finish(); // Exit App
                })
                .show();
    }

    // ✅ Logic called when returning from Settings
    @Override
    protected void onRestart() {
        super.onRestart();
        if (isAnimationDone) {
            checkInternetAndProceed();
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(SplashActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
