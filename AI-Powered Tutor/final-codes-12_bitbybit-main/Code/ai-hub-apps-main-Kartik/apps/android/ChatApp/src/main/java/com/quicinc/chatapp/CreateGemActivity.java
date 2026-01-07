// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.HashSet;
import java.util.Set;

public class CreateGemActivity extends AppCompatActivity {

    private static final int PDF_PICK_CODE = 1000;
    private static final int IMAGE_PICK_CODE = 1001;
    
    private MaterialToolbar toolbar;
    private EditText gemNameEdit, gemDescriptionEdit;
    private Button createGemBtn;
    private CardView uploadPdfCard, uploadImageCard, startChatCard, generateQuizCard;
    private SharedPreferences preferences;
    
    private String selectedPdfUri = null;
    private String selectedImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_gem);
        
        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Create New Gem");
        
        gemNameEdit = findViewById(R.id.gem_name_edit);
        gemDescriptionEdit = findViewById(R.id.gem_description_edit);
        createGemBtn = findViewById(R.id.create_gem_btn);
        uploadPdfCard = findViewById(R.id.upload_pdf_card);
        uploadImageCard = findViewById(R.id.upload_image_card);
        startChatCard = findViewById(R.id.start_chat_card);
        generateQuizCard = findViewById(R.id.generate_quiz_card);
        
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        uploadPdfCard.setOnClickListener(v -> pickPdfFile());
        uploadImageCard.setOnClickListener(v -> pickImageFile());
        
        startChatCard.setOnClickListener(v -> {
            if (validateGemData()) {
                saveGemAndStartChat();
            }
        });
        
        generateQuizCard.setOnClickListener(v -> {
            if (validateGemData()) {
                saveGemAndGenerateQuiz();
            }
        });
        
        createGemBtn.setOnClickListener(v -> {
            if (validateGemData()) {
                saveGem();
                finish();
            }
        });
    }
    
    private void pickPdfFile() {
        Intent intent = new Intent();
        intent.setType("application/pdf");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select PDF"), PDF_PICK_CODE);
    }
    
    private void pickImageFile() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), IMAGE_PICK_CODE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == PDF_PICK_CODE) {
                    selectedPdfUri = uri.toString();
                    Toast.makeText(this, "PDF selected", Toast.LENGTH_SHORT).show();
                } else if (requestCode == IMAGE_PICK_CODE) {
                    selectedImageUri = uri.toString();
                    Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private boolean validateGemData() {
        String name = gemNameEdit.getText().toString().trim();
        if (name.isEmpty()) {
            gemNameEdit.setError("Gem name is required");
            return false;
        }
        return true;
    }
    
    private void saveGem() {
        String gemName = gemNameEdit.getText().toString().trim();
        String gemDescription = gemDescriptionEdit.getText().toString().trim();
        
        // Save gem data to SharedPreferences
        Set<String> existingGems = preferences.getStringSet("user_gems", new HashSet<>());
        String gemData = gemName + "|" + gemDescription + "|" + 
                        (selectedPdfUri != null ? selectedPdfUri : "") + "|" + 
                        (selectedImageUri != null ? selectedImageUri : "");
        
        existingGems.add(gemData);
        preferences.edit()
                .putStringSet("user_gems", existingGems)
                .putInt("gems_created_count", existingGems.size())
                .apply();
        
        Toast.makeText(this, "Gem created successfully!", Toast.LENGTH_SHORT).show();
    }
    
    private void saveGemAndStartChat() {
        saveGem();
        
        Intent intent = new Intent(CreateGemActivity.this, Conversation.class);
        intent.putExtra("mode", "chat");
        intent.putExtra("gem_name", gemNameEdit.getText().toString().trim());
        if (selectedPdfUri != null) {
            intent.putExtra("pdf_uri", selectedPdfUri);
        }
        if (selectedImageUri != null) {
            intent.putExtra("image_uri", selectedImageUri);
        }
        startActivity(intent);
        finish();
    }
    
    private void saveGemAndGenerateQuiz() {
        if (selectedPdfUri == null) {
            Toast.makeText(this, "PDF is required for quiz generation", Toast.LENGTH_SHORT).show();
            return;
        }
        
        saveGem();
        
        Intent intent = new Intent(CreateGemActivity.this, QuizActivity.class);
        intent.putExtra("pdf_uri", selectedPdfUri);
        intent.putExtra("gem_name", gemNameEdit.getText().toString().trim());
        startActivity(intent);
        finish();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}