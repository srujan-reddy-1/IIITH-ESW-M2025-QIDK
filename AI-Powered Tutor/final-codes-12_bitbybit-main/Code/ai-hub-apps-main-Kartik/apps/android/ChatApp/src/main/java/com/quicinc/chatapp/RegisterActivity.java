// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    private Button registerButton;
    private TextView loginLink;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        
        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        
        nameInput = findViewById(R.id.name_input);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        registerButton = findViewById(R.id.register_button);
        loginLink = findViewById(R.id.login_link);
        
        registerButton.setOnClickListener(v -> performRegister());
        loginLink.setOnClickListener(v -> {
            finish();
        });
    }
    
    private void performRegister() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();
        
        android.util.Log.d("RegisterActivity", "Register attempt - Name: " + name + ", Email: " + email);
        
        if (TextUtils.isEmpty(name)) {
            nameInput.setError("Name is required");
            return;
        }
        
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        
        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords don't match");
            return;
        }
        
        // Save user data
        try {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("user_name", name);
            editor.putString("user_email", email);
            editor.putString("user_password", password);
            editor.putBoolean("isLoggedIn", true);
            editor.putString("current_user_name", name);
            boolean saved = editor.commit(); // Use commit() instead of apply() to ensure it's saved immediately
            
            android.util.Log.d("RegisterActivity", "Preferences saved: " + saved);
            android.util.Log.d("RegisterActivity", "Saved - Name: " + preferences.getString("user_name", "NULL") + 
                                                  ", Email: " + preferences.getString("user_email", "NULL") + 
                                                  ", Logged in: " + preferences.getBoolean("isLoggedIn", false));
            
            Toast.makeText(this, "Registration successful! Welcome " + name, Toast.LENGTH_LONG).show();
            
            Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            android.util.Log.e("RegisterActivity", "Error saving preferences", e);
            Toast.makeText(this, "Error saving registration: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
