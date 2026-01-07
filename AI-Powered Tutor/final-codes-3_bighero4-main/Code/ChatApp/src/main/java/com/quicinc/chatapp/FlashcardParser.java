package com.quicinc.chatapp;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM-generated flashcard content from markdown format into structured Flashcard objects
 */
public class FlashcardParser {
    private static final String TAG = "FlashcardParser";

    /**
     * Parse flashcards from LLM response
     * Expected format:
     * 1. **Term**: Definition here
     * OR
     * 1. Term
     * Definition: Explanation here
     */
    public static List<Flashcard> parseFlashcardResponse(String response, String subject) {
        List<Flashcard> flashcards = new ArrayList<>();

        try {
            Log.d(TAG, "Original response length: " + response.length());
            Log.d(TAG, "Response preview (first 300 chars): " + response.substring(0, Math.min(300, response.length())));
            
            // Clean up the response first - remove common AI-generated intro/outro text
            response = cleanResponse(response);
            
            Log.d(TAG, "After cleaning, length: " + response.length());
            Log.d(TAG, "Cleaned response preview (first 300 chars): " + response.substring(0, Math.min(300, response.length())));
            
            // Split by numbered items (1., 2., 3., etc.)
            // Use (?m) multiline mode and require start of line or newline before number
            String[] blocks = response.split("(?m)(?=^\\d+\\.)");
            Log.d(TAG, "Split into " + blocks.length + " blocks");

            for (int i = 0; i < blocks.length; i++) {
                String block = blocks[i].trim();
                if (block.isEmpty()) continue;
                
                Log.d(TAG, "Processing block " + i + ": " + block.substring(0, Math.min(50, block.length())));

                Flashcard flashcard = parseFlashcardBlock(block, subject);
                if (flashcard != null) {
                    flashcards.add(flashcard);
                    Log.d(TAG, "Successfully parsed flashcard: " + flashcard.getFront());
                } else {
                    Log.w(TAG, "Failed to parse block " + i);
                }
            }

            Log.i(TAG, "Parsed " + flashcards.size() + " flashcards from response");
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse flashcard response", e);
        }

        return flashcards;
    }
    
    /**
     * Clean up LLM response by removing intro/outro text
     */
    private static String cleanResponse(String response) {
        // Remove everything before [BEGIN] marker if present
        if (response.contains("[BEGIN]")) {
            response = response.substring(response.indexOf("[BEGIN]") + 7);
        }
        
        // Remove common AI intro phrases (multiline mode)
        response = response.replaceAll("(?im)^.*?here are.*?flashcards.*?\\n", "");
        response = response.replaceAll("(?im)^.*?based on.*?content.*?\\n", "");
        response = response.replaceAll("(?im)^.*?I've created.*?\\n", "");
        response = response.replaceAll("(?im)^.*?below are.*?\\n", "");
        
        // Remove the example flashcards from the prompt (Binary Search and Stack)
        // Remove Binary Search - everything from "1. **Binary Search**" to before next numbered item
        response = response.replaceAll("(?s)1\\.\\s*\\*\\*Binary Search\\*\\*.*?(?=\\d+\\.\\s*\\*\\*)", "");
        // Remove Stack - everything from "2. **Stack**" to before next numbered item or end
        response = response.replaceAll("(?s)2\\.\\s*\\*\\*Stack\\*\\*.*?(?=\\d+\\.\\s*\\*\\*|\\[END\\]|$)", "");
        
        // Remove [END] marker and anything after it
        int endIndex = response.indexOf("[END]");
        if (endIndex != -1) {
            response = response.substring(0, endIndex);
        }
        
        // Remove common AI outro phrases
        response = response.replaceAll("(?i)these flashcards.*$", "");
        response = response.replaceAll("(?i)I hope these.*$", "");
        response = response.replaceAll("(?i)let me know if.*$", "");
        
        return response.trim();
    }

