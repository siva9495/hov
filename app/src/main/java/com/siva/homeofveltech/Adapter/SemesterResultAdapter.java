package com.siva.homeofveltech.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.siva.homeofveltech.Model.SemesterResult;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SemesterResultAdapter extends RecyclerView.Adapter<SemesterResultAdapter.VH> {

    private final List<SemesterResult> items;
    private int expandedPos = -1;
    private boolean showGrade;

    public SemesterResultAdapter(List<SemesterResult> items, boolean showGrade) {
        this.items = items != null ? items : new ArrayList<>();
        this.showGrade = showGrade;
    }

    public void setShowGrade(boolean showGrade) {
        this.showGrade = showGrade;
        notifyDataSetChanged();
    }

    public void setItems(List<SemesterResult> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        expandedPos = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_semester_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SemesterResult it = items.get(position);
        Context ctx = h.itemView.getContext();

        h.txtSemester.setText(it.semesterName == null ? "" : it.semesterName);

        int progress = (int) Math.round((it.tgpa / 10.0) * 100.0);
        h.ring.setProgress(progress);
        h.txtRingValue.setText(String.format(Locale.US, "%.2f", it.tgpa));

        int color = getRingColor(ctx, it.tgpa);
        h.ring.setIndicatorColor(color);

        boolean isExpanded = (position == expandedPos);
        h.detailsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        h.chevron.setRotation(isExpanded ? 180f : 0f);

        if (isExpanded) {
            h.rvSubjects.setLayoutManager(new LinearLayoutManager(ctx));
            h.rvSubjects.setAdapter(new SubjectGradeAdapter(it.subjects, showGrade));
        }

        h.headerClick.setOnClickListener(v -> {
            int old = expandedPos;
            expandedPos = (expandedPos == position) ? -1 : position;
            if (old != -1) notifyItemChanged(old);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View headerClick;
        TextView txtSemester;
        CircularProgressIndicator ring;
        TextView txtRingValue;
        ImageView chevron;

        View detailsContainer;
        RecyclerView rvSubjects;

        VH(@NonNull View itemView) {
            super(itemView);
            headerClick = itemView.findViewById(R.id.header_click);
            txtSemester = itemView.findViewById(R.id.txt_semester_name);
            ring = itemView.findViewById(R.id.ring_tgpa);
            txtRingValue = itemView.findViewById(R.id.txt_ring_value);
            chevron = itemView.findViewById(R.id.img_chevron);

            detailsContainer = itemView.findViewById(R.id.details_container);
            rvSubjects = itemView.findViewById(R.id.rv_subjects);
        }
    }

    private int getRingColor(Context ctx, double tgpa) {
        if (tgpa >= 8.0) return ctx.getColor(R.color.result_green);
        if (tgpa >= 6.0) return ctx.getColor(R.color.result_orange);
        return ctx.getColor(R.color.result_red);
    }
}
