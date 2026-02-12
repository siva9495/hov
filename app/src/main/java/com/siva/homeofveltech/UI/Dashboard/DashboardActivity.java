package com.siva.homeofveltech.UI.Dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Dialog.SessionRefreshDialog;
import com.siva.homeofveltech.UI.Settings.SettingsActivity;
import com.siva.homeofveltech.UI.StudentDashboardActivity;

/**
 * DashboardActivity serves as the main screen of the application, providing
 * users with a high-level
 * overview of their academic and study-related information. It displays a
 * personalized greeting and
 * provides navigation to more detailed sections of the app, such as the Student
 * Dashboard.
 * This activity also features a shimmer animation that is displayed while the
 * main content is being
 * prepared, providing a smooth and professional user experience.
 */
public class DashboardActivity extends AppCompatActivity {

    // UI Elements
    private TextView txtGreeting;
    private View cardAttendance;
    private View contentContainer;
    private ShimmerFrameLayout shimmerLayout;
    private ImageView btnSettings, btnRefresh;

    // Data and Preferences
    private PrefsManager prefs;

    /**
     * Called when the activity is first created. This is where you should do all of
     * your normal
     * static set up: create views, bind data to lists, etc. This method also
     * initializes the
     * UI elements and sets up a delayed handler to simulate data loading for the
     * shimmer effect.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down
     *                           then this Bundle contains the data it most recently
     *                           supplied in onSaveInstanceState(Bundle).
     *                           Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize SharedPreferences manager
        prefs = new PrefsManager(this);

        // Find and assign all UI elements from the layout
        txtGreeting = findViewById(R.id.tvGreeting);
        cardAttendance = findViewById(R.id.cardAcademic);
        contentContainer = findViewById(R.id.contentContainer);
        shimmerLayout = findViewById(R.id.shimmerLayout);
        btnSettings = findViewById(R.id.btnSettings);
        btnRefresh = findViewById(R.id.btnRefresh);

        // Start with the shimmer animation
        setLoading(true);

        // Set a personalized greeting message
        String studentName = prefs.getStudentName();
        String username = prefs.getUsername();
        String displayName = !TextUtils.isEmpty(studentName)
                ? studentName
                : (!TextUtils.isEmpty(username) ? username : "User");

        if (txtGreeting != null)
            txtGreeting.setText(displayName + " 👋");

        // Set up the click listener for the academic details card
        if (cardAttendance != null) {
            cardAttendance.setOnClickListener(v -> {
                startActivity(new Intent(this, StudentDashboardActivity.class));
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> showRefreshDialog());
        }

        // Simulate a delay for data loading before showing the main content
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setLoading(false);
        }, 2000);
    }

    /**
     * Controls the visibility of the shimmer animation and the main content.
     * When loading is true, the shimmer effect is shown, and the main content is
     * hidden.
     * When loading is false, the shimmer effect is hidden, and the main content is
     * made visible.
     *
     * @param loading True to show the shimmer animation, false to show the main
     *                content.
     */
    private void setLoading(boolean loading) {
        if (loading) {
            shimmerLayout.startShimmer();
            shimmerLayout.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
        } else {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showRefreshDialog() {
        SessionRefreshDialog dialog = new SessionRefreshDialog();
        dialog.setCallback(new SessionRefreshDialog.RefreshCallback() {
            @Override
            public void onRefreshSuccess() {
                Toast.makeText(DashboardActivity.this, "Session refreshed ✅", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRefreshFailed(String error) {
                Toast.makeText(DashboardActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "refresh_dialog");
    }
}
