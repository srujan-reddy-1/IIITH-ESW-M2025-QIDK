package com.quicinc.chatapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for selecting documents to generate quizzes or flashcards from
 */
public class DocumentSelectionDialog extends Dialog {
    
    private List<Document> documents;
    private String title;
    private String confirmButtonText;
    private OnDocumentsSelectedListener listener;
    private List<Document> selectedDocuments;
    
    public interface OnDocumentsSelectedListener {
        void onDocumentsSelected(List<Document> selectedDocuments);
    }
    
    public DocumentSelectionDialog(Context context, List<Document> documents, 
                                   String title, String confirmButtonText,
                                   OnDocumentsSelectedListener listener) {
        super(context);
        this.documents = documents;
        this.title = title;
        this.confirmButtonText = confirmButtonText;
        this.listener = listener;
        this.selectedDocuments = new ArrayList<>();
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_document_selection);
        
        setupViews();
    }
    
    private void setupViews() {
        TextView titleView = findViewById(R.id.dialog_title);
        TextView subtitleView = findViewById(R.id.dialog_subtitle);
        LinearLayout checkboxContainer = findViewById(R.id.checkbox_container);
        Button confirmButton = findViewById(R.id.confirm_button);
        Button cancelButton = findViewById(R.id.cancel_button);
        
        // Set title
        titleView.setText(title);
        
        // Set subtitle based on document count
        String subtitle = documents.size() == 1 
            ? "Select document to use:" 
            : "Select documents to use (you can select multiple):";
        subtitleView.setText(subtitle);
        
        // Set confirm button text
        confirmButton.setText(confirmButtonText);
        
        // Create Material checkboxes for each document
        for (Document doc : documents) {
            // Create a MaterialCheckBox with proper styling
            com.google.android.material.checkbox.MaterialCheckBox checkbox = 
                new com.google.android.material.checkbox.MaterialCheckBox(getContext());
            
            checkbox.setText(doc.getDisplayName());
            checkbox.setTextSize(16);
            checkbox.setTextColor(getContext().getColor(android.R.color.tab_indicator_text));
            
            // Add padding for better spacing
            int paddingDp = (int) (12 * getContext().getResources().getDisplayMetrics().density);
            checkbox.setPadding(paddingDp, paddingDp * 2, paddingDp, paddingDp * 2);
            
            // Set font
            checkbox.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            
            // Add click listener
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedDocuments.contains(doc)) {
                        selectedDocuments.add(doc);
                    }
                } else {
                    selectedDocuments.remove(doc);
                }
                
                // Update confirm button state
                confirmButton.setEnabled(!selectedDocuments.isEmpty());
            });
            
            checkboxContainer.addView(checkbox);
        }
        
        // Initially disable confirm button
        confirmButton.setEnabled(false);
        
        // Confirm button click
        confirmButton.setOnClickListener(v -> {
            if (!selectedDocuments.isEmpty() && listener != null) {
                listener.onDocumentsSelected(new ArrayList<>(selectedDocuments));
                dismiss();
            }
        });
        
        // Cancel button click
        cancelButton.setOnClickListener(v -> dismiss());
    }
}
