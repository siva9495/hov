package com.siva.homeofveltech.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.R;

import java.util.List;

public class SubjectFullAttendanceAdapter extends RecyclerView.Adapter<SubjectFullAttendanceAdapter.VH> {

    private final List<PeriodAttendanceItem> items;
    private final Context context;

    public SubjectFullAttendanceAdapter(Context context, List<PeriodAttendanceItem> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_full_attendance, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PeriodAttendanceItem it = items.get(position);

        h.tvCourseName.setText(it.courseName);
        h.tvDateTime.setText(it.date + " • " + it.timeSlot);

        if (it.present) {
            h.tvPA.setText("P");
            h.tvPA.setBackgroundResource(R.drawable.bg_pa_present);
            h.cardRoot.setStrokeColor(ContextCompat.getColor(context, R.color.att_green));
            h.cardRoot.setStrokeWidth(1);
        } else {
            h.tvPA.setText("A");
            h.tvPA.setBackgroundResource(R.drawable.bg_pa_absent);
            // ✅ Absent: thin red border
            h.cardRoot.setStrokeColor(ContextCompat.getColor(context, R.color.att_red));
            h.cardRoot.setStrokeWidth(1);
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        TextView tvCourseName, tvDateTime, tvPA;

        VH(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            tvCourseName = itemView.findViewById(R.id.tvCourseName);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvPA = itemView.findViewById(R.id.tvPA);
        }
    }
}
