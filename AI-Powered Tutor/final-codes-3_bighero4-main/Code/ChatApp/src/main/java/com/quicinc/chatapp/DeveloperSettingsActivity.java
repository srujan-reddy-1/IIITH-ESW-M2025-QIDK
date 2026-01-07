// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class DeveloperSettingsActivity extends AppCompatActivity {

    private static final String TAG = "DeveloperSettings";
    
    // UI components
    private TextView currentModeText;
    private TextView modeDescriptionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_settings);

        initializeViews();
        updateUI();
    }

    private void initializeViews() {
        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Developer Settings");
        }

        // Initialize UI components
        currentModeText = findViewById(R.id.current_mode_text);
        modeDescriptionText = findViewById(R.id.mode_description_text);
    }

    private void updateUI() {
        // Update mode texts - always NPU mode
        currentModeText.setText("Current Mode: Local NPU (Offline)");
        modeDescriptionText.setText("All AI queries are processed locally on the device NPU. Works offline and provides fast, private responses.");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up if needed
    }
}
