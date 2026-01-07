// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView welcomeText;
    private CardView chatCard, pdfCard, imageCard, quizCard, flashcardCard, createGemBtn;
    private RecyclerView gemsRecyclerView;
    private TextView gemsEmptyText;
    private TextView chatSessionsCount, documentsCount, gemsCount;
    private SharedPreferences preferences;
    private GemsAdapter gemsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        chatCard = findViewById(R.id.chat_card);
        pdfCard = findViewById(R.id.pdf_card);
        imageCard = findViewById(R.id.image_card);
        quizCard = findViewById(R.id.quiz_card);
        flashcardCard = findViewById(R.id.flashcard_card);
        createGemBtn = findViewById(R.id.create_gem_btn);
        gemsRecyclerView = findViewById(R.id.gems_recycler_view);
        gemsEmptyText = findViewById(R.id.gems_empty_text);
        chatSessionsCount = findViewById(R.id.chat_sessions_count);
        documentsCount = findViewById(R.id.documents_count);
        gemsCount = findViewById(R.id.gems_count);
        welcomeText = findViewById(R.id.welcome_text);
        
        String userName = preferences.getString("current_user_name", "User");
        welcomeText.setText("Welcome, " + userName);
        
        // Setup RecyclerView for Gems
        gemsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        gemsAdapter = new GemsAdapter(this, new ArrayList<>());
        gemsRecyclerView.setAdapter(gemsAdapter);
        
        // Load and display performance metrics
        loadPerformanceMetrics();
        loadSavedGems();
        
        chatCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, Conversation.class);
            intent.putExtra("mode", "chat");
            startActivity(intent);
        });
        
        pdfCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, Conversation.class);
            intent.putExtra("mode", "pdf");
            startActivity(intent);
        });
        
        imageCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, Conversation.class);
            intent.putExtra("mode", "image");
            startActivity(intent);
        });
        
        quizCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, QuizActivity.class);
            startActivity(intent);
        });
        
        flashcardCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, FlashcardActivity.class);
            startActivity(intent);
        });
        
        createGemBtn.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, CreateGemActivity.class);
            startActivity(intent);
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to home
        loadPerformanceMetrics();
        loadSavedGems();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            performLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void performLogout() {
        // Get current user before clearing
        String currentUser = preferences.getString("current_user_name", "");
        
        // Clear login state and current user
        preferences.edit()
                .putBoolean("isLoggedIn", false)
                .remove("current_user_name")
                .apply();
        
        // Note: We keep user-specific data (flashcards, quiz history) tied to username
        // so it persists if they log in again
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        
        startActivity(new Intent(HomeActivity.this, LoginActivity.class));
        finish();
    }
    
    private void loadPerformanceMetrics() {
        // Load chat sessions count
        int chatSessions = preferences.getInt("chat_sessions_count", 0);
        chatSessionsCount.setText(String.valueOf(chatSessions));
        
        // Load documents count
        int documents = preferences.getInt("documents_analyzed_count", 0);
        documentsCount.setText(String.valueOf(documents));
        
        // Load gems count
        int gems = preferences.getInt("gems_created_count", 0);
        gemsCount.setText(String.valueOf(gems));
    }
    
    private void loadSavedGems() {
        Set<String> gemStrings = preferences.getStringSet("user_gems", new HashSet<>());
        List<GemsAdapter.Gem> gems = new ArrayList<>();
        
        for (String gemString : gemStrings) {
            gems.add(GemsAdapter.Gem.fromString(gemString));
        }
        
        if (gems.isEmpty()) {
            gemsEmptyText.setVisibility(TextView.VISIBLE);
            gemsRecyclerView.setVisibility(RecyclerView.GONE);
        } else {
            gemsEmptyText.setVisibility(TextView.GONE);
            gemsRecyclerView.setVisibility(RecyclerView.VISIBLE);
            gemsAdapter.updateGems(gems);
        }
    }
}
