package com.quicinc.chatapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages chat sessions - saving, loading, and deleting from SharedPreferences
 * Uses JSON serialization for complex object storage
 */
public class ChatSessionManager {
    private static final String TAG = "ChatSessionManager";
    private static final String PREF_NAME = "TutorAppPrefs";
    private static final String KEY_SESSIONS_PREFIX = "chat_sessions_";
    private static final String KEY_CURRENT_SESSION = "current_session_id";
    private static final String KEY_SESSION_LIST = "session_id_list";
    
    private SharedPreferences preferences;
    private Gson gson;
    private String username;
    
    public ChatSessionManager(Context context, String username) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.username = username;
    }
    
    /**
     * Save a chat session
     */
    public void saveSession(ChatSession session) {
        try {
            // Save session data
            String sessionKey = KEY_SESSIONS_PREFIX + username + "_" + session.getSessionId();
            String sessionJson = gson.toJson(session);
            
            preferences.edit()
                .putString(sessionKey, sessionJson)
                .apply();
            
            // Add to session list if not already there
            List<String> sessionIds = getSessionIdList();
            if (!sessionIds.contains(session.getSessionId())) {
                sessionIds.add(session.getSessionId());
                saveSessionIdList(sessionIds);
            }
            
            Log.d(TAG, "Session saved: " + session.getSessionId());
        } catch (Exception e) {
            Log.e(TAG, "Error saving session: " + e.getMessage());
        }
    }
    
    /**
     * Load a specific chat session
     */
    public ChatSession loadSession(String sessionId) {
        try {
            String sessionKey = KEY_SESSIONS_PREFIX + username + "_" + sessionId;
            String sessionJson = preferences.getString(sessionKey, null);
            
            if (sessionJson != null) {
                ChatSession session = gson.fromJson(sessionJson, ChatSession.class);
                Log.d(TAG, "Session loaded: " + sessionId);
                return session;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading session: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Load all chat sessions for current user
     * Returns sorted by timestamp (newest first)
     */
    public List<ChatSession> loadAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        List<String> sessionIds = getSessionIdList();
        
        for (String sessionId : sessionIds) {
            ChatSession session = loadSession(sessionId);
            if (session != null) {
                sessions.add(session);
            }
        }
        
        // Sort by timestamp - newest first
        Collections.sort(sessions, new Comparator<ChatSession>() {
            @Override
            public int compare(ChatSession s1, ChatSession s2) {
                return Long.compare(s2.getTimestamp(), s1.getTimestamp());
            }
        });
        
        Log.d(TAG, "Loaded " + sessions.size() + " sessions");
        return sessions;
    }
    
    /**
     * Delete a chat session
     */
    public void deleteSession(String sessionId) {
        try {
            // Remove session data
            String sessionKey = KEY_SESSIONS_PREFIX + username + "_" + sessionId;
            preferences.edit().remove(sessionKey).apply();
            
            // Remove from session list
            List<String> sessionIds = getSessionIdList();
            sessionIds.remove(sessionId);
            saveSessionIdList(sessionIds);
            
            // If this was current session, clear it
            if (sessionId.equals(getCurrentSessionId())) {
                clearCurrentSessionId();
            }
            
            Log.d(TAG, "Session deleted: " + sessionId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting session: " + e.getMessage());
        }
    }
    
    /**
     * Delete all chat sessions for current user
     */
    public void deleteAllSessions() {
        List<String> sessionIds = getSessionIdList();
        for (String sessionId : sessionIds) {
            String sessionKey = KEY_SESSIONS_PREFIX + username + "_" + sessionId;
            preferences.edit().remove(sessionKey).apply();
        }
        saveSessionIdList(new ArrayList<>());
        clearCurrentSessionId();
        Log.d(TAG, "All sessions deleted");
    }
    
    /**
     * Get current session ID
     */
    public String getCurrentSessionId() {
        return preferences.getString(KEY_CURRENT_SESSION + "_" + username, null);
    }
    
    /**
     * Set current session ID
     */
    public void setCurrentSessionId(String sessionId) {
        preferences.edit()
            .putString(KEY_CURRENT_SESSION + "_" + username, sessionId)
            .apply();
    }
    
    /**
     * Clear current session ID
     */
    public void clearCurrentSessionId() {
        preferences.edit()
            .remove(KEY_CURRENT_SESSION + "_" + username)
            .apply();
    }
    
    /**
     * Get list of all session IDs
     */
    private List<String> getSessionIdList() {
        String json = preferences.getString(KEY_SESSION_LIST + "_" + username, null);
        if (json != null) {
            Type type = new TypeToken<List<String>>(){}.getType();
            return gson.fromJson(json, type);
        }
        return new ArrayList<>();
    }
    
    /**
     * Save list of session IDs
     */
    private void saveSessionIdList(List<String> sessionIds) {
        String json = gson.toJson(sessionIds);
        preferences.edit()
            .putString(KEY_SESSION_LIST + "_" + username, json)
            .apply();
    }
    
    /**
     * Get session count for current user
     */
    public int getSessionCount() {
        return getSessionIdList().size();
    }
    
    /**
     * Check if a session exists
     */
    public boolean sessionExists(String sessionId) {
        String sessionKey = KEY_SESSIONS_PREFIX + username + "_" + sessionId;
        return preferences.contains(sessionKey);
    }
}
