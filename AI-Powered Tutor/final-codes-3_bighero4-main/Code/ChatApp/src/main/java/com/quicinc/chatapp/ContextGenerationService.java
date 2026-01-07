// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds reusable context summaries and topic lists for documents on demand
 */
public class ContextGenerationService {
    private static final String TAG = "ContextGenerationSvc";
    private static final int MAX_PROMPT_LENGTH = 6000;
    private static final int MODEL_TIMEOUT_MS = 35000;
    private static final int MAX_TOPICS = 12;
    private static final int MAX_TOPIC_WORDS = 6;
    private static final int MAX_SUMMARY_SECTIONS = 10;
    private static final int MAX_SECTION_CHARS = 450;
    
    // Token budget for topic generation: 2048 total, ~400 for system prompt, 1600 for content
    private static final int TOPIC_GENERATION_CHUNK_TOKENS = 1600;
    private static final int CHARS_PER_TOKEN = 4;
    private static final int MAX_CHUNK_CHARS = TOPIC_GENERATION_CHUNK_TOKENS * CHARS_PER_TOKEN;
    
    /**
     * Hardcoded topics for known PDFs (matching subjects folder)
     */
    private static final Map<String, List<String>> HARDCODED_TOPICS = new HashMap<>();
    
    static {
        // Automata Theory
        HARDCODED_TOPICS.put("AT_L01", Arrays.asList(
            "Finite Automata", "DFA", "NFA", "Regular Languages", "State Transitions", 
            "Acceptance States", "Alphabet", "Language Recognition"
        ));
        HARDCODED_TOPICS.put("AT_L02", Arrays.asList(
            "Regular Expressions", "Kleene Closure", "Union Operations", "Concatenation",
            "Equivalence", "Conversion", "Regular Grammar"
        ));
        HARDCODED_TOPICS.put("AT_L03", Arrays.asList(
            "Context-Free Grammars", "CFG", "Parse Trees", "Derivations", 
            "Chomsky Normal Form", "Pushdown Automata", "PDA"
        ));
        HARDCODED_TOPICS.put("AT_L04", Arrays.asList(
            "Turing Machines", "Computability", "Decidability", "Halting Problem",
            "Recursive Languages", "Recursively Enumerable", "Complexity Theory"
        ));
        
        // Data Structures and Methods (DSM)
        HARDCODED_TOPICS.put("DSM_L01", Arrays.asList(
            "Arrays", "Linked Lists", "Stacks", "Queues", "Time Complexity",
            "Space Complexity", "Basic Operations", "Data Structure Basics"
        ));
        HARDCODED_TOPICS.put("DSM_L02", Arrays.asList(
            "Trees", "Binary Trees", "BST", "Tree Traversal", "Inorder", 
            "Preorder", "Postorder", "Tree Operations"
        ));
        HARDCODED_TOPICS.put("DSM_L03", Arrays.asList(
            "Graphs", "Graph Representation", "BFS", "DFS", "Shortest Path",
            "Adjacency Matrix", "Adjacency List", "Graph Algorithms"
        ));
        HARDCODED_TOPICS.put("DSM_L04", Arrays.asList(
            "Sorting Algorithms", "Quick Sort", "Merge Sort", "Heap Sort",
            "Hash Tables", "Hashing", "Collision Resolution", "Search Algorithms"
        ));
        
        // Operating Systems and Networking (OSN)
        HARDCODED_TOPICS.put("OSN_L01", Arrays.asList(
            "Process Management", "Threads", "Scheduling", "Context Switching",
            "Process States", "CPU Scheduling", "Multiprocessing"
        ));
        HARDCODED_TOPICS.put("OSN_L02", Arrays.asList(
            "Memory Management", "Virtual Memory", "Paging", "Segmentation",
            "Page Replacement", "Memory Allocation", "Address Translation"
        ));
        HARDCODED_TOPICS.put("OSN_L03", Arrays.asList(
            "File Systems", "I/O Management", "Disk Scheduling", "Storage",
            "File Organization", "Directory Structure", "File Operations"
        ));
        HARDCODED_TOPICS.put("OSN_L04", Arrays.asList(
            "Network Protocols", "TCP/IP", "OSI Model", "Network Layers",
            "Routing", "Switching", "Network Topology", "Data Transmission"
        ));
    }
    

    public interface ContextGenerationCallback {
        void onProgress(String message);
        void onSuccess(ContextResult result);
        void onError(String errorMessage);
    }

    public static class ContextResult {
        private final String summary;
        private final List<String> topics;
        private final int chunkCount;

        public ContextResult(String summary, List<String> topics, int chunkCount) {
            this.summary = summary;
            this.topics = topics;
            this.chunkCount = chunkCount;
        }

        public String getSummary() {
            return summary;
        }

        public List<String> getTopics() {
            return topics;
        }

        public int getChunkCount() {
            return chunkCount;
        }
    }

    private static class ModelResponse {
        String summary;
        List<String> topics;
    }

    private final Context context;
    private final WorkingGenieWrapper genieWrapper;
    private final ContextWindowManager contextWindowManager;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final Gson gson;

