// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class to track user progress and activities
 */
public class ProgressTracker {
    
    private static final String PREFS_NAME = "UserProfile";
    
    /**
     * Increment the count of quizzes taken and track the score
     */
    public static void incrementQuizzesTaken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentCount = prefs.getInt("total_quizzes_taken", 0);
        prefs.edit().putInt("total_quizzes_taken", currentCount + 1).apply();
    }
    
    /**
     * Track quiz score for average calculation
     */
    public static void trackQuizScore(Context context, int score, int totalQuestions) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Accumulate total score and total possible
        int currentTotalScore = prefs.getInt("total_quiz_score", 0);
        int currentTotalPossible = prefs.getInt("total_quiz_possible", 0);
        
        editor.putInt("total_quiz_score", currentTotalScore + score);
        editor.putInt("total_quiz_possible", currentTotalPossible + totalQuestions);
        editor.apply();
    }
    
    /**
     * Increment the count of flashcards reviewed
     */
    public static void incrementFlashcardsReviewed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentCount = prefs.getInt("total_flashcards_reviewed", 0);
        prefs.edit().putInt("total_flashcards_reviewed", currentCount + 1).apply();
    }
    
    /**
     * Increment study hours (call this periodically during study sessions)
     */
    public static void incrementStudyHours(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentHours = prefs.getInt("total_study_hours", 0);
        prefs.edit().putInt("total_study_hours", currentHours + 1).apply();
    }
    
    /**
     * Track a chat session
     */
    public static void trackChatSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentCount = prefs.getInt("total_chat_sessions", 0);
        prefs.edit().putInt("total_chat_sessions", currentCount + 1).apply();
    }
    
    /**
     * Update subject count (called when subjects are modified)
     */
    public static void updateSubjectCount(Context context, int count) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt("subject_count", count).apply();
    }
    
    /**
     * Get subject count
     */
    public static int getSubjectCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return safeGetInt(prefs, "subject_count", 0);
    }
    
    /**
     * Update document count (called when documents are added/removed)
     */
    public static void updateDocumentCount(Context context, int count) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt("document_count", count).apply();
    }
    
    /**
     * Get document count
     */
    public static int getDocumentCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return safeGetInt(prefs, "document_count", 0);
    }
    
    /**
     * Update study streak when user opens the app
     * Returns the current streak count
     */
    public static int updateStudyStreak(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastActiveTime = prefs.getLong("last_active_date", 0);
        long currentTime = System.currentTimeMillis();
        
        // Convert to days (ignoring time of day for simplicity)
        long lastActiveDay = lastActiveTime / (1000 * 60 * 60 * 24);
        long currentDay = currentTime / (1000 * 60 * 60 * 24);
        long dayDifference = currentDay - lastActiveDay;
        
        int currentStreak = safeGetInt(prefs, "study_streak", 0);
        
        if (dayDifference == 0) {
            // Same day, no change to streak
            return currentStreak;
        } else if (dayDifference == 1) {
            // Consecutive day, increment streak
            currentStreak++;
        } else if (dayDifference > 1) {
            // Streak broken, reset to 1
            currentStreak = 1;
        }
        
        // Save the updated streak and timestamp
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("study_streak", currentStreak);
        editor.putLong("last_active_date", currentTime);
        editor.apply();
        
        return currentStreak;
    }
    
    /**
     * Get current study streak
     */
    public static int getStudyStreak(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return safeGetInt(prefs, "study_streak", 1);
    }
    
    /**
     * Get streak badge name based on streak days
     */
    public static String getStreakBadge(int streakDays) {
        if (streakDays >= 100) {
            return "🏆 Legend";
        } else if (streakDays >= 50) {
            return "💎 Diamond";
        } else if (streakDays >= 30) {
            return "🥇 Gold";
        } else if (streakDays >= 14) {
            return "🥈 Silver";
        } else if (streakDays >= 7) {
            return "🥉 Bronze";
        } else if (streakDays >= 3) {
            return "⭐ Rising";
        } else {
            return "🌱 Starter";
        }
    }
    
    /**
     * Get current progress percentage (0-100)
     */
    public static int getProgressPercentage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Safe get methods that handle both Float and Integer types
        int subjectCount = safeGetInt(prefs, "subject_count", 0);
        int totalQuizzesTaken = safeGetInt(prefs, "total_quizzes_taken", 0);
        int totalStudyHours = safeGetInt(prefs, "total_study_hours", 0);
        int chatSessions = safeGetInt(prefs, "total_chat_sessions", 0);
        
        // More realistic progress calculation:
        // Subjects: 8 points each (cap at 5 subjects = 40 points)
        // Quizzes: 3 points each (cap at 10 quizzes = 30 points)
        // Study hours: 2 points each (cap at 10 hours = 20 points)  
        // Chat sessions: 1 point each (cap at 10 sessions = 10 points)
        // Total possible = 100 points
        
        int subjectPoints = Math.min(40, subjectCount * 8);
        int quizPoints = Math.min(30, totalQuizzesTaken * 3);
        int studyPoints = Math.min(20, totalStudyHours * 2);
        int chatPoints = Math.min(10, chatSessions * 1);
        
        int progressScore = subjectPoints + quizPoints + studyPoints + chatPoints;
        
        return Math.min(100, progressScore);
    }
    
    /**
     * Safely get int value from SharedPreferences, handling both Int and Float types
     */
    private static int safeGetInt(SharedPreferences prefs, String key, int defaultValue) {
        try {
            return prefs.getInt(key, defaultValue);
        } catch (ClassCastException e) {
            // Value was stored as Float, try to get it and convert
            try {
                float floatValue = prefs.getFloat(key, defaultValue);
                int intValue = (int) floatValue;
                // Fix the value by re-saving as int
                prefs.edit().putInt(key, intValue).apply();
                return intValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    /**
     * Get progress title based on percentage
     */
    public static String getProgressTitle(int percentage) {
        if (percentage < 20) {
            return "Getting Started";
        } else if (percentage < 40) {
            return "Building Foundation";
        } else if (percentage < 60) {
            return "Making Progress";
        } else if (percentage < 80) {
            return "Advanced Learning";
        } else {
            return "Mastery Level";
        }
    }
    
    /**
     * Reset all progress (use with caution)
     */
    public static void resetProgress(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("total_quizzes_taken", 0);
        editor.putInt("total_flashcards_reviewed", 0);
        editor.putInt("total_study_hours", 0);
        editor.putInt("total_chat_sessions", 0);
        editor.putInt("total_quiz_score", 0);
        editor.putInt("total_quiz_possible", 0);
        editor.apply();
    }
}
