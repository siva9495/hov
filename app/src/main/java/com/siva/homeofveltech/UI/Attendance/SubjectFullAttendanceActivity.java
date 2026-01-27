package com.siva.homeofveltech.UI.Attendance;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.siva.homeofveltech.Adapter.SubjectFullAttendanceAdapter;
import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;

public class SubjectFullAttendanceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_full_attendance);

        String subjectName = getIntent().getStringExtra("subjectName");
        if (subjectName == null) subjectName = "Subject Full Attendance";

        TextView header = findViewById(R.id.tvHeaderCourse);
        header.setText(subjectName);

        RecyclerView rv = findViewById(R.id.rvFullAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // ✅ Dummy periods (mix P/A)
        List<PeriodAttendanceItem> dummy = new ArrayList<>();
        dummy.add(new PeriodAttendanceItem(subjectName, "12-12-2025", "10:00 - 11:00AM", true));
        dummy.add(new PeriodAttendanceItem(subjectName, "13-12-2025", "10:00 - 11:00AM", false));
        dummy.add(new PeriodAttendanceItem(subjectName, "14-12-2025", "10:00 - 11:00AM", true));
        dummy.add(new PeriodAttendanceItem(subjectName, "15-12-2025", "10:00 - 11:00AM", true));
        dummy.add(new PeriodAttendanceItem(subjectName, "16-12-2025", "10:00 - 11:00AM", false));
        dummy.add(new PeriodAttendanceItem(subjectName, "17-12-2025", "10:00 - 11:00AM", true));

        rv.setAdapter(new SubjectFullAttendanceAdapter(this, dummy));
    }
}
