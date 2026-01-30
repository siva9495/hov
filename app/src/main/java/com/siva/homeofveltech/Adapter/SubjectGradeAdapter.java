package com.siva.homeofveltech.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.siva.homeofveltech.Model.SubjectGrade;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubjectGradeAdapter extends RecyclerView.Adapter<SubjectGradeAdapter.VH> {

    private final List<SubjectGrade> items;
    private final boolean showGrade;

    public SubjectGradeAdapter(List<SubjectGrade> items, boolean showGrade) {
        this.items = items != null ? items : new ArrayList<>();
        this.showGrade = showGrade;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject_grade, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SubjectGrade it = items.get(position);

        String subject = it.subjectName == null ? "" : it.subjectName.trim();
        String g = it.grade == null ? "" : it.grade.trim().toUpperCase(Locale.US);

        h.txtSubject.setText(subject);

        // ✅ hide grades if toggle off
        if (!showGrade) {
            h.txtGrade.setText("—");
            h.gradeChip.setCardBackgroundColor(
                    ContextCompat.getColor(h.itemView.getContext(), R.color.chip_gray_bg)
            );
            h.gradeChip.setStrokeColor(
                    ContextCompat.getColor(h.itemView.getContext(), R.color.chip_stroke_gray)
            );
            h.txtGrade.setTextColor(
                    ContextCompat.getColor(h.itemView.getContext(), android.R.color.black)
            );
            return;
        }

        h.txtGrade.setText(g);

        int bg, stroke, text;

        if (g.equals("O") || g.equals("A+")) {
            bg = ContextCompat.getColor(h.itemView.getContext(), R.color.chip_green_bg);
            stroke = ContextCompat.getColor(h.itemView.getContext(), R.color.result_green);
            text = ContextCompat.getColor(h.itemView.getContext(), R.color.result_green);
        } else if (g.equals("A") || g.equals("B+")) {
            bg = ContextCompat.getColor(h.itemView.getContext(), R.color.chip_orange_bg);
            stroke = ContextCompat.getColor(h.itemView.getContext(), R.color.result_orange);
            text = ContextCompat.getColor(h.itemView.getContext(), R.color.result_orange);
        } else if (g.equals("RA") || g.equals("F")) {
            bg = ContextCompat.getColor(h.itemView.getContext(), R.color.chip_red_bg);
            stroke = ContextCompat.getColor(h.itemView.getContext(), R.color.result_red);
            text = ContextCompat.getColor(h.itemView.getContext(), R.color.result_red);
        } else {
            bg = ContextCompat.getColor(h.itemView.getContext(), R.color.chip_gray_bg);
            stroke = ContextCompat.getColor(h.itemView.getContext(), R.color.chip_stroke_gray);
            text = ContextCompat.getColor(h.itemView.getContext(), android.R.color.black);
        }

        h.gradeChip.setCardBackgroundColor(bg);
        h.gradeChip.setStrokeColor(stroke);
        h.txtGrade.setTextColor(text);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView gradeChip;
        TextView txtSubject, txtGrade;

        VH(@NonNull View itemView) {
            super(itemView);
            txtSubject = itemView.findViewById(R.id.txt_subject);
            txtGrade = itemView.findViewById(R.id.txt_grade);
            gradeChip = itemView.findViewById(R.id.grade_chip);
        }
    }
}
