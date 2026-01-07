package com.quicinc.chatapp;

public class Subject {
    private String name;
    private String description;
    private int colorResId;
    private int progress; // 0-100
    private int questionsAnswered;
    private int flashcardsStudied;
    private String pdfUri; // Store the URI of the uploaded PDF

    public Subject(String name, String description, int colorResId, int progress, 
                   int questionsAnswered, int flashcardsStudied) {
        this.name = name;
        this.description = description;
        this.colorResId = colorResId;
        this.progress = progress;
        this.questionsAnswered = questionsAnswered;
        this.flashcardsStudied = flashcardsStudied;
        this.pdfUri = null;
    }

    public Subject(String name, String description, int colorResId, int progress, 
                   int questionsAnswered, int flashcardsStudied, String pdfUri) {
        this.name = name;
        this.description = description;
        this.colorResId = colorResId;
        this.progress = progress;
        this.questionsAnswered = questionsAnswered;
        this.flashcardsStudied = flashcardsStudied;
        this.pdfUri = pdfUri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getColorResId() {
        return colorResId;
    }

    public void setColorResId(int colorResId) {
        this.colorResId = colorResId;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getQuestionsAnswered() {
        return questionsAnswered;
    }

    public void setQuestionsAnswered(int questionsAnswered) {
        this.questionsAnswered = questionsAnswered;
    }

    public int getFlashcardsStudied() {
        return flashcardsStudied;
    }

    public void setFlashcardsStudied(int flashcardsStudied) {
        this.flashcardsStudied = flashcardsStudied;
    }

    public String getPdfUri() {
        return pdfUri;
    }

    public void setPdfUri(String pdfUri) {
        this.pdfUri = pdfUri;
    }

    public boolean hasPdf() {
        return pdfUri != null && !pdfUri.isEmpty();
    }

    public String getProgressLevel() {
        if (progress >= 90) return "Expert";
        else if (progress >= 70) return "Advanced";
        else if (progress >= 50) return "Intermediate";
        else if (progress >= 30) return "Beginner";
        else return "Just Started";
    }
}