package com.siva.homeofveltech.UI.Attendance;

import android.content.Intent;
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
import com.siva.homeofveltech.Adapter.SubjectAttendanceAdapter;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubjectAttendanceActivity extends AppCompatActivity {
    private static final long MIN_SHIMMER_MS = 400L;

    private final AmsClient amsClient = new AmsClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final Type attendanceListType = new TypeToken<List<SubjectAttendanceItem>>() {}.getType();

    private PrefsManager prefs;
    private RecyclerView rv;
    private ShimmerFrameLayout shimmerViewContainer;
    private TextView tvEmpty;
    private SubjectAttendanceAdapter adapter;
    private ImageView btnBack;
    private long loadingStartMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_attendance);
        prefs = new PrefsManager(this);

        rv = findViewById(R.id.rvSubjectAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));

        shimmerViewContainer = findViewById(R.id.shimmer_view_container);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);

        boolean showedCache = tryShowCachedAttendance();
        if (!showedCache) setLoading(true);

        fetchAndDisplayAttendance(showedCache);

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private boolean tryShowCachedAttendance() {
        try {
            if (!prefs.hasAttendanceCache()) return false;
            String json = prefs.getAttendanceCache();
            List<SubjectAttendanceItem> cached = gson.fromJson(json, attendanceListType);
            if (cached == null || cached.isEmpty()) return false;
            renderAttendance(cached);
            setLoading(false);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void fetchAndDisplayAttendance(boolean hasVisibleCache) {

        executor.execute(() -> {
            try {
                List<SubjectAttendanceItem> attendanceList = amsClient.fetchAttendanceData();
                if (attendanceList == null) attendanceList = new ArrayList<>();
                prefs.saveAttendanceCache(gson.toJson(attendanceList, attendanceListType));
                List<SubjectAttendanceItem> finalAttendanceList = attendanceList;

                handler.post(() -> {
                    renderAttendance(finalAttendanceList);
                    if (!hasVisibleCache) setLoading(false);
                });
            } catch (AmsClient.SessionExpiredException e) {
                handler.post(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedAttendance()) {
                        showEmpty("Session expired. Refresh session to load attendance.");
                        Toast.makeText(this, "Session expired. Refresh session from dashboard.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!hasVisibleCache) setLoading(false);
                    if (!tryShowCachedAttendance()) {
                        showEmpty("Failed to load attendance data.");
                        Toast.makeText(this, "Failed to load attendance: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void renderAttendance(List<SubjectAttendanceItem> attendanceList) {
        if (attendanceList != null && !attendanceList.isEmpty()) {
            adapter = new SubjectAttendanceAdapter(this, attendanceList, item -> {
                Intent i = new Intent(this, SubjectFullAttendanceActivity.class);
                i.putExtra("subjectCode", item.subjectCode);
                i.putExtra("subjectName", item.subjectName);
                i.putExtra("facultyName", item.facultyName);
                startActivity(i);
            });
            rv.setAdapter(adapter);
            rv.setVisibility(View.VISIBLE);
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        } else {
            showEmpty("No attendance data available.");
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
