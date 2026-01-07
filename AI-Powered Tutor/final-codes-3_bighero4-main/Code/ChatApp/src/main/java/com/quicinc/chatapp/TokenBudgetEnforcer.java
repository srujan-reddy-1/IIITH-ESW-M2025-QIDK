// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.text.TextUtils;
import android.util.Log;

/**
 * TokenBudgetEnforcer - Ensures prompts stay within the model's context window
 * 
 * Context window: 2048 tokens total
 * Safe budget: 1900 tokens (leaving 148 tokens for model overhead/special tokens)
 * This prevents context overflow that causes model failures
 */
public class TokenBudgetEnforcer {
    private static final String TAG = "TokenBudgetEnforcer";
    
    // Model has 2048 context window, we use 1900 to leave room for special tokens
    private static final int MAX_TOTAL_TOKENS = 1900;
    private static final int CHARS_PER_TOKEN = 4; // Conservative estimate: 1 token ≈ 4 chars
    
    /**
     * Ensure the complete prompt (system + context + user input) fits within budget
     * 
     * @param systemInstruction System/role instruction
     * @param contextContent Document context or reference material
     * @param userInput User's question or prompt
     * @return Complete prompt guaranteed to be under token limit
     */
    public static String enforceTokenLimit(String systemInstruction, String contextContent, String userInput) {
        // Sanitize inputs
        systemInstruction = systemInstruction != null ? systemInstruction : "";
        contextContent = contextContent != null ? contextContent : "";
        userInput = userInput != null ? userInput : "";
        
        // Calculate token counts (rough estimate)
        int systemTokens = estimateTokens(systemInstruction);
        int userTokens = estimateTokens(userInput);
        int contextTokens = estimateTokens(contextContent);
        int totalTokens = systemTokens + userTokens + contextTokens;
        
        Log.d(TAG, String.format("Token budget: system=%d, user=%d, context=%d, total=%d/%d", 
            systemTokens, userTokens, contextTokens, totalTokens, MAX_TOTAL_TOKENS));
        
        // If within budget, return as-is
        if (totalTokens <= MAX_TOTAL_TOKENS) {
            return buildPrompt(systemInstruction, contextContent, userInput);
        }
        
        // Need to truncate - prioritize user input, then system, then context
        int availableForContext = MAX_TOTAL_TOKENS - systemTokens - userTokens;
        
        if (availableForContext < 100) {
            // Very little room left - truncate system instruction if needed
            Log.w(TAG, "Extremely tight budget - truncating system instruction");
            int maxSystemTokens = MAX_TOTAL_TOKENS - userTokens - 200; // Reserve 200 tokens for context
            systemInstruction = truncateToTokens(systemInstruction, Math.max(50, maxSystemTokens));
            availableForContext = 200;
        }
        
        // Truncate context to fit
        if (contextTokens > availableForContext) {
            Log.i(TAG, String.format("Truncating context from %d to %d tokens", contextTokens, availableForContext));
            contextContent = truncateToTokens(contextContent, availableForContext);
        }
        
        String finalPrompt = buildPrompt(systemInstruction, contextContent, userInput);
        int finalTokens = estimateTokens(finalPrompt);
        Log.i(TAG, String.format("Final prompt: %d tokens (truncated from %d)", finalTokens, totalTokens));
        
        return finalPrompt;
    }
    
    /**
     * Simple version for cases with only prompt text (no separate system/context)
     */
    public static String enforceTokenLimit(String promptText) {
        if (TextUtils.isEmpty(promptText)) {
            return "";
        }
        
        int tokens = estimateTokens(promptText);
        if (tokens <= MAX_TOTAL_TOKENS) {
            return promptText;
        }
        
        Log.w(TAG, String.format("Truncating prompt from %d to %d tokens", tokens, MAX_TOTAL_TOKENS));
        return truncateToTokens(promptText, MAX_TOTAL_TOKENS);
    }
    
    /**
     * Check if a prompt exceeds the token budget
     */
    public static boolean exceedsBudget(String promptText) {
        return estimateTokens(promptText) > MAX_TOTAL_TOKENS;
    }
    
    /**
     * Get available tokens given already-used tokens
     */
    public static int getAvailableTokens(int usedTokens) {
        return Math.max(0, MAX_TOTAL_TOKENS - usedTokens);
    }
    
    /**
     * Estimate token count from text
     * Uses conservative 4 chars/token ratio
     */
    public static int estimateTokens(String text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN; // Round up
    }
    
    /**
     * Truncate text to fit within token budget
     */
    private static String truncateToTokens(String text, int maxTokens) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        int maxChars = maxTokens * CHARS_PER_TOKEN;
        if (text.length() <= maxChars) {
            return text;
        }
        
        // Truncate and add ellipsis
        String truncated = text.substring(0, Math.max(0, maxChars - 3));
        
        // Try to break at a sentence boundary for cleaner truncation
        int lastPeriod = truncated.lastIndexOf('.');
        int lastNewline = truncated.lastIndexOf('\n');
        int breakPoint = Math.max(lastPeriod, lastNewline);
        
        if (breakPoint > maxChars * 0.8) { // Only use break if it's near the end
            truncated = truncated.substring(0, breakPoint + 1);
        }
        
        return truncated + "...";
    }
    
    /**
     * Build final prompt from components
     */
    private static String buildPrompt(String systemInstruction, String contextContent, String userInput) {
        StringBuilder prompt = new StringBuilder();
        
        if (!TextUtils.isEmpty(systemInstruction)) {
            prompt.append(systemInstruction);
            if (!systemInstruction.endsWith("\n")) {
                prompt.append("\n\n");
            }
        }
        
        if (!TextUtils.isEmpty(contextContent)) {
            prompt.append(contextContent);
            if (!contextContent.endsWith("\n")) {
                prompt.append("\n\n");
            }
        }
        
        if (!TextUtils.isEmpty(userInput)) {
            prompt.append(userInput);
        }
        
        return prompt.toString();
    }
}
