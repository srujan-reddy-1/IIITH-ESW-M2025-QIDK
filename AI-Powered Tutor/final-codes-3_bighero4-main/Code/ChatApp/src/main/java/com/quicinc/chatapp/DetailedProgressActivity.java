// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DetailedProgressActivity extends AppCompatActivity {
    
    private TextView totalQuizzesText, totalFlashcardsText, totalSubjectsText, totalDocumentsText;
    private TextView averageScoreText, studyStreakText;
    private ProgressBar overallProgressBar, quizProgressBar, flashcardProgressBar;
    private TextView overallProgressText, quizProgressText, flashcardProgressText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detailed_progress);
        
        initializeViews();
        loadProgressData();
        setupClickListeners();
    }
    
    private void initializeViews() {
        ImageButton backButton = findViewById(R.id.back_button);
        
        // Statistics
        totalQuizzesText = findViewById(R.id.total_quizzes_text);
        totalFlashcardsText = findViewById(R.id.total_flashcards_text);
        totalSubjectsText = findViewById(R.id.total_subjects_text);
        totalDocumentsText = findViewById(R.id.total_documents_text);
        
        // Performance
        averageScoreText = findViewById(R.id.average_score_text);
        studyStreakText = findViewById(R.id.study_streak_text);
        
        // Progress bars
        overallProgressBar = findViewById(R.id.overall_progress_bar);
        quizProgressBar = findViewById(R.id.quiz_progress_bar);
        flashcardProgressBar = findViewById(R.id.flashcard_progress_bar);
        
        overallProgressText = findViewById(R.id.overall_progress_text);
        quizProgressText = findViewById(R.id.quiz_progress_text);
        flashcardProgressText = findViewById(R.id.flashcard_progress_text);
        
        backButton.setOnClickListener(v -> finish());
    }
    
    private void loadProgressData() {
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        
        // Get basic stats
        int totalQuizzes = prefs.getInt("total_quizzes_taken", 0);
        int totalFlashcards = prefs.getInt("total_flashcards_reviewed", 0);
        
        // Get subject count from ProgressTracker (dynamically updated by SubjectsActivity)
        int subjectCount = ProgressTracker.getSubjectCount(this);
        
        // Get total documents from ProgressTracker (updated by DocumentManager)
        int totalDocuments = ProgressTracker.getDocumentCount(this);
        
        // Calculate average score from quiz results
        float averageScore = calculateAverageScore(prefs);
        
        // Get and update study streak
        int studyStreak = ProgressTracker.updateStudyStreak(this);
        String streakBadge = ProgressTracker.getStreakBadge(studyStreak);
        
        // Update statistics
        totalQuizzesText.setText(String.valueOf(totalQuizzes));
        totalFlashcardsText.setText(String.valueOf(totalFlashcards));
        totalSubjectsText.setText(String.valueOf(subjectCount));
        totalDocumentsText.setText(String.valueOf(totalDocuments));
        
        // Update performance - show streak badge
        averageScoreText.setText(streakBadge);
        
        // Show streak with proper singular/plural
        if (studyStreak == 1) {
            studyStreakText.setText(studyStreak + " day");
        } else {
            studyStreakText.setText(studyStreak + " days");
        }
        
        // Calculate and update progress bars
        int overallProgress = ProgressTracker.getProgressPercentage(this);
        overallProgressBar.setProgress(overallProgress);
        overallProgressText.setText(overallProgress + "%");
        
        // Quiz progress (out of 50 quizzes target)
        int quizProgress = Math.min(100, (totalQuizzes * 100) / 50);
        quizProgressBar.setProgress(quizProgress);
        quizProgressText.setText(quizProgress + "%");
        
        // Flashcard progress (out of 200 flashcards target)
        int flashcardProgress = Math.min(100, (totalFlashcards * 100) / 200);
        flashcardProgressBar.setProgress(flashcardProgress);
        flashcardProgressText.setText(flashcardProgress + "%");
    }
    
    private int countTotalDocuments() {
        int count = 0;
        try {
            File coursesDir = new File(getFilesDir(), "courses");
            if (coursesDir.exists() && coursesDir.isDirectory()) {
                File[] subjectDirs = coursesDir.listFiles(File::isDirectory);
                if (subjectDirs != null) {
                    for (File subjectDir : subjectDirs) {
                        File[] files = subjectDir.listFiles((dir, name) -> name.endsWith(".txt"));
                        if (files != null) {
                            count += files.length;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }
    
    private float calculateAverageScore(SharedPreferences prefs) {
        // Try to get quiz scores from stored results
        int totalQuizzes = prefs.getInt("total_quizzes_taken", 0);
        if (totalQuizzes == 0) return 0f;
        
        // Get total score and total possible from all quizzes
        int totalScore = prefs.getInt("total_quiz_score", 0);
        int totalPossible = prefs.getInt("total_quiz_possible", 0);
        
        if (totalPossible > 0) {
            return (totalScore * 100f) / totalPossible;
        }
        
        // If no score data is tracked yet, return 0 instead of a fake estimate
        return 0f;
    }
    
    
    private void setupClickListeners() {
        // Quizzes taken card - opens Recent Quizzes activity
        CardView quizzesTakenCard = findViewById(R.id.quizzes_taken_card);
        if (quizzesTakenCard != null) {
            quizzesTakenCard.setOnClickListener(v -> {
                Intent intent = new Intent(DetailedProgressActivity.this, RecentQuizzesActivity.class);
                startActivity(intent);
            });
        }
    }
}
