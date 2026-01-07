package com.quicinc.chatapp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Represents a chat conversation session with all messages and context
 */
public class ChatSession {
    private String sessionId;
    private String title;
    private long timestamp;
    private List<ChatMessage> messages;
    private String pdfContext;
    private String pdfFileName;
    private String imageContext;
    private String imageFileName;
    
    // Constructor for new session
    public ChatSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.messages = new ArrayList<>();
        this.title = "New Chat";
    }
    
    // Constructor for loading existing session
    public ChatSession(String sessionId, String title, long timestamp) {
        this.sessionId = sessionId;
        this.title = title;
        this.timestamp = timestamp;
        this.messages = new ArrayList<>();
    }
    
    // Getters
    public String getSessionId() {
        return sessionId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public List<ChatMessage> getMessages() {
        return messages;
    }
    
    public String getPdfContext() {
        return pdfContext;
    }
    
    public String getPdfFileName() {
        return pdfFileName;
    }
    
    public String getImageContext() {
        return imageContext;
    }
    
    public String getImageFileName() {
        return imageFileName;
    }
    
    // Setters
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
    
    public void setPdfContext(String pdfContext, String pdfFileName) {
        this.pdfContext = pdfContext;
        this.pdfFileName = pdfFileName;
    }
    
    public void setImageContext(String imageContext, String imageFileName) {
        this.imageContext = imageContext;
        this.imageFileName = imageFileName;
    }
    
    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        updateTimestamp();
    }
    
    public void updateTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Generate a smart title from the first user message
     * Limits to first 40 characters
     */
    public void generateTitleFromFirstMessage() {
        for (ChatMessage msg : messages) {
            if (msg.isMessageFromUser()) {
                String firstMsg = msg.getMessage();
                if (firstMsg != null && !firstMsg.isEmpty()) {
                    // Take first 40 chars or first sentence
                    if (firstMsg.length() > 40) {
                        this.title = firstMsg.substring(0, 40) + "...";
                    } else {
                        this.title = firstMsg;
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * Get formatted timestamp for display
     */
    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * Get relative time (e.g., "2 hours ago")
     */
    public String getRelativeTime() {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "Just now";
        }
    }
    
    /**
     * Check if session has any messages
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    /**
     * Clear all PDF/image context
     */
    public void clearContext() {
        this.pdfContext = null;
        this.pdfFileName = null;
        this.imageContext = null;
        this.imageFileName = null;
    }
    
    @Override
    public String toString() {
        return "ChatSession{" +
                "sessionId='" + sessionId + '\'' +
                ", title='" + title + '\'' +
                ", messageCount=" + messages.size() +
                ", timestamp=" + getFormattedTimestamp() +
                '}';
    }
}
