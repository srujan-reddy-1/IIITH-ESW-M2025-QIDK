// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * QuizDetailActivity - Shows detailed results for a single quiz
 */
public class QuizDetailActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView summaryText, infoText;
    private LinearLayout questionsContainer;
    private QuizResult quizResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_detail);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        summaryText = findViewById(R.id.quiz_summary_text);
        infoText = findViewById(R.id.quiz_info_text);
        questionsContainer = findViewById(R.id.questions_container);

        // Load quiz data
        String quizData = getIntent().getStringExtra("quiz_data");
        if (quizData != null) {
            quizResult = QuizResult.fromStorageString(quizData);
            if (quizResult != null) {
                displayQuizDetails();
            }
        }
    }

    private void displayQuizDetails() {
        // Set summary
        summaryText.setText(String.format("Score: %d/%d (%.1f%%) - Grade: %s",
                quizResult.score, quizResult.totalQuestions, quizResult.getPercentage(), quizResult.getGrade()));
        infoText.setText(String.format("Difficulty: %s\nDate: %s",
                quizResult.difficulty, quizResult.timestamp));

        // Display each question with result
        for (int i = 0; i < quizResult.questionResults.size(); i++) {
            QuizResult.QuestionResult qr = quizResult.questionResults.get(i);
            addQuestionCard(i + 1, qr);
        }
    }

    private void addQuestionCard(int questionNumber, QuizResult.QuestionResult qr) {
        // Create card view
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 32);
        card.setLayoutParams(cardParams);
        card.setRadius(24f);
        card.setCardElevation(8f);

        // Create inner layout
        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding(40, 40, 40, 40);
        
        // Set background based on correctness
        if (qr.isCorrect) {
            innerLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark, null));
        } else {
            innerLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark, null));
        }

        // Question number and correctness indicator
        TextView headerText = new TextView(this);
        headerText.setText(String.format("Question %d - %s",
                questionNumber, qr.isCorrect ? "✓ Correct" : "✗ Incorrect"));
        headerText.setTextSize(18);
        headerText.setTextColor(Color.WHITE);
        headerText.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        headerText.setGravity(Gravity.CENTER);
        headerText.setPadding(0, 0, 0, 24);
        innerLayout.addView(headerText);

        // Question text
        TextView questionText = new TextView(this);
        questionText.setText(qr.question);
        questionText.setTextSize(16);
        questionText.setTextColor(Color.WHITE);
        questionText.setPadding(0, 0, 0, 24);
        innerLayout.addView(questionText);

        // Options
        addOptionView(innerLayout, "A) " + qr.optionA, "A", qr);
        addOptionView(innerLayout, "B) " + qr.optionB, "B", qr);
        addOptionView(innerLayout, "C) " + qr.optionC, "C", qr);
        addOptionView(innerLayout, "D) " + qr.optionD, "D", qr);

        card.addView(innerLayout);
        questionsContainer.addView(card);
    }

    private void addOptionView(LinearLayout parent, String text, String option, QuizResult.QuestionResult qr) {
        TextView optionText = new TextView(this);
        optionText.setText(text);
        optionText.setTextSize(14);
        optionText.setPadding(24, 16, 24, 16);

        // Highlight user's answer and correct answer
        if (option.equals(qr.correctAnswer)) {
            optionText.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light, null));
            optionText.setTextColor(Color.BLACK);
            optionText.setText(text + " ✓ (Correct Answer)");
        } else if (option.equals(qr.userAnswer)) {
            optionText.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light, null));
            optionText.setTextColor(Color.BLACK);
            optionText.setText(text + " ✗ (Your Answer)");
        } else {
            optionText.setTextColor(Color.WHITE);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        optionText.setLayoutParams(params);

        parent.addView(optionText);
    }
}
