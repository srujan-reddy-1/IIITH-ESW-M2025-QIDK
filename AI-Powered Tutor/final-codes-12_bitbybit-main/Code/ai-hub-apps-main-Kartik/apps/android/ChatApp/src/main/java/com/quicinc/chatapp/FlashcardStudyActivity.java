// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;

/**
 * FlashcardStudyActivity - Study mode with swipe-like interface (tap buttons)
 * Similar to Quizlet's study mode
 */
public class FlashcardStudyActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private CardView flashcardView;
    private TextView cardLabel;
    private TextView flashcardContent;
    private TextView tapHint;
    private TextView progressText;
    private Button wrongButton;
    private Button correctButton;
    private TextView correctCount;
    private TextView wrongCount;

    private ArrayList<Flashcard> flashcards;
    private int currentCardIndex = 0;
    private int correctAnswers = 0;
    private int wrongAnswers = 0;
    private boolean isShowingQuestion = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_study);

        // Get flashcard set from intent
        FlashcardSet flashcardSet = (FlashcardSet) getIntent().getSerializableExtra("flashcard_set");
        if (flashcardSet == null || flashcardSet.getFlashcardCount() == 0) {
            Toast.makeText(this, "No flashcards to study", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        flashcards = new ArrayList<>(flashcardSet.getFlashcards());
        // Shuffle for varied practice
        Collections.shuffle(flashcards);

        initializeViews();
        setupToolbar(flashcardSet.getTitle());
        setupClickListeners();
        displayCurrentCard();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        flashcardView = findViewById(R.id.flashcard_view);
        cardLabel = findViewById(R.id.card_label);
        flashcardContent = findViewById(R.id.flashcard_content);
        tapHint = findViewById(R.id.tap_hint);
        progressText = findViewById(R.id.progress_text);
        wrongButton = findViewById(R.id.wrong_button);
        correctButton = findViewById(R.id.correct_button);
        correctCount = findViewById(R.id.correct_count);
        wrongCount = findViewById(R.id.wrong_count);
    }

    private void setupToolbar(String title) {
        setSupportActionBar(toolbar);
        toolbar.setTitle("Study: " + title);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        // Tap card to flip
        flashcardView.setOnClickListener(v -> flipCard());

        // Wrong answer - move to next card
        wrongButton.setOnClickListener(v -> {
            if (!isShowingQuestion) {
                recordAnswer(false);
                moveToNextCard();
            } else {
                Toast.makeText(this, "Flip the card to see the answer first", Toast.LENGTH_SHORT).show();
            }
        });

        // Correct answer - move to next card
        correctButton.setOnClickListener(v -> {
            if (!isShowingQuestion) {
                recordAnswer(true);
                moveToNextCard();
            } else {
                Toast.makeText(this, "Flip the card to see the answer first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayCurrentCard() {
        if (currentCardIndex >= flashcards.size()) {
            showCompletionScreen();
            return;
        }

        Flashcard currentCard = flashcards.get(currentCardIndex);
        isShowingQuestion = true;

        // Update progress
        progressText.setText(String.format("Card %d of %d", 
            currentCardIndex + 1, flashcards.size()));

        // Display question
        cardLabel.setText("QUESTION");
        flashcardContent.setText(currentCard.getQuestion());
        tapHint.setVisibility(View.VISIBLE);

        // Animate card entrance
        animateCardEntrance();
    }

    private void flipCard() {
        Flashcard currentCard = flashcards.get(currentCardIndex);
        
        // Fade out animation
        flashcardView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                // Toggle between question and answer
                isShowingQuestion = !isShowingQuestion;
                
                if (isShowingQuestion) {
                    cardLabel.setText("QUESTION");
                    flashcardContent.setText(currentCard.getQuestion());
                    tapHint.setVisibility(View.VISIBLE);
                    tapHint.setText("Tap to flip");
                } else {
                    cardLabel.setText("ANSWER");
                    flashcardContent.setText(currentCard.answer());
                    tapHint.setVisibility(View.VISIBLE);
                    tapHint.setText("Rate your answer below");
                }
                
                // Fade in animation
                flashcardView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start();
            })
            .start();
    }

    private void recordAnswer(boolean correct) {
        if (correct) {
            correctAnswers++;
            correctCount.setText("✓ " + correctAnswers);
        } else {
            wrongAnswers++;
            wrongCount.setText("❌ " + wrongAnswers);
        }
    }

    private void moveToNextCard() {
        // Slide out animation
        flashcardView.animate()
            .translationX(1000)
            .alpha(0)
            .setDuration(200)
            .withEndAction(() -> {
                currentCardIndex++;
                flashcardView.setTranslationX(-1000);
                flashcardView.setAlpha(0);
                displayCurrentCard();
            })
            .start();
    }

    private void animateCardEntrance() {
        flashcardView.animate()
            .translationX(0)
            .alpha(1)
            .setDuration(200)
            .start();
    }

    private void showCompletionScreen() {
        // Calculate score
        int totalCards = flashcards.size();
        int score = (int) ((correctAnswers / (double) totalCards) * 100);

        String message = String.format(
            "Study Session Complete!\n\n" +
            "✓ Correct: %d\n" +
            "❌ Wrong: %d\n" +
            "Score: %d%%",
            correctAnswers, wrongAnswers, score
        );

        new android.app.AlertDialog.Builder(this)
            .setTitle("🎉 Great Work!")
            .setMessage(message)
            .setPositiveButton("Finish", (dialog, which) -> finish())
            .setNegativeButton("Study Again", (dialog, which) -> restartStudySession())
            .setCancelable(false)
            .show();
    }

    private void restartStudySession() {
        currentCardIndex = 0;
        correctAnswers = 0;
        wrongAnswers = 0;
        correctCount.setText("✓ 0");
        wrongCount.setText("❌ 0");
        Collections.shuffle(flashcards);
        displayCurrentCard();
    }
}
