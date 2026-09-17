package com.dhanaram.assistanttwin.ui;

public class ChatMessage {
    public final String role; // "user" | "assistant"
    public String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
