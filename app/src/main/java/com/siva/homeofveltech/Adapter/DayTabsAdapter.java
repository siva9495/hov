package com.siva.homeofveltech.Adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DayTabsAdapter extends RecyclerView.Adapter<DayTabsAdapter.VH> {

    public interface OnDayClickListener {
        void onDayClick(int position, String dayName);
    }

    private final List<String> days = new ArrayList<>();
    private int selectedPosition = 0;
    private final OnDayClickListener listener;

    public DayTabsAdapter(List<String> initialDays, int selectedPosition, OnDayClickListener listener) {
        if (initialDays != null) days.addAll(initialDays);
        this.selectedPosition = Math.max(0, selectedPosition);
        this.listener = listener;
    }

    public void setDays(List<String> newDays) {
        days.clear();
        if (newDays != null) days.addAll(newDays);
        if (selectedPosition >= days.size()) selectedPosition = 0;
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int newPosition) {
        if (newPosition < 0 || newPosition >= days.size()) return;
        int old = selectedPosition;
        selectedPosition = newPosition;
        notifyItemChanged(old);
        notifyItemChanged(newPosition);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timetable_day_chip, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String day = days.get(position);
        holder.dayText.setText(shortDay(day));
        applySelectionStyle(holder, position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            if (position == selectedPosition) return;
            int old = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(old);
            notifyItemChanged(position);
            if (listener != null) listener.onDayClick(position, day);
        });
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    private void applySelectionStyle(VH h, boolean selected) {
        if (selected) {
            h.card.setCardBackgroundColor(Color.parseColor("#F5A300"));
            h.card.setStrokeColor(Color.TRANSPARENT);
            h.dayText.setTextColor(Color.WHITE);
        } else {
            h.card.setCardBackgroundColor(Color.parseColor("#FFFFFFFF"));
            h.card.setStrokeColor(Color.parseColor("#33F5A300"));
            h.dayText.setTextColor(Color.parseColor("#545454"));
        }
    }

    private String shortDay(String day) {
        if (day == null) return "";
        String d = day.trim();
        if (d.length() <= 3) return d;
        return d.substring(0, 3).toUpperCase(Locale.US);
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView dayText;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_day_chip);
            dayText = itemView.findViewById(R.id.txt_day_chip);
        }
    }
}
