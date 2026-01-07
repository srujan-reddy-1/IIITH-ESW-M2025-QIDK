package com.quicinc.chatapp;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_DARK_MODE = "darkMode";
    private static final String KEY_NOTIFICATIONS = "notifications";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize dark mode switch
        SwitchCompat darkModeSwitch = findViewById(R.id.dark_mode_switch);
        if (darkModeSwitch != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);

            // Debug logging
            android.util.Log.d("SettingsActivity", "Found darkModeSwitch, isDarkMode: " + isDarkMode);

            // Set the switch state to match saved preference
            darkModeSwitch.setChecked(isDarkMode);
            
            Log.d("SettingsActivity", "Initial dark mode state: " + isDarkMode);

            darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Log.d("SettingsActivity", "Switch toggled to: " + isChecked);
                
                // Save preference immediately
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_DARK_MODE, isChecked);
                editor.apply();
                
                // Apply theme
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                
                Log.d("SettingsActivity", "Theme applied, recreating activity");
                recreate();
            });
        }

        // Initialize notifications switch
        SwitchCompat notificationsSwitch = findViewById(R.id.notifications_switch);
        if (notificationsSwitch != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isNotificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true);
            notificationsSwitch.setChecked(isNotificationsEnabled);

            notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_NOTIFICATIONS, isChecked);
                editor.apply();
                // Add logic to enable/disable notifications if needed
            });
        }

        // Initialize About Phone dropdown
        LinearLayout aboutPhoneHeader = findViewById(R.id.about_phone_header);
        LinearLayout aboutPhoneDetails = findViewById(R.id.about_phone_details);
        ImageView aboutPhoneArrow = findViewById(R.id.about_phone_arrow);

        // Populate device information
        TextView deviceNameText = aboutPhoneDetails.findViewById(R.id.device_name_text);
        TextView androidVersionText = aboutPhoneDetails.findViewById(R.id.android_version_text);
        TextView modelText = aboutPhoneDetails.findViewById(R.id.model_text);

        if (deviceNameText != null) {
            deviceNameText.setText("Device Name: " + Build.DEVICE);
        }
        if (androidVersionText != null) {
            androidVersionText.setText("Android Version: " + Build.VERSION.RELEASE);
        }
        if (modelText != null) {
            modelText.setText("Model: " + Build.MODEL);
        }

        if (aboutPhoneHeader != null && aboutPhoneDetails != null && aboutPhoneArrow != null) {
            aboutPhoneHeader.setOnClickListener(v -> {
                Log.d("SettingsActivity", "About Phone header clicked");
                if (aboutPhoneDetails.getVisibility() == View.VISIBLE) {
                    Log.d("SettingsActivity", "Hiding phone details");
                    aboutPhoneDetails.setVisibility(View.GONE);
                    aboutPhoneArrow.setRotation(0f);
                } else {
                    Log.d("SettingsActivity", "Showing phone details");
                    aboutPhoneDetails.setVisibility(View.VISIBLE);
                    aboutPhoneArrow.setRotation(180f);
                }
            });
        } else {
            Log.e("SettingsActivity", "About Phone views not found - header: " + aboutPhoneHeader + 
                  ", details: " + aboutPhoneDetails + ", arrow: " + aboutPhoneArrow);
        }
    }

    private void applyTheme(boolean isDarkMode) {
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        int newMode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (currentMode != newMode) {
            AppCompatDelegate.setDefaultNightMode(newMode);
            recreate();
        }
    }
}