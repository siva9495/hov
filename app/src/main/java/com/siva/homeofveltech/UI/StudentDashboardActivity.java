package com.siva.homeofveltech.UI;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.siva.homeofveltech.Adapter.TimetableAdapter;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudentDashboardActivity extends AppCompatActivity {

    private TextView txtGreeting, txtTimetableTitle, txtOverlayAttendance;
    private RecyclerView recyclerViewTimetable;

    private TimetableAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        prefs = new PrefsManager(this);

        // ✅ IDs from your StudentDashboard layout
        txtGreeting = findViewById(R.id.txt_greeting);
        txtTimetableTitle = findViewById(R.id.txt_timetable_title);
        txtOverlayAttendance = findViewById(R.id.txt_overlay_attendance_percentage);
        recyclerViewTimetable = findViewById(R.id.recyclerViewTimetable);

        // Greeting
        String studentName = prefs.getStudentName();
        String username = prefs.getUsername();
        String displayName = !TextUtils.isEmpty(studentName) ? studentName
                : (!TextUtils.isEmpty(username) ? username : "User");

        if (txtGreeting != null) txtGreeting.setText("Hello, " + displayName + " 👋");

        // Horizontal timetable
        adapter = new TimetableAdapter(new ArrayList<>());
        recyclerViewTimetable.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        recyclerViewTimetable.setAdapter(adapter);

        if (txtOverlayAttendance != null) txtOverlayAttendance.setText("--%");

        loadDashboard();
    }

    private void loadDashboard() {
        String username = prefs.getUsername();
        String password = prefs.getPassword();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if (txtTimetableTitle != null) txtTimetableTitle.setText(todayName + " Timetable");

        executor.execute(() -> {
            try {
                boolean ok = amsClient.isSessionValid() || amsClient.login(username, password);
                if (!ok) throw new Exception("Session expired. Login failed.");

                StudentDashboardData data = amsClient.fetchStudentDashboardData();

                runOnUiThread(() -> {
                    // ✅ attendance overlay
                    if (txtOverlayAttendance != null) {
                        if (data.overallAttendancePercent >= 0) {
                            txtOverlayAttendance.setText(((int) Math.round(data.overallAttendancePercent)) + "%");
                        } else {
                            txtOverlayAttendance.setText("--%");
                        }
                    }

                    // ✅ weekend message
                    if (isWeekend(todayName)) {
                        adapter.setItems(singleMessage("No classes available (Weekend)"));
                        return;
                    }

                    // ✅ today's timetable, but hide Completed
                    List<TimetableItem> todayItems = data.getTimetableForDay(todayName);
                    List<TimetableItem> filtered = new ArrayList<>();

                    if (todayItems != null) {
                        for (TimetableItem it : todayItems) {
                            if (it == null) continue;
                            if ("Completed".equalsIgnoreCase(it.status)) continue; // hide time-over
                            filtered.add(new TimetableItem(it.code, "", it.time, it.status)); // show only code+time+status
                        }
                    }

                    if (filtered.isEmpty()) {
                        adapter.setItems(singleMessage("No classes available today"));
                    } else {
                        adapter.setItems(filtered);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (txtOverlayAttendance != null) txtOverlayAttendance.setText("--%");
                    adapter.setItems(singleMessage("Timetable not available"));
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean isWeekend(String day) {
        if (day == null) return false;
        String d = day.trim().toLowerCase(Locale.US);
        return d.equals("saturday") || d.equals("sunday");
    }

    private List<TimetableItem> singleMessage(String msg) {
        List<TimetableItem> list = new ArrayList<>();
        // message card => status/time empty
        list.add(new TimetableItem(msg, "", "", ""));
        return list;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
