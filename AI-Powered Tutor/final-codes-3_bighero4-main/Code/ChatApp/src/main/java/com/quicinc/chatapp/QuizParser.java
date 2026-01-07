package com.quicinc.chatapp;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM-generated quiz content from markdown format into structured QuizQuestion objects
 */
public class QuizParser {
    private static final String TAG = "QuizParser";

    /**
     * Parse quiz questions from LLM response
     * Expected format:
     * 1. Question text here?
     * A) Option 1
     * B) Option 2
     * C) Option 3
     * D) Option 4
     * Answer: B
     */
    public static List<QuizQuestion> parseQuizResponse(String response) {
        List<QuizQuestion> questions = new ArrayList<>();
        final int MAX_QUESTIONS = 10;  // Hard limit to prevent AI from generating too many

        try {
            Log.d(TAG, "=== QUIZ PARSER DEBUG ===");
            Log.d(TAG, "Raw response length: " + response.length());
            Log.d(TAG, "Raw response preview: " + response.substring(0, Math.min(150, response.length())));
            
            // Clean the response first - remove AI intro/outro
            response = cleanQuizResponse(response);
            
            Log.d(TAG, "After cleaning length: " + response.length());
            Log.d(TAG, "After cleaning preview: " + response.substring(0, Math.min(150, response.length())));
            
            // Split by question numbers (1., 2., 3., etc.) at start of line
            // Handle both plain "1." and bold "**1." formats
            String[] questionBlocks = response.split("(?m)^(?=\\*{0,2}\\d+\\.)");
            
            Log.d(TAG, "Split into " + questionBlocks.length + " blocks");
            for (int i = 0; i < Math.min(3, questionBlocks.length); i++) {
                String blockPreview = questionBlocks[i].length() > 50 ? 
                    questionBlocks[i].substring(0, 50) : questionBlocks[i];
                Log.d(TAG, "Block " + i + " (len=" + questionBlocks[i].length() + "): '" + blockPreview + "'");
                if (questionBlocks[i].length() > 0) {
                    Log.d(TAG, "  First 3 chars: [" + (int)questionBlocks[i].charAt(0) + ", " + 
                          (questionBlocks[i].length() > 1 ? (int)questionBlocks[i].charAt(1) : 0) + ", " +
                          (questionBlocks[i].length() > 2 ? (int)questionBlocks[i].charAt(2) : 0) + "]");
                }
            }

            for (String block : questionBlocks) {
                String trimmedBlock = block.trim();
                
                if (trimmedBlock.isEmpty()) {
                    Log.v(TAG, "Skipping empty block");
                    continue;
                }
                
                // Skip blocks that don't start with a number followed by period (intro text)
                // Check if starts with optional bold markers (**), then digits, then period
                // Use (?s) for DOTALL mode to match newlines with .*
                if (!trimmedBlock.matches("(?s)^\\*{0,2}\\d+\\..*")) {
                    String preview = trimmedBlock.substring(0, Math.min(50, trimmedBlock.length()));
                    Log.v(TAG, "Skipping non-numbered block: '" + preview + "'");
                    if (trimmedBlock.length() > 0) {
                        Log.v(TAG, "  First char: '" + trimmedBlock.charAt(0) + "' (code: " + (int)trimmedBlock.charAt(0) + ")");
                        if (trimmedBlock.length() > 1) {
                            Log.v(TAG, "  Second char: '" + trimmedBlock.charAt(1) + "' (code: " + (int)trimmedBlock.charAt(1) + ")");
                        }
                    }
                    continue;
                }
                
                // Stop after MAX_QUESTIONS to enforce limit
                if (questions.size() >= MAX_QUESTIONS) {
                    Log.w(TAG, "Reached max question limit (" + MAX_QUESTIONS + "), ignoring remaining questions");
                    break;
                }

                QuizQuestion question = parseQuestionBlock(trimmedBlock);
                if (question != null) {
                    questions.add(question);
                    Log.d(TAG, "Successfully parsed question #" + questions.size());
                } else {
                    Log.w(TAG, "Failed to parse question block");
                }
            }

            Log.i(TAG, "Parsed " + questions.size() + " questions from response");
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse quiz response", e);
        }

        return questions;
    }

