package com.quicinc.chatapp;

/**
 * Represents a single quiz question with multiple choice options.
 */
public class QuizQuestion {
    private final String question;
    private final String[] options;
    private final int correctAnswer;
    private int selectedAnswer = -1;

    public QuizQuestion(String question, String[] options, int correctAnswer) {
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer;
    }

    public String getQuestion() {
        return question;
    }

    public String[] getOptions() {
        return options;
    }

    public int getCorrectAnswer() {
        return correctAnswer;
    }

    public int getSelectedAnswer() {
        return selectedAnswer;
    }

    public void setSelectedAnswer(int selectedAnswer) {
        this.selectedAnswer = selectedAnswer;
    }

    public boolean isCorrect() {
        return selectedAnswer == correctAnswer;
    }
}