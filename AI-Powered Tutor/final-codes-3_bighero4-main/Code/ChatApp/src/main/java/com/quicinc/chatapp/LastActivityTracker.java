package com.quicinc.chatapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Tracks the last activity the user engaged with globally (across all subjects)
 * Used by the Study button in bottom navigation to resume last activity
 */
public class LastActivityTracker {
    private static final String PREFS_NAME = "LastActivityPrefs";
    private static final String KEY_ACTIVITY_TYPE = "lastActivityType";
    private static final String KEY_ACTIVITY_CLASS = "lastActivityClass";
    private static final String KEY_SUBJECT_NAME = "lastSubjectName";
    private static final String KEY_HTP_CONFIG = "lastHtpConfig";
    private static final String KEY_MODEL_NAME = "lastModelName";
    private static final String KEY_TIMESTAMP = "lastTimestamp";
    
    /**
     * Save the last activity that was opened
     */
    public static void saveLastActivity(Context context, String activityType, String activityClass, 
                                       String subjectName, String htpConfig, String modelName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString(KEY_ACTIVITY_TYPE, activityType);
        editor.putString(KEY_ACTIVITY_CLASS, activityClass);
        editor.putString(KEY_SUBJECT_NAME, subjectName);
        editor.putString(KEY_HTP_CONFIG, htpConfig != null ? htpConfig : "");
        editor.putString(KEY_MODEL_NAME, modelName != null ? modelName : "");
        editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
        
        editor.apply();
    }
    
    /**
     * Save Quiz activity
     */
    public static void saveQuizActivity(Context context, String subjectName) {
        saveLastActivity(context, "QUIZ", "QuizActivity", subjectName, null, null);
    }
    
    /**
     * Save Flashcard activity
     */
    public static void saveFlashcardActivity(Context context, String subjectName) {
        saveLastActivity(context, "FLASHCARDS", "FlashcardActivity", subjectName, null, null);
    }
    
    /**
     * Save Chat/Conversation activity
     */
    public static void saveChatActivity(Context context, String subjectName, String htpConfig, String modelName) {
        saveLastActivity(context, "CHAT", "Conversation", subjectName, htpConfig, modelName);
    }
    
    /**
     * Get intent to launch the last activity
     * Returns null if no activity has been saved yet
     */
    public static Intent getLastActivityIntent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        String activityType = prefs.getString(KEY_ACTIVITY_TYPE, null);
        String activityClass = prefs.getString(KEY_ACTIVITY_CLASS, null);
        String subjectName = prefs.getString(KEY_SUBJECT_NAME, null);
        
        if (activityClass == null || activityType == null) {
            // No activity saved yet, default to Quiz
            Intent intent = new Intent(context, QuizActivity.class);
            return intent;
        }
        
        Intent intent = null;
        
        try {
            switch (activityClass) {
                case "QuizActivity":
                    intent = new Intent(context, QuizActivity.class);
                    if (subjectName != null) {
                        intent.putExtra("subject", subjectName);
                    }
                    break;
                    
                case "FlashcardActivity":
                    intent = new Intent(context, FlashcardActivity.class);
                    if (subjectName != null) {
                        intent.putExtra("subject", subjectName);
                    }
                    break;
                    
                case "Conversation":
                    intent = new Intent(context, Conversation.class);
                    String htpConfig = prefs.getString(KEY_HTP_CONFIG, "");
                    String modelName = prefs.getString(KEY_MODEL_NAME, "llm");
                    
                    if (!htpConfig.isEmpty()) {
                        intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, htpConfig);
                    }
                    intent.putExtra(Conversation.cConversationActivityKeyModelName, modelName);
                    
                    if (subjectName != null) {
                        intent.putExtra("subject_context", subjectName);
                    }
                    break;
                    
                default:
                    // Default to Quiz
                    intent = new Intent(context, QuizActivity.class);
                    break;
            }
        } catch (Exception e) {
            // If anything fails, default to Quiz
            intent = new Intent(context, QuizActivity.class);
        }
        
        return intent;
    }
    
    /**
     * Get a description of the last activity
     */
    public static String getLastActivityDescription(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        String activityType = prefs.getString(KEY_ACTIVITY_TYPE, null);
        String subjectName = prefs.getString(KEY_SUBJECT_NAME, null);
        
        if (activityType == null) {
            return "No recent activity";
        }
        
        String subject = subjectName != null ? " - " + subjectName : "";
        
        switch (activityType) {
            case "QUIZ":
                return "Resume Quiz" + subject;
            case "FLASHCARDS":
                return "Resume Flashcards" + subject;
            case "CHAT":
                return "Resume Chat" + subject;
            default:
                return "Resume Study";
        }
    }
}
