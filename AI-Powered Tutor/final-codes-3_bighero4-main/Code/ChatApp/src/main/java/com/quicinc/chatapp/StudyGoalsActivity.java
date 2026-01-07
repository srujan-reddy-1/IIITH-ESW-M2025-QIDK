// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StudyGoalsActivity extends AppCompatActivity {

    private LinearLayout goalsContainer;
    private TextView motivationalQuote;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_goals);

        initializeViews();
        generateAIGoals();
    }

    private void initializeViews() {
        goalsContainer = findViewById(R.id.goals_container);
        motivationalQuote = findViewById(R.id.motivational_quote);
        backButton = findViewById(R.id.back_button);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Set random motivational quote
        setMotivationalQuote();
    }

    /**
     * Generate personalized AI-based study goals
     */
    private void generateAIGoals() {
        List<StudyGoal> goals = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        int totalQuizzes = prefs.getInt("total_quizzes_taken", 0);
        int totalFlashcards = prefs.getInt("total_flashcards_reviewed", 0);
        int subjectCount = prefs.getInt("subject_count", 0);
        int studyStreak = prefs.getInt("study_streak", 0);

        // Count uploaded documents
        int totalDocuments = countTotalDocuments();

        // AI-Generated Goal 1: Quiz Mastery
        if (totalQuizzes < 10) {
            goals.add(new StudyGoal(
                "🎯 Quiz Beginner Challenge",
                "Take " + (10 - totalQuizzes) + " more quizzes to reach 10",
                totalQuizzes,
                10,
                "#FF6B6B",
                "#4ECDC4"
            ));
        } else if (totalQuizzes < 50) {
            goals.add(new StudyGoal(
                "🚀 Quiz Expert Path",
                "Complete " + (50 - totalQuizzes) + " more quizzes to reach 50",
                totalQuizzes,
                50,
                "#A8E6CF",
                "#3D84A8"
            ));
        } else {
            goals.add(new StudyGoal(
                "🏆 Quiz Master Status",
                "You've taken " + totalQuizzes + " quizzes! Keep it up!",
                totalQuizzes,
                totalQuizzes + 10,
                "#FFD93D",
                "#FF6B6B"
            ));
        }

        // AI-Generated Goal 2: Flashcard Champion
        if (totalFlashcards < 50) {
            goals.add(new StudyGoal(
                "📚 Flashcard Explorer",
                "Review " + (50 - totalFlashcards) + " more flashcards to reach 50",
                totalFlashcards,
                50,
                "#667EEA",
                "#764BA2"
            ));
        } else if (totalFlashcards < 200) {
            goals.add(new StudyGoal(
                "🌟 Flashcard Pro",
                "Review " + (200 - totalFlashcards) + " more flashcards to reach 200",
                totalFlashcards,
                200,
                "#F093FB",
                "#F5576C"
            ));
        } else {
            goals.add(new StudyGoal(
                "💎 Flashcard Legend",
                "Amazing! " + totalFlashcards + " flashcards reviewed!",
                totalFlashcards,
                totalFlashcards + 50,
                "#4FACFE",
                "#00F2FE"
            ));
        }

        // AI-Generated Goal 3: Subject Diversification
        if (subjectCount < 3) {
            goals.add(new StudyGoal(
                "🌱 Knowledge Garden",
                "Upload PDFs in " + (3 - subjectCount) + " more subjects to reach 3",
                subjectCount,
                3,
                "#43E97B",
                "#38F9D7"
            ));
        } else if (subjectCount < 5) {
            goals.add(new StudyGoal(
                "🎓 Academic Explorer",
                "Upload PDFs in " + (5 - subjectCount) + " more subjects to reach 5",
                subjectCount,
                5,
                "#FA8BFF",
                "#2BD2FF"
            ));
        } else {
            goals.add(new StudyGoal(
                "🌈 Knowledge Polymath",
                "Excellent! " + subjectCount + " subjects mastered!",
                subjectCount,
                subjectCount + 2,
                "#FF9A56",
                "#FF6A88"
            ));
        }

        // AI-Generated Goal 4: Consistency Streak
        if (studyStreak < 7) {
            goals.add(new StudyGoal(
                "🔥 Build Your Streak",
                "Study for " + (7 - studyStreak) + " more days to reach 7-day streak",
                studyStreak,
                7,
                "#FFA07A",
                "#FF6347"
            ));
        } else if (studyStreak < 30) {
            goals.add(new StudyGoal(
                "⚡ Consistency Champion",
                "Keep going! " + (30 - studyStreak) + " days to reach 30-day streak",
                studyStreak,
                30,
                "#FFE66D",
                "#FF6B6B"
            ));
        } else {
            goals.add(new StudyGoal(
                "👑 Dedication King/Queen",
                "Incredible " + studyStreak + "-day streak!",
                studyStreak,
                studyStreak + 7,
                "#00C9FF",
                "#92FE9D"
            ));
        }

        // AI-Generated Goal 5: Content Library
        if (totalDocuments < 10) {
            goals.add(new StudyGoal(
                "📖 Library Builder",
                "Upload " + (10 - totalDocuments) + " more PDFs to reach 10",
                totalDocuments,
                10,
                "#667EEA",
                "#FF6B95"
            ));
        } else if (totalDocuments < 25) {
            goals.add(new StudyGoal(
                "📚 Content Curator",
                "Upload " + (25 - totalDocuments) + " more PDFs to reach 25",
                totalDocuments,
                25,
                "#FF75A0",
                "#FFA647"
            ));
        } else {
            goals.add(new StudyGoal(
                "🗂️ Knowledge Vault",
                "Amazing library of " + totalDocuments + " documents!",
                totalDocuments,
                totalDocuments + 10,
                "#17EAD9",
                "#6078EA"
            ));
        }

        // Render goals
        displayGoals(goals);
    }

    /**
     * Display goals in the UI
     */
    private void displayGoals(List<StudyGoal> goals) {
        goalsContainer.removeAllViews();

        for (StudyGoal goal : goals) {
            View goalCard = createGoalCard(goal);
            goalsContainer.addView(goalCard);
        }
    }

    /**
     * Create a beautiful gradient card for each goal
     */
    private View createGoalCard(StudyGoal goal) {
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        cardView.setLayoutParams(cardParams);
        cardView.setRadius(dpToPx(16));
        cardView.setCardElevation(dpToPx(8));

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        // Set theme-aware background with subtle border
        GradientDrawable background = new GradientDrawable();
        
        // Get theme colors
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
        int surfaceColor = typedValue.data;
        
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int onSurfaceColor = typedValue.data;
        
        background.setColor(surfaceColor);
        background.setCornerRadius(dpToPx(16));
        background.setStroke(dpToPx(2), onSurfaceColor & 0x33FFFFFF); // 20% opacity border
        contentLayout.setBackground(background);

        // Goal title
        TextView titleView = new TextView(this);
        titleView.setText(goal.title);
        titleView.setTextSize(20);
        titleView.setTextColor(onSurfaceColor);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        contentLayout.addView(titleView);

        // Goal description
        TextView descView = new TextView(this);
        descView.setText(goal.description);
        descView.setTextSize(14);
        descView.setTextColor(onSurfaceColor);
        descView.setAlpha(0.7f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, dpToPx(8), 0, dpToPx(16));
        descView.setLayoutParams(descParams);
        contentLayout.addView(descView);

        // Progress bar
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(8)
        );
        progressParams.setMargins(0, 0, 0, dpToPx(8));
        progressBar.setLayoutParams(progressParams);
        progressBar.setMax(goal.target);
        progressBar.setProgress(goal.current);
        
        // Get primary color for progress bar
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;
        
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(primaryColor));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(onSurfaceColor & 0x1AFFFFFF)); // 10% opacity
        contentLayout.addView(progressBar);

        // Progress text
        TextView progressText = new TextView(this);
        int percentage = goal.target > 0 ? (int) ((goal.current * 100f) / goal.target) : 0;
        progressText.setText(goal.current + " / " + goal.target + " (" + percentage + "%)");
        progressText.setTextSize(12);
        progressText.setTextColor(onSurfaceColor);
        progressText.setAlpha(0.7f);
        contentLayout.addView(progressText);

        cardView.addView(contentLayout);
        return cardView;
    }

    /**
     * Set motivational quote based on progress
     */
    private void setMotivationalQuote() {
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        int totalQuizzes = prefs.getInt("total_quizzes_taken", 0);
        int percentage = ProgressTracker.getProgressPercentage(this);

        String[] quotes = {
            "🌟 Every expert was once a beginner. Keep going!",
            "💪 Your only limit is you. Push harder!",
            "🚀 Success is the sum of small efforts repeated daily.",
            "🎯 Focus on progress, not perfection.",
            "⚡ The secret to getting ahead is getting started.",
            "🌈 Believe you can and you're halfway there.",
            "🔥 You're doing amazing! Keep the momentum!",
            "✨ Small steps every day lead to big results.",
            "🌱 Growth happens outside your comfort zone.",
            "💎 You're building something incredible!"
        };

        // Choose quote based on progress
        int quoteIndex = (percentage / 10) % quotes.length;
        if (motivationalQuote != null) {
            motivationalQuote.setText(quotes[quoteIndex]);
        }
    }

    /**
     * Count total documents across all subjects
     */
    private int countTotalDocuments() {
        int count = 0;
        try {
            File coursesDir = new File(getFilesDir(), "courses");
            if (coursesDir.exists() && coursesDir.isDirectory()) {
                File[] subjectDirs = coursesDir.listFiles();
                if (subjectDirs != null) {
                    for (File subjectDir : subjectDirs) {
                        if (subjectDir.isDirectory()) {
                            File[] files = subjectDir.listFiles();
                            if (files != null) {
                                count += files.length;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Study Goal data class
     */
    private static class StudyGoal {
        String title;
        String description;
        int current;
        int target;
        String colorStart;
        String colorEnd;

        StudyGoal(String title, String description, int current, int target, String colorStart, String colorEnd) {
            this.title = title;
            this.description = description;
            this.current = current;
            this.target = target;
            this.colorStart = colorStart;
            this.colorEnd = colorEnd;
        }
    }
}