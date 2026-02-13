package com.siva.homeofveltech.Adapter;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FullTimeTableAdapter extends RecyclerView.Adapter<FullTimeTableAdapter.VH> {

    private List<TimetableItem> items = new ArrayList<>();

    public FullTimeTableAdapter(List<TimetableItem> initialItems) {
        this.items = initialItems == null ? new ArrayList<>() : initialItems;
    }

    public void setItems(List<TimetableItem> newItems) {
        this.items = newItems == null ? new ArrayList<>() : newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_full_timetable_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TimetableItem it = items.get(position);

        String subject = (it.subject == null || it.subject.trim().isEmpty()) ? it.code : it.subject;
        h.tvSubject.setText(subject);
        h.tvCode.setText(it.code == null ? "" : it.code);
        h.tvTime.setText(prettySlot(it.time));

        String status = normalizeStatus(it.status);
        h.tvStatus.setText(status);

        int colorRes;
        boolean lightChip = false;
        if ("Live".equalsIgnoreCase(status)) {
            colorRes = R.color.status_ongoing;
        } else if ("Done".equalsIgnoreCase(status)) {
            colorRes = R.color.status_completed;
            lightChip = true;
        } else {
            colorRes = R.color.status_upcoming;
        }

        h.tvStatus.setBackgroundTintList(
                ContextCompat.getColorStateList(h.itemView.getContext(), colorRes)
        );
        h.tvStatus.setTextColor(ContextCompat.getColor(
                h.itemView.getContext(),
                lightChip ? R.color.status_text_dark : android.R.color.white
        ));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String normalizeStatus(String raw) {
        String st = raw == null ? "" : raw.trim();
        if (st.equalsIgnoreCase("On Going")) return "Live";
        if (st.equalsIgnoreCase("Completed")) return "Done";
        return "Next";
    }

    private String prettySlot(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace(" ", "");

        Pattern p = Pattern.compile(
                "([0-9]{1,2})(?:\\.|:)([0-9]{1,2})-([0-9]{1,2})(?:\\.|:)([0-9]{1,2})(AM|PM)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(s);
        if (m.find()) {
            String sh = m.group(1);
            String sm = two(m.group(2));
            String eh = m.group(3);
            String em = two(m.group(4));
            String ap = m.group(5).toUpperCase(Locale.US);
            return sh + ":" + sm + " - " + eh + ":" + em + " " + ap;
        }

        return raw.replaceAll("(\\d)\\.(\\d)", "$1:$2");
    }

    private String two(String mm) {
        if (mm == null) return "00";
        mm = mm.trim();
        if (mm.length() == 1) return mm + "0";
        return mm;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvSubject, tvCode, tvTime, tvStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            tvSubject = itemView.findViewById(R.id.tv_subject);
            tvCode = itemView.findViewById(R.id.tv_code);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvStatus = itemView.findViewById(R.id.statusText);
        }
    }
}
