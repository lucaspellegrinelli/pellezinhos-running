package com.ellep.runningcompanion;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class HistoryAdapter extends BaseAdapter {
    Context context;
    List<HistoryItem> items;

    private static LayoutInflater inflater = null;

    public HistoryAdapter(Context context, List<HistoryItem> items) {
        this.context = context;
        this.items = items;

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int i) {
        return items.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View vi = view;
        if (vi == null) {
            vi = inflater.inflate(R.layout.activity_listview, null);
        }

        TextView distance = vi.findViewById(R.id.distance);
        distance.setText(String.format("%.2f km", this.items.get(i).getDistance()));

        TextView time = vi.findViewById(R.id.time);
        time.setText(Utils.formatTime(this.items.get(i).getTime()));

        TextView pace = vi.findViewById(R.id.pace);
        pace.setText(String.format("%.2f min/km", this.items.get(i).getPace()));

        TextView when = vi.findViewById(R.id.when);
        when.setText(Utils.formatDateTime(this.items.get(i).getWhen()));

        return vi;
    }
}
