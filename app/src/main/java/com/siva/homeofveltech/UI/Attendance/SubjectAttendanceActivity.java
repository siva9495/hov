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
import com.siva.homeofveltech.Adapter.SubjectAttendanceAdapter;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubjectAttendanceActivity extends AppCompatActivity {

    private AmsClient amsClient;
    private RecyclerView rv;
    private ShimmerFrameLayout shimmerViewContainer;
    private TextView tvEmpty;
    private SubjectAttendanceAdapter adapter;
    private ImageView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_attendance);

        rv = findViewById(R.id.rvSubjectAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));

        shimmerViewContainer = findViewById(R.id.shimmer_view_container);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);

        shimmerViewContainer.startShimmer();

        amsClient = new AmsClient();

        fetchAndDisplayAttendance();

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void fetchAndDisplayAttendance() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                List<SubjectAttendanceItem> attendanceList = amsClient.fetchAttendanceData();

                handler.post(() -> {
                    shimmerViewContainer.stopShimmer();
                    shimmerViewContainer.setVisibility(View.GONE);

                    if (attendanceList != null && !attendanceList.isEmpty()) {
                        adapter = new SubjectAttendanceAdapter(this, attendanceList, item -> {
                            Intent i = new Intent(this, SubjectFullAttendanceActivity.class);
                            i.putExtra("subjectCode", item.subjectCode);
                            i.putExtra("subjectName", item.subjectName);
                            i.putExtra("facultyName", item.facultyName);
                            startActivity(i);
                        });
                        rv.setAdapter(adapter);

                        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    } else {
                        if (tvEmpty != null) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("No attendance data available.");
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    shimmerViewContainer.stopShimmer();
                    shimmerViewContainer.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load attendance: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Failed to load attendance data.");
                    }
                });
            }
        });
    }
}