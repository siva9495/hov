package com.siva.homeofveltech.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.siva.homeofveltech.Model.TimetableItem;
import com.siva.homeofveltech.R;

import java.util.ArrayList;
import java.util.List;

public class FullTimeTableAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static class Row {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_ITEM = 1;
        public static final int TYPE_EMPTY = 2;

        public final int type;
        public final String header;
        public final TimetableItem item;
        public final String emptyText;

        private Row(int type, String header, TimetableItem item, String emptyText) {
            this.type = type;
            this.header = header;
            this.item = item;
            this.emptyText = emptyText;
        }

        public static Row header(String day) { return new Row(TYPE_HEADER, day, null, null); }
        public static Row item(TimetableItem t) { return new Row(TYPE_ITEM, null, t, null); }
        public static Row empty(String msg) { return new Row(TYPE_EMPTY, null, null, msg); }
    }

    private List<Row> rows = new ArrayList<>();

    public FullTimeTableAdapter(List<Row> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public void setRows(List<Row> newRows) {
        this.rows = newRows == null ? new ArrayList<>() : newRows;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == Row.TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_full_timetable_header, parent, false));
        } else if (viewType == Row.TYPE_EMPTY) {
            return new EmptyVH(inf.inflate(R.layout.item_full_timetable_empty, parent, false));
        }
        return new ItemVH(inf.inflate(R.layout.item_full_timetable_class, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row r = rows.get(position);

        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).title.setText(r.header);
        } else if (holder instanceof EmptyVH) {
            ((EmptyVH) holder).text.setText(r.emptyText);
        } else if (holder instanceof ItemVH) {
            TimetableItem t = r.item;
            ItemVH h = (ItemVH) holder;
            h.code.setText(t.code);
            h.subject.setText(t.subject);
            h.time.setText(t.time);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView title;
        HeaderVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_day_header);
        }
    }

    static class EmptyVH extends RecyclerView.ViewHolder {
        TextView text;
        EmptyVH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_empty);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView code, subject, time;
        ItemVH(@NonNull View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.tv_code);
            subject = itemView.findViewById(R.id.tv_subject);
            time = itemView.findViewById(R.id.tv_time);
        }
    }
}
