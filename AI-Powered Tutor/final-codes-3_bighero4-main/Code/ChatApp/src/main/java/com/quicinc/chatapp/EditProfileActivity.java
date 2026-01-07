// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText nameEditText, emailEditText, phoneEditText, preparingForEditText;
    private RadioGroup difficultyRadioGroup;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initializeViews();
        setupClickListeners();
        loadUserData();
    }

    private void initializeViews() {
        nameEditText = findViewById(R.id.name_edit_text);
        emailEditText = findViewById(R.id.email_edit_text);
        phoneEditText = findViewById(R.id.phone_edit_text);
        preparingForEditText = findViewById(R.id.preparing_for_edit_text);
        difficultyRadioGroup = findViewById(R.id.difficulty_radio_group);

        sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);
    }

    private void setupClickListeners() {
        Button saveButton = findViewById(R.id.save_button);

        // Back button removed

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveProfile());
        }
    }

    private void loadUserData() {
        // Load existing user data from SharedPreferences
        String name = sharedPreferences.getString("user_name", "Study Master");
        String email = sharedPreferences.getString("user_email", "");
        String phone = sharedPreferences.getString("user_phone", "");
        String preparingFor = sharedPreferences.getString("preparing_for", "");
        String difficultyLevel = sharedPreferences.getString("difficulty_level", "intermediate");

        nameEditText.setText(name);
        if (!TextUtils.isEmpty(email)) {
            emailEditText.setText(email);
        }
        if (!TextUtils.isEmpty(phone)) {
            phoneEditText.setText(phone);
        }
        if (!TextUtils.isEmpty(preparingFor)) {
            preparingForEditText.setText(preparingFor);
        }
        
        // Set difficulty level radio button
        if (difficultyLevel.equals("beginner")) {
            difficultyRadioGroup.check(R.id.difficulty_beginner);
        } else if (difficultyLevel.equals("advanced")) {
            difficultyRadioGroup.check(R.id.difficulty_advanced);
        } else {
            difficultyRadioGroup.check(R.id.difficulty_intermediate);
        }
    }

    private void saveProfile() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String preparingFor = preparingForEditText.getText().toString().trim();
        
        // Get selected difficulty level
        int selectedDifficultyId = difficultyRadioGroup.getCheckedRadioButtonId();
        String difficultyLevel = "intermediate"; // default
        if (selectedDifficultyId == R.id.difficulty_beginner) {
            difficultyLevel = "beginner";
        } else if (selectedDifficultyId == R.id.difficulty_advanced) {
            difficultyLevel = "advanced";
        }

        // Validate input
        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Name is required");
            nameEditText.requestFocus();
            return;
        }

        // Email is optional - only validate if provided
        if (!TextUtils.isEmpty(email) && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email address");
            emailEditText.requestFocus();
            return;
        }

        // Save to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("user_name", name);
        editor.putString("user_email", email);
        editor.putString("user_phone", phone);
        editor.putString("preparing_for", preparingFor);
        editor.putString("difficulty_level", difficultyLevel);
        editor.apply();

        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();

        // Return to profile with updated data
        Intent resultIntent = new Intent();
        resultIntent.putExtra("updated_name", name);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}

