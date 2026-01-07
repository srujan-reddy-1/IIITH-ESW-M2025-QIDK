package com.quicinc.chatapp;

public class Flashcard {
    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    private String front;
    private String back;
    private String subject;
    private Difficulty difficulty;
    private int timesReviewed;
    private long lastReviewed;

    public Flashcard(String front, String back, String subject) {
        this.front = front;
        this.back = back;
        this.subject = subject;
        this.difficulty = Difficulty.MEDIUM;
        this.timesReviewed = 0;
        this.lastReviewed = 0;
    }

    public String getFront() {
        return front;
    }

    public void setFront(String front) {
        this.front = front;
    }

    public String getBack() {
        return back;
    }

    public void setBack(String back) {
        this.back = back;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getTimesReviewed() {
        return timesReviewed;
    }

    public void setTimesReviewed(int timesReviewed) {
        this.timesReviewed = timesReviewed;
    }

    public long getLastReviewed() {
        return lastReviewed;
    }

    public void setLastReviewed(long lastReviewed) {
        this.lastReviewed = lastReviewed;
    }

    public void markReviewed() {
        this.timesReviewed++;
        this.lastReviewed = System.currentTimeMillis();
    }

    public boolean isDueForReview() {
        if (lastReviewed == 0) return true;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceReview = currentTime - lastReviewed;
        
        // Simple spaced repetition logic
        long reviewInterval;
        switch (difficulty) {
            case EASY:
                reviewInterval = 7 * 24 * 60 * 60 * 1000L; // 7 days
                break;
            case HARD:
                reviewInterval = 1 * 24 * 60 * 60 * 1000L; // 1 day
                break;
            case MEDIUM:
            default:
                reviewInterval = 3 * 24 * 60 * 60 * 1000L; // 3 days
                break;
        }
        
        return timeSinceReview >= reviewInterval;
    }
}