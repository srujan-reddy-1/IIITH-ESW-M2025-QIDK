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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FlashcardPdfGeneratorActivity - Generate flashcards from PDF using AI
 */
public class FlashcardPdfGeneratorActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 101;
    private static final String TAG = "FlashcardPdfGenerator";

    private MaterialToolbar toolbar;
    private TextInputEditText setTitleInput;
    private Button selectPdfButton;
    private TextView pdfStatusText;
    private Button generateButton;
    private ProgressBar progressBar;
    private TextView progressText;
    private SharedPreferences preferences;

    private String pdfContent = "";
    private String pdfFileName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_pdf_generator);

        // Initialize PDFBox
        PDFBoxResourceLoader.init(getApplicationContext());

        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        setupClickListeners();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        setTitleInput = findViewById(R.id.set_title_input);
        selectPdfButton = findViewById(R.id.select_pdf_button);
        pdfStatusText = findViewById(R.id.pdf_status_text);
        generateButton = findViewById(R.id.generate_button);
        progressBar = findViewById(R.id.progress_bar);
        progressText = findViewById(R.id.progress_text);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        selectPdfButton.setOnClickListener(v -> selectPdf());
        generateButton.setOnClickListener(v -> generateFlashcards());
    }

    private void selectPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_PDF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri pdfUri = data.getData();
                loadPdfInBackground(pdfUri);
            }
        }
    }

    private void loadPdfInBackground(Uri pdfUri) {
        progressBar.setVisibility(android.view.View.VISIBLE);
        progressText.setText("Loading PDF...");
        progressText.setVisibility(android.view.View.VISIBLE);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                PDDocument document = PDDocument.load(getContentResolver().openInputStream(pdfUri));
                PDFTextStripper stripper = new PDFTextStripper();
                
                String fullText = stripper.getText(document);
                int pageCount = document.getNumberOfPages();
                
                // Get filename
                String[] pathSegments = pdfUri.getPath().split("/");
                pdfFileName = pathSegments[pathSegments.length - 1];
                if (pdfFileName.toLowerCase().endsWith(".pdf")) {
                    pdfFileName = pdfFileName.substring(0, pdfFileName.length() - 4);
                }

                document.close();

                // Limit content size to fit model context (1024 tokens ~= 3000-4000 chars)
                // Using 2500 to leave room for flashcard generation prompt
                pdfContent = fullText.length() > 2500 ? fullText.substring(0, 2500) : fullText;

                runOnUiThread(() -> {
                    pdfStatusText.setText(String.format("✓ %s (%d pages)", pdfFileName, pageCount));
                    pdfStatusText.setVisibility(android.view.View.VISIBLE);
                    progressBar.setVisibility(android.view.View.GONE);
                    progressText.setVisibility(android.view.View.GONE);
                    generateButton.setEnabled(true);
                    
                    // Auto-fill title if empty
                    if (setTitleInput.getText().toString().trim().isEmpty()) {
                        setTitleInput.setText(pdfFileName);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading PDF: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    progressText.setVisibility(android.view.View.GONE);
                    Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void generateFlashcards() {
        String title = setTitleInput.getText().toString().trim();
        if (title.isEmpty()) {
            setTitleInput.setError("Title is required");
            return;
        }

        if (pdfContent.isEmpty()) {
            Toast.makeText(this, "Please select a PDF first", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(android.view.View.VISIBLE);
        progressText.setText("Generating flashcards with AI...");
        progressText.setVisibility(android.view.View.VISIBLE);
        generateButton.setEnabled(false);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String prompt = String.format(
                    "Generate exactly 10 flashcards from this document. " +
                    "Format each flashcard EXACTLY as:\n" +
                    "Q: [question]\n" +
                    "A: [answer]\n\n" +
                    "Make questions clear and concise. Make answers detailed but focused.\n\n" +
                    "Document content:\n%s",
                    pdfContent
                );

                GenieWrapper genie = new GenieWrapper("/data/local/tmp/genie_bundle", "genie_config.json");
                final StringBuilder response = new StringBuilder();

                genie.getResponseForPrompt(prompt, new StringCallback() {
                    @Override
                    public void onNewString(String text) {
                        response.append(text);
                    }
                });

                // Parse flashcards from response
                ArrayList<Flashcard> flashcards = parseFlashcards(response.toString());

                if (flashcards.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to generate flashcards. Please try again.", Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(android.view.View.GONE);
                        progressText.setVisibility(android.view.View.GONE);
                        generateButton.setEnabled(true);
                    });
                    return;
                }

                // Create flashcard set
                FlashcardSet flashcardSet = new FlashcardSet(title, "Generated from " + pdfFileName, "PDF");
                for (Flashcard fc : flashcards) {
                    flashcardSet.addFlashcard(fc);
                }
                
                // Verify flashcards were added
                int cardCount = flashcardSet.getFlashcardCount();
                Log.d(TAG, "FlashcardSet created with " + cardCount + " cards");
                
                if (cardCount == 0) {
                    Log.e(TAG, "ERROR: Flashcard set has 0 cards after adding!");
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error: Failed to create flashcard set", Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(android.view.View.GONE);
                        progressText.setVisibility(android.view.View.GONE);
                        generateButton.setEnabled(true);
                    });
                    return;
                }

                // Save to SharedPreferences
                saveFlashcardSet(flashcardSet);

                runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    progressText.setVisibility(android.view.View.GONE);
                    Toast.makeText(this, "Generated " + flashcards.size() + " flashcards!", Toast.LENGTH_SHORT).show();
                    
                    // Navigate to study mode
                    Intent intent = new Intent(FlashcardPdfGeneratorActivity.this, FlashcardStudyActivity.class);
                    intent.putExtra("flashcard_set", flashcardSet);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error generating flashcards: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    progressText.setVisibility(android.view.View.GONE);
                    generateButton.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private ArrayList<Flashcard> parseFlashcards(String response) {
        ArrayList<Flashcard> flashcards = new ArrayList<>();
        
        Log.d(TAG, "=== Parsing Flashcards ===");
        Log.d(TAG, "Response length: " + response.length());
        Log.d(TAG, "Response preview: " + (response.length() > 500 ? response.substring(0, 500) : response));
        
        // Pattern to match Q: ... A: ... format
        Pattern pattern = Pattern.compile("Q:\\s*(.+?)\\s*A:\\s*(.+?)(?=Q:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        int matchCount = 0;
        while (matcher.find()) {
            matchCount++;
            String question = matcher.group(1).trim();
            String answer = matcher.group(2).trim();
            
            // Clean up question and answer
            question = question.replaceAll("\\n+", " ").trim();
            answer = answer.replaceAll("\\n+", " ").trim();
            
            Log.d(TAG, "Match " + matchCount + " - Q: " + question.substring(0, Math.min(50, question.length())));
            Log.d(TAG, "Match " + matchCount + " - A: " + answer.substring(0, Math.min(50, answer.length())));
            
            if (!question.isEmpty() && !answer.isEmpty()) {
                flashcards.add(new Flashcard(question, answer));
            }
        }

        Log.d(TAG, "Parsed " + flashcards.size() + " flashcards from response");
        
        // If no flashcards parsed, try alternative formats
        if (flashcards.isEmpty()) {
            Log.d(TAG, "Trying alternative parsing methods...");
            flashcards = parseAlternativeFormat(response);
        }
        
        return flashcards;
    }
    
    /**
     * Try alternative parsing formats in case the AI doesn't follow Q:/A: format
     */
    private ArrayList<Flashcard> parseAlternativeFormat(String response) {
        ArrayList<Flashcard> flashcards = new ArrayList<>();
        
        // Try format: "Question: ... Answer: ..."
        Pattern pattern1 = Pattern.compile("Question:\\s*(.+?)\\s*Answer:\\s*(.+?)(?=Question:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(response);
        
        while (matcher1.find()) {
            String question = matcher1.group(1).trim().replaceAll("\\n+", " ");
            String answer = matcher1.group(2).trim().replaceAll("\\n+", " ");
            if (!question.isEmpty() && !answer.isEmpty()) {
                flashcards.add(new Flashcard(question, answer));
                Log.d(TAG, "Alternative format match found");
            }
        }
        
        // Try format: numbered list "1. ... Answer: ..."
        if (flashcards.isEmpty()) {
            Pattern pattern2 = Pattern.compile("\\d+\\.\\s*(.+?)(?:Answer|A):\\s*(.+?)(?=\\d+\\.|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher matcher2 = pattern2.matcher(response);
            
            while (matcher2.find()) {
                String question = matcher2.group(1).trim().replaceAll("\\n+", " ");
                String answer = matcher2.group(2).trim().replaceAll("\\n+", " ");
                if (!question.isEmpty() && !answer.isEmpty()) {
                    flashcards.add(new Flashcard(question, answer));
                    Log.d(TAG, "Numbered format match found");
                }
            }
        }
        
        Log.d(TAG, "Alternative parsing found " + flashcards.size() + " flashcards");
        return flashcards;
    }

    private void saveFlashcardSet(FlashcardSet flashcardSet) {
        String currentUser = preferences.getString("current_user_name", "default_user");
        String key = "flashcard_sets_" + currentUser;
        
        Log.d(TAG, "Saving flashcard set: " + flashcardSet.getTitle());
        Log.d(TAG, "Number of cards in set: " + flashcardSet.getFlashcardCount());
        
        String storageString = flashcardSet.toStorageString();
        Log.d(TAG, "Storage string length: " + storageString.length());
        Log.d(TAG, "Storage string preview: " + (storageString.length() > 200 ? storageString.substring(0, 200) + "..." : storageString));
        
        Set<String> sets = preferences.getStringSet(key, new HashSet<>());
        // Create a new HashSet to avoid SharedPreferences issues
        Set<String> updatedSets = new HashSet<>();
        if (sets != null) {
            updatedSets.addAll(sets);
        }
        updatedSets.add(storageString);
        
        // Clear the old set first, then save the new one
        preferences.edit()
            .remove(key)
            .apply();
        preferences.edit()
            .putStringSet(key, updatedSets)
            .commit(); // Use commit() to ensure it's saved immediately
        
        Log.d(TAG, "Saved flashcard set for user: " + currentUser + ". Total sets: " + updatedSets.size());
    }
}
