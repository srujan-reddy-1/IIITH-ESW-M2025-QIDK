// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import java.io.Serializable;

/**
 * PDFChunk - Represents a chunk of a PDF document with its summary
 */
public class PDFChunk implements Serializable {
    private String chunkId;
    private String documentId;
    private int chunkIndex;
    private String fullText;
    private String summary;
    private int tokenCount;
    private int startPage;
    private int endPage;

    public PDFChunk(String documentId, int chunkIndex, String fullText, int startPage, int endPage) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.fullText = fullText;
        this.startPage = startPage;
        this.endPage = endPage;
        this.chunkId = documentId + "_chunk_" + chunkIndex;
        this.tokenCount = estimateTokenCount(fullText);
    }

    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters)
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }

    // Getters and Setters
    public String getChunkId() {
        return chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
        this.tokenCount = estimateTokenCount(fullText);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public int getStartPage() {
        return startPage;
    }

    public int getEndPage() {
        return endPage;
    }

    @Override
    public String toString() {
        return "PDFChunk{" +
                "chunkId='" + chunkId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", tokenCount=" + tokenCount +
                ", pages=" + startPage + "-" + endPage +
                ", hasSummary=" + (summary != null) +
                '}';
    }
}
