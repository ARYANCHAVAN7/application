package com.example.application.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.application.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatViewHolder> {

    private final List<ChatMessage> messages;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public ChatMessageAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        String formattedTime = timeFormat.format(new Date(message.getTimestamp()));

        if (message.isUser()) {
            holder.layoutUserMessage.setVisibility(View.VISIBLE);
            holder.layoutBotMessage.setVisibility(View.GONE);
            holder.textUserMessage.setText(message.getText());
            holder.textUserTime.setText(formattedTime);
        } else {
            holder.layoutUserMessage.setVisibility(View.GONE);
            holder.layoutBotMessage.setVisibility(View.VISIBLE);
            holder.textBotTime.setText(formattedTime);

            if (message.isTyping()) {
                holder.layoutTypingIndicator.setVisibility(View.VISIBLE);
                holder.textBotMessage.setVisibility(View.GONE);
            } else {
                holder.layoutTypingIndicator.setVisibility(View.GONE);
                holder.textBotMessage.setVisibility(View.VISIBLE);
                holder.textBotMessage.setText(message.getText());
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutUserMessage;
        TextView textUserMessage;
        TextView textUserTime;

        LinearLayout layoutBotMessage;
        LinearLayout cardBotBubble;
        TextView textBotMessage;
        LinearLayout layoutTypingIndicator;
        TextView textBotTime;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUserMessage = itemView.findViewById(R.id.layoutUserMessage);
            textUserMessage = itemView.findViewById(R.id.textUserMessage);
            textUserTime = itemView.findViewById(R.id.textUserTime);

            layoutBotMessage = itemView.findViewById(R.id.layoutBotMessage);
            cardBotBubble = itemView.findViewById(R.id.cardBotBubble);
            textBotMessage = itemView.findViewById(R.id.textBotMessage);
            layoutTypingIndicator = itemView.findViewById(R.id.layoutTypingIndicator);
            textBotTime = itemView.findViewById(R.id.textBotTime);
        }
    }
}
