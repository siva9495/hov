package com.siva.homeofveltech.UI.TimeTable;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.siva.homeofveltech.Adapter.FullTimeTableAdapter;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FullTimeTableActivity extends AppCompatActivity {
    private static final long MIN_SHIMMER_MS = 400L;

    private RecyclerView recycler;
    private FullTimeTableAdapter adapter;

    private ShimmerFrameLayout shimmer;
    private View content;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private final Gson gson = new Gson();
    private PrefsManager prefs;
    private long loadingStartMs = 0L;

    private final List<String> DAYS = Arrays.asList(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_time_table);

        prefs = new PrefsManager(this);

        shimmer = findViewById(R.id.shimmer_layout);
        content = findViewById(R.id.content_container);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        recycler = findViewById(R.id.recyclerFullTimetable);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FullTimeTableAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        boolean showedCache = tryShowCachedTimetable();
        if (!showedCache) setLoading(true);
        loadFullTimetable(showedCache);
    }

    private void loadFullTimetable(boolean hasVisibleCache) {
        executor.execute(() -> {
            try {
                StudentDashboardData data = amsClient.fetchStudentDashboardData();
                prefs.saveDashboardCache(gson.toJson(data));
                prefs.saveTimetableCache(gson.toJson(data.weekTimetable));

                List<FullTimeTableAdapter.Row> rows = buildRows(data.weekTimetable);

                runOnUiThread(() -> {
                    adapter.setRows(rows);
                    if (!hasVisibleCache) setLoading(false);
                });

            } catch (AmsClient.SessionExpiredException e) {
                runOnUiThread(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedTimetable()) {
                        Toast.makeText(this, "Session expired. Refresh session from dashboard.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedTimetable()) {
                        Toast.makeText(this, "Failed to load timetable: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private boolean tryShowCachedTimetable() {
        try {
            Map<String, List<TimetableItem>> week = readTimetableFromCache();
            if (week == null || week.isEmpty()) return false;

            List<FullTimeTableAdapter.Row> rows = buildRows(week);
            adapter.setRows(rows);
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

    private List<FullTimeTableAdapter.Row> buildRows(Map<String, List<TimetableItem>> timetableByDay) {
        List<FullTimeTableAdapter.Row> rows = new ArrayList<>();
        for (String day : DAYS) {
            rows.add(FullTimeTableAdapter.Row.header(day));

            List<TimetableItem> items = timetableByDay == null ? null : timetableByDay.get(day);
            if (items == null || items.isEmpty()) {
                rows.add(FullTimeTableAdapter.Row.empty("No classes"));
            } else {
                for (TimetableItem it : items) {
                    rows.add(FullTimeTableAdapter.Row.item(it));
                }
            }
        }
        return rows;
    }

    private void setLoading(boolean loading) {
        if (loading) {
            loadingStartMs = System.currentTimeMillis();
            shimmer.startShimmer();
            shimmer.setVisibility(View.VISIBLE);
            content.setVisibility(View.GONE);
        } else {
            long elapsed = System.currentTimeMillis() - loadingStartMs;
            long delay = Math.max(0L, MIN_SHIMMER_MS - elapsed);
            shimmer.postDelayed(() -> {
                shimmer.stopShimmer();
                shimmer.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
            }, delay);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
