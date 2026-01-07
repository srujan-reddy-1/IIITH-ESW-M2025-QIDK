// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Native library loading removed - now using shell-based Genie implementation

    /**
     * copyAssetsDir: Copies provided assets to output path
     *
     * @param inputAssetRelPath relative path to asset from asset root
     * @param outputPath        output path to copy assets to
     * @throws IOException
     * @throws NullPointerException
     */
    void copyAssetsDir(String inputAssetRelPath, String outputPath) throws IOException, NullPointerException {
        File outputAssetPath = new File(Paths.get(outputPath, inputAssetRelPath).toString());

        String[] subAssetList = this.getAssets().list(inputAssetRelPath);
        if (subAssetList == null || subAssetList.length == 0) {
            // If file already present, skip copy.
            if (!outputAssetPath.exists()) {
                copyFile(inputAssetRelPath, outputAssetPath);
            }
            return;
        }

        // Input asset is a directory, create directory if not present already.
        if (!outputAssetPath.exists()) {
            outputAssetPath.mkdirs();
        }
        for (String subAssetName : subAssetList) {
            // Copy content of sub-directory
            String input_sub_asset_path = Paths.get(inputAssetRelPath, subAssetName).toString();
            // NOTE: Not to modify output path, relative asset path is being updated.
            copyAssetsDir(input_sub_asset_path, outputPath);
        }
    }

    /**
     * copyFile: Copies provided input file asset into output asset file
     *
     * @param inputFilePath   relative file path from asset root directory
     * @param outputAssetFile output file to copy input asset file into
     * @throws IOException
     */
    void copyFile(String inputFilePath, File outputAssetFile) throws IOException {
        try (InputStream in = this.getAssets().open(inputFilePath);
             OutputStream out = new FileOutputStream(outputAssetFile)) {

            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Get SoC model from build properties
            HashMap<String, String> supportedSocModel = new HashMap<>();
            supportedSocModel.put("SM8750", "qualcomm-snapdragon-8-elite.json");
            supportedSocModel.put("SM8650", "qualcomm-snapdragon-8-gen3.json");
            supportedSocModel.put("QCS8550", "qualcomm-snapdragon-8-gen2.json");
            supportedSocModel.put("SM8475", "qualcomm-snapdragon-8-gen2.json"); // Added support for SM8475 (Snapdragon 8+ Gen 1)

            String socModel = android.os.Build.SOC_MODEL;
            if (!supportedSocModel.containsKey(socModel)) {
                String errorMsg = "Unsupported device. Please ensure you have one of the following devices to run ChatApp: " +
                        supportedSocModel.keySet().toString();
                Log.e("ChatApp", errorMsg);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Copy assets to External cache
            String externalDir = getExternalCacheDir().getAbsolutePath();
            try {
                copyAssetsDir("models", externalDir);
                copyAssetsDir("htp_config", externalDir);
            } catch (IOException e) {
                String errorMsg = "Error during copying model asset to external storage: " + e.getMessage();
                Log.e("ChatApp", errorMsg);
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Path htpExtConfigPath = Paths.get(externalDir, "htp_config", supportedSocModel.get(socModel));

            // Apply saved theme before setting content view
            applyThemeFromPreferences();

            setContentView(R.layout.activity_dashboard);

            // Set dynamic greeting based on time
            TextView greetingTextView = findViewById(R.id.greeting_text);
            if (greetingTextView != null) {
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                String greeting = getGreeting(hour);
                greetingTextView.setText(greeting);
                
                // Add long-press to show diagnostics and clear stuck generation flags
                greetingTextView.setOnLongClickListener(v -> {
                    showDiagnostics();
                    return true;
                });
            }

            // Update dynamic progress section
            updateProgressSection();

            // Setup Bottom Navigation
            setupBottomNavigation();
            
            // Progress Overview card click - launch detailed progress
            CardView progressCard = findViewById(R.id.progress_overview_card);
            if (progressCard != null) {
                progressCard.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, DetailedProgressActivity.class);
                    startActivity(intent);
                });
            }

            // Chat with Atom card click
            CardView chatCard = findViewById(R.id.chat_card);
            if (chatCard != null) {
                chatCard.setOnClickListener(v -> {
                    // Track this activity
                    LastActivityTracker.saveChatActivity(MainActivity.this, "General", htpExtConfigPath.toString(), "llm");
                    
                    Intent intent = new Intent(MainActivity.this, Conversation.class);
                    intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, htpExtConfigPath.toString());
                    intent.putExtra(Conversation.cConversationActivityKeyModelName, "llm");
                    startActivity(intent);
                });
            }

            // Quiz card click
            CardView quizCard = findViewById(R.id.quiz_card);
            if (quizCard != null) {
                quizCard.setOnClickListener(v -> {
                    // Track this activity
                    LastActivityTracker.saveQuizActivity(MainActivity.this, null);
                    
                    Intent intent = new Intent(MainActivity.this, QuizActivity.class);
                    startActivity(intent);
                });
            }

            // Flashcards card click
            CardView flashcardsCard = findViewById(R.id.flashcards_card);
            if (flashcardsCard != null) {
                flashcardsCard.setOnClickListener(v -> {
                    // Track this activity
                    LastActivityTracker.saveFlashcardActivity(MainActivity.this, null);
                    
                    Intent intent = new Intent(MainActivity.this, FlashcardActivity.class);
                    startActivity(intent);
                });
            }

            // Subjects card click
            CardView subjectsCard = findViewById(R.id.subjects_card);
            if (subjectsCard != null) {
                subjectsCard.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, SubjectsActivity.class);
                    startActivity(intent);
                });
            }

            // Remove profile button - now in bottom nav
            // MaterialCardView profileButton = findViewById(R.id.profile_button);
            // Profile is now in bottom navigation

        } catch (Exception e) {
            String errorMsg = "Unexpected error occurred while running ChatApp: " + e.getMessage();
            Log.e("ChatApp", errorMsg);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Get the appropriate greeting based on the current hour
     * @param hour Current hour in 24-hour format
     * @return String containing the appropriate greeting
     */
    private String getGreeting(int hour) {
        if (hour >= 5 && hour < 12) {
            return "Good Morning!";
        } else if (hour >= 12 && hour < 17) {
            return "Good Afternoon!";
        } else {
            return "Good Evening!";
        }
    }

    /**
     * Update the progress section with real-time data from user activities
     */
    private void updateProgressSection() {
        // Get current progress percentage
        int progressScore = ProgressTracker.getProgressPercentage(this);
        
        // Update UI elements
        TextView progressSubtitle = findViewById(R.id.progress_subtitle);
        ProgressBar progressBar = findViewById(R.id.progress_bar);
        TextView progressTitle = findViewById(R.id.progress_title);
        
        if (progressSubtitle != null) {
            progressSubtitle.setText(progressScore + "% Mastery Achieved");
        }
        
        if (progressBar != null) {
            progressBar.setProgress(progressScore);
        }
        
        if (progressTitle != null) {
            progressTitle.setText(ProgressTracker.getProgressTitle(progressScore));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Update study streak when app is opened
        ProgressTracker.updateStudyStreak(this);
        
        // Update progress when returning to dashboard
        updateProgressSection();
        
        // Clear any stuck generation flags (older than 2 minutes)
        clearStuckGenerationFlags();
        
        // Highlight Home in bottom nav
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }
    
    /**
     * Show diagnostic information and offer to clear stuck flags
     */
    private void showDiagnostics() {
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("🔍 AI Tutor Diagnostics\n\n");
        
        // Check Genie Bundle
        File genieBundleDir = new File("/data/local/tmp/genie_bundle");
        File genieConfig = new File(genieBundleDir, "genie_config.json");
        File genieExecutable = new File(genieBundleDir, "genie-t2t-run");
        
        diagnostics.append("📦 Genie Bundle: ");
        if (genieBundleDir.exists()) {
            diagnostics.append("✓ Found\n");
        } else {
            diagnostics.append("✗ Missing\n");
        }
        
        diagnostics.append("⚙️ Config File: ");
        if (genieConfig.exists()) {
            diagnostics.append("✓ Found\n");
        } else {
            diagnostics.append("✗ Missing\n");
        }
        
        diagnostics.append("🚀 Executable: ");
        if (genieExecutable.exists()) {
            diagnostics.append("✓ Found\n");
        } else {
            diagnostics.append("✗ Missing\n");
        }
        
        // Check generation flags
        SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
        Map<String, ?> allPrefs = prefs.getAll();
        int activeFlags = 0;
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith("generating_") && entry.getValue() instanceof Boolean && (Boolean) entry.getValue()) {
                String subject = entry.getKey().substring("generating_".length());
                long startTime = prefs.getLong("gen_start_time_" + subject, 0);
                long elapsed = (currentTime - startTime) / 1000; // seconds
                activeFlags++;
            }
        }
        
        diagnostics.append("\n🚩 Active Generation Flags: ").append(activeFlags).append("\n");
        
        // Check quiz/flashcard data
        QuizBank quizBank = QuizBank.getInstance();
        quizBank.init(this);
        int totalQuizzes = quizBank.getAllQuizzes().size();
        
        FlashcardBank flashcardBank = FlashcardBank.getInstance();
        flashcardBank.init(this);
        int totalFlashcards = flashcardBank.getAllFlashcards().size();
        
        diagnostics.append("📝 Total Quizzes: ").append(totalQuizzes).append("\n");
        diagnostics.append("🎴 Total Flashcards: ").append(totalFlashcards).append("\n");
        
        // Show dialog with multiple options
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Diagnostics")
            .setMessage(diagnostics.toString())
            .setPositiveButton("Clear Flags", (dialog, which) -> {
                clearAllGenerationFlags();
                Toast.makeText(this, "Cleared all generation flags ✓", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Clear All Data", (dialog, which) -> {
                // Confirmation dialog
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("⚠️ Clear All Data?")
                    .setMessage("This will delete:\n\n• All uploaded PDFs\n• All generated quizzes\n• All generated flashcards\n• All context data\n• All generation flags\n\nThis action cannot be undone!\n\nAre you sure?")
                    .setPositiveButton("Yes, Clear All", (d, w) -> {
                        clearAllGeneratedData();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .setNegativeButton("Close", null)
            .show();
    }
    
    /**
     * Clear all generated quiz/flashcard data
     */
    private void clearAllGeneratedData() {
        try {
            // Clear generation flags
            clearAllGenerationFlags();
            
            // Clear all quizzes
            QuizBank quizBank = QuizBank.getInstance();
            quizBank.init(this);
            boolean quizzesCleared = quizBank.clearAllQuizzes();
            
            // Clear all flashcards
            FlashcardBank flashcardBank = FlashcardBank.getInstance();
            flashcardBank.init(this);
            boolean flashcardsCleared = flashcardBank.clearAllFlashcards();
            
            // Clear all uploaded PDFs/documents
            DocumentManager documentManager = DocumentManager.getInstance(this);
            documentManager.clearAllDocuments();
            
            // Clear all context window data
            try {
                java.io.File externalCacheDir = getExternalCacheDir();
                if (externalCacheDir != null) {
                    String modelDir = java.nio.file.Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm").toString();
                    String htpExtConfigPath = java.nio.file.Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm", "htp_backend_ext_config.json").toString();
                    WorkingGenieWrapper genieWrapper = new WorkingGenieWrapper(modelDir, htpExtConfigPath, this);
                    ContextWindowManager contextManager = ContextWindowManager.getInstance(this, genieWrapper);
                    
                    // Clear all context data from SharedPreferences
                    android.content.SharedPreferences contextPrefs = getSharedPreferences("ContextWindowPrefs", MODE_PRIVATE);
                    contextPrefs.edit().clear().apply();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error clearing context data: " + e.getMessage());
            }
            
            // Clear recent activity
            SharedPreferences activityPrefs = getSharedPreferences("RecentActivity", MODE_PRIVATE);
            activityPrefs.edit().clear().apply();
            
            // Reinitialize to recreate empty directories
            quizBank.init(this);
            flashcardBank.init(this);
            
            if (quizzesCleared && flashcardsCleared) {
                Toast.makeText(this, "✓ All data cleared successfully!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "✓ Data cleared!", Toast.LENGTH_LONG).show();
            }
            
            // Refresh the UI
            updateProgressSection();
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error clearing data", e);
            Toast.makeText(this, "Error clearing data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Clear generation flags that might be stuck from previous sessions
     */
    private void clearStuckGenerationFlags() {
        try {
            SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
            Map<String, ?> allPrefs = prefs.getAll();
            long currentTime = System.currentTimeMillis();
            
            SharedPreferences.Editor editor = prefs.edit();
            boolean hasChanges = false;
            
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("generating_")) {
                    // Check if there's a corresponding timestamp
                    String subject = key.substring("generating_".length());
                    long startTime = prefs.getLong("gen_start_time_" + subject, 0);
                    
                    // If flag is true and timestamp is older than 90 seconds, or no timestamp exists
                    if (entry.getValue() instanceof Boolean && (Boolean) entry.getValue()) {
                        if (startTime == 0 || (currentTime - startTime) > 90000) { // 90 seconds
                            editor.putBoolean(key, false);
                            editor.remove("gen_start_time_" + subject);
                            hasChanges = true;
                        }
                    }
                }
            }
            
            if (hasChanges) {
                editor.apply();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error clearing stuck flags", e);
        }
    }
    
    /**
     * Clear ALL generation flags (debug method triggered by long-press)
     */
    private void clearAllGenerationFlags() {
        SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Clear all generation flags and timestamps
        Map<String, ?> allPrefs = prefs.getAll();
        for (String key : allPrefs.keySet()) {
            if (key.startsWith("generating_") || key.startsWith("gen_start_time_")) {
                editor.remove(key);
            }
        }
        
        editor.apply();
    }

    /**
     * Setup bottom navigation bar
     */
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
            
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                
                if (itemId == R.id.nav_home) {
                    // Already on home, do nothing
                    return true;
                } else if (itemId == R.id.nav_ai) {
                    // Open Atom AI Chat
                    openAtomAI();
                    return true;
                } else if (itemId == R.id.nav_study) {
                    // Open subjects section
                    Intent intent = new Intent(MainActivity.this, SubjectsActivity.class);
                    startActivity(intent);
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    // Open profile
                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Open Atom AI Chat
     */
    private void openAtomAI() {
        try {
            // Get HTP config path
            java.io.File externalCacheDir = getExternalCacheDir();
            java.nio.file.Path htpExtConfigPath = java.nio.file.Paths.get(
                externalCacheDir.getAbsolutePath(), 
                "htp_config", 
                getSocConfigFile()
            );
            
            // Track this activity
            LastActivityTracker.saveChatActivity(this, "General", htpExtConfigPath.toString(), "llm");
            
            Intent intent = new Intent(MainActivity.this, Conversation.class);
            intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, htpExtConfigPath.toString());
            intent.putExtra(Conversation.cConversationActivityKeyModelName, "llm");
            intent.putExtra("is_global_chat", true); // Mark as global chat with all subjects
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening Atom AI: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get the SoC config file name based on device
     */
    private String getSocConfigFile() {
        String socModel = android.os.Build.SOC_MODEL;
        switch (socModel) {
            case "SM8750":
                return "qualcomm-snapdragon-8-elite.json";
            case "SM8650":
                return "qualcomm-snapdragon-8-gen3.json";
            case "QCS8550":
            case "SM8475":
                return "qualcomm-snapdragon-8-gen2.json";
            default:
                return "qualcomm-snapdragon-8-gen3.json"; // Default fallback
        }
    }

    /**
     * Open the last study activity using LastActivityTracker
     */
    private void openLastStudyActivity() {
        Intent intent = LastActivityTracker.getLastActivityIntent(this);
        if (intent != null) {
            startActivity(intent);
        } else {
            // Fallback to Quiz if no activity tracked
            Intent quizIntent = new Intent(MainActivity.this, QuizActivity.class);
            startActivity(quizIntent);
        }
    }

    /**
     * Apply the saved theme preference from SharedPreferences
     */
    private void applyThemeFromPreferences() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("darkMode", false);
        
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        int newMode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        
        if (currentMode != newMode) {
            AppCompatDelegate.setDefaultNightMode(newMode);
        }
    }
}