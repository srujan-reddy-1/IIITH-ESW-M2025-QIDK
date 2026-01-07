// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuizActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1;
    
    private MaterialToolbar toolbar;
    private CardView setupCard, quizCard;
    private LinearLayout setupLayout, quizLayout;
    private TextView pdfNameText, questionText, scoreText, weakTopicsInfo;
    private ChipGroup difficultyChips, numberChips;
    private Button selectPdfButton, generateQuizButton, submitAnswerButton, nextQuestionButton, viewQuizHistoryButton;
    private ProgressBar progressBar;
    private RadioGroup optionsGroup;
    
    private Uri pdfUri;
    private String pdfContent;
    private String pdfFileName = ""; // Store PDF filename for QuizResult
    private List<QuizQuestion> questions;
    private int currentQuestionIndex = 0;
    private int score = 0;
    private String selectedDifficulty = "Medium";
    private int selectedNumber = 5;
    private SharedPreferences preferences;
    private QuizResult currentQuizResult;  // Track current quiz for saving

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize PDFBox
        PDFBoxResourceLoader.init(getApplicationContext());
        
        // Initialize preferences
        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        
        setContentView(R.layout.activity_quiz);
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        setupCard = findViewById(R.id.setup_card);
        quizCard = findViewById(R.id.quiz_card);
        setupLayout = findViewById(R.id.setup_layout);
        quizLayout = findViewById(R.id.quiz_layout);
        pdfNameText = findViewById(R.id.pdf_name_text);
        questionText = findViewById(R.id.question_text);
        scoreText = findViewById(R.id.score_text);
        weakTopicsInfo = findViewById(R.id.weak_topics_info);
        difficultyChips = findViewById(R.id.difficulty_chips);
        numberChips = findViewById(R.id.number_chips);
        selectPdfButton = findViewById(R.id.select_pdf_button);
        generateQuizButton = findViewById(R.id.generate_quiz_button);
        submitAnswerButton = findViewById(R.id.submit_answer_button);
        nextQuestionButton = findViewById(R.id.next_question_button);
        viewQuizHistoryButton = findViewById(R.id.view_quiz_history_button);
        progressBar = findViewById(R.id.progress_bar);
        optionsGroup = findViewById(R.id.options_group);
        
        setupChipListeners();
        
        // Show weak topics info if available
        displayWeakTopicsInfo();
        
        // Check for PDF URI from gem intent
        Intent intent = getIntent();
        String gemName = intent.getStringExtra("gem_name");
        String pdfUriString = intent.getStringExtra("pdf_uri");
        
        if (pdfUriString != null && !pdfUriString.isEmpty()) {
            // Auto-load PDF from gem
            try {
                pdfUri = Uri.parse(pdfUriString);
                String fileName = gemName != null ? gemName + ".pdf" : "Document.pdf";
                pdfFileName = fileName; // Store filename
                pdfNameText.setText(fileName);
                pdfNameText.setVisibility(View.VISIBLE);
                selectPdfButton.setText("✓ " + fileName);
                selectPdfButton.setEnabled(false);
                generateQuizButton.setEnabled(true);
                
                // Auto-load PDF content
                loadPdfContent();
            } catch (Exception e) {
                Log.e("QuizActivity", "Error loading PDF from gem: " + e.getMessage());
                // Fall back to normal file picker flow
            }
        }
        
        selectPdfButton.setOnClickListener(v -> openPdfPicker());
        generateQuizButton.setOnClickListener(v -> generateQuiz());
        submitAnswerButton.setOnClickListener(v -> checkAnswer());
        nextQuestionButton.setOnClickListener(v -> showNextQuestion());
        viewQuizHistoryButton.setOnClickListener(v -> {
            Intent historyIntent = new Intent(QuizActivity.this, QuizHistoryActivity.class);
            startActivity(historyIntent);
        });
    }
    
    private void setupChipListeners() {
        difficultyChips.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = findViewById(checkedId);
            if (chip != null) {
                selectedDifficulty = chip.getText().toString();
            }
        });
        
        numberChips.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = findViewById(checkedId);
            if (chip != null) {
                selectedNumber = Integer.parseInt(chip.getText().toString());
            }
        });
    }
    
    private void displayWeakTopicsInfo() {
        String currentUser = preferences.getString("current_user_name", "default_user");
        
        if (WeakTopicsAnalyzer.hasWeakTopics(preferences, currentUser)) {
            String summary = WeakTopicsAnalyzer.getWeakTopicsSummary(preferences, currentUser);
            weakTopicsInfo.setText("🎯 Personalized Quiz:\n" + summary + "\nThe quiz will focus on these topics!");
            weakTopicsInfo.setVisibility(View.VISIBLE);
        } else {
            weakTopicsInfo.setVisibility(View.GONE);
        }
    }
    
    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_PDF_REQUEST);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                pdfUri = data.getData();
                String fileName = pdfUri.getLastPathSegment();
                pdfFileName = fileName != null ? fileName : "Unknown.pdf"; // Store filename
                pdfNameText.setText("📄 " + fileName);
                pdfNameText.setVisibility(View.VISIBLE);
                generateQuizButton.setEnabled(true);
                
                // Load PDF content
                loadPdfContent();
            }
        }
    }
    
    private void loadPdfContent() {
        progressBar.setVisibility(View.VISIBLE);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                InputStream input = getContentResolver().openInputStream(pdfUri);
                PDDocument document = PDDocument.load(input);
                
                PDFTextStripper stripper = new PDFTextStripper();
                pdfContent = stripper.getText(document);
                
                document.close();
                input.close();
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "PDF loaded successfully", Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e("QuizActivity", "Error loading PDF", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void generateQuiz() {
        if (pdfContent == null || pdfContent.isEmpty()) {
            Toast.makeText(this, "Please wait for PDF to load", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        generateQuizButton.setEnabled(false);
        
        // Get current user for personalization
        String currentUser = preferences.getString("current_user_name", "default_user");
        
        // Get weak topics for personalization
        String weakTopicsPrompt = WeakTopicsAnalyzer.getWeakTopicsPrompt(preferences, currentUser);
        
        // Set content limit based on model context window
        // Model has 1024 token context, roughly 3000-4000 chars including prompt overhead
        int contentLimit = 2500; // Conservative limit to leave room for prompt + quiz format
        String pdfContentForQuiz = pdfContent.length() > contentLimit ? 
            pdfContent.substring(0, contentLimit) + "..." : pdfContent;
        
        // Create prompt for quiz generation with personalization
        String prompt = String.format(
            "You are a quiz generator. You MUST generate EXACTLY %d multiple choice questions. " +
            "No more, no less than %d questions. " +
            "Difficulty: %s. " +
            "CRITICAL: Follow this EXACT format for each question:\n\n" +
            "Q1: What is the main topic?\n" +
            "A) Option one\n" +
            "B) Option two\n" +
            "C) Option three\n" +
            "D) Option four\n" +
            "Correct: A\n\n" +
            "Q2: What is another concept?\n" +
            "A) Another option\n" +
            "B) Different option\n" +
            "C) Third option\n" +
            "D) Fourth option\n" +
            "Correct: B\n\n" +
            "%s" + // Weak topics personalization
            "REMEMBER: Generate exactly %d questions, numbered Q1 through Q%d.\n" +
            "Generate the questions based on this text:\n%s",
            selectedNumber, selectedNumber,
            selectedDifficulty,
            weakTopicsPrompt,
            selectedNumber, selectedNumber,
            pdfContentForQuiz
        );
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            GenieWrapper genie = new GenieWrapper("/data/local/tmp/genie_bundle", "genie_config.json");
            
            final StringBuilder quizResponse = new StringBuilder();
            
            genie.getResponseForPrompt(prompt, new StringCallback() {
                @Override
                public void onNewString(String response) {
                    quizResponse.append(response);
                }
            });
            
            // Parse the generated quiz
            questions = parseQuizQuestions(quizResponse.toString());
            
            // If parsing failed, try a simpler fallback approach
            if (questions == null || questions.isEmpty()) {
                android.util.Log.d("QuizActivity", "Primary parsing failed, trying fallback");
                questions = parseQuizQuestionsFallback(quizResponse.toString());
            }
            
            // Validate question count and retry if needed
            if (questions != null && questions.size() != selectedNumber) {
                android.util.Log.d("QuizActivity", String.format("Expected %d questions, got %d. Response: %s", 
                    selectedNumber, questions.size(), quizResponse.toString()));
                
                // If we got fewer questions, pad with additional ones if we have enough content
                if (questions.size() < selectedNumber && questions.size() > 0) {
                    // Keep what we have for now - could implement retry logic here
                    android.util.Log.d("QuizActivity", "Using partial quiz with " + questions.size() + " questions");
                }
            }
            
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                generateQuizButton.setEnabled(true);
                
                if (questions != null && !questions.isEmpty()) {
                    setupCard.setVisibility(View.GONE);
                    quizCard.setVisibility(View.VISIBLE);
                    currentQuestionIndex = 0;
                    score = 0;
                    
                    // Initialize QuizResult for tracking with PDF source
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    currentQuizResult = new QuizResult(timestamp, selectedDifficulty, questions.size(), 0, pdfFileName);
                    
                    showQuestion();
                } else {
                    // Show the actual response for debugging
                    String debugMsg = "Failed to generate quiz. Response was: " + 
                        (quizResponse.length() > 200 ? quizResponse.substring(0, 200) + "..." : quizResponse.toString());
                    android.util.Log.e("QuizActivity", debugMsg);
                    Toast.makeText(this, "Failed to generate quiz. Check logs for details.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    
    private List<QuizQuestion> parseQuizQuestions(String response) {
        List<QuizQuestion> parsedQuestions = new ArrayList<>();
        
        try {
            android.util.Log.d("QuizActivity", "Raw quiz response length: " + response.length());
            android.util.Log.d("QuizActivity", "Raw quiz response: " + response);
            
            // Split response into individual questions first
            String[] questionBlocks = response.split("(?=Q\\d+)");
            
            for (String block : questionBlocks) {
                if (block.trim().isEmpty()) continue;
                
                android.util.Log.d("QuizActivity", "Processing block: " + block);
                
                // Extract question
                Pattern questionPattern = Pattern.compile("Q\\d+[:.)]?\\s*(.+?)(?=A\\))", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                Matcher questionMatcher = questionPattern.matcher(block);
                
                if (!questionMatcher.find()) {
                    android.util.Log.d("QuizActivity", "No question found in block");
                    continue;
                }
                
                String question = questionMatcher.group(1).trim();
                
                // Extract options A, B, C, D
                String optionA = extractOption(block, "A");
                String optionB = extractOption(block, "B");
                String optionC = extractOption(block, "C");
                String optionD = extractOption(block, "D");
                
                // Extract correct answer - try multiple patterns
                String correct = extractCorrectAnswer(block);
                
                if (question.isEmpty() || optionA.isEmpty() || optionB.isEmpty() || 
                    optionC.isEmpty() || optionD.isEmpty() || correct.isEmpty()) {
                    android.util.Log.d("QuizActivity", "Incomplete question data, skipping");
                    continue;
                }
                
                android.util.Log.d("QuizActivity", String.format("Parsed Question: %s (Correct: %s)", question, correct));
                
                parsedQuestions.add(new QuizQuestion(
                    question, optionA, optionB, optionC, optionD, correct.toUpperCase()
                ));
            }
            
            android.util.Log.d("QuizActivity", "Total questions parsed: " + parsedQuestions.size());
            
        } catch (Exception e) {
            android.util.Log.e("QuizActivity", "Error parsing quiz", e);
        }
        
        return parsedQuestions;
    }
    
    private String extractOption(String text, String option) {
        Pattern pattern = Pattern.compile(option + "\\)\\s*(.+?)(?=[ABCD]\\)|Correct|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
    
    private String extractCorrectAnswer(String text) {
        // Try multiple patterns for correct answer
        String[] patterns = {
            "Correct:\\s*([A-Da-d])\\)",  // Correct: A)
            "Correct:\\s*([A-Da-d])",    // Correct: A
            "Answer:\\s*([A-Da-d])\\)",  // Answer: A)
            "Answer:\\s*([A-Da-d])",     // Answer: A
            "Correct\\s*Answer:\\s*([A-Da-d])\\)", // Correct Answer: A)
            "Correct\\s*Answer:\\s*([A-Da-d])"     // Correct Answer: A
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).toUpperCase();
            }
        }
        
        android.util.Log.d("QuizActivity", "No correct answer found in: " + text);
        return "";
    }
    
    private List<QuizQuestion> parseQuizQuestionsFallback(String response) {
        List<QuizQuestion> parsedQuestions = new ArrayList<>();
        
        try {
            android.util.Log.d("QuizActivity", "Using fallback parsing method");
            
            // Very simple line-by-line parsing as fallback
            String[] lines = response.split("\n");
            String currentQuestion = "";
            String optionA = "", optionB = "", optionC = "", optionD = "";
            String correct = "";
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.matches("Q\\d+.*")) {
                    // New question found, save previous if complete
                    if (!currentQuestion.isEmpty() && !correct.isEmpty()) {
                        parsedQuestions.add(new QuizQuestion(currentQuestion, optionA, optionB, optionC, optionD, correct));
                    }
                    // Reset for new question
                    currentQuestion = line.replaceFirst("Q\\d+[:.)]?\\s*", "");
                    optionA = optionB = optionC = optionD = correct = "";
                } else if (line.startsWith("A)")) {
                    optionA = line.substring(2).trim();
                } else if (line.startsWith("B)")) {
                    optionB = line.substring(2).trim();
                } else if (line.startsWith("C)")) {
                    optionC = line.substring(2).trim();
                } else if (line.startsWith("D)")) {
                    optionD = line.substring(2).trim();
                } else if (line.toLowerCase().contains("correct")) {
                    // Extract just the letter
                    if (line.contains("A")) correct = "A";
                    else if (line.contains("B")) correct = "B";
                    else if (line.contains("C")) correct = "C";
                    else if (line.contains("D")) correct = "D";
                }
            }
            
            // Don't forget the last question
            if (!currentQuestion.isEmpty() && !correct.isEmpty()) {
                parsedQuestions.add(new QuizQuestion(currentQuestion, optionA, optionB, optionC, optionD, correct));
            }
            
            android.util.Log.d("QuizActivity", "Fallback parsing found: " + parsedQuestions.size() + " questions");
            
        } catch (Exception e) {
            android.util.Log.e("QuizActivity", "Error in fallback parsing", e);
        }
        
        return parsedQuestions;
    }
    
    private void showQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            showResults();
            return;
        }
        
        QuizQuestion q = questions.get(currentQuestionIndex);
        
        questionText.setText(String.format("Question %d of %d\n\n%s", 
            currentQuestionIndex + 1, questions.size(), q.question));
        
        optionsGroup.removeAllViews();
        optionsGroup.clearCheck();
        
        RadioButton optionA = new RadioButton(this);
        optionA.setText("A) " + q.optionA);
        optionA.setId(View.generateViewId());
        optionA.setTag("A");
        
        RadioButton optionB = new RadioButton(this);
        optionB.setText("B) " + q.optionB);
        optionB.setId(View.generateViewId());
        optionB.setTag("B");
        
        RadioButton optionC = new RadioButton(this);
        optionC.setText("C) " + q.optionC);
        optionC.setId(View.generateViewId());
        optionC.setTag("C");
        
        RadioButton optionD = new RadioButton(this);
        optionD.setText("D) " + q.optionD);
        optionD.setId(View.generateViewId());
        optionD.setTag("D");
        
        optionsGroup.addView(optionA);
        optionsGroup.addView(optionB);
        optionsGroup.addView(optionC);
        optionsGroup.addView(optionD);
        
        submitAnswerButton.setVisibility(View.VISIBLE);
        nextQuestionButton.setVisibility(View.GONE);
        scoreText.setText(String.format("Score: %d/%d", score, currentQuestionIndex));
    }
    
    private void checkAnswer() {
        int selectedId = optionsGroup.getCheckedRadioButtonId();
        
        if (selectedId == -1) {
            Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show();
            return;
        }
        
        RadioButton selected = findViewById(selectedId);
        String selectedAnswer = (String) selected.getTag();
        
        QuizQuestion q = questions.get(currentQuestionIndex);
        boolean isCorrect = selectedAnswer.equals(q.correctAnswer);
        
        // Record the answer in QuizResult
        if (currentQuizResult != null) {
            currentQuizResult.addQuestionResult(
                q.question, q.optionA, q.optionB, q.optionC, q.optionD,
                q.correctAnswer, selectedAnswer, isCorrect
            );
        }
        
        if (isCorrect) {
            score++;
            selected.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            Toast.makeText(this, "✓ Correct!", Toast.LENGTH_SHORT).show();
        } else {
            selected.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            
            // Highlight correct answer
            for (int i = 0; i < optionsGroup.getChildCount(); i++) {
                RadioButton rb = (RadioButton) optionsGroup.getChildAt(i);
                if (rb.getTag().equals(q.correctAnswer)) {
                    rb.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                }
            }
            
            Toast.makeText(this, "✗ Incorrect. Correct answer: " + q.correctAnswer, Toast.LENGTH_LONG).show();
        }
        
        // Disable all options
        for (int i = 0; i < optionsGroup.getChildCount(); i++) {
            optionsGroup.getChildAt(i).setEnabled(false);
        }
        
        submitAnswerButton.setVisibility(View.GONE);
        nextQuestionButton.setVisibility(View.VISIBLE);
        scoreText.setText(String.format("Score: %d/%d", score, currentQuestionIndex + 1));
    }
    
    private void showNextQuestion() {
        currentQuestionIndex++;
        
        // Re-enable options for next question
        for (int i = 0; i < optionsGroup.getChildCount(); i++) {
            RadioButton rb = (RadioButton) optionsGroup.getChildAt(i);
            rb.setEnabled(true);
            rb.setTextColor(getResources().getColor(android.R.color.black, null));
        }
        
        showQuestion();
    }
    
    private void showResults() {
        double percentage = (score * 100.0) / questions.size();
        
        // Update and save quiz result
        if (currentQuizResult != null) {
            currentQuizResult.score = score;
            saveQuizResult(currentQuizResult);
        }
        
        questionText.setText(String.format(
            "Quiz Complete!\n\n" +
            "Your Score: %d/%d (%.1f%%)\n" +
            "Grade: %s\n\n" +
            "%s\n\n" +
            "Your quiz has been saved to history!",
            score, questions.size(), percentage,
            currentQuizResult != null ? currentQuizResult.getGrade() : "",
            percentage >= 80 ? "Excellent! 🎉" :
            percentage >= 60 ? "Good job! 👍" :
            percentage >= 40 ? "Not bad, keep practicing! 📚" :
            "Keep studying! You can do better! 💪"
        ));
        
        optionsGroup.removeAllViews();
        submitAnswerButton.setVisibility(View.GONE);
        
        // Update button to show quiz history or start new quiz
        nextQuestionButton.setText("View Quiz History");
        nextQuestionButton.setOnClickListener(v -> {
            Intent intent = new Intent(QuizActivity.this, QuizHistoryActivity.class);
            startActivity(intent);
            finish();
        });
    }
    
    private void saveQuizResult(QuizResult result) {
        try {
            String currentUser = preferences.getString("current_user_name", "default_user");
            String key = "quiz_history_" + currentUser;
            
            Set<String> existingQuizzes = preferences.getStringSet(key, new HashSet<>());
            Set<String> updatedQuizzes = new HashSet<>(existingQuizzes);
            
            updatedQuizzes.add(result.toStorageString());
            
            preferences.edit()
                    .remove(key)
                    .commit();
            preferences.edit()
                    .putStringSet(key, updatedQuizzes)
                    .apply();
            
            Log.d("QuizActivity", "Quiz result saved successfully for user: " + currentUser);
        } catch (Exception e) {
            Log.e("QuizActivity", "Error saving quiz result: " + e.getMessage());
            Toast.makeText(this, "Error saving quiz result", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    // Inner class for quiz questions
    private static class QuizQuestion {
        String question;
        String optionA, optionB, optionC, optionD;
        String correctAnswer;
        
        QuizQuestion(String question, String optionA, String optionB, String optionC, String optionD, String correctAnswer) {
            this.question = question;
            this.optionA = optionA;
            this.optionB = optionB;
            this.optionC = optionC;
            this.optionD = optionD;
            this.correctAnswer = correctAnswer;
        }
    }
}
