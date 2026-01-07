// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SharedPreferences preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);
        
        // Check if user is logged in
        if (preferences.getBoolean("isLoggedIn", false)) {
            // Go to Home
            startActivity(new Intent(MainActivity.this, HomeActivity.class));
        } else {
            // Go to Login
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }
        
        finish();
    }
}
