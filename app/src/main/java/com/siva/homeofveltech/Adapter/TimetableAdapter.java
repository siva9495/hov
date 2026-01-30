package com.siva.homeofveltech.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimetableAdapter extends RecyclerView.Adapter<TimetableAdapter.VH> {

    private List<TimetableItem> items;

    public TimetableAdapter(List<TimetableItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public void setItems(List<TimetableItem> newItems) {
        this.items = newItems == null ? new ArrayList<>() : newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timetable, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TimetableItem it = items.get(position);

        // Subject fallback
        String subject = (it.subject == null || it.subject.trim().isEmpty()) ? it.code : it.subject;
        h.tvSubject.setText(subject);
        h.tvCode.setText(it.code == null ? "" : it.code);

        h.tvTime.setText(prettySlot(it.time));
        h.tvTime.setText(prettySlot(it.time));

        String st = (it.status == null) ? "" : it.status.trim();
        if (st.equalsIgnoreCase("On Going")) st = "Live";
        else if (st.equalsIgnoreCase("Upcoming")) st = "Next";
        else if (st.equalsIgnoreCase("Completed")) st = "Done";
        h.tvStatus.setText(st);


        int colorRes;
        boolean lightChip = false;

        if ("On Going".equalsIgnoreCase(it.status)) {
            colorRes = R.color.status_ongoing;
        } else if ("Completed".equalsIgnoreCase(it.status)) {
            colorRes = R.color.status_completed;
            lightChip = true;
        } else {
            colorRes = R.color.status_upcoming;
        }

        h.tvStatus.setBackgroundTintList(
                ContextCompat.getColorStateList(h.itemView.getContext(), colorRes)
        );

        // ✅ readable text on chip
        h.tvStatus.setTextColor(ContextCompat.getColor(
                h.itemView.getContext(),
                lightChip ? R.color.status_text_dark : android.R.color.white
        ));
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCode, tvSubject, tvTime, tvStatus, emptyText;

        VH(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tv_code);
            tvSubject = itemView.findViewById(R.id.tv_subject);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvStatus = itemView.findViewById(R.id.statusText);
            emptyText = itemView.findViewById(R.id.emptyText);
        }
    }

    // ✅ 2.45-3.35 PM  ->  2:45 – 3:35 PM
    private String prettySlot(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace(" ", "");

        // Pattern: 2.45-3.35PM OR 2:45-3:35PM
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
            return sh + ":" + sm + " – " + eh + ":" + em + " " + ap;
        }

        // Fallback: just replace dot between digits
        return raw.replaceAll("(\\d)\\.(\\d)", "$1:$2");
    }

    private String two(String mm) {
        if (mm == null) return "00";
        mm = mm.trim();
        if (mm.length() == 1) return mm + "0";
        return mm;
    }
}
