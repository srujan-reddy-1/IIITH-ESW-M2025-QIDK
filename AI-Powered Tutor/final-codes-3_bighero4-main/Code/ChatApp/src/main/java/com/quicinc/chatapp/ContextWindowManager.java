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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ContextWindowManager - Manages PDF chunking, summarization, and intelligent retrieval
 * Handles large PDFs that exceed the model's context window
 */
public class ContextWindowManager {
    private static final String TAG = "ContextWindowManager";
    private static final int MAX_CHUNK_TOKENS = 1000; // Max tokens per chunk
    private static final int TARGET_SUMMARY_TOKENS = 300; // Target summary size
    private static final int CHARS_PER_TOKEN = 4; // Approximation: 1 token ≈ 4 chars
    private static final String PREFS_NAME = "ContextWindowPrefs";
    private static final String KEY_CHUNKS_PREFIX = "chunks_";

    private static ContextWindowManager instance;
    private Map<String, List<PDFChunk>> documentChunks; // documentId -> list of chunks
    private Context context;
    private Gson gson;
    private WorkingGenieWrapper aiWrapper;

    private ContextWindowManager(Context context, WorkingGenieWrapper aiWrapper) {
        this.context = context.getApplicationContext();
        this.aiWrapper = aiWrapper;
        this.gson = new Gson();
        this.documentChunks = new HashMap<>();
    }

    public static synchronized ContextWindowManager getInstance(Context context, WorkingGenieWrapper aiWrapper) {
        if (instance == null) {
            instance = new ContextWindowManager(context, aiWrapper);
        }
        return instance;
    }

