package com.dhanaram.assistanttwin.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public static class VH extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView bubble;
        VH(LinearLayout row, TextView bubble) {
            super(row);
            this.row = row;
            this.bubble = bubble;
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(24, 8, 24, 8);

        TextView bubble = new TextView(parent.getContext());
        bubble.setPadding(28, 20, 28, 20);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(15f);
        row.addView(bubble, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new VH(row, bubble);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatMessage msg = messages.get(position);
        holder.bubble.setText(msg.content);

        boolean isUser = "user".equals(msg.role);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(36f);
        bg.setColor(Color.parseColor(isUser ? "#4A6CF7" : "#E4E6EB"));
        holder.bubble.setBackground(bg);
        holder.bubble.setTextColor(Color.parseColor(isUser ? "#FFFFFF" : "#1C1E21"));
        holder.bubble.setMaxWidth((int) (holder.row.getResources().getDisplayMetrics().widthPixels * 0.75));
        holder.row.setGravity(isUser ? Gravity.END : Gravity.START);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLast(String text) {
        if (messages.isEmpty()) return;
        messages.get(messages.size() - 1).content = text;
        notifyItemChanged(messages.size() - 1);
    }
}
