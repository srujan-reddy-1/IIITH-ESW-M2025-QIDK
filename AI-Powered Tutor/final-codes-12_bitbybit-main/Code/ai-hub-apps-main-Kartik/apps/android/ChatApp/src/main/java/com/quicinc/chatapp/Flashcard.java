// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import java.io.Serializable;

/**
 * Flashcard data model representing a single flashcard with a question and answer
 */
public class Flashcard implements Serializable {
    private String question;
    private String answer;
    private boolean isFlipped;
    private int reviewCount;
    private boolean wasCorrect; // For tracking user's self-assessment

    public Flashcard(String question, String answer) {
        this.question = question;
        this.answer = answer;
        this.isFlipped = false;
        this.reviewCount = 0;
        this.wasCorrect = false;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String answer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isFlipped() {
        return isFlipped;
    }

    public void setFlipped(boolean flipped) {
        isFlipped = flipped;
    }

    public void flip() {
        isFlipped = !isFlipped;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void incrementReviewCount() {
        reviewCount++;
    }

    public boolean wasCorrect() {
        return wasCorrect;
    }

    public void setWasCorrect(boolean correct) {
        wasCorrect = correct;
    }

    /**
     * Get the current displayed content (question or answer based on flip state)
     */
    public String getDisplayContent() {
        return isFlipped ? answer : question;
    }

    /**
     * Convert to string for storage
     */
    public String toStorageString() {
        return question + "|||" + answer;
    }

    /**
     * Create flashcard from storage string
     */
    public static Flashcard fromStorageString(String storageString) {
        android.util.Log.d("Flashcard", "Parsing flashcard from: " + storageString);
        String[] parts = storageString.split("\\|\\|\\|");
        android.util.Log.d("Flashcard", "Split into " + parts.length + " parts");
        
        if (parts.length >= 2) {
            android.util.Log.d("Flashcard", "Q: " + parts[0].substring(0, Math.min(30, parts[0].length())));
            android.util.Log.d("Flashcard", "A: " + parts[1].substring(0, Math.min(30, parts[1].length())));
            return new Flashcard(parts[0], parts[1]);
        }
        
        android.util.Log.e("Flashcard", "ERROR: Not enough parts (need 2, got " + parts.length + ")");
        return null;
    }
}
