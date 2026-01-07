// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * FlashcardSet represents a collection of flashcards
 */
public class FlashcardSet implements Serializable {
    private String title;
    private String description;
    private List<Flashcard> flashcards;
    private long createdTimestamp;
    private String sourceType; // "PDF" or "MANUAL"

    public FlashcardSet(String title, String description, String sourceType) {
        this.title = title;
        this.description = description;
        this.flashcards = new ArrayList<>();
        this.createdTimestamp = System.currentTimeMillis();
        this.sourceType = sourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Flashcard> getFlashcards() {
        return flashcards;
    }

    public void addFlashcard(Flashcard flashcard) {
        flashcards.add(flashcard);
    }

    public void removeFlashcard(int position) {
        if (position >= 0 && position < flashcards.size()) {
            flashcards.remove(position);
        }
    }

    public int getFlashcardCount() {
        return flashcards.size();
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * Convert to storage string (format: title<<<SEP>>>description<<<SEP>>>sourceType<<<SEP>>>timestamp<<<SEP>>>flashcard1:::flashcard2...)
     */
    public String toStorageString() {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("<<<SEP>>>");
        sb.append(description).append("<<<SEP>>>");
        sb.append(sourceType).append("<<<SEP>>>");
        sb.append(createdTimestamp).append("<<<SEP>>>");
        
        for (Flashcard fc : flashcards) {
            sb.append(fc.toStorageString()).append(":::");
        }
        
        return sb.toString();
    }

    /**
     * Create FlashcardSet from storage string
     */
    public static FlashcardSet fromStorageString(String storageString) {
        android.util.Log.d("FlashcardSet", "=== Parsing FlashcardSet ===");
        android.util.Log.d("FlashcardSet", "Storage string length: " + storageString.length());
        
        String[] parts = storageString.split("<<<SEP>>>");
        android.util.Log.d("FlashcardSet", "Split into " + parts.length + " parts");
        
        if (parts.length < 4) {
            android.util.Log.e("FlashcardSet", "ERROR: Not enough parts (need 4, got " + parts.length + ")");
            return null;
        }
        
        android.util.Log.d("FlashcardSet", "Title: " + parts[0]);
        android.util.Log.d("FlashcardSet", "Description: " + parts[1]);
        android.util.Log.d("FlashcardSet", "Source: " + parts[2]);
        android.util.Log.d("FlashcardSet", "Timestamp: " + parts[3]);
        
        FlashcardSet set = new FlashcardSet(parts[0], parts[1], parts[2]);
        try {
            set.createdTimestamp = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            set.createdTimestamp = System.currentTimeMillis();
        }
        
        if (parts.length > 4 && parts[4] != null && !parts[4].trim().isEmpty()) {
            android.util.Log.d("FlashcardSet", "Flashcard data length: " + parts[4].length());
            String[] flashcardStrings = parts[4].split(":::");
            android.util.Log.d("FlashcardSet", "Found " + flashcardStrings.length + " flashcard strings");
            
            for (int i = 0; i < flashcardStrings.length; i++) {
                String fcStr = flashcardStrings[i];
                
                if (!fcStr.trim().isEmpty()) {
                    Flashcard fc = Flashcard.fromStorageString(fcStr);
                    if (fc != null) {
                        set.addFlashcard(fc);
                        android.util.Log.d("FlashcardSet", "Added flashcard " + (i+1));
                    } else {
                        android.util.Log.e("FlashcardSet", "Failed to parse flashcard " + (i+1));
                    }
                } else {
                    android.util.Log.d("FlashcardSet", "Skipping empty flashcard string " + i);
                }
            }
        } else {
            android.util.Log.w("FlashcardSet", "No flashcard data found");
        }
        
        android.util.Log.d("FlashcardSet", "Final flashcard count: " + set.getFlashcardCount());
        return set;
    }
}
