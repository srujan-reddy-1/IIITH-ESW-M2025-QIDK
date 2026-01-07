// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import java.util.ArrayList;
import java.util.List;

/**
 * QuizResult - Stores a complete quiz session with all questions, answers, and results
 */
public class QuizResult {
    public String timestamp;
    public String difficulty;
    public int totalQuestions;
    public int score;
    public List<QuestionResult> questionResults;
    public String pdfSource;  // Name of PDF source for this quiz
    
    public QuizResult(String timestamp, String difficulty, int totalQuestions, int score) {
        this.timestamp = timestamp;
        this.difficulty = difficulty;
        this.totalQuestions = totalQuestions;
        this.score = score;
        this.questionResults = new ArrayList<>();
        this.pdfSource = "";
    }
    
    public QuizResult(String timestamp, String difficulty, int totalQuestions, int score, String pdfSource) {
        this.timestamp = timestamp;
        this.difficulty = difficulty;
        this.totalQuestions = totalQuestions;
        this.score = score;
        this.questionResults = new ArrayList<>();
        this.pdfSource = pdfSource != null ? pdfSource : "";
    }
    
    public void addQuestionResult(String question, String optionA, String optionB, 
                                  String optionC, String optionD, String correctAnswer, 
                                  String userAnswer, boolean isCorrect) {
        questionResults.add(new QuestionResult(question, optionA, optionB, optionC, 
                                              optionD, correctAnswer, userAnswer, isCorrect));
    }
    
    public double getPercentage() {
        return (score * 100.0) / totalQuestions;
    }
    
    public String getGrade() {
        double percentage = getPercentage();
        if (percentage >= 90) return "A+";
        if (percentage >= 80) return "A";
        if (percentage >= 70) return "B";
        if (percentage >= 60) return "C";
        if (percentage >= 50) return "D";
        return "F";
    }
    
    /**
     * Extract key topics from questions where user answered incorrectly
     * Returns list of question text (first 50 chars) from wrong answers
     */
    public List<String> getWeakTopics() {
        List<String> weakTopics = new ArrayList<>();
        for (QuestionResult qr : questionResults) {
            if (!qr.isCorrect && qr.question != null) {
                // Extract topic from question (simplified - take first meaningful words)
                String topic = extractTopicFromQuestion(qr.question);
                if (!topic.isEmpty() && !weakTopics.contains(topic)) {
                    weakTopics.add(topic);
                }
            }
        }
        return weakTopics;
    }
    
    /**
     * Extract topic keywords from a question
     */
    private String extractTopicFromQuestion(String question) {
        if (question == null || question.isEmpty()) return "";
        
        // Remove common question words and get key content
        String cleaned = question.toLowerCase()
            .replaceAll("^(what|which|who|when|where|why|how|is|are|was|were|do|does|did)\\s+", "")
            .replaceAll("\\?", "")
            .trim();
        
        // Take first 50 characters as topic identifier
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50);
        }
        
        return cleaned.trim();
    }
    
    /**
     * Convert to storage string for SharedPreferences
     * Format: timestamp|||difficulty|||totalQuestions|||score|||pdfSource|||question1###optionA###optionB###optionC###optionD###correct###userAnswer###isCorrect|||question2...
     */
    public String toStorageString() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append("|||");
        sb.append(difficulty).append("|||");
        sb.append(totalQuestions).append("|||");
        sb.append(score).append("|||");
        sb.append(pdfSource.replace("|||", " ")).append("|||");
        
        for (int i = 0; i < questionResults.size(); i++) {
            QuestionResult qr = questionResults.get(i);
            sb.append(qr.question.replace("|||", " ").replace("###", " ")).append("###");
            sb.append(qr.optionA.replace("|||", " ").replace("###", " ")).append("###");
            sb.append(qr.optionB.replace("|||", " ").replace("###", " ")).append("###");
            sb.append(qr.optionC.replace("|||", " ").replace("###", " ")).append("###");
            sb.append(qr.optionD.replace("|||", " ").replace("###", " ")).append("###");
            sb.append(qr.correctAnswer).append("###");
            sb.append(qr.userAnswer).append("###");
            sb.append(qr.isCorrect);
            if (i < questionResults.size() - 1) sb.append("|||");
        }
        
        return sb.toString();
    }
    
    /**
     * Parse from storage string
     */
    public static QuizResult fromStorageString(String str) {
        try {
            String[] parts = str.split("\\|\\|\\|");
            if (parts.length < 5) {
                // Backward compatibility: old format without pdfSource
                if (parts.length >= 4) {
                    String timestamp = parts[0];
                    String difficulty = parts[1];
                    int totalQuestions = Integer.parseInt(parts[2]);
                    int score = Integer.parseInt(parts[3]);
                    QuizResult result = new QuizResult(timestamp, difficulty, totalQuestions, score);
                    
                    // Parse question results
                    for (int i = 4; i < parts.length; i++) {
                        String[] qData = parts[i].split("###");
                        if (qData.length >= 8) {
                            result.addQuestionResult(
                                qData[0], qData[1], qData[2], qData[3], qData[4],
                                qData[5], qData[6], Boolean.parseBoolean(qData[7])
                            );
                        }
                    }
                    return result;
                }
                return null;
            }
            
            String timestamp = parts[0];
            String difficulty = parts[1];
            int totalQuestions = Integer.parseInt(parts[2]);
            int score = Integer.parseInt(parts[3]);
            String pdfSource = parts[4];
            
            QuizResult result = new QuizResult(timestamp, difficulty, totalQuestions, score, pdfSource);
            
            // Parse question results (starting from index 5)
            for (int i = 5; i < parts.length; i++) {
                String[] qData = parts[i].split("###");
                if (qData.length >= 8) {
                    result.addQuestionResult(
                        qData[0],  // question
                        qData[1],  // optionA
                        qData[2],  // optionB
                        qData[3],  // optionC
                        qData[4],  // optionD
                        qData[5],  // correctAnswer
                        qData[6],  // userAnswer
                        Boolean.parseBoolean(qData[7])  // isCorrect
                    );
                }
            }
            
            return result;
        } catch (Exception e) {
            android.util.Log.e("QuizResult", "Error parsing quiz result: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Inner class for individual question results
     */
    public static class QuestionResult {
        public String question;
        public String optionA, optionB, optionC, optionD;
        public String correctAnswer;
        public String userAnswer;
        public boolean isCorrect;
        
        public QuestionResult(String question, String optionA, String optionB, String optionC, 
                             String optionD, String correctAnswer, String userAnswer, boolean isCorrect) {
            this.question = question;
            this.optionA = optionA;
            this.optionB = optionB;
            this.optionC = optionC;
            this.optionD = optionD;
            this.correctAnswer = correctAnswer;
            this.userAnswer = userAnswer;
            this.isCorrect = isCorrect;
        }
    }
}
