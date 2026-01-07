package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;

public class RecentQuizzesActivity extends AppCompatActivity {

    private RecyclerView recentQuizzesRecycler;
    private ProfileActivityAdapter quizzesAdapter;
    private View noQuizzesLayout;
    private static final String TAG = "RecentQuizzesActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_quizzes);

        initializeViews();
        loadRecentQuizzes();
    }

    private void initializeViews() {
        recentQuizzesRecycler = findViewById(R.id.recent_quizzes_recycler);
        noQuizzesLayout = findViewById(R.id.no_quizzes_text);

        recentQuizzesRecycler.setLayoutManager(new LinearLayoutManager(this));

        // Setup adapter with click listener
        quizzesAdapter = new ProfileActivityAdapter(this, quizData -> {
            // Handle click - launch QuizReviewActivity
            try {
                String type = quizData.optString("type", "");

                if (type.equals("quiz")) {
                    Intent intent = new Intent(RecentQuizzesActivity.this, QuizReviewActivity.class);
                    intent.putExtra("quiz_data", quizData.toString());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Activity details coming soon!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error launching quiz review: " + e.getMessage());
                Toast.makeText(this, "Failed to load quiz details", Toast.LENGTH_SHORT).show();
            }
        });

        recentQuizzesRecycler.setAdapter(quizzesAdapter);
    }

    private void loadRecentQuizzes() {
        SharedPreferences prefs = getSharedPreferences("RecentActivity", MODE_PRIVATE);
        String activitiesJson = prefs.getString("activities", "[]");

        try {
            JSONArray activities = new JSONArray(activitiesJson);
            quizzesAdapter.setActivities(activities);

            // Show/hide empty state
            if (activities.length() == 0) {
                recentQuizzesRecycler.setVisibility(View.GONE);
                noQuizzesLayout.setVisibility(View.VISIBLE);
            } else {
                recentQuizzesRecycler.setVisibility(View.VISIBLE);
                noQuizzesLayout.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading recent quizzes: " + e.getMessage());
            noQuizzesLayout.setVisibility(View.VISIBLE);
            recentQuizzesRecycler.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh quiz list when returning from review
        loadRecentQuizzes();
    }
}
