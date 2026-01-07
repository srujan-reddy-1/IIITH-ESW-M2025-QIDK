// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeViews();
        setupImagePicker();
        setupClickListeners();
    }

    private void initializeViews() {
        // Load user data from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);
        
        TextView usernameText = findViewById(R.id.username_text);
        TextView levelText = findViewById(R.id.level_text);
        ImageView profileImage = findViewById(R.id.profile_image);

        // Load user data
        String userName = sharedPreferences.getString("user_name", "Study Master");
        
        // Get subject count from ProgressTracker (dynamically updated by SubjectsActivity)
        int subjectCount = ProgressTracker.getSubjectCount(this);
        
        String profileImageUri = sharedPreferences.getString("profile_image_uri", "");
        
        // Load statistics for level calculation
        int totalQuizzes = sharedPreferences.getInt("total_quizzes_taken", 0);
        int totalFlashcards = sharedPreferences.getInt("total_flashcards_reviewed", 0);
        int totalStudyHours = sharedPreferences.getInt("total_study_hours", 0);
        int totalChatSessions = sharedPreferences.getInt("total_chat_sessions", 0);
        
        // Calculate average quiz score for quality metric
        int totalQuizScore = sharedPreferences.getInt("total_quiz_score", 0);
        int totalQuizPossible = sharedPreferences.getInt("total_quiz_possible", 0);
        float averageScore = totalQuizPossible > 0 ? (totalQuizScore * 100f) / totalQuizPossible : 0f;
        
        // Update stats display
        TextView statQuizzes = findViewById(R.id.stat_quizzes);
        TextView statFlashcards = findViewById(R.id.stat_flashcards);
        TextView statSubjects = findViewById(R.id.stat_subjects);
        
        if (statQuizzes != null) statQuizzes.setText(String.valueOf(totalQuizzes));
        if (statFlashcards != null) statFlashcards.setText(String.valueOf(totalFlashcards));
        if (statSubjects != null) statSubjects.setText(String.valueOf(subjectCount));

        // Update UI with user data
        if (usernameText != null) {
            usernameText.setText(userName);
        }
        
        // Calculate user level based on comprehensive metrics
        int userLevel = calculateUserLevel(subjectCount, totalQuizzes, totalFlashcards, totalStudyHours, totalChatSessions, averageScore);
        String[] titleAndLevel = getUserTitleAndLevel(userLevel, averageScore);
        
        if (levelText != null) {
            levelText.setText(titleAndLevel[0] + " • Level " + userLevel + " • " + titleAndLevel[1]);
        }

        // Load profile image if available
        if (profileImage != null && !TextUtils.isEmpty(profileImageUri)) {
            try {
                Uri imageUri = Uri.parse(profileImageUri);
                // Check if URI is still valid before setting
                getContentResolver().takePersistableUriPermission(imageUri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                // Clear any previous tint and padding to show the actual image
                profileImage.setImageTintList(null);
                profileImage.setPadding(0, 0, 0, 0);
                profileImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                
                profileImage.setImageURI(imageUri);
            } catch (Exception e) {
                // URI is invalid or expired, clear it and keep default image
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove("profile_image_uri");
                editor.apply();
                Log.w("ProfileActivity", "Cleared invalid profile image URI: " + e.getMessage());
            }
        }
    }

    private void setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        try {
                            // Take persistable URI permission so the image remains accessible
                            getContentResolver().takePersistableUriPermission(result, 
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            
                            ImageView profileImage = findViewById(R.id.profile_image);
                            if (profileImage != null) {
                                // Clear any previous tint and padding to show the actual image
                                profileImage.setImageTintList(null);
                                profileImage.setPadding(0, 0, 0, 0);
                                profileImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                
                                // Set the selected image
                                profileImage.setImageURI(result);
                                
                                // Save the image URI to SharedPreferences for persistence
                                SharedPreferences sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("profile_image_uri", result.toString());
                                editor.apply();
                                
                                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Log.e("ProfileActivity", "Error loading image: " + e.getMessage());
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void setupClickListeners() {
        // Profile image click to change picture
        ImageView profileImage = findViewById(R.id.profile_image);
        if (profileImage != null) {
            profileImage.setOnClickListener(v -> {
                if (pickImageLauncher != null) {
                    pickImageLauncher.launch("image/*");
                } else {
                    Toast.makeText(this, "Image picker not initialized", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // View Detailed Progress button
        findViewById(R.id.view_detailed_progress_button).setOnClickListener(v -> {
            startActivity(new Intent(this, DetailedProgressActivity.class));
        });

        // Edit Profile card
        findViewById(R.id.edit_profile_card).setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivityForResult(intent, 100);
        });

        // Study Goals card (keep existing functionality if it exists)
        findViewById(R.id.study_goals_card).setOnClickListener(v -> {
            startActivity(new Intent(this, StudyGoalsActivity.class));
        });

        // Developer Settings card
        // findViewById(R.id.developer_settings_card).setOnClickListener(v -> {
        //     startActivity(new Intent(this, DeveloperSettingsActivity.class));
        // });

        // Settings card
        findViewById(R.id.settings_card).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Help & Support card
        findViewById(R.id.help_card).setOnClickListener(v -> {
            startActivity(new Intent(this, HelpActivity.class));
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            // Profile was updated, refresh the UI
            String updatedName = data.getStringExtra("updated_name");
            if (updatedName != null) {
                TextView usernameText = findViewById(R.id.username_text);
                if (usernameText != null) {
                    usernameText.setText(updatedName);
                }
                
                // Refresh the entire view to update dynamic title and level
                initializeViews();
            }
        }
    }
    
    /**
     * Calculate user level based on multiple activity metrics
     * XP System:
     * - Subjects: 20 XP each
     * - Quizzes: 5 XP each
     * - Flashcards: 2 XP each
     * - Study Hours: 10 XP each
     * - Chat Sessions: 3 XP each
     * - Bonus: +20% XP if average score >= 80%
     */
    private int calculateUserLevel(int subjects, int quizzes, int flashcards, int hours, int chatSessions, float avgScore) {
        // Calculate base XP
        int xp = (subjects * 20) + (quizzes * 5) + (flashcards * 2) + (hours * 10) + (chatSessions * 3);
        
        // Apply performance bonus
        if (avgScore >= 80f && quizzes > 0) {
            xp = (int)(xp * 1.2f); // 20% bonus for high performers
        } else if (avgScore >= 90f && quizzes >= 5) {
            xp = (int)(xp * 1.3f); // 30% bonus for excellent performers
        }
        
        // Convert XP to level (exponential growth: level = sqrt(XP/10))
        // This means: Level 1 = 10 XP, Level 10 = 1000 XP, Level 20 = 4000 XP
        int level = Math.max(1, (int)Math.sqrt(xp / 10.0));
        
        return Math.min(level, 99); // Cap at level 99
    }
    
    /**
     * Get user title and motivational message based on level and performance
     * Returns String array: [title, message]
     */
    private String[] getUserTitleAndLevel(int level, float avgScore) {
        String title;
        String message;
        
        if (level <= 1) {
            title = "Beginner";
            message = "Start your journey!";
        } else if (level <= 5) {
            title = "Learning Explorer";
            message = "Building foundations";
        } else if (level <= 10) {
            title = "Study Enthusiast";
            message = "Great progress!";
        } else if (level <= 15) {
            title = "Knowledge Seeker";
            message = "Keep pushing forward";
        } else if (level <= 20) {
            title = "Dedicated Scholar";
            message = "Outstanding effort!";
        } else if (level <= 30) {
            title = "Academic Champion";
            message = "You're excelling!";
        } else if (level <= 40) {
            title = "Master Learner";
            message = "Impressive mastery!";
        } else if (level <= 50) {
            title = "Study Virtuoso";
            message = "Exceptional skills!";
        } else {
            title = "Legend";
            message = "Ultimate mastery!";
        }
        
        // Add performance-based suffix
        if (avgScore >= 90f) {
            message += " 🌟";
        } else if (avgScore >= 80f) {
            message += " ⭐";
        }
        
        return new String[]{title, message};
    }
}
