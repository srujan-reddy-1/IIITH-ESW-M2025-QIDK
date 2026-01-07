// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WeakTopicsAnalyzer - Analyzes quiz history to identify topics where user needs improvement
 */
public class WeakTopicsAnalyzer {
    
    private static final String TAG = "WeakTopicsAnalyzer";
    private static final double WEAK_THRESHOLD = 0.6; // 60% - topics below this are weak
    
    /**
     * Analyze all quiz history and identify weak topics
     * Returns a formatted string of weak topics for quiz generation prompt
     */
    public static String getWeakTopicsPrompt(SharedPreferences preferences, String userName) {
        List<QuizResult> quizHistory = loadQuizHistory(preferences, userName);
        
        if (quizHistory == null || quizHistory.isEmpty()) {
            return ""; // No history, no weak topics
        }
        
        // Collect all weak topics from recent quizzes
        Set<String> allWeakTopics = new HashSet<>();
        int recentQuizCount = Math.min(5, quizHistory.size()); // Look at last 5 quizzes
        
        for (int i = 0; i < recentQuizCount; i++) {
            QuizResult result = quizHistory.get(i);
            if (result.getPercentage() < 80) { // Only consider quizzes where user struggled
                List<String> weakTopics = result.getWeakTopics();
                allWeakTopics.addAll(weakTopics);
            }
        }
        
        if (allWeakTopics.isEmpty()) {
            return "";
        }
        
        // Build prompt addition
        StringBuilder promptAddition = new StringBuilder();
        promptAddition.append("\n\nIMPORTANT - Focus on these topics where the user needs improvement:\n");
        int count = 0;
        for (String topic : allWeakTopics) {
            if (count >= 3) break; // Limit to top 3 weak topics
            promptAddition.append("- ").append(topic).append("\n");
            count++;
        }
        promptAddition.append("\nGenerate questions that specifically address these weak areas.\n");
        
        Log.d(TAG, "Generated weak topics prompt: " + promptAddition.toString());
        return promptAddition.toString();
    }
    
    /**
     * Get summary of weak topics for display
     */
    public static String getWeakTopicsSummary(SharedPreferences preferences, String userName) {
        List<QuizResult> quizHistory = loadQuizHistory(preferences, userName);
        
        if (quizHistory == null || quizHistory.isEmpty()) {
            return "No quiz history available";
        }
        
        Set<String> allWeakTopics = new HashSet<>();
        int quizzesAnalyzed = 0;
        
        for (QuizResult result : quizHistory) {
            if (result.getPercentage() < 80) {
                allWeakTopics.addAll(result.getWeakTopics());
                quizzesAnalyzed++;
            }
        }
        
        if (allWeakTopics.isEmpty()) {
            return "✅ Great job! No weak topics identified.";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("📊 Areas to improve (from ").append(quizzesAnalyzed).append(" quiz");
        if (quizzesAnalyzed != 1) summary.append("es");
        summary.append("):\n");
        
        int count = 0;
        for (String topic : allWeakTopics) {
            if (count >= 3) break;
            summary.append("• ").append(topic.substring(0, Math.min(40, topic.length())));
            if (topic.length() > 40) summary.append("...");
            summary.append("\n");
            count++;
        }
        
        return summary.toString();
    }
    
    /**
     * Check if user has weak topics (for UI logic)
     */
    public static boolean hasWeakTopics(SharedPreferences preferences, String userName) {
        List<QuizResult> quizHistory = loadQuizHistory(preferences, userName);
        
        if (quizHistory == null || quizHistory.isEmpty()) {
            return false;
        }
        
        for (QuizResult result : quizHistory) {
            if (result.getPercentage() < 80 && !result.getWeakTopics().isEmpty()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Load quiz history from SharedPreferences
     */
    private static List<QuizResult> loadQuizHistory(SharedPreferences preferences, String userName) {
        String key = "quiz_history_" + userName;
        Set<String> quizStrings = preferences.getStringSet(key, new HashSet<>());
        
        List<QuizResult> quizHistory = new ArrayList<>();
        for (String quizString : quizStrings) {
            QuizResult result = QuizResult.fromStorageString(quizString);
            if (result != null) {
                quizHistory.add(result);
            }
        }
        
        // Sort by timestamp (most recent first)
        quizHistory.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
        
        return quizHistory;
    }
}
