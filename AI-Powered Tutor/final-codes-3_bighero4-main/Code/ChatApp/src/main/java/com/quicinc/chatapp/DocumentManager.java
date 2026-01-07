// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * DocumentManager - Singleton class to manage PDF documents and their text content
 */
public class DocumentManager {
    private static final String TAG = "DocumentManager";
    private static final String PREFS_NAME = "DocumentsPrefs";
    private static final String KEY_DOCUMENTS = "documents_list";
    private static final String KEY_SELECTED_DOCUMENT = "selected_document_id";

    private static DocumentManager instance;
    private List<Document> documents;
    private String selectedDocumentId;
    private Context context;
    private Gson gson;

    private DocumentManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.documents = new ArrayList<>();
        loadDocuments();
    }

    public static synchronized DocumentManager getInstance(Context context) {
        if (instance == null) {
            instance = new DocumentManager(context);
        }
        return instance;
    }

    public static DocumentManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DocumentManager not initialized. Call getInstance(context) first.");
        }
        return instance;
    }

    /**
     * Update metadata for an existing document and persist immediately
     */
    public synchronized void updateDocument(Document updatedDocument) {
        if (updatedDocument == null) {
            return;
        }

        for (int i = 0; i < documents.size(); i++) {
            Document existing = documents.get(i);
            if (existing.getId().equals(updatedDocument.getId())) {
                documents.set(i, updatedDocument);
                saveDocuments();
                return;
            }
        }

        // If document was not part of the list (edge case), add it now
        addDocument(updatedDocument);
    }

    /**
     * Persist current in-memory document list without altering entries
     */
    public synchronized void persistDocuments() {
        saveDocuments();
    }

    /**
     * Add a new document to the manager
     * If a document with the same display name exists, it will be replaced
     */
    public void addDocument(Document document) {
        if (document != null) {
            // Check if a document with the same display name already exists
            Document existingDoc = findDocumentByDisplayName(document.getDisplayName());
            if (existingDoc != null) {
                Log.i(TAG, "Replacing existing document with same name: " + document.getDisplayName());
                // Remove the old document (including its context data)
                removeDocument(existingDoc.getId());
            }
            
            // Remove any existing document with the same ID (shouldn't happen but just in case)
            removeDocument(document.getId());
            
            documents.add(document);
            saveDocuments();
            
            // Update document count in progress tracker
            ProgressTracker.updateDocumentCount(context, documents.size());
            
            Log.i(TAG, "Added document: " + document.getDisplayName());
        }
    }
    
    /**
     * Find a document by its display name
     */
    private Document findDocumentByDisplayName(String displayName) {
        for (Document doc : documents) {
            if (doc.getDisplayName().equals(displayName)) {
                return doc;
            }
        }
        return null;
    }

    /**
     * Remove a document by ID
     */
    public void removeDocument(String documentId) {
        documents.removeIf(doc -> doc.getId().equals(documentId));
        if (selectedDocumentId != null && selectedDocumentId.equals(documentId)) {
            selectedDocumentId = null;
        }
        saveDocuments();
        
        // Update document count in progress tracker
        ProgressTracker.updateDocumentCount(context, documents.size());
    }

    /**
     * Delete a document by ID (alias for removeDocument)
     */
    public void deleteDocument(String documentId) {
        removeDocument(documentId);
    }

    /**
     * Get all documents
     */
    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents);
    }

    /**
     * Get a document by ID
     */
    public Document getDocument(String documentId) {
        return documents.stream()
                .filter(doc -> doc.getId().equals(documentId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Set the selected document for chat context
     */
    public void setSelectedDocument(String documentId) {
        this.selectedDocumentId = documentId;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SELECTED_DOCUMENT, documentId).apply();
        Log.i(TAG, "Selected document: " + documentId);
    }

    /**
     * Get the currently selected document
     */
    public Document getSelectedDocument() {
        if (selectedDocumentId == null) {
            return null;
        }
        return getDocument(selectedDocumentId);
    }

    /**
     * Clear the selected document
     */
    public void clearSelectedDocument() {
        setSelectedDocument(null);
    }
    
    /**
     * Clear the selected document without saving to preferences (for fresh session start)
     */
    public void clearSelectedDocumentTemporarily() {
        this.selectedDocumentId = null;
        Log.i(TAG, "Temporarily cleared selected document for fresh session");
    }

    /**
     * Check if a document is currently selected
     */
    public boolean hasSelectedDocument() {
        return selectedDocumentId != null && getSelectedDocument() != null;
    }

    /**
     * Get context for AI prompt based on selected document
     */
    public String getContextForAI() {
        Document selectedDoc = getSelectedDocument();
        if (selectedDoc == null) {
            return "";
        }

        // This method is kept for compatibility but context creation 
        // is now handled in Conversation.java for better control
        return "";
    }

    /**
     * Save documents to SharedPreferences
     */
    private void saveDocuments() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String documentsJson = gson.toJson(documents);
        prefs.edit().putString(KEY_DOCUMENTS, documentsJson).apply();
        Log.d(TAG, "Saved " + documents.size() + " documents");
    }

    /**
     * Load documents from SharedPreferences
     */
    private void loadDocuments() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String documentsJson = prefs.getString(KEY_DOCUMENTS, "[]");
        selectedDocumentId = prefs.getString(KEY_SELECTED_DOCUMENT, null);

        try {
            Type listType = new TypeToken<List<Document>>(){}.getType();
            documents = gson.fromJson(documentsJson, listType);
            if (documents == null) {
                documents = new ArrayList<>();
            }
            Log.d(TAG, "Loaded " + documents.size() + " documents");
        } catch (Exception e) {
            Log.e(TAG, "Error loading documents: " + e.getMessage());
            documents = new ArrayList<>();
        }
    }

    /**
     * Clear all documents
     */
    public void clearAllDocuments() {
        documents.clear();
        selectedDocumentId = null;
        saveDocuments();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_SELECTED_DOCUMENT).apply();
        Log.i(TAG, "Cleared all documents");
    }

    /**
     * Get document count
     */
    public int getDocumentCount() {
        return documents.size();
    }
    
    /**
     * Clean up duplicate documents based on file name and content
     */
    public void cleanupDuplicates() {
        List<Document> uniqueDocuments = new ArrayList<>();
        List<String> seenNames = new ArrayList<>();
        
        for (Document doc : documents) {
            String key = doc.getFileName() + "_" + (doc.getTextContent() != null ? doc.getTextContent().hashCode() : 0);
            if (!seenNames.contains(key)) {
                seenNames.add(key);
                uniqueDocuments.add(doc);
            }
        }
        
        if (uniqueDocuments.size() != documents.size()) {
            documents.clear();
            documents.addAll(uniqueDocuments);
            saveDocuments();
            Log.i(TAG, "Cleaned up duplicates. Remaining documents: " + documents.size());
        }
    }
}