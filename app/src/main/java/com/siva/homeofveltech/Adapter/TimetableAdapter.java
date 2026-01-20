package com.siva.homeofveltech.Adapter;

import android.text.TextUtils;
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

        boolean isMessageCard =
                TextUtils.isEmpty(it.time) && TextUtils.isEmpty(it.status) && !TextUtils.isEmpty(it.code);

        if (isMessageCard) {
            h.emptyText.setVisibility(View.VISIBLE);
            h.emptyText.setText(it.code);

            h.tvCode.setVisibility(View.GONE);
            h.tvTime.setVisibility(View.GONE);
            h.tvStatus.setVisibility(View.GONE);
            return;
        }

        h.emptyText.setVisibility(View.GONE);

        h.tvCode.setVisibility(View.VISIBLE);
        h.tvTime.setVisibility(View.VISIBLE);
        h.tvStatus.setVisibility(View.VISIBLE);

        h.tvCode.setText(it.code);     // ✅ course code only
        h.tvTime.setText(it.time);     // ✅ time only
        h.tvStatus.setText(it.status); // ✅ status only

        int colorRes;
        if ("On Going".equalsIgnoreCase(it.status)) colorRes = R.color.status_ongoing;
        else if ("Completed".equalsIgnoreCase(it.status)) colorRes = R.color.status_completed;
        else colorRes = R.color.status_upcoming;

        // status chip tint (works because statusText has bg drawable)
        h.tvStatus.setBackgroundTintList(
                ContextCompat.getColorStateList(h.itemView.getContext(), colorRes)
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCode, tvTime, tvStatus, emptyText;

        VH(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.subject);
            tvTime = itemView.findViewById(R.id.timeSlot);
            tvStatus = itemView.findViewById(R.id.statusText);
            emptyText = itemView.findViewById(R.id.emptyText);
        }
    }
}