    public ContextGenerationService(Context context,
                                    WorkingGenieWrapper genieWrapper,
                                    ContextWindowManager contextWindowManager) {
        this.context = context.getApplicationContext();
        this.genieWrapper = genieWrapper;
        this.contextWindowManager = contextWindowManager;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    public void generateContext(String subjectName, Document document, ContextGenerationCallback callback) {
        generateContext(subjectName, document, callback, false);
    }

    /**
     * Generate context for a document
     * @param forceRegenerate If true, deletes existing chunks and regenerates fresh
     */
    public void generateContext(String subjectName, Document document, ContextGenerationCallback callback, boolean forceRegenerate) {
        if (document == null || TextUtils.isEmpty(document.getTextContent())) {
            postError(callback, "Document is empty. Upload a readable PDF first.");
            return;
        }

        executorService.execute(() -> {
            // Check if we already have context and don't need to regenerate
            boolean hasExistingContext = !TextUtils.isEmpty(document.getContextSummary()) 
                && document.getContextTopics() != null 
                && !document.getContextTopics().isEmpty();
            
            if (hasExistingContext && !forceRegenerate) {
                // Return existing context immediately
                Log.d(TAG, "Using existing context for: " + document.getDisplayName());
                postProgress(callback, "Context already exists");
                ContextResult existing = new ContextResult(
                    document.getContextSummary(),
                    document.getContextTopics(),
                    0
                );
                postSuccess(callback, existing);
                return;
            }
            
            // Check for hardcoded topics first
            String docName = document.getDisplayName();
            String docKey = extractDocumentKey(docName);
            List<String> hardcodedTopics = getHardcodedTopics(docKey);
            
            if (hardcodedTopics != null && !hardcodedTopics.isEmpty()) {
                Log.i(TAG, "Using hardcoded topics for: " + docKey);
                postProgress(callback, "Loading predefined topics...");
                
                // Generate summary using AI if available, otherwise use fallback
                if (genieWrapper != null && contextWindowManager != null) {
                    postProgress(callback, "Generating summary...");
                    generateSummaryWithChunking(subjectName, document, hardcodedTopics, callback);
                } else {
                    postProgress(callback, "Creating summary...");
                    String summary = createBasicSummary(document.getTextContent());
                    ContextResult result = new ContextResult(summary, hardcodedTopics, 0);
                    postSuccess(callback, result);
                }
                return;
            }
            
            // No hardcoded topics - use AI generation
            if (genieWrapper == null || contextWindowManager == null) {
                Log.w(TAG, "AI model unavailable. Falling back to heuristic summary.");
                postProgress(callback, "AI model unavailable, creating quick summary...");
                ContextResult fallback = buildFallbackResult(document);
                postSuccess(callback, fallback);
                return;
            }
            
            // Delete old chunks and regenerate fresh
            postProgress(callback, "Preparing context generation...");
            contextWindowManager.deleteDocumentChunks(document.getId());
            
            postProgress(callback, "Analyzing document structure...");
            contextWindowManager.processDocument(document.getId(), document.getTextContent(), new ContextWindowManager.ProcessCallback() {
                @Override
                public void onSuccess(int chunkCount, boolean needsSummarization) {
                    postProgress(callback, needsSummarization
                            ? "Generating summaries for " + chunkCount + " sections..."
                            : "Processing " + chunkCount + " section(s)...");
                    requestSummaryFromModel(subjectName, document, chunkCount, callback);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Chunking failed: " + error);
                    postError(callback, error != null ? error : "Failed to analyze document");
                }
            });
        });
    }
    
    /**
     * Extract document key from display name (e.g., "AT_L01.pdf" -> "AT_L01")
     */
    private String extractDocumentKey(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return "";
        }
        // Remove .pdf extension
        String key = displayName.replaceAll("(?i)\\.pdf$", "");
        // Remove any path components
        if (key.contains("/")) {
            key = key.substring(key.lastIndexOf("/") + 1);
        }
        return key.trim();
    }
    
    /**
     * Get hardcoded topics for a document key
     */
    private List<String> getHardcodedTopics(String docKey) {
        if (TextUtils.isEmpty(docKey)) {
            return null;
        }
        return HARDCODED_TOPICS.get(docKey);
    }
    
    /**
     * Generate summary with proper chunking (1600 tokens per chunk)
     */
    private void generateSummaryWithChunking(String subjectName, Document document, 
                                             List<String> topics, ContextGenerationCallback callback) {
        String fullText = document.getTextContent();
        int totalTokens = TokenBudgetEnforcer.estimateTokens(fullText);
        
        Log.d(TAG, "Document has " + totalTokens + " tokens, chunking at " + TOPIC_GENERATION_CHUNK_TOKENS + " tokens per chunk");
        
        if (totalTokens <= TOPIC_GENERATION_CHUNK_TOKENS) {
            // Single chunk - process directly
            postProgress(callback, "Generating summary from document...");
            requestSummaryForChunk(subjectName, document, fullText, topics, 0, 1, callback);
        } else {
            // Multiple chunks - process sequentially
            List<String> chunks = chunkTextForTopicGeneration(fullText);
            Log.d(TAG, "Split document into " + chunks.size() + " chunks for topic generation");
            
            processChunksSequentially(subjectName, document, chunks, topics, callback);
        }
    }
    
