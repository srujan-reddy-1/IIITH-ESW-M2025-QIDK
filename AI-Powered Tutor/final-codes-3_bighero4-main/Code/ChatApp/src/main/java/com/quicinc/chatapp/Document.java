// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Document model class to store PDF content and metadata
 */
public class Document implements Serializable {
    private String id;
    private String fileName;
    private String displayName;
    private String textContent;
    private Date dateAdded;
    private long fileSize;
    private String filePath;
    private String subjectName; // Track which subject this document belongs to
    private String contextSummary;
    private List<String> contextTopics;
    private long contextGeneratedAt;

    public Document() {
        this.dateAdded = new Date();
        this.contextTopics = new ArrayList<>();
    }

    public Document(String fileName, String textContent) {
        this();
        this.fileName = fileName;
        this.textContent = textContent;
        this.displayName = fileName;
        this.id = generateId();
        this.contextTopics = new ArrayList<>();
    }

    public Document(String id, String fileName, String displayName, String textContent, String filePath) {
        this();
        this.id = id;
        this.fileName = fileName;
        this.displayName = displayName;
        this.textContent = textContent;
        this.filePath = filePath;
    }

    private String generateId() {
        // Generate ID based on file name and content hash to avoid duplicates
        String baseString = fileName + "_" + (textContent != null ? textContent.hashCode() : 0);
        return "doc_" + Math.abs(baseString.hashCode());
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public Date getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getContextSummary() {
        return contextSummary;
    }

    public void setContextSummary(String contextSummary) {
        this.contextSummary = contextSummary;
    }

    public List<String> getContextTopics() {
        if (contextTopics == null) {
            contextTopics = new ArrayList<>();
        }
        return contextTopics;
    }

    public void setContextTopics(List<String> contextTopics) {
        if (contextTopics == null) {
            this.contextTopics = new ArrayList<>();
        } else {
            this.contextTopics = contextTopics;
        }
    }

    public long getContextGeneratedAt() {
        return contextGeneratedAt;
    }

    public void setContextGeneratedAt(long contextGeneratedAt) {
        this.contextGeneratedAt = contextGeneratedAt;
    }

    /**
     * Get a summary of the document content (first 200 characters)
     */
    public String getSummary() {
        if (textContent == null || textContent.trim().isEmpty()) {
            return "No content available";
        }
        String summary = textContent.trim();
        if (summary.length() > 200) {
            summary = summary.substring(0, 200) + "...";
        }
        return summary;
    }

    /**
     * Get word count of the document
     */
    public int getWordCount() {
        if (textContent == null || textContent.trim().isEmpty()) {
            return 0;
        }
        return textContent.trim().split("\\s+").length;
    }

    @Override
    public String toString() {
        return displayName + " (" + getWordCount() + " words)";
    }
}