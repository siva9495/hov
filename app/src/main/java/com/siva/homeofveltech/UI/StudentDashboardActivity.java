package com.siva.homeofveltech.UI;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * StudentDashboardActivity displays the main dashboard for the student.
 * It shows a greeting, the daily timetable, and other essential college information.
 * This activity handles loading data from the network, displaying it to the user,
 * and showing a shimmer animation while the data is being fetched.
 */
public class StudentDashboardActivity extends AppCompatActivity {

    // UI Elements
    private TextView txtGreeting, txtTimetableTitle, txtOverlayAttendance;
    private RecyclerView recyclerViewTimetable;
    private TimetableAdapter adapter;
    private ShimmerFrameLayout shimmerLayout;
    private View contentContainer;

    // Background processing and data
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private PrefsManager prefs;

    /**
     * Called when the activity is first created. This is where you should do all of your normal
     * static set up: create views, bind data to lists, etc.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     *                           then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     *                           Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        // Initialize SharedPreferences manager
        prefs = new PrefsManager(this);

        // Find and assign all UI elements from the layout
        txtGreeting = findViewById(R.id.txt_greeting);
        txtTimetableTitle = findViewById(R.id.txt_timetable_title);
        txtOverlayAttendance = findViewById(R.id.txt_overlay_attendance_percentage);
        recyclerViewTimetable = findViewById(R.id.recyclerViewTimetable);
        shimmerLayout = findViewById(R.id.shimmer_layout);
        contentContainer = findViewById(R.id.content_container);

        findViewById(R.id.collegeEssentialAttendance).setOnClickListener(v -> {
            startActivity(new Intent(this, SubjectAttendanceActivity.class));
        });


        // Set a personalized greeting message
        String studentName = prefs.getStudentName();
        String username = prefs.getUsername();
        String displayName = !TextUtils.isEmpty(studentName) ? studentName
                : (!TextUtils.isEmpty(username) ? username : "User");

        if (txtGreeting != null) txtGreeting.setText("Hello, " + displayName + " 👋");

        // Set up the RecyclerView for the timetable
        adapter = new TimetableAdapter(new ArrayList<>());
        recyclerViewTimetable.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        recyclerViewTimetable.setAdapter(adapter);

        // Set a default value for the attendance overlay
        if (txtOverlayAttendance != null) txtOverlayAttendance.setText("--%");

        // Start loading the dashboard data
        loadDashboard();
    }

    /**
     * This method orchestrates the loading of the dashboard data. It handles showing and hiding the
     * shimmer animation, fetching data from the network in a background thread, and updating the UI
     * on the main thread.
     */
    private void loadDashboard() {
        // Show the shimmer animation while data is loading
        setLoading(true);

        // Get stored user credentials
        String username = prefs.getUsername();
        String password = prefs.getPassword();

        // If credentials are not available, ask the user to log in again
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish(); // Close the activity
            return;
        }

        // Set the title for the timetable section with the current day's name
        final String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if (txtTimetableTitle != null) txtTimetableTitle.setText(todayName + " Timetable");

        // Execute the network request on a background thread
        executor.execute(() -> {
            try {
                // Ensure the session is valid, or log in again to create a new session
                boolean ok = amsClient.isSessionValid() || amsClient.login(username, password);
                if (!ok) throw new Exception("Session expired. Login failed.");

                // Fetch the student's dashboard data
                StudentDashboardData data = amsClient.fetchStudentDashboardData();

                // Update the UI on the main thread with the fetched data
                runOnUiThread(() -> {
                    setLoading(false); // Hide the shimmer animation

                    // Update the attendance percentage overlay
                    if (txtOverlayAttendance != null) {
                        if (data.overallAttendancePercent >= 0) {
                            txtOverlayAttendance.setText(((int) Math.round(data.overallAttendancePercent)) + "%");
                        } else {
                            txtOverlayAttendance.setText("--%");
                        }
                    }

                    // If it's a weekend, display a message instead of the timetable
                    if (isWeekend(todayName)) {
                        adapter.setItems(singleMessage("No classes available (Weekend)"));
                        return;
                    }

                    // Filter the timetable to show only upcoming classes for today
                    List<TimetableItem> todayItems = data.getTimetableForDay(todayName);
                    List<TimetableItem> filtered = new ArrayList<>();

                    if (todayItems != null) {
                        for (TimetableItem it : todayItems) {
                            if (it == null) continue;
                            if ("Completed".equalsIgnoreCase(it.status)) continue; // Don't show completed classes
                            filtered.add(new TimetableItem(it.code, "", it.time, it.status));
                        }
                    }

                    // Update the RecyclerView with the filtered timetable
                    if (filtered.isEmpty()) {
                        adapter.setItems(singleMessage("No classes available today"));
                    } else {
                        adapter.setItems(filtered);
                    }
                });

            } catch (Exception e) {
                // Handle any errors that occur during the network request
                runOnUiThread(() -> {
                    setLoading(false); // Hide the shimmer animation
                    if (txtOverlayAttendance != null) txtOverlayAttendance.setText("--%");
                    adapter.setItems(singleMessage("Timetable not available"));
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Checks if a given day is a weekend (Saturday or Sunday).
     *
     * @param day The name of the day (e.g., "Monday").
     * @return True if the day is a weekend, false otherwise.
     */
    private boolean isWeekend(String day) {
        if (day == null) return false;
        String d = day.trim().toLowerCase(Locale.US);
        return d.equals("saturday") || d.equals("sunday");
    }

    /**
     * Creates a list containing a single TimetableItem, which is used to display messages
     * in the RecyclerView (e.g., "No classes today").
     *
     * @param msg The message to display.
     * @return A list containing a single TimetableItem with the given message.
     */
    private List<TimetableItem> singleMessage(String msg) {
        List<TimetableItem> list = new ArrayList<>();
        list.add(new TimetableItem(msg, "", "", ""));
        return list;
    }

    /**
     * Controls the visibility of the shimmer animation and the main content.
     *
     * @param loading True to show the shimmer animation, false to show the main content.
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

    /**
     * Called when the activity is being destroyed. This is the final call that the activity
     * will receive. It is used to release resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shut down the executor service to prevent memory leaks
        executor.shutdownNow();
    }
}
