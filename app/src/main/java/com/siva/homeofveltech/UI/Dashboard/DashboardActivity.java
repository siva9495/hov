package com.siva.homeofveltech.UI.Dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.siva.homeofveltech.Adapter.TimetableAdapter;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.StudentDashboardActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {

    private TextView txtGreeting;
    private TextView txtTimetableTitle;
    private TextView txtOverlayAttendance;

    private RecyclerView recyclerViewTimetable;
    private TimetableAdapter timetableAdapter;

    private android.view.View cardAttendance;

    private PrefsManager prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        prefs = new PrefsManager(this);

        txtGreeting = findViewById(R.id.tvGreeting);
        // txtTimetableTitle = findViewById(R.id.txt_timetable_title); // ID not found
        // txtOverlayAttendance = findViewById(R.id.txt_overlay_attendance_percentage); // ID not found

        // recyclerViewTimetable = findViewById(R.id.recyclerViewTimetable); // ID not found
        cardAttendance = findViewById(R.id.cardAcademic);

        // Greeting
        String studentName = prefs.getStudentName();
        String username = prefs.getUsername();

        String displayName = !TextUtils.isEmpty(studentName)
                ? studentName
                : (!TextUtils.isEmpty(username) ? username : "User");

        if(txtGreeting != null) txtGreeting.setText("Hello, " + displayName + " 👋");

        // Title includes day name
        String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if(txtTimetableTitle != null) txtTimetableTitle.setText(todayName + " Timetable");

        // Recycler setup (horizontal)
        if (recyclerViewTimetable != null) {
            timetableAdapter = new TimetableAdapter(new ArrayList<>());
            recyclerViewTimetable.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            );
            recyclerViewTimetable.setAdapter(timetableAdapter);
        }

        if(txtOverlayAttendance != null) txtOverlayAttendance.setText("--%");

        // Click -> StudentDashboardActivity
        if (cardAttendance != null) {
            cardAttendance.setOnClickListener(v -> {
                startActivity(new Intent(this, StudentDashboardActivity.class));
            });
        }

        loadDashboardData(todayName);
    }

    private void loadDashboardData(String todayName) {
        if (!prefs.hasCredentials()) {
            showNoData("No saved login");
            return;
        }

        final String u = prefs.getUsername();
        final String p = prefs.getPassword();

        executor.execute(() -> {
            try {
                boolean ok = amsClient.isSessionValid() || amsClient.login(u, p);
                if (!ok) {
                    runOnUiThread(() -> showNoData("Login failed"));
                    return;
                }

                // ✅ Now this exists in AmsClient (fixed)
                StudentDashboardData data = amsClient.fetchStudentDashboardData();

                List<TimetableItem> todayItems = data.getTimetableForDay(todayName);

                runOnUiThread(() -> {
                    if (txtOverlayAttendance != null) {
                        if (data.overallAttendancePercent >= 0) {
                            int pct = (int) Math.round(data.overallAttendancePercent);
                            txtOverlayAttendance.setText(pct + "%");
                        } else {
                            txtOverlayAttendance.setText("--%");
                        }
                    }

                    if (timetableAdapter != null) {
                        if (todayItems == null || todayItems.isEmpty()) {
                            List<TimetableItem> fallback = new ArrayList<>();
                            fallback.add(new TimetableItem("—", "No classes today", "", "Upcoming"));
                            timetableAdapter.setItems(fallback);
                        } else {
                            timetableAdapter.setItems(todayItems);
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> showNoData(e.getMessage()));
            }
        });
    }

    private void showNoData(String reason) {
        if(txtOverlayAttendance != null) txtOverlayAttendance.setText("--%");
        if (timetableAdapter != null) {
            List<TimetableItem> fallback = new ArrayList<>();
            fallback.add(new TimetableItem("—", "Not available", reason == null ? "" : reason, "Upcoming"));
            timetableAdapter.setItems(fallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
