// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manages weak topics tracking for personalized learning
 * Tracks topics that users struggle with based on quiz performance
 */
public class WeakTopicManager {
    private static final String TAG = "WeakTopicManager";
    private static final String WEAK_TOPICS_FOLDER = "weak_topics";
    private static final String WEAK_TOPICS_FILE = "weak_topics.json";
    
    private final Context context;
    private final Gson gson;
    
    /**
     * Represents a weak topic entry
     */
    public static class WeakTopicEntry {
        public String topic;
        public String documentId;
        public String documentName;
        public String subjectName;
        public int failureCount; // Number of times user got this topic wrong
        public long firstFailedAt; // Timestamp of first failure
        public long lastFailedAt; // Timestamp of most recent failure
        public long lastReviewedAt; // When user last reviewed this topic
        public boolean needsRevision; // Flag for topics needing revision
        public int improvementCount; // Number of times user improved on this topic
        
        public WeakTopicEntry() {
            this.failureCount = 1;
            long now = System.currentTimeMillis();
            this.firstFailedAt = now;
            this.lastFailedAt = now;
            this.lastReviewedAt = 0;
            this.needsRevision = true;
            this.improvementCount = 0;
        }
    }
    
    public WeakTopicManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
    }
    
    /**
     * Get weak topics file
     */
    private File getWeakTopicsFile() {
        File baseDir = new File(context.getFilesDir(), WEAK_TOPICS_FOLDER);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, WEAK_TOPICS_FILE);
    }
    
    /**
     * Load all weak topics
     */
    private synchronized List<WeakTopicEntry> loadWeakTopics() {
        File file = getWeakTopicsFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<WeakTopicEntry>>(){}.getType();
            List<WeakTopicEntry> entries = gson.fromJson(reader, listType);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read weak topics file", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Save weak topics
     */
    private synchronized void saveWeakTopics(List<WeakTopicEntry> entries) {
        File file = getWeakTopicsFile();
        try (FileWriter writer = new FileWriter(file, false)) {
            gson.toJson(entries, writer);
            Log.d(TAG, "Saved " + entries.size() + " weak topic entries");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save weak topics file", e);
        }
    }
    
    /**
     * Mark topics as weak based on quiz performance
     * Extracts topics from incorrect quiz questions and marks them
     */
    public synchronized void markWeakTopicsFromQuiz(String subjectName, String documentTitle, 
                                                   List<QuizQuestion> questions, int score, int totalQuestions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        
        // Only mark weak topics if score is below 70%
        int percentage = (int) (((float) score / totalQuestions) * 100);
        if (percentage >= 70) {
            Log.d(TAG, "Score is " + percentage + "%, not marking weak topics");
            return;
        }
        
        Log.i(TAG, "Quiz score is " + percentage + "%, analyzing weak topics from " + 
              (totalQuestions - score) + " incorrect answers");
        
        List<WeakTopicEntry> allWeakTopics = loadWeakTopics();
        DocumentManager docManager = DocumentManager.getInstance(context);
        
        // Find the document ID from document title
        String documentId = null;
        if (documentTitle != null && !documentTitle.isEmpty()) {
            List<Document> allDocs = docManager.getAllDocuments();
            for (Document doc : allDocs) {
                if (doc.getDisplayName().equals(documentTitle) || 
                    doc.getDisplayName().contains(documentTitle) ||
                    documentTitle.contains(doc.getDisplayName())) {
                    documentId = doc.getId();
                    break;
                }
            }
        }
        
        // Extract topics from incorrect questions
        for (QuizQuestion question : questions) {
            if (!question.isCorrect()) {
                // Extract topic from question text
                String topic = extractTopicFromQuestion(question.getQuestion());
                
                if (topic != null && !topic.trim().isEmpty()) {
                    // Check if this topic already exists
                    WeakTopicEntry existingEntry = findWeakTopic(allWeakTopics, topic, documentId, subjectName);
                    
                    if (existingEntry != null) {
                        // Update existing entry
                        existingEntry.failureCount++;
                        existingEntry.lastFailedAt = System.currentTimeMillis();
                        existingEntry.needsRevision = true;
                        Log.d(TAG, "Updated weak topic: " + topic + " (failures: " + existingEntry.failureCount + ")");
                    } else {
                        // Create new weak topic entry
                        WeakTopicEntry newEntry = new WeakTopicEntry();
                        newEntry.topic = topic.trim();
                        newEntry.documentId = documentId;
                        newEntry.documentName = documentTitle;
                        newEntry.subjectName = subjectName;
                        allWeakTopics.add(newEntry);
                        Log.d(TAG, "Added new weak topic: " + topic);
                    }
                }
            }
        }
        
        // Save updated weak topics
        saveWeakTopics(allWeakTopics);
        Log.i(TAG, "Marked weak topics from quiz. Total weak topics: " + allWeakTopics.size());
    }
    
    /**
     * Extract topic/concept from question text
     */
    private String extractTopicFromQuestion(String question) {
        if (TextUtils.isEmpty(question)) {
            return null;
        }
        
        // Remove question marks and common question words
        String cleaned = question.replaceAll("[?]", "").trim();
        
        // Try to extract key terms
        // Look for patterns like "What is X?", "Explain X", "Define X", etc.
        String[] patterns = {
            "(?i)what is (.+?)\\?",
            "(?i)explain (.+?)\\?",
            "(?i)define (.+?)\\?",
            "(?i)describe (.+?)\\?",
            "(?i)what are (.+?)\\?",
            "(?i)how does (.+?)\\?",
            "(?i)what does (.+?)\\?",
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(cleaned);
            if (m.find()) {
                String topic = m.group(1).trim();
                // Clean up the topic
                topic = topic.replaceAll("\\s+", " ");
                // Limit length
                if (topic.length() > 50) {
                    topic = topic.substring(0, 50).trim();
                }
                return topic;
            }
        }
        
        // Fallback: extract first few meaningful words
        String[] words = cleaned.split("\\s+");
        if (words.length >= 2) {
            // Skip common question words
            int startIdx = 0;
            String[] skipWords = {"what", "is", "are", "the", "a", "an", "how", "does", "do", "explain", "define", "describe"};
            for (int i = 0; i < words.length; i++) {
                boolean shouldSkip = false;
                for (String skip : skipWords) {
                    if (words[i].equalsIgnoreCase(skip)) {
                        shouldSkip = true;
                        break;
                    }
                }
                if (!shouldSkip) {
                    startIdx = i;
                    break;
                }
            }
            
            // Take 2-4 words as topic
            StringBuilder topic = new StringBuilder();
            int wordCount = 0;
            for (int i = startIdx; i < words.length && wordCount < 4; i++) {
                if (words[i].length() > 2) { // Skip very short words
                    if (topic.length() > 0) topic.append(" ");
                    topic.append(words[i]);
                    wordCount++;
                }
            }
            
            if (topic.length() > 0) {
                return topic.toString();
            }
        }
        
        // Last resort: return first 30 chars
        if (cleaned.length() > 30) {
            return cleaned.substring(0, 30).trim() + "...";
        }
        return cleaned.trim();
    }
    
    /**
     * Find existing weak topic entry
     */
    private WeakTopicEntry findWeakTopic(List<WeakTopicEntry> entries, String topic, 
                                        String documentId, String subjectName) {
        if (entries == null || topic == null) {
            return null;
        }
        
        String normalizedTopic = topic.toLowerCase().trim();
        
        for (WeakTopicEntry entry : entries) {
            // Match by topic name (case-insensitive) and document/subject
            if (entry.topic != null && entry.topic.toLowerCase().trim().equals(normalizedTopic)) {
                // Also check if document/subject matches (if provided)
                if (documentId != null && entry.documentId != null && 
                    entry.documentId.equals(documentId)) {
                    return entry;
                } else if (subjectName != null && entry.subjectName != null &&
                          entry.subjectName.equals(subjectName)) {
                    return entry;
                } else if (documentId == null && subjectName == null) {
                    // No specific document/subject, match by topic only
                    return entry;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get all weak topics for a subject
     */
    public synchronized List<WeakTopicEntry> getWeakTopicsForSubject(String subjectName) {
        List<WeakTopicEntry> allTopics = loadWeakTopics();
        List<WeakTopicEntry> subjectTopics = new ArrayList<>();
        
        for (WeakTopicEntry entry : allTopics) {
            if (entry.subjectName != null && entry.subjectName.equals(subjectName)) {
                subjectTopics.add(entry);
            }
        }
        
        return subjectTopics;
    }
    
    /**
     * Get all weak topics for a document
     */
    public synchronized List<WeakTopicEntry> getWeakTopicsForDocument(String documentId) {
        List<WeakTopicEntry> allTopics = loadWeakTopics();
        List<WeakTopicEntry> documentTopics = new ArrayList<>();
        
        for (WeakTopicEntry entry : allTopics) {
            if (entry.documentId != null && entry.documentId.equals(documentId)) {
                documentTopics.add(entry);
            }
        }
        
        return documentTopics;
    }
    
    /**
     * Get all weak topics that need revision
     */
    public synchronized List<WeakTopicEntry> getTopicsNeedingRevision() {
        List<WeakTopicEntry> allTopics = loadWeakTopics();
        List<WeakTopicEntry> needsRevision = new ArrayList<>();
        
        for (WeakTopicEntry entry : allTopics) {
            if (entry.needsRevision) {
                needsRevision.add(entry);
            }
        }
        
        return needsRevision;
    }
    
    /**
     * Mark topic as reviewed (user studied it)
     */
    public synchronized void markTopicAsReviewed(String topic, String documentId, String subjectName) {
        List<WeakTopicEntry> allTopics = loadWeakTopics();
        WeakTopicEntry entry = findWeakTopic(allTopics, topic, documentId, subjectName);
        
        if (entry != null) {
            entry.lastReviewedAt = System.currentTimeMillis();
            saveWeakTopics(allTopics);
            Log.d(TAG, "Marked topic as reviewed: " + topic);
        }
    }
    
    /**
     * Mark topic as improved (user got it right in retest)
     */
    public synchronized void markTopicAsImproved(String topic, String documentId, String subjectName) {
        List<WeakTopicEntry> allTopics = loadWeakTopics();
        WeakTopicEntry entry = findWeakTopic(allTopics, topic, documentId, subjectName);
        
        if (entry != null) {
            entry.improvementCount++;
            entry.lastReviewedAt = System.currentTimeMillis();
            
            // If improved multiple times, remove from needs revision
            if (entry.improvementCount >= 2) {
                entry.needsRevision = false;
                Log.d(TAG, "Topic improved enough, removing from needs revision: " + topic);
            }
            
            saveWeakTopics(allTopics);
            Log.d(TAG, "Marked topic as improved: " + topic + " (improvements: " + entry.improvementCount + ")");
        }
    }
    
    /**
     * Remove weak topic (user mastered it)
     */
    public synchronized void removeWeakTopic(String topic, String documentId, String subjectName) {
        List<WeakTopicEntry> allTopics = loadWeakTopics();
        Iterator<WeakTopicEntry> iterator = allTopics.iterator();
        boolean removed = false;
        
        while (iterator.hasNext()) {
            WeakTopicEntry entry = iterator.next();
            if (entry.topic != null && entry.topic.equals(topic)) {
                if ((documentId != null && entry.documentId != null && entry.documentId.equals(documentId)) ||
                    (subjectName != null && entry.subjectName != null && entry.subjectName.equals(subjectName))) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }
        }
        
        if (removed) {
            saveWeakTopics(allTopics);
            Log.d(TAG, "Removed weak topic: " + topic);
        }
    }
    
    /**
     * Get count of weak topics for a subject
     */
    public synchronized int getWeakTopicCount(String subjectName) {
        return getWeakTopicsForSubject(subjectName).size();
    }
    
    /**
     * Get count of topics needing revision
     */
    public synchronized int getTopicsNeedingRevisionCount() {
        return getTopicsNeedingRevision().size();
    }
}

