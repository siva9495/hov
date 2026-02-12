package com.siva.homeofveltech.UI;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.siva.homeofveltech.Adapter.TimetableAdapter;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Attendance.SubjectAttendanceActivity;
import com.siva.homeofveltech.UI.Dialog.SessionRefreshDialog;
import com.siva.homeofveltech.UI.Login.LoginActivity;
import com.siva.homeofveltech.UI.Result.StudentResultsActivity;
import com.siva.homeofveltech.UI.TimeTable.FullTimeTableActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudentDashboardActivity extends AppCompatActivity {

    // Header
    private TextView txtWelcome;
    private TextView txtStudentName;
    private ImageView btnRefresh;

    // Timetable section
    private TextView txtTimetableTitle;
    private RecyclerView recyclerViewTimetable;
    private TimetableAdapter adapter;

    private View timetableEmptyCard; // ✅ NEW empty state card
    private TextView txtTimetableEmpty; // ✅ message inside empty card

    // Overlays
    private TextView txtOverlayAttendance;
    private TextView txtOverlayCgpa; // ✅ NEW

    // Loading
    private ShimmerFrameLayout shimmerLayout;
    private View contentContainer;

    // Background
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        prefs = new PrefsManager(this);

        // Header
        txtWelcome = findViewById(R.id.txt_welcome);
        txtStudentName = findViewById(R.id.txt_student_name);
        btnRefresh = findViewById(R.id.btnRefresh);

        // Timetable
        txtTimetableTitle = findViewById(R.id.txt_timetable_title);
        recyclerViewTimetable = findViewById(R.id.recyclerViewTimetable);

        timetableEmptyCard = findViewById(R.id.card_timetable_empty);
        txtTimetableEmpty = findViewById(R.id.txt_timetable_empty);

        // Overlays
        txtOverlayAttendance = findViewById(R.id.txt_overlay_attendance_percentage);
        txtOverlayCgpa = findViewById(R.id.txt_overlay_results_cgpa);

        // Loading
        shimmerLayout = findViewById(R.id.shimmer_layout);
        contentContainer = findViewById(R.id.content_container);

        // Essentials clicks
        findViewById(R.id.collegeEssentialAttendance)
                .setOnClickListener(v -> startActivity(new Intent(this, SubjectAttendanceActivity.class)));

        findViewById(R.id.collegeEssentialResults)
                .setOnClickListener(v -> startActivity(new Intent(this, StudentResultsActivity.class)));

        // ✅ NEW: Timetable card click (College Essentials Timetable)
        findViewById(R.id.collegeEssentialTimetable)
                .setOnClickListener(v -> startActivity(new Intent(this, FullTimeTableActivity.class)));

        // Refresh button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> showRefreshDialog());
        }

        // Recycler
        adapter = new TimetableAdapter(new ArrayList<>());
        recyclerViewTimetable.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerViewTimetable.setAdapter(adapter);

        // Header text (professional)
        String studentName = prefs.getStudentName();
        String username = prefs.getUsername();
        String displayName = !TextUtils.isEmpty(studentName) ? studentName
                : (!TextUtils.isEmpty(username) ? username : "Student");

        if (txtWelcome != null)
            txtWelcome.setText("Welcome");
        if (txtStudentName != null)
            txtStudentName.setText(displayName);

        // Defaults
        if (txtOverlayAttendance != null)
            txtOverlayAttendance.setText("--%");
        if (txtOverlayCgpa != null) {
            double cgpa = prefs.getResultsCacheCgpa();
            txtOverlayCgpa.setText(cgpa > 0 ? String.format(Locale.US, "%.2f", cgpa) : "--");
        }

        loadDashboard();
    }

    private void loadDashboard() {
        setLoading(true);

        final String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if (txtTimetableTitle != null)
            txtTimetableTitle.setText(todayName + " Timetable");

        executor.execute(() -> {
            try {
                StudentDashboardData data = amsClient.fetchStudentDashboardData();

                runOnUiThread(() -> {
                    setLoading(false);

                    // Attendance overlay
                    if (txtOverlayAttendance != null) {
                        if (data.overallAttendancePercent >= 0) {
                            txtOverlayAttendance.setText(((int) Math.round(data.overallAttendancePercent)) + "%");
                        } else {
                            txtOverlayAttendance.setText("--%");
                        }
                    }

                    // ✅ Result overlay
                    if (txtOverlayCgpa != null) {
                        if (data.overallGpa > 0) {
                            txtOverlayCgpa.setText(String.format(Locale.US, "%.2f", data.overallGpa));
                        } else {
                            txtOverlayCgpa.setText("--");
                        }
                    }

                    // Weekend handling (show empty-state nicely)
                    if (isWeekend(todayName)) {
                        showTimetableEmpty("No classes (Weekend)");
                        return;
                    }

                    List<TimetableItem> todayItems = data.getTimetableForDay(todayName);
                    List<TimetableItem> filtered = new ArrayList<>();

                    if (todayItems != null) {
                        for (TimetableItem it : todayItems) {
                            if (it == null)
                                continue;
                            // if ("Completed".equalsIgnoreCase(it.status)) continue;

                            // ✅ KEEP subject now (for better UI)
                            filtered.add(new TimetableItem(it.code, it.subject, it.time, it.status));
                        }
                    }

                    if (filtered.isEmpty()) {
                        showTimetableEmpty("No classes today");
                    } else {
                        showTimetableList(filtered);
                    }
                });

            } catch (AmsClient.SessionExpiredException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(StudentDashboardActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (txtOverlayAttendance != null)
                        txtOverlayAttendance.setText("--%");
                    if (txtOverlayCgpa != null)
                        txtOverlayCgpa.setText("--");
                    showTimetableEmpty("Timetable not available");
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showTimetableEmpty(String msg) {
        recyclerViewTimetable.setVisibility(View.GONE);
        timetableEmptyCard.setVisibility(View.VISIBLE);
        if (txtTimetableEmpty != null)
            txtTimetableEmpty.setText(msg);
    }

    private void showTimetableList(List<TimetableItem> items) {
        timetableEmptyCard.setVisibility(View.GONE);
        recyclerViewTimetable.setVisibility(View.VISIBLE);
        adapter.setItems(items);
    }

    private boolean isWeekend(String day) {
        if (day == null)
            return false;
        String d = day.trim().toLowerCase(Locale.US);
        return d.equals("saturday") || d.equals("sunday");
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    public void refreshData() {
        loadDashboard();
    }

    private void showRefreshDialog() {
        SessionRefreshDialog dialog = new SessionRefreshDialog();
        dialog.setCallback(new SessionRefreshDialog.RefreshCallback() {
            @Override
            public void onRefreshSuccess() {
                refreshData();
                Toast.makeText(StudentDashboardActivity.this, "Data refreshed ✅", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRefreshFailed(String error) {
                Toast.makeText(StudentDashboardActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "refresh_dialog");
    }
}
