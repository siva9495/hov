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
import com.siva.homeofveltech.Adapter.SubjectFullAttendanceAdapter;
import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubjectFullAttendanceActivity extends AppCompatActivity {

    private AmsClient amsClient;
    private RecyclerView rv;
    private ShimmerFrameLayout shimmerViewContainer;
    private TextView tvEmpty;
    private ImageView btnBack;

    private String subjectCode;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_full_attendance);

        // Get data from intent
        subjectName = getIntent().getStringExtra("subjectName");
        subjectCode = getIntent().getStringExtra("subjectCode");

        if (subjectName == null) subjectName = "Subject Full Attendance";
        if (subjectCode == null) subjectCode = "";

        // Initialize views
        TextView header = findViewById(R.id.tvHeaderCourse);
        header.setText(subjectName);

        rv = findViewById(R.id.rvFullAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));

        shimmerViewContainer = findViewById(R.id.shimmer_view_container);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);

        shimmerViewContainer.startShimmer();

        // Initialize client
        amsClient = new AmsClient();

        // Fetch real data
        fetchSubjectAttendance();

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void fetchSubjectAttendance() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // Fetch the detailed attendance for this subject
                List<PeriodAttendanceItem> periods = amsClient.fetchSubjectFullAttendance(
                        subjectCode,
                        subjectName
                );

                handler.post(() -> {
                    shimmerViewContainer.stopShimmer();
                    shimmerViewContainer.setVisibility(View.GONE);

                    if (periods != null && !periods.isEmpty()) {
                        rv.setAdapter(new SubjectFullAttendanceAdapter(this, periods));
                        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    } else {
                        // Show empty state
                        if (tvEmpty != null) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("No attendance records found for this subject.");
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    shimmerViewContainer.stopShimmer();
                    shimmerViewContainer.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load attendance: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    // Show error state
                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Failed to load attendance data. Please try again.");
                    }
                });
            }
        });
    }
}