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
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
import com.siva.homeofveltech.R;

import java.util.List;

public class SubjectAttendanceAdapter extends RecyclerView.Adapter<SubjectAttendanceAdapter.VH> {

    public interface OnSubjectClickListener {
        void onSubjectClick(SubjectAttendanceItem item);
    }

    private final List<SubjectAttendanceItem> items;
    private final Context context;
    private final OnSubjectClickListener listener;

    public SubjectAttendanceAdapter(Context context, List<SubjectAttendanceItem> items, OnSubjectClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_attendance, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SubjectAttendanceItem it = items.get(position);

        h.tvSubjectName.setText(it.subjectName);
        h.tvSubjectCode.setText("Subject Code: " + it.subjectCode);
        h.tvFacultyName.setText("Faculty Name: " + it.facultyName);

        h.tvTotalValue.setText(String.valueOf(it.totalSessions));
        h.tvAttendedValue.setText(String.valueOf(it.attendedSessions));
        h.tvConductedValue.setText(String.valueOf(it.conductedSessions));

        int pct = it.presentPercentage;

        h.tvPercent.setText(pct + "%");
        h.cpiAttendance.setMax(100);
        h.cpiAttendance.setProgress(pct);

        // ring color rule
        int colorRes;
        if (pct < 75) colorRes = R.color.att_red;
        else if (pct <= 90) colorRes = R.color.att_orange;
        else colorRes = R.color.att_green;
        h.cpiAttendance.setIndicatorColor(ContextCompat.getColor(context, colorRes));

        // Engaging bunk / required text
        h.tvBunkInfo.setText(buildBunkText(it.attendedSessions, it.conductedSessions));

        // Click to open full attendance
        h.rootCard.setOnClickListener(v -> {
            if (listener != null) listener.onSubjectClick(it);
        });
    }

    private String buildBunkText(int attended, int conducted) {
        if (conducted <= 0) {
            return "No classes held yet.";
        }

        double currentPercentage = (double) attended / conducted * 100;

        if (currentPercentage >= 75) {
            int canBunk = (int) Math.floor((4.0 * attended - 3.0 * conducted) / 3.0);
            if (canBunk > 0) {
                return "You can bunk " + canBunk + " more class(es).";
            } else {
                return "Attend next class to increase bunk options.";
            }
        } else {
            int needed = (int) Math.ceil((3.0 * conducted - 4.0 * attended));
            if(needed > 0){
                 return "Attend next " + needed + " class(es) to reach 75%.";
            }else{
                 return "You are on the edge, attend next class.";
            }
           
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView rootCard;
        TextView tvSubjectName, tvSubjectCode, tvFacultyName;
        TextView tvTotalValue, tvAttendedValue, tvConductedValue;
        TextView tvPercent, tvBunkInfo;
        CircularProgressIndicator cpiAttendance;

        VH(@NonNull View itemView) {
            super(itemView);
            rootCard = itemView.findViewById(R.id.rootCard);

            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvSubjectCode = itemView.findViewById(R.id.tvSubjectCode);
            tvFacultyName = itemView.findViewById(R.id.tvFacultyName);

            tvTotalValue = itemView.findViewById(R.id.tvTotalValue);
            tvAttendedValue = itemView.findViewById(R.id.tvAttendedValue);
            tvConductedValue = itemView.findViewById(R.id.tvConductedValue);

            tvPercent = itemView.findViewById(R.id.tvPercent);
            tvBunkInfo = itemView.findViewById(R.id.tvBunkInfo);

            cpiAttendance = itemView.findViewById(R.id.cpiAttendance);
        }
    }
}