    private static QuizQuestion parseQuestionBlock(String block) {
        try {
            Log.v(TAG, "=== Parsing question block ===");
            Log.v(TAG, "Block preview: " + block.substring(0, Math.min(100, block.length())));
            
            // Clean the block first - remove AI intro/outro text
            block = cleanQuizBlock(block);
            
            Log.v(TAG, "After cleaning: " + block.substring(0, Math.min(100, block.length())));
            
            // Extract question text (everything before first option)
            // Handle both plain "1." and bold "**1." formats
            Pattern questionPattern = Pattern.compile("\\*{0,2}\\d+\\.\\s*(.+?)(?=[A-D]\\))", Pattern.DOTALL);
            Matcher questionMatcher = questionPattern.matcher(block);
            
            String questionText = "";
            if (questionMatcher.find()) {
                questionText = questionMatcher.group(1).trim();
                // Remove bold markers from question text
                questionText = questionText.replaceAll("\\*\\*", "");
            } else {
                // Fallback: try to get first line
                String[] lines = block.split("\\n");
                if (lines.length > 0) {
                    questionText = lines[0].replaceFirst("^\\d+\\.\\s*", "").trim();
                }
            }

            if (questionText.isEmpty()) {
                Log.w(TAG, "Could not extract question text from block");
                return null;
            }
            
            Log.v(TAG, "Question text: " + questionText.substring(0, Math.min(50, questionText.length())));

            // Extract options (A), B), C), D))
            List<String> options = new ArrayList<>();
            Pattern optionPattern = Pattern.compile("([A-D])\\)\\s*(.+?)(?=[A-D]\\)|Answer:|$)", Pattern.DOTALL);
            Matcher optionMatcher = optionPattern.matcher(block);

            while (optionMatcher.find()) {
                String optionText = optionMatcher.group(2).trim();
                // Clean each option - stop at newline or next numbered item
                optionText = cleanOption(optionText);
                if (!optionText.isEmpty()) {
                    options.add(optionText);
                }
            }

            // If we didn't get 4 options, try alternative parsing
            if (options.size() < 4) {
                options.clear();
                String[] lines = block.split("\\n");
                for (String line : lines) {
                    if (line.matches("^[A-D]\\).*")) {
                        String optionText = line.replaceFirst("^[A-D]\\)\\s*", "").trim();
                        optionText = cleanOption(optionText);
                        if (!optionText.isEmpty()) {
                            options.add(optionText);
                        }
                    }
                }
            }

            if (options.size() < 2) {
                Log.w(TAG, "Not enough options found for question: " + questionText);
                Log.w(TAG, "Block was: " + block);
                return null;
            }
            
            Log.v(TAG, "Found " + options.size() + " options");

            // Ensure we have exactly 4 options (pad if necessary)
            while (options.size() < 4) {
                options.add("Option " + (options.size() + 1));
            }
            if (options.size() > 4) {
                options = options.subList(0, 4);
            }

            // Extract correct answer
            Pattern answerPattern = Pattern.compile("Answer:\\s*([A-D])", Pattern.CASE_INSENSITIVE);
            Matcher answerMatcher = answerPattern.matcher(block);

            int correctAnswer = 0; // Default to first option
            if (answerMatcher.find()) {
                String answerLetter = answerMatcher.group(1).toUpperCase();
                correctAnswer = answerLetter.charAt(0) - 'A';
            } else {
                Log.w(TAG, "Could not find answer, defaulting to A");
            }
            
            Log.v(TAG, "Correct answer: " + (char)('A' + correctAnswer));

            // Ensure answer index is valid
            if (correctAnswer < 0 || correctAnswer >= options.size()) {
                correctAnswer = 0;
            }
            
            Log.v(TAG, "✓ Successfully created question");

            return new QuizQuestion(questionText, options.toArray(new String[0]), correctAnswer);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse question block", e);
            Log.e(TAG, "Block was: " + block);
            return null;
        }
    }

