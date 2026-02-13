package com.siva.homeofveltech.UI;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.siva.homeofveltech.Adapter.TimetableAdapter;
import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.Model.SemesterResult;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Attendance.SubjectAttendanceActivity;
import com.siva.homeofveltech.UI.Dialog.SessionRefreshDialog;
import com.siva.homeofveltech.UI.Result.StudentResultsActivity;
import com.siva.homeofveltech.UI.TimeTable.FullTimeTableActivity;
import com.siva.homeofveltech.Utils.TimetableStatusUtils;

import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudentDashboardActivity extends AppCompatActivity {
    private static final long MIN_SHIMMER_MS = 400L;

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
    private long loadingStartMs = 0L;

    // Background
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private PrefsManager prefs;
    private StudentDashboardData currentDashboardData;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            if (currentDashboardData != null) {
                updateUI(currentDashboardData);
            }
            statusHandler.postDelayed(this, 30_000L);
        }
    };

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

        loadDashboard(false); // false = not a manual refresh
    }

    private void loadDashboard(boolean manualRefresh) {
        // If manual refresh, fetch from server
        if (manualRefresh) {
            fetchFromServer(!prefs.hasDashboardCache());
            return;
        }

        // Otherwise, show cached data instantly (no shimmer!)
        if (prefs.hasDashboardCache()) {
            loadFromCache();
        } else {
            // No cache, must fetch (first login)
            fetchFromServer(true);
        }
    }

    private void loadFromCache() {
        try {
            String json = prefs.getDashboardCache();
            Gson gson = new Gson();
            StudentDashboardData data = gson.fromJson(json, StudentDashboardData.class);
            if (data == null) throw new IllegalStateException("Empty dashboard cache");
            updateUI(data);
            setLoading(false);
        } catch (Exception e) {
            // Cache corrupted, fetch fresh
            fetchFromServer(true);
        }
    }

    private void fetchFromServer(boolean showBlockingLoader) {
        if (showBlockingLoader) setLoading(true);
        final String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if (txtTimetableTitle != null)
            txtTimetableTitle.setText(todayName + " Timetable");

        executor.execute(() -> {
            try {
                StudentDashboardData data = amsClient.fetchStudentDashboardData();

                // Save to cache
                Gson gson = new Gson();
                String json = gson.toJson(data);
                prefs.saveDashboardCache(json);
                prefs.saveTimetableCache(gson.toJson(data.weekTimetable));
                prefs.saveStudentProfile(data.studentName, data.branch);
                executor.execute(() -> refreshSecondaryCaches(gson));

                runOnUiThread(() -> {
                    updateUI(data);
                    if (showBlockingLoader) setLoading(false);
                });
            } catch (AmsClient.SessionExpiredException e) {
                runOnUiThread(() -> {
                    if (showBlockingLoader) setLoading(false);
                    if (prefs.hasDashboardCache()) {
                        loadFromCache();
                    } else {
                        Toast.makeText(this, "No saved dashboard data available.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (showBlockingLoader) setLoading(false);
                    if (prefs.hasDashboardCache()) {
                        loadFromCache();
                    } else {
                        Toast.makeText(this, "No saved dashboard data available.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void refreshSecondaryCaches(Gson gson) {
        try {
            List<SubjectAttendanceItem> attendance = amsClient.fetchAttendanceData();
            prefs.saveAttendanceCache(gson.toJson(attendance));

            if (attendance != null) {
                for (SubjectAttendanceItem subject : attendance) {
                    if (subject == null) continue;
                    try {
                        List<PeriodAttendanceItem> periods = amsClient.fetchSubjectFullAttendance(
                                subject.subjectCode,
                                subject.subjectName
                        );
                        prefs.saveSubjectFullAttendanceCache(
                                subject.subjectCode,
                                subject.subjectName,
                                gson.toJson(periods)
                        );
                    } catch (Exception ignored) {
                        // continue with other subjects
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            List<SemesterResult> results = amsClient.fetchAllSemesterResultsRegular();
            double cgpa = 0.0;
            if (results != null && !results.isEmpty()) {
                cgpa = results.get(results.size() - 1).tgpa;
            }
            prefs.saveResultsCache(gson.toJson(results), cgpa);
        } catch (Exception ignored) {
        }
    }

    private void updateUI(StudentDashboardData data) {
        currentDashboardData = data;
        final String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if (txtTimetableTitle != null)
            txtTimetableTitle.setText(todayName + " Timetable");

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
                String computedStatus = TimetableStatusUtils.computeStatusForDay(todayName, it.time);
                filtered.add(new TimetableItem(it.code, it.subject, it.time, computedStatus));
            }
        }

        // Keep the same order as full timetable: Live -> Next -> Done
        Collections.sort(filtered, (a, b) -> Integer.compare(statusPriority(a.status), statusPriority(b.status)));

        if (filtered.isEmpty()) {
            showTimetableEmpty("No classes today");
        } else {
            showTimetableItems(filtered);
        }
    }

    private int statusPriority(String status) {
        if (status == null) return 1;
        String s = status.trim();
        if (s.equalsIgnoreCase("On Going") || s.equalsIgnoreCase("Live")) return 0;
        if (s.equalsIgnoreCase("Completed") || s.equalsIgnoreCase("Done")) return 2;
        return 1; // Upcoming/Next
    }

    private void showTimetableItems(List<TimetableItem> items) {
        recyclerViewTimetable.setVisibility(View.VISIBLE);
        timetableEmptyCard.setVisibility(View.GONE);
        adapter.setItems(items);
    }

    private void showTimetableEmpty(String msg) {
        recyclerViewTimetable.setVisibility(View.GONE);
        timetableEmptyCard.setVisibility(View.VISIBLE);
        if (txtTimetableEmpty != null)
            txtTimetableEmpty.setText(msg);
    }

    private boolean isWeekend(String day) {
        if (day == null)
            return false;
        String d = day.trim().toLowerCase(Locale.US);
        return d.equals("saturday") || d.equals("sunday");
    }

    private void setLoading(boolean loading) {
        if (loading) {
            loadingStartMs = System.currentTimeMillis();
            shimmerLayout.startShimmer();
            shimmerLayout.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
        } else {
            long elapsed = System.currentTimeMillis() - loadingStartMs;
            long delay = Math.max(0L, MIN_SHIMMER_MS - elapsed);
            shimmerLayout.postDelayed(() -> {
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
            }, delay);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startStatusTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopStatusTicker();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatusTicker();
        executor.shutdownNow();
    }

    private void startStatusTicker() {
        statusHandler.removeCallbacks(statusTick);
        statusHandler.post(statusTick);
    }

    private void stopStatusTicker() {
        statusHandler.removeCallbacks(statusTick);
    }

    public void refreshData() {
        fetchFromServer(!prefs.hasDashboardCache());
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
