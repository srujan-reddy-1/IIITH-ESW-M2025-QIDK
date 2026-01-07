// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QuizHistoryActivity - Displays all past quiz results for the current user
 */
public class QuizHistoryActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView historyRecycler;
    private TextView emptyHistoryText;
    private SharedPreferences preferences;
    private List<QuizResult> quizResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_history);

        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        historyRecycler = findViewById(R.id.quiz_history_recycler);
        emptyHistoryText = findViewById(R.id.empty_history_text);

        loadQuizHistory();
    }

    private void loadQuizHistory() {
        String currentUser = preferences.getString("current_user_name", "default_user");
        String key = "quiz_history_" + currentUser;

        Set<String> quizStrings = preferences.getStringSet(key, new HashSet<>());
        quizResults = new ArrayList<>();

        for (String quizString : quizStrings) {
            QuizResult result = QuizResult.fromStorageString(quizString);
            if (result != null) {
                quizResults.add(result);
            }
        }

        // Sort by timestamp (most recent first)
        Collections.sort(quizResults, (a, b) -> b.timestamp.compareTo(a.timestamp));

        if (quizResults.isEmpty()) {
            emptyHistoryText.setVisibility(View.VISIBLE);
            historyRecycler.setVisibility(View.GONE);
        } else {
            emptyHistoryText.setVisibility(View.GONE);
            historyRecycler.setVisibility(View.VISIBLE);
            setupRecyclerView();
        }
    }

    private void setupRecyclerView() {
        historyRecycler.setLayoutManager(new LinearLayoutManager(this));
        QuizHistoryAdapter adapter = new QuizHistoryAdapter(this, quizResults, quizResult -> {
            // Open detail view when quiz is clicked
            Intent intent = new Intent(QuizHistoryActivity.this, QuizDetailActivity.class);
            intent.putExtra("quiz_index", quizResults.indexOf(quizResult));
            intent.putExtra("quiz_data", quizResult.toStorageString());
            startActivity(intent);
        });
        historyRecycler.setAdapter(adapter);
    }
}
