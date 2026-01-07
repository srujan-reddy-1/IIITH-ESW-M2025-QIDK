// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailInput, passwordInput;
    private Button loginButton;
    private TextView registerLink;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        
        // Check if user is already logged in
        if (preferences.getBoolean("isLoggedIn", false)) {
            navigateToHome();
            return;
        }
        
        setContentView(R.layout.activity_login);
        
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        registerLink = findViewById(R.id.register_link);
        
        loginButton.setOnClickListener(v -> performLogin());
        registerLink.setOnClickListener(v -> navigateToRegister());
    }
    
    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        android.util.Log.d("LoginActivity", "Login attempt - Email: " + email);
        
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        
        // Simple validation - check if user exists in SharedPreferences
        String savedEmail = preferences.getString("user_email", "");
        String savedPassword = preferences.getString("user_password", "");
        
        android.util.Log.d("LoginActivity", "Saved credentials - Email: " + savedEmail + ", Has password: " + !savedPassword.isEmpty());
        
        if (email.equals(savedEmail) && password.equals(savedPassword)) {
            // Login successful
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("isLoggedIn", true);
            editor.putString("current_user_name", preferences.getString("user_name", "User"));
            editor.commit(); // Use commit() to ensure immediate save
            
            android.util.Log.d("LoginActivity", "Login successful");
            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
            navigateToHome();
        } else {
            android.util.Log.d("LoginActivity", "Login failed - credentials don't match");
            Toast.makeText(this, "Invalid credentials. Please register if you're new.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void navigateToRegister() {
        startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
    }
    
    private void navigateToHome() {
        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
        finish();
    }
}