    /**
     * Clean AI response - remove intro/outro text before parsing
     */
    private static String cleanQuizResponse(String response) {
        // Remove common AI intro phrases (using (?m) for multiline matching)
        response = response.replaceAll("(?i)\\[BEGIN\\]:?.*?\\n", "");
        response = response.replaceAll("(?im)^.*?here are (the )?\\d+.*?questions?:?.*?\\n", "");
        response = response.replaceAll("(?im)^.*?multiple[- ]choice.*?\\n", "");
        response = response.replaceAll("(?im)^.*?based on.*?content.*?\\n", "");
        
        // Remove creative intro sections like "**Quiz Time!**" or "Test your knowledge"
        response = response.replaceAll("(?im)^\\*\\*Quiz Time!?\\*\\*.*?\\n", "");
        response = response.replaceAll("(?im)^Test your knowledge.*?\\n", "");
        response = response.replaceAll("(?im)^.*?see how well you.*?\\n", "");
        
        // Remove common AI outro phrases and markers
        response = response.replaceAll("(?i)I hope these.*$", "");
        response = response.replaceAll("(?i)let me know if.*$", "");
        response = response.replaceAll("(?i)\\[END\\].*?$", ""); // Remove [END] and anything after
        response = response.replaceAll("\\[END\\]", ""); // Remove [END] marker anywhere
        
        return response.trim();
    }

    /**
     * Clean quiz block - remove AI intro/outro text
     */
    private static String cleanQuizBlock(String block) {
        // Remove common AI phrases at the start
        block = block.replaceAll("(?i)^.*?here are.*?questions?.*?\\n", "");
        block = block.replaceAll("(?i)^.*?based on.*?content.*?\\n", "");
        block = block.replaceAll("(?i)^.*?I've created.*?\\n", "");
        block = block.replaceAll("(?i)^.*?below (is|are).*?\\n", "");
        
        // Remove common AI phrases at the end
        block = block.replaceAll("(?i)I hope these.*$", "");
        block = block.replaceAll("(?i)let me know if.*$", "");
        block = block.replaceAll("(?i)these questions.*$", "");
        
        return block.trim();
    }
    
    /**
     * Clean individual option text - stop at newlines, remove junk
     */
    private static String cleanOption(String option) {
        // Stop at first newline (don't let options span multiple lines)
        option = option.split("\\n")[0].trim();
        
        // Stop at any "Here are" or similar intro text
        option = option.split("(?i)here are")[0].trim();
        option = option.split("(?i)based on")[0].trim();
        
        // Remove any remaining numbered list markers that might have leaked in
        option = option.replaceAll("^\\d+\\.\\s*", "");
        
        // Limit length to prevent ridiculously long options
        if (option.length() > 200) {
            option = option.substring(0, 197) + "...";
        }
        
        return option.trim();
    }

    /**
     * Generate a prompt for the LLM to create quiz questions
     */
    public static String generateQuizPrompt(String courseContent) {
        // NOTE: Content should already be truncated by QuizGenerationService to fit token budget
        // This method just builds the prompt template - don't truncate here to avoid double truncation
        
        return "You are creating a quiz to test understanding of CORE CONCEPTS AND TECHNICAL KNOWLEDGE from educational content.\n\n" +
               "CRITICAL RULES:\n" +
               "- Focus ONLY on meaningful technical content: definitions, algorithms, theories, processes, formulas, applications\n" +
               "- DO NOT ask about: instructor names, course codes, page numbers, dates, administrative details, or metadata\n" +
               "- Ask about: What concepts mean, how things work, when to use techniques, why methods are important, problem-solving\n" +
               "- Make questions test understanding, not memorization of trivia\n\n" +
               "Generate EXACTLY 10 multiple choice questions. STOP after question 10.\n" +
               "Each question must have 4 options (A, B, C, D) and indicate the correct answer.\n\n" +
               "Format each question EXACTLY like this:\n" +
               "1. What is the time complexity of binary search?\n" +
               "A) O(n)\n" +
               "B) O(log n)\n" +
               "C) O(n²)\n" +
               "D) O(1)\n" +
               "Answer: B\n\n" +
               "Content:\n" + courseContent + "\n\n" +
               "Generate EXACTLY 10 meaningful quiz questions testing technical understanding:";
    }
}
