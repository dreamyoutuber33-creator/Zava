package com.example;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern dynamic RecyclerView adapter for real-time live speech captions and assistant dialogue.
 */
public class CaptionsAdapter extends RecyclerView.Adapter<CaptionsAdapter.CaptionViewHolder> {

    private final Context context;
    private final List<CaptionItem> items = new ArrayList<>();

    public CaptionsAdapter(Context context) {
        this.context = context;
    }

    public synchronized void updateLiveUserCaption(String partialText) {
        if (partialText == null || partialText.trim().isEmpty()) return;

        if (!items.isEmpty()) {
            CaptionItem last = items.get(items.size() - 1);
            if (CaptionItem.SENDER_USER.equals(last.getSender()) && last.isLive()) {
                last.setText(partialText);
                notifyItemChanged(items.size() - 1);
                return;
            }
        }

        // Otherwise append new live item
        items.add(new CaptionItem(CaptionItem.SENDER_USER, partialText, true));
        notifyItemInserted(items.size() - 1);
    }

    public synchronized void finalizeUserCaption(String finalText) {
        if (finalText == null || finalText.trim().isEmpty()) return;

        if (!items.isEmpty()) {
            CaptionItem last = items.get(items.size() - 1);
            if (CaptionItem.SENDER_USER.equals(last.getSender()) && last.isLive()) {
                last.setText(finalText);
                last.setLive(false);
                notifyItemChanged(items.size() - 1);
                return;
            }
        }

        // Add confirmed item
        items.add(new CaptionItem(CaptionItem.SENDER_USER, finalText, false));
        notifyItemInserted(items.size() - 1);
    }

    public synchronized void addAssistantCaption(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // If there was a lingering live user caption, finalize it first
        if (!items.isEmpty()) {
            CaptionItem last = items.get(items.size() - 1);
            if (last.isLive()) {
                last.setLive(false);
                notifyItemChanged(items.size() - 1);
            }
        }

        items.add(new CaptionItem(CaptionItem.SENDER_ZAVA, text, false));
        notifyItemInserted(items.size() - 1);
    }

    public synchronized void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CaptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_caption, parent, false);
        return new CaptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CaptionViewHolder holder, int position) {
        CaptionItem item = items.get(position);
        boolean isUser = CaptionItem.SENDER_USER.equals(item.getSender());

        if (isUser) {
            holder.tvSender.setText("YOU");
            holder.tvSender.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan));
            holder.tvSender.setBackgroundResource(R.drawable.bg_status_pill);

            if (item.isLive()) {
                holder.tvLiveIndicator.setVisibility(View.VISIBLE);
                holder.tvLiveIndicator.setText("● LIVE");
                holder.tvLiveIndicator.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan));
                holder.tvText.setTypeface(null, Typeface.ITALIC);
                holder.tvText.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan));
                holder.tvText.setText("“" + item.getText() + "…”");
                holder.cardCaption.setStrokeColor(ContextCompat.getColor(context, R.color.neon_cyan));
            } else {
                holder.tvLiveIndicator.setVisibility(View.GONE);
                holder.tvText.setTypeface(null, Typeface.NORMAL);
                holder.tvText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                holder.tvText.setText("“" + item.getText() + "”");
                holder.cardCaption.setStrokeColor(ContextCompat.getColor(context, R.color.card_border));
            }
            holder.cardCaption.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_surface));
        } else {
            holder.tvSender.setText("ZAVA");
            holder.tvSender.setTextColor(ContextCompat.getColor(context, R.color.neon_purple));
            holder.tvSender.setBackgroundResource(R.drawable.bg_status_pill);

            holder.tvLiveIndicator.setVisibility(View.GONE);
            holder.tvText.setTypeface(null, Typeface.NORMAL);
            holder.tvText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.tvText.setText(item.getText());

            holder.cardCaption.setStrokeColor(ContextCompat.getColor(context, R.color.neon_purple));
            holder.cardCaption.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_surface_glass));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CaptionViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardCaption;
        final TextView tvSender;
        final TextView tvLiveIndicator;
        final TextView tvText;

        public CaptionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardCaption = itemView.findViewById(R.id.cardCaption);
            tvSender = itemView.findViewById(R.id.tvCaptionSender);
            tvLiveIndicator = itemView.findViewById(R.id.tvLiveIndicator);
            tvText = itemView.findViewById(R.id.tvCaptionText);
        }
    }
}
