package com.quicinc.chatapp;

public class RecentActivity {
    private String description;
    private String timestamp;
    private String score;
    private int iconResId;
    private ActivityType type;

    public enum ActivityType {
        QUIZ, FLASHCARDS, CHAT, UPLOAD, VIEW
    }

    public RecentActivity(String description, String timestamp, ActivityType type) {
        this.description = description;
        this.timestamp = timestamp;
        this.type = type;
        this.iconResId = getIconForType(type);
    }

    public RecentActivity(String description, String timestamp, String score, ActivityType type) {
        this(description, timestamp, type);
        this.score = score;
    }

    private int getIconForType(ActivityType type) {
        switch (type) {
            case QUIZ:
                return R.drawable.ic_quizes;
            case FLASHCARDS:
                return R.drawable.ic_flashcard;
            case CHAT:
                return R.drawable.ic_atom;
            case UPLOAD:
                return R.drawable.ic_upload;
            case VIEW:
                return android.R.drawable.ic_menu_view;
            default:
                return android.R.drawable.ic_menu_info_details;
        }
    }

    // Getters and setters
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    public ActivityType getType() {
        return type;
    }

    public void setType(ActivityType type) {
        this.type = type;
    }

    public boolean hasScore() {
        return score != null && !score.isEmpty();
    }
}