    /**
     * Process a document into chunks and generate summaries
     */
    public void processDocument(String documentId, String fullText, ProcessCallback callback) {
        new Thread(() -> {
            try {
                Log.i(TAG, "Processing document: " + documentId);
                
                if (fullText == null || fullText.trim().isEmpty()) {
                    callback.onError("Document content is empty");
                    return;
                }
                
                // Check if document needs chunking
                int totalTokens = estimateTokenCount(fullText);
                Log.i(TAG, "Document size: ~" + totalTokens + " tokens");
                
                if (totalTokens <= MAX_CHUNK_TOKENS) {
                    // Small document - create single chunk with simple summary
                    PDFChunk singleChunk = new PDFChunk(documentId, 0, fullText, 1, 1);
                    
                    // Create a heuristic summary for small docs
                    String heuristicSummary = createHeuristicSummary(fullText);
                    singleChunk.setSummary(heuristicSummary);
                    
                    List<PDFChunk> chunks = new ArrayList<>();
                    chunks.add(singleChunk);
                    documentChunks.put(documentId, chunks);
                    saveChunks(documentId, chunks);
                    
                    Log.d(TAG, "Small document processed with heuristic summary");
                    callback.onSuccess(1, false);
                    return;
                }
                
                // Large document - chunk it
                List<PDFChunk> chunks = chunkDocument(documentId, fullText);
                Log.i(TAG, "Created " + chunks.size() + " chunks");
                
                // For large documents, also use heuristic summaries (faster and more reliable)
                for (PDFChunk chunk : chunks) {
                    String heuristicSummary = createHeuristicSummary(chunk.getFullText());
                    chunk.setSummary(heuristicSummary);
                }
                
                documentChunks.put(documentId, chunks);
                saveChunks(documentId, chunks);
                
                Log.d(TAG, "Large document processed with " + chunks.size() + " chunks");
                callback.onSuccess(chunks.size(), false);  // false = don't need AI summarization
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing document", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    /**
     * Chunk document into smaller pieces based on semantic boundaries
     * Improved for large file handling with better memory management
     */
    private List<PDFChunk> chunkDocument(String documentId, String fullText) {
        List<PDFChunk> chunks = new ArrayList<>();
        int maxChunkChars = MAX_CHUNK_TOKENS * CHARS_PER_TOKEN;
        
        // Safety check for extremely large documents
        if (fullText.length() > 10_000_000) { // 10MB text limit
            Log.w(TAG, "Document is extremely large (" + fullText.length() + " chars), truncating");
            fullText = fullText.substring(0, 10_000_000) + "\n\n[Document truncated for processing]";
        }
        
        // Split by paragraphs (double newlines) - more memory efficient
        String[] paragraphs = fullText.split("\n\n+");
        
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        int currentPage = 1; // Approximate page tracking
        int totalCharsProcessed = 0;
        
        for (String paragraph : paragraphs) {
            String trimmedPara = paragraph.trim();
            if (trimmedPara.isEmpty()) continue;
            
            // Check if adding this paragraph would exceed chunk size
            if (currentChunk.length() > 0 && 
                currentChunk.length() + trimmedPara.length() + 2 > maxChunkChars) {
                
                // Save current chunk before it gets too large
                if (currentChunk.length() > 0) {
                    PDFChunk chunk = new PDFChunk(documentId, chunkIndex++, 
                        currentChunk.toString(), currentPage, currentPage);
                    chunks.add(chunk);
                    totalCharsProcessed += currentChunk.length();
                    
                    // Start new chunk
                    currentChunk = new StringBuilder();
                    currentPage++;
                }
            }
            
            // If single paragraph is larger than chunk size, split it
            if (trimmedPara.length() > maxChunkChars) {
                // Split large paragraph into smaller pieces
                for (int i = 0; i < trimmedPara.length(); i += maxChunkChars) {
                    int end = Math.min(i + maxChunkChars, trimmedPara.length());
                    String paraChunk = trimmedPara.substring(i, end);
                    PDFChunk chunk = new PDFChunk(documentId, chunkIndex++, 
                        paraChunk, currentPage, currentPage);
                    chunks.add(chunk);
                    totalCharsProcessed += paraChunk.length();
                    currentPage++;
                }
            } else {
                // Add paragraph to current chunk
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(trimmedPara);
            }
        }
        
        // Add final chunk if it has content
        if (currentChunk.length() > 0) {
            PDFChunk chunk = new PDFChunk(documentId, chunkIndex, 
                currentChunk.toString(), currentPage, currentPage);
            chunks.add(chunk);
            totalCharsProcessed += currentChunk.length();
        }
        
        // Fallback: if no chunks created (no paragraph breaks), split by character count
        if (chunks.isEmpty()) {
            Log.w(TAG, "No paragraph breaks found, splitting by character count");
            for (int i = 0; i < fullText.length(); i += maxChunkChars) {
                int end = Math.min(i + maxChunkChars, fullText.length());
                String chunkText = fullText.substring(i, end);
                PDFChunk chunk = new PDFChunk(documentId, chunkIndex++, 
                    chunkText, currentPage, currentPage);
                chunks.add(chunk);
                currentPage++;
            }
        }
        
        Log.i(TAG, "Chunked document into " + chunks.size() + " chunks (" + totalCharsProcessed + " chars processed)");
        return chunks;
    }

    /**
     * Generate AI summaries for all chunks - SEQUENTIAL to avoid race conditions
     * The AI callback fires multiple times during streaming, so we process one at a time
     */
    private void generateSummaries(List<PDFChunk> chunks, SummaryCallback callback) {
        if (chunks == null || chunks.isEmpty()) {
            callback.onComplete();
            return;
        }
        
        final int total = chunks.size();
        
        // Process chunks sequentially using recursion
        processChunkSequentially(chunks, 0, total, callback);
    }
    
    /**
     * Process chunks one at a time to handle streaming callbacks properly
     */
    private void processChunkSequentially(List<PDFChunk> chunks, int index, int total, SummaryCallback callback) {
        if (index >= total) {
            Log.d(TAG, "All " + total + " chunks summarized");
            callback.onComplete();
            return;
        }
        
        PDFChunk chunk = chunks.get(index);
        
        // Simple prompt optimized for small model
        String prompt = "List the main concepts from this text in 2-3 sentences:\n\n" + 
            truncateForPrompt(chunk.getFullText(), 1500);
        
        // Track if we've already processed this chunk's response
        final boolean[] processed = {false};
        final StringBuilder responseBuilder = new StringBuilder();
        
        Log.d(TAG, "Processing chunk " + (index + 1) + "/" + total);
        
        aiWrapper.getResponseForPrompt(prompt, new StringCallback() {
            @Override
            public void onNewString(String response) {
                if (processed[0]) {
                    return; // Already handled
                }
                
                // Accumulate streaming response
                if (response != null && !response.isEmpty()) {
                    responseBuilder.setLength(0);
                    responseBuilder.append(response);
                }
                
                // Check if response looks complete (has sentence ending or is substantial)
                String current = responseBuilder.toString().trim();
                if (current.length() > 30 && 
                    (current.endsWith(".") || current.endsWith("!") || current.endsWith("?") || 
                     current.length() > 200)) {
                    
                    processed[0] = true;
                    String cleanedSummary = cleanSummary(current);
                    chunk.setSummary(cleanedSummary);
                    
                    Log.d(TAG, "Chunk " + (index + 1) + " summary: " + cleanedSummary.substring(0, Math.min(50, cleanedSummary.length())) + "...");
                    callback.onProgress(index + 1, total);
                    
                    // Small delay then process next chunk
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Sleep interrupted");
                    }
                    
                    // Process next chunk
                    processChunkSequentially(chunks, index + 1, total, callback);
                }
            }
        });
        
        // Timeout fallback - if no response after 15 seconds, use heuristic summary
        new Thread(() -> {
            try {
                Thread.sleep(15000);
                if (!processed[0]) {
                    processed[0] = true;
                    Log.w(TAG, "Timeout for chunk " + (index + 1) + ", using heuristic");
                    String fallback = createHeuristicSummary(chunk.getFullText());
                    chunk.setSummary(fallback);
                    callback.onProgress(index + 1, total);
                    processChunkSequentially(chunks, index + 1, total, callback);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        }).start();
    }
    
    /**
     * Create a simple heuristic summary from first sentences
     */
    private String createHeuristicSummary(String text) {
        if (text == null || text.isEmpty()) {
            return "No content available.";
        }
        
        // Take first 3 sentences or 200 chars
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String s : sentences) {
            if (count >= 3 || sb.length() > 200) break;
            String trimmed = s.trim();
            if (trimmed.length() > 10) {
                sb.append(trimmed).append(" ");
                count++;
            }
        }
        
        String result = sb.toString().trim();
        return result.isEmpty() ? text.substring(0, Math.min(200, text.length())) : result;
    }
    
    /**
     * Truncate text for prompt to stay within limits
     */
    private String truncateForPrompt(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        
        // Try to cut at sentence boundary
        String truncated = text.substring(0, maxChars);
        int lastPeriod = truncated.lastIndexOf('.');
        if (lastPeriod > maxChars / 2) {
            return truncated.substring(0, lastPeriod + 1);
        }
        return truncated + "...";
    }

    /**
     * Get relevant context for a query using intelligent retrieval
     */
    public String getRelevantContext(String documentId, String query) {
        List<PDFChunk> chunks = loadChunks(documentId);
        if (chunks == null || chunks.isEmpty()) {
            Log.w(TAG, "No chunks found for document: " + documentId);
            return "";
        }
        
        // If only one chunk, return its full text
        if (chunks.size() == 1) {
            return chunks.get(0).getFullText();
        }
        
        // Search summaries for relevant chunks
        List<ChunkScore> scores = new ArrayList<>();
        String queryLower = query.toLowerCase();
        
        for (PDFChunk chunk : chunks) {
            int score = 0;
            String summary = chunk.getSummary();
            if (summary != null) {
                String summaryLower = summary.toLowerCase();
                
                // Simple keyword matching (could be improved with TF-IDF or embeddings)
                String[] queryWords = queryLower.split("\\s+");
                for (String word : queryWords) {
                    if (word.length() > 3) { // Ignore short words
                        if (summaryLower.contains(word)) {
                            score += 10;
                        }
                    }
                }
            }
            
            scores.add(new ChunkScore(chunk, score));
        }
        
        // Sort by relevance score
        scores.sort((a, b) -> Integer.compare(b.score, a.score));
        
        // If top chunk has good score, return its full text
        if (!scores.isEmpty() && scores.get(0).score > 0) {
            PDFChunk bestChunk = scores.get(0).chunk;
            Log.i(TAG, "Selected chunk " + bestChunk.getChunkIndex() + " (score: " + scores.get(0).score + ")");
            return bestChunk.getFullText();
        }
        
        // Fallback: return all summaries combined
        Log.i(TAG, "No specific match, returning combined summaries");
        StringBuilder combinedContext = new StringBuilder();
        for (PDFChunk chunk : chunks) {
            if (chunk.getSummary() != null) {
                combinedContext.append(chunk.getSummary()).append("\n\n");
            }
        }
        
        return combinedContext.toString();
    }

    /**
     * Get all chunk summaries for a document
     */
    public String getAllSummaries(String documentId) {
        List<PDFChunk> chunks = loadChunks(documentId);
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        
        StringBuilder allSummaries = new StringBuilder();
        for (PDFChunk chunk : chunks) {
            if (chunk.getSummary() != null) {
                allSummaries.append("Section ").append(chunk.getChunkIndex() + 1)
                    .append(":\n").append(chunk.getSummary()).append("\n\n");
            }
        }
        
        return allSummaries.toString();
    }

    /**
     * Retrieve the processed chunks for a given document
     */
    public List<PDFChunk> getChunksForDocument(String documentId) {
        return loadChunks(documentId);
    }

    /**
     * Save chunks to SharedPreferences
     */
    private void saveChunks(String documentId, List<PDFChunk> chunks) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = gson.toJson(chunks);
        prefs.edit().putString(KEY_CHUNKS_PREFIX + documentId, json).apply();
        Log.d(TAG, "Saved " + chunks.size() + " chunks for document: " + documentId);
    }

    /**
     * Load chunks from SharedPreferences
     */
    private List<PDFChunk> loadChunks(String documentId) {
        // Check in-memory cache first
        if (documentChunks.containsKey(documentId)) {
            return documentChunks.get(documentId);
        }
        
        // Load from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CHUNKS_PREFIX + documentId, null);
        
        if (json != null) {
            Type type = new TypeToken<List<PDFChunk>>(){}.getType();
            List<PDFChunk> chunks = gson.fromJson(json, type);
            documentChunks.put(documentId, chunks);
            return chunks;
        }
        
        return null;
    }

    /**
     * Delete chunks for a document
     */
    public void deleteDocumentChunks(String documentId) {
        documentChunks.remove(documentId);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CHUNKS_PREFIX + documentId).apply();
        Log.i(TAG, "Deleted chunks for document: " + documentId);
    }
    
    /**
     * Get relevant context from all documents (for global chat)
     * Returns context from all available documents based on the query
     * Improved with better document processing and relevance scoring
     */
    public String getGlobalRelevantContext(String query) {
        Log.d(TAG, "getGlobalRelevantContext called with query: " + query);
        Log.d(TAG, "documentChunks size: " + documentChunks.size());
        
        // Get DocumentManager to access documents
        DocumentManager docManager = DocumentManager.getInstance(context);
        List<Document> allDocs = docManager.getAllDocuments();
        
        // If no chunks in memory, try to process documents on-the-fly
        if (documentChunks.isEmpty() && !allDocs.isEmpty()) {
            Log.i(TAG, "No chunks in memory, processing documents for global context");
            
            // Process documents that haven't been chunked yet
            for (Document doc : allDocs) {
                if (doc.getTextContent() != null && !doc.getTextContent().trim().isEmpty()) {
                    try {
                        // Process synchronously for immediate use
                        String docId = doc.getId();
                        if (!documentChunks.containsKey(docId)) {
                            // Quick process without callback (fire and forget)
                            processDocument(docId, doc.getTextContent(), new ProcessCallback() {
                                @Override
                                public void onSuccess(int chunkCount, boolean needsSummarization) {
                                    Log.d(TAG, "Processed " + doc.getDisplayName() + " for global context: " + chunkCount + " chunks");
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.w(TAG, "Error processing " + doc.getDisplayName() + ": " + error);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error processing document " + doc.getDisplayName() + " for global context", e);
                    }
                }
            }
            
            // Wait a bit for processing to start, then continue
            try {
                Thread.sleep(500); // Give processing a moment
            } catch (InterruptedException e) {
                // Continue anyway
            }
        }
        
        if (documentChunks.isEmpty()) {
            Log.w(TAG, "No document chunks available for global context");
            return "";
        }
        
        StringBuilder globalContext = new StringBuilder();
        globalContext.append("Below is relevant information from the student's study materials across all subjects. ");
        globalContext.append("Use this information to provide helpful, accurate, and educational responses.\n\n");
        
        // Score and rank documents by relevance
        List<DocumentScore> scoredDocs = new ArrayList<>();
        String queryLower = query.toLowerCase();
        
        for (Map.Entry<String, List<PDFChunk>> entry : documentChunks.entrySet()) {
            String documentId = entry.getKey();
            String relevantContent = getRelevantContext(documentId, query);
            
            if (relevantContent != null && !relevantContent.trim().isEmpty()) {
                // Score relevance based on keyword matches
                int score = 0;
                String contentLower = relevantContent.toLowerCase();
                String[] queryWords = queryLower.split("\\s+");
                for (String word : queryWords) {
                    if (word.length() > 3 && contentLower.contains(word)) {
                        score += 10;
                    }
                }
                
                Document doc = docManager.getDocument(documentId);
                String displayName = doc != null ? doc.getDisplayName() : documentId;
                
                scoredDocs.add(new DocumentScore(displayName, relevantContent, score));
            }
        }
        
        // Sort by relevance score (highest first)
        scoredDocs.sort((a, b) -> Integer.compare(b.score, a.score));
        
        // Take top documents
        int maxDocuments = 5; // Limit to avoid context overflow
        int maxCharsPerDocument = 1000; // Increased for better context
        
        int documentsProcessed = 0;
        for (DocumentScore docScore : scoredDocs) {
            if (documentsProcessed >= maxDocuments) {
                break;
            }
            
            String relevantContent = docScore.content;
            
            // Truncate if too long (try to preserve sentence boundaries)
            if (relevantContent.length() > maxCharsPerDocument) {
                String truncated = relevantContent.substring(0, maxCharsPerDocument);
                int lastPeriod = truncated.lastIndexOf('.');
                if (lastPeriod > maxCharsPerDocument * 0.7) {
                    relevantContent = truncated.substring(0, lastPeriod + 1);
                } else {
                    relevantContent = truncated + "...";
                }
            }
            
            globalContext.append("=== From: ").append(docScore.displayName).append(" ===\n");
            globalContext.append(relevantContent).append("\n\n");
            documentsProcessed++;
        }
        
        if (documentsProcessed == 0) {
            Log.d(TAG, "No relevant content found in documents for query: " + query);
            return "";
        }
        
        Log.d(TAG, "Global context created from " + documentsProcessed + " documents (top " + scoredDocs.size() + " scored)");
        return globalContext.toString();
    }
    
    /**
     * Helper class for scoring document relevance
     */
    private static class DocumentScore {
        String displayName;
        String content;
        int score;
        
        DocumentScore(String displayName, String content, int score) {
            this.displayName = displayName;
            this.content = content;
            this.score = score;
        }
    }

    /**
     * Estimate token count from text
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;
        return text.length() / CHARS_PER_TOKEN;
    }

    /**
     * Clean summary text by removing administrative/metadata content
     */
    private String cleanSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return summary;
        }
        
        // Patterns to remove (case insensitive)
        String[] patternsToRemove = {
            "(?i)professor\\s+[A-Z][a-z]+",  // Professor Name
            "(?i)prof\\.?\\s+[A-Z][a-z]+",   // Prof. Name
            "(?i)dr\\.?\\s+[A-Z][a-z]+",     // Dr. Name
            "(?i)instructor:\\s*[^,\\.]+",   // Instructor: Name
            "(?i)taught by\\s+[^,\\.]+",     // Taught by Name
            "(?i)course\\s*code[:\\s]+[A-Z0-9]+", // Course code: CS101
            "(?i)[A-Z]{2,4}\\s*\\d{3,4}[A-Z]?",  // CS101, ECE2020
            "(?i)lecture\\s*#?\\d+",         // Lecture 1, Lecture #2
            "(?i)week\\s*\\d+",              // Week 1
            "(?i)semester\\s*(fall|spring|summer)?\\s*\\d*", // Semester Fall 2024
            "(?i)\\d{1,2}/\\d{1,2}/\\d{2,4}", // Dates like 01/15/2024
            "(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2},?\\s*\\d{0,4}", // January 15, 2024
            "(?i)page\\s*\\d+",              // Page 1
            "(?i)slide\\s*\\d+",             // Slide 1
        };
        
        String cleaned = summary;
        for (String pattern : patternsToRemove) {
            cleaned = cleaned.replaceAll(pattern, "").trim();
        }
        
        // Clean up extra whitespace and punctuation artifacts
        cleaned = cleaned.replaceAll("\\s+", " ");  // Multiple spaces to single
        cleaned = cleaned.replaceAll("^[,\\.\\s]+", "");  // Leading punctuation
        cleaned = cleaned.replaceAll("[,\\.\\s]+$", ".");  // Trailing punctuation
        cleaned = cleaned.replaceAll("\\s+,", ",");  // Space before comma
        cleaned = cleaned.replaceAll(",\\s*,", ",");  // Double commas
        cleaned = cleaned.replaceAll("\\.\\s*\\.", ".");  // Double periods
        
        return cleaned.trim();
    }

    /**
     * Helper class for scoring chunks
     */
    private static class ChunkScore {
        PDFChunk chunk;
        int score;
        
        ChunkScore(PDFChunk chunk, int score) {
            this.chunk = chunk;
            this.score = score;
        }
    }

    /**
     * Callback for document processing
     */
    public interface ProcessCallback {
        void onSuccess(int chunkCount, boolean needsSummarization);
        void onError(String error);
    }

    /**
     * Callback for summarization progress
     */
    private interface SummaryCallback {
        void onProgress(int completed, int total);
        void onComplete();
        void onError(String error);
    }
}
