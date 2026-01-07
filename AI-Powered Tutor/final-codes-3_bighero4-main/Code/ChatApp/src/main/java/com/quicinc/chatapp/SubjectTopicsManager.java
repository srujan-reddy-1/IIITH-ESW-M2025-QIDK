// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages per-subject topic index files for generated contexts
 */
public class SubjectTopicsManager {
    private static final String TAG = "SubjectTopicsManager";
    private static final String CONTEXT_FOLDER = "context_library";
    private static final String TOPICS_FILE_NAME = "topics.json";

    private final Context context;
    private final Gson gson;

    public SubjectTopicsManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
    }

    /**
     * Represents one topic entry and the document that covers it
     */
    public static class TopicEntry {
        public String topic;
        public String documentId;
        public String documentName;
        public String documentPath;
        public long updatedAt;
    }

    private File ensureSubjectDirectory(String subjectName) {
        String safeName = subjectName == null ? "subject" : subjectName.replaceAll("[^a-zA-Z0-9_-]", "_");
        File baseDir = new File(context.getFilesDir(), CONTEXT_FOLDER);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        File subjectDir = new File(baseDir, safeName);
        if (!subjectDir.exists()) {
            subjectDir.mkdirs();
        }
        return subjectDir;
    }

    public File getTopicsFile(String subjectName) {
        return new File(ensureSubjectDirectory(subjectName), TOPICS_FILE_NAME);
    }

    public synchronized List<TopicEntry> getTopicsForSubject(String subjectName) {
        File file = getTopicsFile(subjectName);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<TopicEntry>>(){}.getType();
            List<TopicEntry> entries = gson.fromJson(reader, listType);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read topics file", e);
            return new ArrayList<>();
        }
    }

    public synchronized void replaceTopicsForDocument(String subjectName, Document document, List<String> topics) {
        List<TopicEntry> entries = getTopicsForSubject(subjectName);

        // Remove existing entries for this document
        Iterator<TopicEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            TopicEntry entry = iterator.next();
            if (entry.documentId != null && entry.documentId.equals(document.getId())) {
                iterator.remove();
            }
        }

        long timestamp = System.currentTimeMillis();
        if (topics != null) {
            for (String topic : topics) {
                if (TextUtils.isEmpty(topic)) {
                    continue;
                }
                TopicEntry entry = new TopicEntry();
                entry.topic = topic.trim();
                entry.documentId = document.getId();
                entry.documentName = document.getDisplayName();
                entry.documentPath = document.getFilePath();
                entry.updatedAt = timestamp;
                entries.add(entry);
            }
        }

        saveEntries(subjectName, entries);
    }

    public synchronized void deleteTopicsForDocument(String subjectName, String documentId) {
        List<TopicEntry> entries = getTopicsForSubject(subjectName);
        if (entries.isEmpty()) {
            return;
        }
        boolean modified = false;
        Iterator<TopicEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            TopicEntry entry = iterator.next();
            // Delete by document ID or document name (for safety)
            if (entry.documentId != null && entry.documentId.equals(documentId)) {
                iterator.remove();
                modified = true;
            } else if (entry.documentName != null && entry.documentName.equals(documentId)) {
                // Also check if documentId parameter is actually a document name
                iterator.remove();
                modified = true;
            }
        }
        if (modified) {
            saveEntries(subjectName, entries);
            Log.d(TAG, "Deleted topics for document: " + documentId);
        }
    }

    public String buildDisplayList(String subjectName) {
        List<TopicEntry> entries = getTopicsForSubject(subjectName);
        if (entries.isEmpty()) {
            return "No topics generated yet";
        }
        StringBuilder builder = new StringBuilder();
        for (TopicEntry entry : entries) {
            builder.append("• ")
                   .append(entry.topic)
                   .append(" — ")
                   .append(entry.documentName != null ? entry.documentName : "Unknown PDF")
                   .append("\n");
        }
        return builder.toString().trim();
    }

    private void saveEntries(String subjectName, List<TopicEntry> entries) {
        File file = getTopicsFile(subjectName);
        try (FileWriter writer = new FileWriter(file, false)) {
            gson.toJson(entries, writer);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save topics file", e);
        }
    }
}
