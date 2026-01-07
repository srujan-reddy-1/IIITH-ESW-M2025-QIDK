// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * CreateFlashcardSetActivity - Manually create custom flashcard sets
 */
public class CreateFlashcardSetActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextInputEditText setTitleInput;
    private TextInputEditText setDescriptionInput;
    private LinearLayout flashcardsContainer;
    private Button addFlashcardButton;
    private Button cancelButton;
    private Button saveButton;
    private SharedPreferences preferences;

    private ArrayList<FlashcardInputView> flashcardInputViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_flashcard_set);

        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        setupClickListeners();
        
        // Start with 3 empty flashcards
        addFlashcardInput();
        addFlashcardInput();
        addFlashcardInput();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        setTitleInput = findViewById(R.id.set_title_input);
        setDescriptionInput = findViewById(R.id.set_description_input);
        flashcardsContainer = findViewById(R.id.flashcards_container);
        addFlashcardButton = findViewById(R.id.add_flashcard_button);
        cancelButton = findViewById(R.id.cancel_button);
        saveButton = findViewById(R.id.save_button);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        addFlashcardButton.setOnClickListener(v -> addFlashcardInput());
        cancelButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveAndStudy());
    }

    private void addFlashcardInput() {
        View flashcardView = LayoutInflater.from(this).inflate(R.layout.item_flashcard_input, flashcardsContainer, false);
        
        TextView cardNumber = flashcardView.findViewById(R.id.card_number);
        TextInputEditText questionInput = flashcardView.findViewById(R.id.question_input);
        TextInputEditText answerInput = flashcardView.findViewById(R.id.answer_input);
        ImageButton deleteButton = flashcardView.findViewById(R.id.delete_card_button);

        int cardNum = flashcardInputViews.size() + 1;
        cardNumber.setText("Card " + cardNum);

        FlashcardInputView inputView = new FlashcardInputView(flashcardView, questionInput, answerInput);
        flashcardInputViews.add(inputView);

        deleteButton.setOnClickListener(v -> {
            if (flashcardInputViews.size() > 1) {
                flashcardsContainer.removeView(flashcardView);
                flashcardInputViews.remove(inputView);
                updateCardNumbers();
            } else {
                Toast.makeText(this, "You need at least one flashcard", Toast.LENGTH_SHORT).show();
            }
        });

        flashcardsContainer.addView(flashcardView);
    }

    private void updateCardNumbers() {
        for (int i = 0; i < flashcardInputViews.size(); i++) {
            TextView cardNumber = flashcardInputViews.get(i).view.findViewById(R.id.card_number);
            cardNumber.setText("Card " + (i + 1));
        }
    }

    private void saveAndStudy() {
        String title = setTitleInput.getText().toString().trim();
        String description = setDescriptionInput.getText().toString().trim();

        if (title.isEmpty()) {
            setTitleInput.setError("Title is required");
            return;
        }

        // Collect flashcards
        ArrayList<Flashcard> flashcards = new ArrayList<>();
        for (FlashcardInputView inputView : flashcardInputViews) {
            String question = inputView.questionInput.getText().toString().trim();
            String answer = inputView.answerInput.getText().toString().trim();

            if (!question.isEmpty() && !answer.isEmpty()) {
                flashcards.add(new Flashcard(question, answer));
            }
        }

        if (flashcards.isEmpty()) {
            Toast.makeText(this, "Please add at least one flashcard with a question and answer", Toast.LENGTH_LONG).show();
            return;
        }

        // Create flashcard set
        FlashcardSet flashcardSet = new FlashcardSet(title, description, "MANUAL");
        for (Flashcard fc : flashcards) {
            flashcardSet.addFlashcard(fc);
        }

        // Save to SharedPreferences
        saveFlashcardSet(flashcardSet);

        Toast.makeText(this, "Created " + flashcards.size() + " flashcards!", Toast.LENGTH_SHORT).show();

        // Navigate to study mode
        Intent intent = new Intent(CreateFlashcardSetActivity.this, FlashcardStudyActivity.class);
        intent.putExtra("flashcard_set", flashcardSet);
        startActivity(intent);
        finish();
    }

    private void saveFlashcardSet(FlashcardSet flashcardSet) {
        String currentUser = preferences.getString("current_user_name", "default_user");
        String key = "flashcard_sets_" + currentUser;
        
        Set<String> sets = preferences.getStringSet(key, new HashSet<>());
        // Create a new HashSet to avoid SharedPreferences issues
        Set<String> updatedSets = new HashSet<>();
        if (sets != null) {
            updatedSets.addAll(sets);
        }
        updatedSets.add(flashcardSet.toStorageString());
        
        // Clear the old set first, then save the new one
        preferences.edit()
            .remove(key)
            .apply();
        preferences.edit()
            .putStringSet(key, updatedSets)
            .commit(); // Use commit() to ensure it's saved immediately
        
        android.util.Log.d("CreateFlashcardSet", "Saved flashcard set for user: " + currentUser + ". Total sets: " + updatedSets.size());
    }

    /**
     * Helper class to track flashcard input views
     */
    private static class FlashcardInputView {
        View view;
        TextInputEditText questionInput;
        TextInputEditText answerInput;

        FlashcardInputView(View view, TextInputEditText questionInput, TextInputEditText answerInput) {
            this.view = view;
            this.questionInput = questionInput;
            this.answerInput = answerInput;
        }
    }
}