    /**
     * Chunk text for topic generation (1600 tokens per chunk)
     */
    private List<String> chunkTextForTopicGeneration(String text) {
        List<String> chunks = new ArrayList<>();
        
        // Split by paragraphs first
        String[] paragraphs = text.split("\n\n+");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;
            
            // Check if adding this paragraph would exceed chunk size
            int currentSize = TokenBudgetEnforcer.estimateTokens(currentChunk.toString());
            int paraSize = TokenBudgetEnforcer.estimateTokens(trimmed);
            
            if (currentSize > 0 && currentSize + paraSize > TOPIC_GENERATION_CHUNK_TOKENS) {
                // Save current chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
            }
            
            // If paragraph itself is too large, split it
            if (paraSize > TOPIC_GENERATION_CHUNK_TOKENS) {
                // Split large paragraph
                int maxChars = MAX_CHUNK_CHARS;
                for (int i = 0; i < trimmed.length(); i += maxChars) {
                    int end = Math.min(i + maxChars, trimmed.length());
                    String paraChunk = trimmed.substring(i, end);
                    chunks.add(paraChunk);
                }
            } else {
                // Add paragraph to current chunk
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(trimmed);
            }
        }
        
        // Add final chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
    }
    
    /**
     * Process chunks sequentially and combine results
     */
    private void processChunksSequentially(String subjectName, Document document, 
                                          List<String> chunks, List<String> topics,
                                          ContextGenerationCallback callback) {
        final int totalChunks = chunks.size();
        final StringBuilder combinedSummary = new StringBuilder();
        final AtomicBoolean[] processing = {new AtomicBoolean(false)};
        final int[] currentChunkIndex = {0};
        
        postProgress(callback, "Processing chunk 1/" + totalChunks + "...");
        
        processNextChunk(subjectName, document, chunks, topics, combinedSummary, 
                        currentChunkIndex, totalChunks, processing, callback);
    }
    
    /**
     * Process next chunk recursively
     */
    private void processNextChunk(String subjectName, Document document, List<String> chunks,
                                  List<String> topics, StringBuilder combinedSummary,
                                  int[] currentIndex, int totalChunks, AtomicBoolean[] processing,
                                  ContextGenerationCallback callback) {
        if (currentIndex[0] >= chunks.size()) {
            // All chunks processed
            processing[0].set(false);
            String finalSummary = combinedSummary.toString().trim();
            if (finalSummary.isEmpty()) {
                finalSummary = createBasicSummary(document.getTextContent());
            }
            ContextResult result = new ContextResult(finalSummary, topics, totalChunks);
            postSuccess(callback, result);
            return;
        }
        
        processing[0].set(true);
        String chunkText = chunks.get(currentIndex[0]);
        int chunkNum = currentIndex[0] + 1;
        
        postProgress(callback, "Processing chunk " + chunkNum + "/" + totalChunks + "...");
        
        requestSummaryForChunk(subjectName, document, chunkText, topics, currentIndex[0], totalChunks, 
            new ContextGenerationCallback() {
                @Override
                public void onProgress(String message) {
                    postProgress(callback, message);
                }
                
                @Override
                public void onSuccess(ContextResult result) {
                    // Append this chunk's summary
                    if (result.getSummary() != null && !result.getSummary().trim().isEmpty()) {
                        if (combinedSummary.length() > 0) {
                            combinedSummary.append(" ");
                        }
                        combinedSummary.append(result.getSummary().trim());
                    }
                    
                    // Process next chunk
                    currentIndex[0]++;
                    processNextChunk(subjectName, document, chunks, topics, combinedSummary,
                                    currentIndex, totalChunks, processing, callback);
                }
                
                @Override
                public void onError(String errorMessage) {
                    Log.w(TAG, "Error processing chunk " + chunkNum + ": " + errorMessage);
                    // Continue with next chunk anyway
                    currentIndex[0]++;
                    processNextChunk(subjectName, document, chunks, topics, combinedSummary,
                                    currentIndex, totalChunks, processing, callback);
                }
            });
    }
    
    /**
     * Request summary for a single chunk
     */
    private void requestSummaryForChunk(String subjectName, Document document, String chunkText,
                                       List<String> topics, int chunkIndex, int totalChunks,
                                       ContextGenerationCallback callback) {
        // Build prompt with proper token budget (1600 tokens for content, ~400 for system)
        String prompt = buildSummaryPrompt(subjectName, document.getDisplayName(), chunkText);
        
        // Verify token budget and create final copy for lambda
        String finalChunkText;
        int promptTokens = TokenBudgetEnforcer.estimateTokens(prompt);
        if (promptTokens > 2048) {
            Log.w(TAG, "Prompt exceeds budget (" + promptTokens + " tokens), truncating chunk");
            int maxChars = (2048 - 400) * CHARS_PER_TOKEN; // Reserve 400 for system
            finalChunkText = chunkText.substring(0, Math.min(maxChars, chunkText.length()));
            prompt = buildSummaryPrompt(subjectName, document.getDisplayName(), finalChunkText);
        } else {
            finalChunkText = chunkText; // Make effectively final
        }
        
        AtomicBoolean completed = new AtomicBoolean(false);
        StringBuilder responseBuffer = new StringBuilder();
        
        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                Log.w(TAG, "AI summary timed out for chunk " + (chunkIndex + 1));
                String fallbackSummary = createBasicSummary(finalChunkText);
                ContextResult result = new ContextResult(fallbackSummary, topics, totalChunks);
                callback.onSuccess(result);
            }
        };
        
        genieWrapper.getResponseForPrompt(prompt, new StringCallback() {
            @Override
            public void onNewString(String response) {
                if (completed.get()) {
                    return;
                }
                
                if (TextUtils.isEmpty(response)) {
                    return;
                }
                
                mainHandler.removeCallbacks(timeoutRunnable);
                mainHandler.postDelayed(timeoutRunnable, MODEL_TIMEOUT_MS);
                
                responseBuffer.setLength(0);
                responseBuffer.append(response);
                
                // Try to parse summary
                String summary = extractSummaryFromResponse(response);
                if (summary != null && !summary.isEmpty() && completed.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable);
                    ContextResult result = new ContextResult(summary, topics, totalChunks);
                    callback.onSuccess(result);
                }
            }
        });
        
        mainHandler.postDelayed(timeoutRunnable, MODEL_TIMEOUT_MS);
    }
    
    /**
     * Build prompt for summary generation only (topics already provided)
     */
    private String buildSummaryPrompt(String subjectName, String documentName, String chunkText) {
        String systemInstruction = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
                "You are an AI assistant that creates concise summaries of educational content. " +
                "You MUST respond with ONLY a valid JSON object: {\"summary\": \"...\"}. " +
                "Do not include any other text or markdown.<|eot_id|>";
        
        String userPrompt = "<|start_header_id|>user<|end_header_id|>\n\n" +
                "Create a concise summary (2-3 sentences) of the following text from '" + 
                documentName + "' (" + subjectName + ").\n\n" +
                "Focus on the main concepts and key information.\n\n" +
                "Text:\n" + truncateContent(chunkText, MAX_CHUNK_CHARS) + 
                "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
        
        return systemInstruction + userPrompt;
    }
    
    /**
     * Extract summary from AI response
     */
    private String extractSummaryFromResponse(String response) {
        if (TextUtils.isEmpty(response)) {
            return null;
        }
        
        // Try to find JSON
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            String json = response.substring(start, end + 1);
            try {
                ModelResponse parsed = gson.fromJson(json, ModelResponse.class);
                if (parsed != null && !TextUtils.isEmpty(parsed.summary)) {
                    return parsed.summary.trim();
                }
            } catch (Exception e) {
                Log.d(TAG, "JSON parse failed: " + e.getMessage());
            }
        }
        
        // Fallback: extract summary-like content
        String lower = response.toLowerCase();
        int summaryIdx = lower.indexOf("summary");
        if (summaryIdx >= 0) {
            String afterSummary = response.substring(Math.min(summaryIdx + 8, response.length()));
            afterSummary = afterSummary.replaceAll("^[:\\s\"]+", "").trim();
            int endIdx = afterSummary.indexOf("\"");
            if (endIdx < 0) endIdx = afterSummary.indexOf("\n");
            if (endIdx < 0) endIdx = Math.min(300, afterSummary.length());
            if (endIdx > 20) {
                return afterSummary.substring(0, endIdx).trim();
            }
        }
        
        // Last resort: use response directly (cleaned)
        if (response.length() > 50) {
            String cleaned = response.replaceAll("[{}\\[\\]\"]", " ")
                                    .replaceAll("summary|topics|:", " ")
                                    .replaceAll("\\s+", " ")
                                    .trim();
            if (cleaned.length() > 400) {
                cleaned = cleaned.substring(0, 400) + "...";
            }
            return cleaned;
        }
        
        return null;
    }

    private void requestSummaryFromModel(String subjectName,
                                         Document document,
                                         int chunkCount,
                                         ContextGenerationCallback callback) {
        String chunkSummaries = contextWindowManager.getAllSummaries(document.getId());
        if (TextUtils.isEmpty(chunkSummaries)) {
            chunkSummaries = safeTrim(document.getTextContent());
        } else {
            chunkSummaries = compressSummariesForPrompt(chunkSummaries);
        }

        String prompt = buildPrompt(subjectName, document.getDisplayName(), chunkSummaries);
        AtomicBoolean completed = new AtomicBoolean(false);
        StringBuilder responseBuffer = new StringBuilder();

        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (completed.compareAndSet(false, true)) {
                    Log.w(TAG, "AI summary timed out, using fallback");
                    postProgress(callback, "Creating summary...");
                    
                    // Try to parse whatever we have, or use fallback
                    ContextResult result = null;
                    if (responseBuffer.length() > 0) {
                        result = tryParseModelResponse(responseBuffer.toString(), document, chunkCount);
                    }
                    if (result == null) {
                        Log.d(TAG, "Using fallback result generation");
                        result = buildFallbackResult(document);
                    }
                    postSuccess(callback, result);
                }
            }
        };

        postProgress(callback, "Generating summary and topics...");
        
        // Also set a shorter initial timeout in case AI doesn't respond at all
        final boolean[] gotAnyResponse = {false};
        
        genieWrapper.getResponseForPrompt(prompt, new StringCallback() {
            @Override
            public void onNewString(String response) {
                if (completed.get()) {
                    return;
                }
                
                gotAnyResponse[0] = true;
                
                if (TextUtils.isEmpty(response)) {
                    return;
                }
                
                // Reset timeout on each new data
                mainHandler.removeCallbacks(timeoutRunnable);
                mainHandler.postDelayed(timeoutRunnable, MODEL_TIMEOUT_MS);
                
                // Accumulate the response
                responseBuffer.setLength(0);
                responseBuffer.append(response);
                
                // Try to parse - only succeed if we have valid JSON
                ContextResult parsed = tryParseModelResponse(response, document, chunkCount);
                if (parsed != null && completed.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable);
                    Log.d(TAG, "Successfully parsed AI response");
                    postSuccess(callback, parsed);
                }
            }
        });

        mainHandler.postDelayed(timeoutRunnable, MODEL_TIMEOUT_MS);
        
        // Extra fallback: if no response at all after 10 seconds, use fallback
        mainHandler.postDelayed(() -> {
            if (!gotAnyResponse[0] && completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable);
                Log.w(TAG, "No AI response received, using fallback");
                postProgress(callback, "Creating summary...");
                postSuccess(callback, buildFallbackResult(document));
            }
        }, 10000);
    }

    private String compressSummariesForPrompt(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String[] sections = raw.split("(?=Section \\d+:)");
        StringBuilder builder = new StringBuilder();
        int included = 0;
        
        for (String section : sections) {
            String trimmed = section.trim();
            if (TextUtils.isEmpty(trimmed)) {
                continue;
            }
            
            // Allow more content per section to capture full summaries
            if (trimmed.length() > MAX_SECTION_CHARS) {
                // Try to cut at a sentence boundary
                int cutPoint = trimmed.lastIndexOf(". ", MAX_SECTION_CHARS);
                if (cutPoint > MAX_SECTION_CHARS / 2) {
                    trimmed = trimmed.substring(0, cutPoint + 1);
                } else {
                    trimmed = trimmed.substring(0, MAX_SECTION_CHARS) + "...";
                }
            }
            
            builder.append(trimmed).append("\n\n");
            included++;
            
            // Include more sections before cutting off
            if (included >= MAX_SUMMARY_SECTIONS || builder.length() >= MAX_PROMPT_LENGTH - 500) {
                break;
            }
        }
        
        if (builder.length() == 0) {
            return safeTrim(raw);
        }
        
        Log.d(TAG, "Compressed " + sections.length + " sections to " + included + " for prompt");
        return safeTrim(builder.toString());
    }

    private ContextResult tryParseModelResponse(String response,
                                                Document document,
                                                int chunkCount) {
        if (TextUtils.isEmpty(response)) {
            return null;
        }
        
        String summary = null;
        List<String> topics = new ArrayList<>();
        
        // Try to find JSON first
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            String json = response.substring(start, end + 1);
            try {
                ModelResponse parsed = gson.fromJson(json, ModelResponse.class);
                if (parsed != null) {
                    if (!TextUtils.isEmpty(parsed.summary)) {
                        summary = parsed.summary.trim();
                    }
                    if (parsed.topics != null && !parsed.topics.isEmpty()) {
                        // Pre-filter topics to remove obvious person names before sanitization
                        List<String> preFiltered = new ArrayList<>();
                        for (String topic : parsed.topics) {
                            if (topic != null && !topic.trim().isEmpty()) {
                                String trimmed = topic.trim();
                                // Skip if it looks like a person name
                                if (!isLikelyPersonName(trimmed)) {
                                    preFiltered.add(trimmed);
                                } else {
                                    Log.d(TAG, "Pre-filtered person name from AI response: " + trimmed);
                                }
                            }
                        }
                        topics = sanitizeTopics(preFiltered);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "JSON parse failed, trying text extraction: " + e.getMessage());
            }
        }
        
        // If no summary from JSON, try to extract from response text
        if (TextUtils.isEmpty(summary)) {
            // Look for summary-like content
            String lowerResponse = response.toLowerCase();
            int summaryIdx = lowerResponse.indexOf("summary");
            if (summaryIdx >= 0) {
                // Extract text after "summary"
                String afterSummary = response.substring(Math.min(summaryIdx + 8, response.length()));
                // Clean it up
                afterSummary = afterSummary.replaceAll("^[:\\s\"]+", "").trim();
                int endIdx = afterSummary.indexOf("\"");
                if (endIdx < 0) endIdx = afterSummary.indexOf("\n");
                if (endIdx < 0) endIdx = Math.min(300, afterSummary.length());
                if (endIdx > 20) {
                    summary = afterSummary.substring(0, endIdx).trim();
                }
            }
        }
        
        // If still no summary and response is substantial, use response as summary
        if (TextUtils.isEmpty(summary) && response.length() > 50) {
            // Clean and use response directly
            summary = response.replaceAll("[{}\\[\\]\"]", " ")
                              .replaceAll("summary|topics|:", " ")
                              .replaceAll("\\s+", " ")
                              .trim();
            if (summary.length() > 400) {
                summary = summary.substring(0, 400) + "...";
            }
        }
        
        // If we have something useful, return it
        if (!TextUtils.isEmpty(summary) || !topics.isEmpty()) {
            if (TextUtils.isEmpty(summary)) {
                summary = createBasicSummary(document.getTextContent());
            }
            if (topics.isEmpty()) {
                topics = extractKeyTerms(document.getTextContent());
            }
            return new ContextResult(summary, topics, chunkCount);
        }
        
        return null;
    }

    private ContextResult buildFallbackResult(Document document) {
        String text = document.getTextContent();
        
        // Create a basic summary from first meaningful sentences
        String baseSummary = createBasicSummary(text);
        
        // Extract topics using keyword extraction
        List<String> topics = extractKeyTerms(text);
        
        return new ContextResult(baseSummary, topics, 0);
    }
    
    /**
     * Create a basic summary from the first few meaningful sentences
     */
    private String createBasicSummary(String text) {
        if (TextUtils.isEmpty(text)) {
            return "Content summary not available.";
        }
        
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder summary = new StringBuilder();
        int count = 0;
        
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            // Skip short sentences or those that look administrative
            if (trimmed.length() < 20) continue;
            if (trimmed.matches("(?i).*(professor|instructor|office|email|phone|grading|assignment|due date).*")) continue;
            if (trimmed.matches("(?i)^(lecture|week|chapter|slide|page)\\s*\\d+.*")) continue;
            
            summary.append(trimmed).append(" ");
            count++;
            
            if (count >= 3 || summary.length() > 400) break;
        }
        
        String result = summary.toString().trim();
        if (result.isEmpty()) {
            // Last resort: just take first 300 chars
            result = text.substring(0, Math.min(300, text.length())).trim();
            if (!result.endsWith(".")) result += "...";
        }
        
        return result;
    }
    
    /**
     * Extract key technical terms from text using simple heuristics
     * Improved to avoid extracting person names and administrative content
     */
    private List<String> extractKeyTerms(String text) {
        List<String> terms = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return terms;
        }
        
        // Common technical patterns to look for (avoiding person names)
        String[] patterns = {
            // Terms after "called", "known as", "is a", "refers to" (but not person names)
            "(?:called|known as|is a|refers to|defined as)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?(?:\\s+(?:algorithm|method|process|technique|approach|structure|system|theory|concept))?)",
            // Terms in quotes (technical terms, not names)
            "\"([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)\"",
            // Terms before "algorithm", "method", "process", "technique"
            "([A-Z][a-z]+(?:\\s+[A-Z]?[a-z]+)?)\\s+(?:algorithm|method|process|technique|approach|structure|system|theory)",
            // Common technical compound terms
            "(?:Binary|Linear|Graph|Tree|Hash|Stack|Queue|Array|List|Matrix|Vector)\\s+[A-Z][a-z]+",
        };
        
        Set<String> found = new LinkedHashSet<>();
        
        for (String pattern : patterns) {
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(text);
                while (m.find() && found.size() < 15) {
                    String term = m.group(1);
                    if (term != null) {
                        term = term.trim();
                        // Skip if it looks like a person name (two capitalized words that aren't technical)
                        if (isLikelyPersonName(term)) {
                            Log.d(TAG, "Skipping likely person name: " + term);
                            continue;
                        }
                        if (term.length() >= 3 && term.length() <= 40) {
                            found.add(term);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Pattern matching error: " + e.getMessage());
            }
        }
        
        // Also look for bold/emphasized terms (words that appear multiple times)
        // But exclude common names and administrative terms
        String[] words = text.toLowerCase().split("\\W+");
        Map<String, Integer> wordCounts = new HashMap<>();
        for (String word : words) {
            if (word.length() >= 5 && !isStopWord(word) && !isCommonName(word)) {
                wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
            }
        }
        
        // Add frequently occurring words as topics (but filter out names)
        wordCounts.entrySet().stream()
            .filter(e -> e.getValue() >= 3)
            .filter(e -> !isCommonName(e.getKey()))
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(5)
            .forEach(e -> {
                String capitalized = capitalize(e.getKey());
                if (!isLikelyPersonName(capitalized)) {
                    found.add(capitalized);
                }
            });
        
        terms.addAll(found);
        return sanitizeTopics(terms);
    }
    
    /**
     * Check if a term looks like a person's name
     * Person names typically: 2-3 capitalized words, common first/last name patterns
     */
    private boolean isLikelyPersonName(String term) {
        if (TextUtils.isEmpty(term)) {
            return false;
        }
        
        String trimmed = term.trim();
        String[] words = trimmed.split("\\s+");
        
        // Person names are typically 2-3 words, all capitalized
        if (words.length < 2 || words.length > 3) {
            return false;
        }
        
        // Check if all words start with capital letter (name pattern)
        boolean allCapitalized = true;
        for (String word : words) {
            if (word.length() == 0 || !Character.isUpperCase(word.charAt(0))) {
                allCapitalized = false;
                break;
            }
        }
        
        if (!allCapitalized) {
            return false;
        }
        
        // Check against common first names (simple heuristic)
        String firstWord = words[0].toLowerCase();
        String[] commonFirstNames = {
            "john", "jane", "david", "mary", "michael", "sarah", "james", "lisa",
            "robert", "jennifer", "william", "emily", "richard", "jessica", "thomas",
            "amanda", "chris", "christopher", "daniel", "danielle", "mark", "michelle",
            "paul", "stephanie", "steven", "stephen", "andrew", "ashley", "joshua",
            "kimberly", "kevin", "nicole", "brian", "elizabeth", "george", "lauren",
            "edward", "megan", "ronald", "angela", "timothy", "rebecca", "jason",
            "samantha", "jeffrey", "stephanie", "ryan", "deborah", "jacob", "rachel",
            "gary", "carolyn", "nicholas", "janet", "eric", "catherine", "jonathan",
            "maria", "stephen", "frances", "larry", "christine", "justin", "samantha",
            "scott", "debra", "brandon", "rachel", "benjamin", "cynthia", "samuel",
            "sharon", "frank", "kathleen", "gregory", "amy", "raymond", "anna",
            "alexander", "virginia", "patrick", "rebecca", "jack", "carol", "dennis",
            "joyce", "jerry", "victoria", "tyler", "diana", "aaron", "beverly",
            "jose", "denise", "henry", "marilyn", "adam", "danielle", "douglas",
            "theresa", "nathan", "madison", "zachary", "lauren", "kyle", "olivia",
            "noah", "emma", "ethan", "sophia", "mason", "isabella", "lucas", "ava"
        };
        
        for (String name : commonFirstNames) {
            if (firstWord.equals(name)) {
                Log.d(TAG, "Detected likely person name (common first name): " + trimmed);
                return true;
            }
        }
        
        // Check if it's a pattern like "Dr. Name" or "Prof. Name"
        if (trimmed.matches("(?i)(dr|prof|professor|mr|mrs|ms|miss)\\.?\\s+[A-Z][a-z]+")) {
            return true;
        }
        
        // If it's 2 words and both are capitalized but not technical terms, likely a name
        if (words.length == 2) {
            // Check if it's NOT a known technical term pattern
            String combined = trimmed.toLowerCase();
            if (!combined.contains("search") && !combined.contains("tree") && 
                !combined.contains("graph") && !combined.contains("sort") &&
                !combined.contains("hash") && !combined.contains("stack") &&
                !combined.contains("queue") && !combined.contains("array") &&
                !combined.contains("list") && !combined.contains("matrix") &&
                !combined.contains("vector") && !combined.contains("system") &&
                !combined.contains("theory") && !combined.contains("algorithm")) {
                // Likely a person name
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a word is a common first or last name
     */
    private boolean isCommonName(String word) {
        if (TextUtils.isEmpty(word) || word.length() < 3) {
            return false;
        }
        
        String lower = word.toLowerCase();
        String[] commonNames = {
            "smith", "johnson", "williams", "brown", "jones", "garcia", "miller",
            "davis", "rodriguez", "martinez", "hernandez", "lopez", "wilson",
            "anderson", "thomas", "taylor", "moore", "jackson", "martin", "lee",
            "thompson", "white", "harris", "sanchez", "clark", "ramirez", "lewis",
            "robinson", "walker", "young", "allen", "king", "wright", "scott",
            "torres", "nguyen", "hill", "flores", "green", "adams", "nelson",
            "baker", "hall", "rivera", "campbell", "mitchell", "carter", "roberts"
        };
        
        for (String name : commonNames) {
            if (lower.equals(name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if word is a common stop word
     */
    private boolean isStopWord(String word) {
        String[] stopWords = {"the", "and", "for", "that", "this", "with", "from", "have", "been", 
            "will", "would", "could", "should", "about", "which", "their", "there", "these", 
            "those", "other", "some", "such", "into", "also", "only", "than", "then", "when",
            "where", "while", "what", "more", "most", "each", "both", "after", "before"};
        for (String sw : stopWords) {
            if (sw.equals(word)) return true;
        }
        return false;
    }

    private String capitalize(String input) {
        if (TextUtils.isEmpty(input)) {
            return input;
        }
        input = input.trim();
        if (input.length() == 1) {
            return input.toUpperCase();
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private List<String> sanitizeTopics(List<String> rawTopics) {
        List<String> sanitized = new ArrayList<>();
        if (rawTopics == null) {
            return sanitized;
        }

        // Words/patterns that indicate administrative content (not real topics)
        String[] blacklistPatterns = {
            // Person titles and names
            "(?i)professor", "(?i)prof\\.", "(?i)instructor", "(?i)dr\\.", "(?i)doctor",
            "(?i)mr\\.", "(?i)mrs\\.", "(?i)ms\\.", "(?i)miss",
            // Administrative terms
            "(?i)lecture\\s*\\d*", "(?i)week\\s*\\d*", "(?i)chapter\\s*\\d*",
            "(?i)course", "(?i)syllabus", "(?i)assignment", "(?i)exam",
            "(?i)grading", "(?i)office\\s*hours", "(?i)semester",
            "(?i)spring|fall|summer|winter",
            "(?i)^\\d+$",  // Just numbers
            "(?i)page\\s*\\d*", "(?i)slide\\s*\\d*",
            "(?i)university", "(?i)college", "(?i)department",
            "(?i)introduction$", "(?i)overview$", "(?i)summary$",
            "(?i)^the\\s", "(?i)^a\\s", "(?i)^an\\s",  // Articles at start
            // Common name patterns (2-3 capitalized words that aren't technical)
            "(?i)^[A-Z][a-z]+\\s+[A-Z][a-z]+$",  // Two capitalized words (likely name)
            "(?i)^[A-Z][a-z]+\\s+[A-Z][a-z]+\\s+[A-Z][a-z]+$",  // Three capitalized words
        };

        Set<String> unique = new LinkedHashSet<>();
        for (String topic : rawTopics) {
            String normalized = normalizeTopic(topic);
            if (TextUtils.isEmpty(normalized)) {
                continue;
            }
            
            // Check if it looks like a person name first (more specific check)
            if (isLikelyPersonName(normalized)) {
                Log.d(TAG, "Filtered out topic (person name): " + normalized);
                continue;
            }
            
            // Check against blacklist patterns
            boolean isBlacklisted = false;
            for (String pattern : blacklistPatterns) {
                // Handle both anchored (^...$) and unanchored patterns
                String testPattern = pattern;
                if (!pattern.startsWith("^") && !pattern.endsWith("$")) {
                    // Unanchored pattern - match anywhere
                    testPattern = ".*" + pattern + ".*";
                } else if (pattern.startsWith("^") && pattern.endsWith("$")) {
                    // Fully anchored - use as-is
                    testPattern = pattern;
                } else if (pattern.startsWith("^")) {
                    // Starts with anchor - match at start
                    testPattern = pattern + ".*";
                } else if (pattern.endsWith("$")) {
                    // Ends with anchor - match at end
                    testPattern = ".*" + pattern;
                }
                
                if (normalized.matches(testPattern)) {
                    isBlacklisted = true;
                    Log.d(TAG, "Filtered out topic (blacklisted): " + normalized);
                    break;
                }
            }
            
            // Additional check: if it's 2-3 words all capitalized and not technical, likely a name
            if (!isBlacklisted && normalized.matches("^[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){1,2}$")) {
                String lower = normalized.toLowerCase();
                // Check if it contains technical terms - if not, likely a name
                if (!lower.contains("search") && !lower.contains("tree") && 
                    !lower.contains("graph") && !lower.contains("sort") &&
                    !lower.contains("hash") && !lower.contains("stack") &&
                    !lower.contains("queue") && !lower.contains("array") &&
                    !lower.contains("list") && !lower.contains("matrix") &&
                    !lower.contains("vector") && !lower.contains("system") &&
                    !lower.contains("theory") && !lower.contains("algorithm") &&
                    !lower.contains("method") && !lower.contains("process") &&
                    !lower.contains("structure") && !lower.contains("data") &&
                    !lower.contains("network") && !lower.contains("protocol") &&
                    !lower.contains("memory") && !lower.contains("cache") &&
                    !lower.contains("database") && !lower.contains("server")) {
                    isBlacklisted = true;
                    Log.d(TAG, "Filtered out topic (likely person name): " + normalized);
                }
            }
            
            if (!isBlacklisted && normalized.length() >= 3) {
                unique.add(normalized);
            }
            
            if (unique.size() >= MAX_TOPICS) {
                break;
            }
        }
        sanitized.addAll(unique);
        return sanitized;
    }

    private String normalizeTopic(String topic) {
        if (TextUtils.isEmpty(topic)) {
            return "";
        }
        String trimmed = topic.trim();
        // Strip trailing punctuation to avoid duplicate keys like "Paging." vs "Paging"
        while (trimmed.length() > 0 && !Character.isLetterOrDigit(trimmed.charAt(trimmed.length() - 1))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (TextUtils.isEmpty(trimmed)) {
            return "";
        }

        String[] words = trimmed.split("\\s+");
        if (words.length > MAX_TOPIC_WORDS) {
            words = Arrays.copyOf(words, MAX_TOPIC_WORDS);
        }

        for (int i = 0; i < words.length; i++) {
            words[i] = toTitleCaseWord(words[i]);
        }

        String normalized = TextUtils.join(" ", words).trim();
        return normalized.length() >= 3 ? normalized : "";
    }

    private String toTitleCaseWord(String input) {
        if (TextUtils.isEmpty(input)) {
            return input;
        }
        String lower = input.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String safeTrim(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        if (text.length() <= MAX_PROMPT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_PROMPT_LENGTH) + "...";
    }

    private String buildPrompt(String subjectName, String documentName, String chunkSummaries) {
        // Use Llama 3 format for better instruction following
        String systemInstruction = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
                "You are an AI assistant that analyzes educational documents. " +
                "Your task is to create concise summaries and extract key topics. " +
                "You MUST respond with ONLY a valid JSON object in this exact format: " +
                "{\"summary\": \"...\", \"topics\": [\"...\", \"...\"]}. " +
                "Do not include any other text, markdown, or explanations.<|eot_id|>";
        
        String userPrompt = "<|start_header_id|>user<|end_header_id|>\n\n" +
                "Analyze the following text from '" + documentName + "' (" + subjectName + ").\n\n" +
                "Task:\n" +
                "1. Write a concise summary (max 3 sentences) capturing the core concepts.\n" +
                "2. Extract 5-8 key technical terms or topics.\n\n" +
                "IMPORTANT RULES FOR TOPICS:\n" +
                "- Extract ONLY technical concepts, algorithms, data structures, theories, or methodologies\n" +
                "- DO NOT include person names (professors, authors, instructors)\n" +
                "- DO NOT include administrative terms (course codes, lecture numbers, dates, semesters)\n" +
                "- DO NOT include generic words (introduction, overview, summary)\n" +
                "- Focus on actual subject matter topics (e.g., 'Binary Search', 'Operating Systems', 'Graph Theory')\n" +
                "- Each topic should be 1-4 words describing a technical concept\n\n" +
                "Text to analyze:\n" + truncateContent(chunkSummaries, 5000) + 
                "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
        
        return systemInstruction + userPrompt;
    }
    
    /**
     * Truncate content to fit within model limits
     */
    private String truncateContent(String content, int maxChars) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        // Cut at sentence boundary if possible
        String truncated = content.substring(0, maxChars);
        int lastPeriod = truncated.lastIndexOf('.');
        if (lastPeriod > maxChars * 0.7) {
            return truncated.substring(0, lastPeriod + 1);
        }
        return truncated + "...";
    }

    private void postProgress(ContextGenerationCallback callback, String message) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onProgress(message));
    }

    private void postSuccess(ContextGenerationCallback callback, ContextResult result) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private void postError(ContextGenerationCallback callback, String errorMessage) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onError(errorMessage));
    }
}
