package com.siva.homeofveltech.UI.Attendance;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private SubjectAttendanceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_attendance);

        rv = findViewById(R.id.rvSubjectAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));

        amsClient = new AmsClient();

        fetchAndDisplayAttendance();
    }

    private void fetchAndDisplayAttendance() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                List<SubjectAttendanceItem> attendanceList = amsClient.fetchAttendanceData();

                handler.post(() -> {
                    adapter = new SubjectAttendanceAdapter(this, attendanceList, item -> {
                        Intent i = new Intent(this, SubjectFullAttendanceActivity.class);
                        i.putExtra("subjectName", item.subjectName);
                        i.putExtra("subjectCode", item.subjectCode);
                        i.putExtra("facultyName", item.facultyName);
                        startActivity(i);
                    });
                    rv.setAdapter(adapter);
                });
            } catch (IOException e) {
                // Handle error
                e.printStackTrace();
            }
        });
    }
}
