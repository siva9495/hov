package com.siva.homeofveltech.UI.TimeTable;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.siva.homeofveltech.Adapter.FullTimeTableAdapter;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FullTimeTableActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private FullTimeTableAdapter adapter;

    private ShimmerFrameLayout shimmer;
    private View content;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();
    private PrefsManager prefs;

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

        loadFullTimetable();
    }

    private void loadFullTimetable() {
        setLoading(true);

        String username = prefs.getUsername();
        String password = prefs.getPassword();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        executor.execute(() -> {
            try {
                boolean ok = amsClient.isSessionValid() || amsClient.login(username, password);
                if (!ok) throw new Exception("Session expired. Login failed.");

                StudentDashboardData data = amsClient.fetchStudentDashboardData();

                List<FullTimeTableAdapter.Row> rows = new ArrayList<>();

                for (String day : DAYS) {
                    rows.add(FullTimeTableAdapter.Row.header(day));

                    List<TimetableItem> items = data.getTimetableForDay(day);
                    if (items == null || items.isEmpty()) {
                        rows.add(FullTimeTableAdapter.Row.empty("No classes"));
                    } else {
                        for (TimetableItem it : items) {
                            rows.add(FullTimeTableAdapter.Row.item(it));
                        }
                    }
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    adapter.setRows(rows);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        if (loading) {
            shimmer.startShimmer();
            shimmer.setVisibility(View.VISIBLE);
            content.setVisibility(View.GONE);
        } else {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
