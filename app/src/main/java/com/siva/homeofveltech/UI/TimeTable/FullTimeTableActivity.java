package com.siva.homeofveltech.UI.TimeTable;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.siva.homeofveltech.Adapter.DayTabsAdapter;
import com.siva.homeofveltech.Adapter.FullTimeTableAdapter;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.Utils.TimetableStatusUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FullTimeTableActivity extends AppCompatActivity {
    private static final long MIN_SHIMMER_MS = 400L;

    private RecyclerView recyclerSessions;
    private RecyclerView recyclerDayTabs;
    private FullTimeTableAdapter sessionAdapter;
    private DayTabsAdapter dayTabsAdapter;
    private ShimmerFrameLayout shimmer;
    private TextView txtSelectedDay;
    private TextView txtEmptyDay;
    private boolean isLoading = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private final Gson gson = new Gson();
    private PrefsManager prefs;
    private long loadingStartMs = 0L;

    private final List<String> BASE_DAYS = Arrays.asList(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"
    );
    private final List<String> orderedDays = new ArrayList<>();
    private final Map<String, List<TimetableItem>> weekTimetable = new HashMap<>();
    private String selectedDay = "Monday";
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            renderSelectedDay();
            statusHandler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_time_table);

        prefs = new PrefsManager(this);

        shimmer = findViewById(R.id.shimmer_layout);
        txtSelectedDay = findViewById(R.id.txt_selected_day);
        txtEmptyDay = findViewById(R.id.txt_empty_day);
        recyclerDayTabs = findViewById(R.id.recycler_day_tabs);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        recyclerSessions = findViewById(R.id.recyclerFullTimetable);
        recyclerSessions.setLayoutManager(new LinearLayoutManager(this));
        sessionAdapter = new FullTimeTableAdapter(new ArrayList<>());
        recyclerSessions.setAdapter(sessionAdapter);

        orderedDays.addAll(buildOrderedDays());
        selectedDay = orderedDays.isEmpty() ? "Monday" : orderedDays.get(0);

        recyclerDayTabs.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dayTabsAdapter = new DayTabsAdapter(orderedDays, 0, (position, dayName) -> {
            selectedDay = dayName;
            renderSelectedDay();
        });
        recyclerDayTabs.setAdapter(dayTabsAdapter);

        boolean showedCache = tryShowCachedTimetable();
        if (!showedCache) {
            setLoading(true);
            loadFullTimetable(false);
        }
    }

    private void loadFullTimetable(boolean hasVisibleCache) {
        executor.execute(() -> {
            try {
                StudentDashboardData data = amsClient.fetchStudentDashboardData();
                prefs.saveDashboardCache(gson.toJson(data));
                prefs.saveTimetableCache(gson.toJson(data.weekTimetable));
                weekTimetable.clear();
                if (data.weekTimetable != null) {
                    weekTimetable.putAll(data.weekTimetable);
                }

                runOnUiThread(() -> {
                    renderSelectedDay();
                    if (!hasVisibleCache) setLoading(false);
                });

            } catch (AmsClient.SessionExpiredException e) {
                runOnUiThread(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedTimetable()) {
                        showNoSavedState();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedTimetable()) {
                        showNoSavedState();
                    }
                });
            }
        });
    }

    private boolean tryShowCachedTimetable() {
        try {
            Map<String, List<TimetableItem>> week = readTimetableFromCache();
            if (week == null || week.isEmpty()) return false;

            weekTimetable.clear();
            weekTimetable.putAll(week);
            renderSelectedDay();
            setLoading(false);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, List<TimetableItem>> readTimetableFromCache() {
        Type mapType = new TypeToken<Map<String, List<TimetableItem>>>() {}.getType();

        String timetableJson = prefs.getTimetableCache();
        if (timetableJson != null && !timetableJson.trim().isEmpty()) {
            Map<String, List<TimetableItem>> timetable = gson.fromJson(timetableJson, mapType);
            if (timetable != null && !timetable.isEmpty()) return timetable;
        }

        String dashboardJson = prefs.getDashboardCache();
        if (dashboardJson != null && !dashboardJson.trim().isEmpty()) {
            StudentDashboardData cachedDashboard = gson.fromJson(dashboardJson, StudentDashboardData.class);
            if (cachedDashboard != null && cachedDashboard.weekTimetable != null && !cachedDashboard.weekTimetable.isEmpty()) {
                prefs.saveTimetableCache(gson.toJson(cachedDashboard.weekTimetable, mapType));
                return cachedDashboard.weekTimetable;
            }
        }

        return new HashMap<>();
    }

    private List<String> buildOrderedDays() {
        List<String> days = new ArrayList<>();
        if (BASE_DAYS.isEmpty()) return days;

        String startDay = getAutoStartDay();
        int startIdx = BASE_DAYS.indexOf(startDay);
        if (startIdx < 0) startIdx = 0;

        for (int i = 0; i < BASE_DAYS.size(); i++) {
            days.add(BASE_DAYS.get((startIdx + i) % BASE_DAYS.size()));
        }
        return days;
    }

    private String getAutoStartDay() {
        String day = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        if ("Saturday".equalsIgnoreCase(day) || "Sunday".equalsIgnoreCase(day)) {
            return "Monday";
        }
        return BASE_DAYS.contains(day) ? day : "Monday";
    }

    private void renderSelectedDay() {
        if (txtSelectedDay != null) {
            txtSelectedDay.setText(selectedDay + " Timetable");
        }

        if (weekTimetable.isEmpty()) {
            sessionAdapter.setItems(new ArrayList<>());
            if (txtEmptyDay != null) txtEmptyDay.setText("No saved timetable data available.");
            if (!isLoading) applyContentVisibility(false);
            return;
        }

        List<TimetableItem> raw = weekTimetable.get(selectedDay);
        List<TimetableItem> display = buildDisplayItems(selectedDay, raw);
        sessionAdapter.setItems(display);

        if (display.isEmpty() && txtEmptyDay != null) {
            txtEmptyDay.setText("No classes for " + selectedDay);
        }
        if (!isLoading) applyContentVisibility(!display.isEmpty());
    }

    private List<TimetableItem> buildDisplayItems(String day, List<TimetableItem> raw) {
        List<TimetableItem> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        for (TimetableItem it : raw) {
            if (it == null) continue;
            String status = TimetableStatusUtils.computeStatusForDay(day, it.time);
            out.add(new TimetableItem(it.code, it.subject, it.time, status));
        }

        // Order cards as requested: Live -> Next -> Done
        Collections.sort(out, (a, b) -> Integer.compare(statusPriority(a.status), statusPriority(b.status)));
        return out;
    }

    private int statusPriority(String status) {
        if (status == null) return 1;
        String s = status.trim();
        if (s.equalsIgnoreCase("On Going") || s.equalsIgnoreCase("Live")) return 0;
        if (s.equalsIgnoreCase("Completed") || s.equalsIgnoreCase("Done")) return 2;
        return 1; // Upcoming/Next
    }

    private void showNoSavedState() {
        weekTimetable.clear();
        renderSelectedDay();
        Toast.makeText(this, "No saved timetable data available.", Toast.LENGTH_SHORT).show();
    }

    private void applyContentVisibility(boolean hasItems) {
        if (recyclerSessions != null) {
            recyclerSessions.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }
        if (txtEmptyDay != null) {
            txtEmptyDay.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        }
    }

    private void setLoading(boolean loading) {
        if (loading) {
            isLoading = true;
            loadingStartMs = System.currentTimeMillis();
            shimmer.startShimmer();
            shimmer.setVisibility(View.VISIBLE);
            if (txtEmptyDay != null) txtEmptyDay.setVisibility(View.GONE);
            if (recyclerSessions != null) recyclerSessions.setVisibility(View.GONE);
        } else {
            long elapsed = System.currentTimeMillis() - loadingStartMs;
            long delay = Math.max(0L, MIN_SHIMMER_MS - elapsed);
            shimmer.postDelayed(() -> {
                isLoading = false;
                shimmer.stopShimmer();
                shimmer.setVisibility(View.GONE);
                applyContentVisibility(sessionAdapter.getItemCount() > 0);
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
}
