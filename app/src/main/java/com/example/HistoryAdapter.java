package com.example;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryItem> items = new ArrayList<>();

    public void setItems(List<HistoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = items.get(position);
        holder.tvIntent.setText(item.getIntent());
        holder.tvCommand.setText("“" + item.getCommand() + "”");
        holder.tvResult.setText(item.getResultMessage());

        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                item.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
        );
        holder.tvTime.setText(timeAgo);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIntent, tvCommand, tvResult, tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            tvIntent = itemView.findViewById(R.id.tvHistoryIntent);
            tvCommand = itemView.findViewById(R.id.tvHistoryCommand);
            tvResult = itemView.findViewById(R.id.tvHistoryResult);
            tvTime = itemView.findViewById(R.id.tvHistoryTime);
        }
    }
}
