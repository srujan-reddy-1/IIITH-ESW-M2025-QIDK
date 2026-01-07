package com.quicinc.chatapp;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentActivityManager {
    private static final String PREF_NAME = "recent_activities";
    private static final String KEY_ACTIVITIES = "activities_";
    private static final int MAX_ACTIVITIES = 10;

    private Context context;
    private SharedPreferences sharedPreferences;
    private Gson gson;

    public RecentActivityManager(Context context) {
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void addActivity(String subjectName, String description, RecentActivity.ActivityType type) {
        addActivity(subjectName, description, type, null);
    }

    public void addActivity(String subjectName, String description, RecentActivity.ActivityType type, String score) {
        String timestamp = getCurrentTimestamp();
        RecentActivity newActivity = new RecentActivity(description, timestamp, score, type);
        
        List<RecentActivity> activities = getActivitiesForSubject(subjectName);
        activities.add(0, newActivity); // Add to the beginning
        
        // Keep only the latest MAX_ACTIVITIES
        if (activities.size() > MAX_ACTIVITIES) {
            activities = activities.subList(0, MAX_ACTIVITIES);
        }
        
        saveActivitiesForSubject(subjectName, activities);
    }

    public List<RecentActivity> getActivitiesForSubject(String subjectName) {
        String json = sharedPreferences.getString(KEY_ACTIVITIES + subjectName, null);
        if (json != null) {
            try {
                return gson.fromJson(json, new TypeToken<List<RecentActivity>>(){}.getType());
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    private void saveActivitiesForSubject(String subjectName, List<RecentActivity> activities) {
        String json = gson.toJson(activities);
        sharedPreferences.edit()
                .putString(KEY_ACTIVITIES + subjectName, json)
                .apply();
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        return sdf.format(new Date());
    }

    public String getRelativeTimestamp(String timestamp) {
        // For now, return the timestamp as is
        // You can implement relative time logic here (e.g., "2 hours ago")
        return timestamp;
    }
}