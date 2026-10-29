package com.example.application.utils;

public class ChatMessage {
    private final String text;
    private final boolean isUser;
    private final long timestamp;
    private final boolean isTyping;

    public ChatMessage(String text, boolean isUser) {
        this(text, isUser, System.currentTimeMillis(), false);
    }

    public ChatMessage(String text, boolean isUser, long timestamp, boolean isTyping) {
        this.text = text;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.isTyping = isTyping;
    }

    public static ChatMessage typingIndicator() {
        return new ChatMessage("", false, System.currentTimeMillis(), true);
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isTyping() {
        return isTyping;
    }
}
