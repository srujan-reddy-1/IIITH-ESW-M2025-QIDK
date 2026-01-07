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
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FlashcardActivity - Main flashcard hub with options to create flashcards from PDF or manually
 */
public class FlashcardActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private CardView pdfFlashcardCard;
    private CardView manualFlashcardCard;
    private RecyclerView flashcardSetsRecycler;
    private TextView emptySetsText;
    private SharedPreferences preferences;
    private List<FlashcardSet> flashcardSets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);

        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        flashcardSets = new ArrayList<>();

        initializeViews();
        setupToolbar();
        setupClickListeners();
        loadFlashcardSets();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        pdfFlashcardCard = findViewById(R.id.pdf_flashcard_card);
        manualFlashcardCard = findViewById(R.id.manual_flashcard_card);
        flashcardSetsRecycler = findViewById(R.id.flashcard_sets_recycler);
        emptySetsText = findViewById(R.id.empty_sets_text);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        // PDF Flashcard Generation
        pdfFlashcardCard.setOnClickListener(v -> {
            Intent intent = new Intent(FlashcardActivity.this, FlashcardPdfGeneratorActivity.class);
            startActivity(intent);
        });

        // Manual Flashcard Creation
        manualFlashcardCard.setOnClickListener(v -> {
            Intent intent = new Intent(FlashcardActivity.this, CreateFlashcardSetActivity.class);
            startActivity(intent);
        });
    }

    private void loadFlashcardSets() {
        // Load saved flashcard sets from SharedPreferences (user-specific)
        String currentUser = preferences.getString("current_user_name", "default_user");
        String key = "flashcard_sets_" + currentUser;
        
        android.util.Log.d("FlashcardActivity", "Loading flashcards for user: " + currentUser);
        
        Set<String> setStrings = preferences.getStringSet(key, new HashSet<>());
        android.util.Log.d("FlashcardActivity", "Found " + (setStrings != null ? setStrings.size() : 0) + " saved sets");
        
        flashcardSets.clear();

        if (setStrings != null) {
            int loadedCount = 0;
            for (String setString : setStrings) {
                android.util.Log.d("FlashcardActivity", "Parsing set string (length: " + setString.length() + ")");
                FlashcardSet set = FlashcardSet.fromStorageString(setString);
                if (set != null) {
                    flashcardSets.add(set);
                    loadedCount++;
                    android.util.Log.d("FlashcardActivity", "Loaded set: " + set.getTitle() + " with " + set.getFlashcardCount() + " cards");
                } else {
                    android.util.Log.e("FlashcardActivity", "Failed to parse flashcard set from string");
                }
            }
            android.util.Log.d("FlashcardActivity", "Successfully loaded " + loadedCount + " flashcard sets");
        }

        // Show/hide empty state
        if (flashcardSets.isEmpty()) {
            emptySetsText.setVisibility(View.VISIBLE);
            flashcardSetsRecycler.setVisibility(View.GONE);
        } else {
            emptySetsText.setVisibility(View.GONE);
            flashcardSetsRecycler.setVisibility(View.VISIBLE);
            setupRecyclerView();
        }
    }

    private void setupRecyclerView() {
        flashcardSetsRecycler.setLayoutManager(new LinearLayoutManager(this));
        FlashcardSetsAdapter adapter = new FlashcardSetsAdapter(this, flashcardSets);
        flashcardSetsRecycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the list when returning from create/study activities
        loadFlashcardSets();
    }
}