    private static Flashcard parseFlashcardBlock(String block, String subject) {
        try {
            // Remove the number prefix first
            block = block.replaceFirst("^\\d+\\.\\s*", "").trim();
            
            // Try format: "**Term**: Definition"
            Pattern pattern1 = Pattern.compile("\\*\\*(.+?)\\*\\*:\\s*(.+?)(?=\\n\\n|$)", Pattern.DOTALL);
            Matcher matcher1 = pattern1.matcher(block);
            
            if (matcher1.find()) {
                String term = matcher1.group(1).trim();
                String definition = matcher1.group(2).trim();
                // Stop at first paragraph break or next numbered item
                definition = definition.split("\\n\\n")[0].split("(?=\\d+\\.)")[0].trim();
                // Clean up any remaining markers
                definition = cleanDefinition(definition);
                return new Flashcard(term, definition, subject);
            }

            // Try format: "Term\nDefinition: text"
            Pattern pattern2 = Pattern.compile("(.+?)\\s*(?:Definition|Answer|Explanation):\\s*(.+?)(?=\\n\\n|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher matcher2 = pattern2.matcher(block);
            
            if (matcher2.find()) {
                String term = matcher2.group(1).trim();
                String definition = matcher2.group(2).trim();
                definition = definition.split("\\n\\n")[0].split("(?=\\d+\\.)")[0].trim();
                definition = cleanDefinition(definition);
                return new Flashcard(term, definition, subject);
            }

            // Try format: "Term\n\nDefinition text" (split by double newline)
            String[] parts = block.split("\\n\\n", 2);
            if (parts.length == 2) {
                String term = parts[0].trim().replaceAll("\\*\\*", ""); // Remove bold markers
                String definition = parts[1].split("(?=\\d+\\.)")[0].trim();
                definition = cleanDefinition(definition);
                if (!term.isEmpty() && !definition.isEmpty()) {
                    return new Flashcard(term, definition, subject);
                }
            }

            // Fallback: split by first newline
            String[] lines = block.split("\\n", 2);
            if (lines.length >= 2) {
                String term = lines[0].trim().replaceAll("\\*\\*", "");
                String definition = lines[1].split("\\n\\n")[0].split("(?=\\d+\\.)")[0].trim();
                definition = cleanDefinition(definition);
                if (!term.isEmpty() && !definition.isEmpty()) {
                    return new Flashcard(term, definition, subject);
                }
            }

            Log.w(TAG, "Could not parse flashcard block: " + block.substring(0, Math.min(50, block.length())));
            return null;

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse flashcard block", e);
            return null;
        }
    }
    
    /**
     * Clean up definition text by removing extra junk
     */
    private static String cleanDefinition(String definition) {
        // Remove any "Here are..." or similar phrases that snuck in
        definition = definition.replaceAll("(?i)^.*?here are.*?:\\s*", "");
        definition = definition.replaceAll("(?i)^.*?based on.*?:\\s*", "");
        
        // Remove markdown bold markers
        definition = definition.replaceAll("\\*\\*", "");
        
        // Trim excessive whitespace
        definition = definition.replaceAll("\\s+", " ").trim();
        
        // Limit length to reasonable size (500 chars max)
        if (definition.length() > 500) {
            definition = definition.substring(0, 497) + "...";
        }
        
        return definition;
    }

    /**
     * Generate a prompt for the LLM to create flashcards
     */
    public static String generateFlashcardPrompt(String courseContent) {
        // Truncate content MORE aggressively to prevent OOM (exit 137)
        // Keep only first 2000 chars to reduce memory pressure
        String truncatedContent = courseContent.length() > 2000 
            ? courseContent.substring(0, 2000) + "..." 
            : courseContent;

        return "You are creating flashcards to help students learn CORE CONCEPTS AND TECHNICAL KNOWLEDGE from educational content.\n\n" +
               "CRITICAL RULES:\n" +
               "- Focus ONLY on meaningful technical content: key terms, definitions, algorithms, theories, formulas, processes, applications\n" +
               "- DO NOT create cards for: instructor names, course codes, page numbers, dates, administrative details, or metadata\n" +
               "- Create cards that help students: understand concepts, remember definitions, learn techniques, grasp relationships\n" +
               "- Keep definitions clear, concise, and educational\n\n" +
               "Generate exactly 10 flashcards for studying.\n" +
               "Format each flashcard EXACTLY like this:\n" +
               "1. **Binary Search**: An efficient algorithm for finding an item in a sorted list by repeatedly dividing the search interval in half. Time complexity is O(log n).\n\n" +
               "2. **Stack**: A Last-In-First-Out (LIFO) data structure where elements are added and removed from the same end, commonly used for function calls and undo operations.\n\n" +
               "Content:\n" + truncatedContent + "\n\n" +
               "Generate 10 meaningful flashcards testing technical knowledge:";
    }
}
