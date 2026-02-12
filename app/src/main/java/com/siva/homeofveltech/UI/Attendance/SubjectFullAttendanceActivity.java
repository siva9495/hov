package com.siva.homeofveltech.UI.Attendance;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.siva.homeofveltech.Adapter.SubjectFullAttendanceAdapter;
import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubjectFullAttendanceActivity extends AppCompatActivity {
    private static final long MIN_SHIMMER_MS = 400L;

    private final AmsClient amsClient = new AmsClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final Type fullAttendanceType = new TypeToken<List<PeriodAttendanceItem>>() {}.getType();

    private PrefsManager prefs;
    private RecyclerView rv;
    private ShimmerFrameLayout shimmerViewContainer;
    private TextView tvEmpty;
    private ImageView btnBack;
    private long loadingStartMs = 0L;

    private String subjectCode;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_full_attendance);
        prefs = new PrefsManager(this);

        // Get data from intent
        subjectName = getIntent().getStringExtra("subjectName");
        subjectCode = getIntent().getStringExtra("subjectCode");

        if (subjectName == null) subjectName = "Subject Full Attendance";
        if (subjectCode == null) subjectCode = "";
        subjectName = subjectName.trim();
        subjectCode = subjectCode.trim();

        // Initialize views
        TextView header = findViewById(R.id.tvHeaderCourse);
        header.setText(subjectName);

        rv = findViewById(R.id.rvFullAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));

        shimmerViewContainer = findViewById(R.id.shimmer_view_container);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);

        boolean showedCache = tryShowCachedFullAttendance();
        if (!showedCache) setLoading(true);
        fetchSubjectAttendance(showedCache);

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private boolean tryShowCachedFullAttendance() {
        try {
            if (!prefs.hasSubjectFullAttendanceCache(subjectCode, subjectName)) return false;
            String json = prefs.getSubjectFullAttendanceCache(subjectCode, subjectName);
            List<PeriodAttendanceItem> cached = gson.fromJson(json, fullAttendanceType);
            if (cached == null || cached.isEmpty()) return false;
            renderData(cached);
            setLoading(false);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void fetchSubjectAttendance(boolean hasVisibleCache) {

        executor.execute(() -> {
            try {
                // Fetch the detailed attendance for this subject
                List<PeriodAttendanceItem> periods = amsClient.fetchSubjectFullAttendance(
                        subjectCode,
                        subjectName
                );
                if (periods == null) periods = new ArrayList<>();
                prefs.saveSubjectFullAttendanceCache(subjectCode, subjectName, gson.toJson(periods, fullAttendanceType));
                List<PeriodAttendanceItem> finalPeriods = periods;

                handler.post(() -> {
                    renderData(finalPeriods);
                    if (!hasVisibleCache) setLoading(false);
                });
            } catch (AmsClient.SessionExpiredException e) {
                handler.post(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedFullAttendance()) {
                        showEmpty("Session expired. Refresh session and try again.");
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedFullAttendance()) {
                        Toast.makeText(this, "Failed to load attendance: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        showEmpty("Failed to load attendance data. Please try again.");
                    }
                });
            }
        });
    }

    private void renderData(List<PeriodAttendanceItem> periods) {
        if (periods != null && !periods.isEmpty()) {
            rv.setVisibility(View.VISIBLE);
            rv.setAdapter(new SubjectFullAttendanceAdapter(this, periods));
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        } else {
            showEmpty("No attendance records found for this subject.");
        }
    }

    private void showEmpty(String message) {
        rv.setVisibility(View.GONE);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        }
    }

    private void setLoading(boolean loading) {
        if (loading) {
            loadingStartMs = System.currentTimeMillis();
            shimmerViewContainer.startShimmer();
            shimmerViewContainer.setVisibility(View.VISIBLE);
        } else {
            long elapsed = System.currentTimeMillis() - loadingStartMs;
            long delay = Math.max(0L, MIN_SHIMMER_MS - elapsed);
            shimmerViewContainer.postDelayed(() -> {
                shimmerViewContainer.stopShimmer();
                shimmerViewContainer.setVisibility(View.GONE);
            }, delay);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
