package com.quicinc.chatapp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages persistent storage of dynamically generated quizzes from PDF content
 */
public class QuizBank {
    private static QuizBank instance;
    private File baseDir;
    private static final String TAG = "QuizBank";

    private QuizBank() {
    }

    public static synchronized QuizBank getInstance() {
        if (instance == null) instance = new QuizBank();
        return instance;
    }

    public synchronized void init(Context ctx) {
        if (baseDir != null) return; // already initialized
        baseDir = new File(ctx.getFilesDir(), "quizbank");
        if (!baseDir.exists()) baseDir.mkdirs();
        Log.i(TAG, "QuizBank initialized at: " + baseDir.getAbsolutePath());
    }

    /**
     * Save quiz questions for a subject/course
     */
    public synchronized void saveQuizzes(String subject, String courseTitle, List<QuizQuestion> questions) {
        if (baseDir == null) {
            Log.w(TAG, "QuizBank not initialized");
            return;
        }

        try {
            File subjectDir = new File(baseDir, subject.toLowerCase());
            if (!subjectDir.exists()) subjectDir.mkdirs();

            String sanitizedName = sanitizeFilename(courseTitle);
            File quizFile = new File(subjectDir, sanitizedName + ".json");

            Log.i(TAG, "Saving quizzes: subject=" + subject + ", courseTitle=" + courseTitle + 
                      ", sanitized=" + sanitizedName + ", file=" + quizFile.getAbsolutePath());

            JSONArray jsonArray = new JSONArray();
            for (QuizQuestion q : questions) {
                JSONObject obj = new JSONObject();
                obj.put("question", q.getQuestion());
                obj.put("options", new JSONArray(q.getOptions()));
                obj.put("correctAnswer", q.getCorrectAnswer());
                jsonArray.put(obj);
            }

            try (FileOutputStream fos = new FileOutputStream(quizFile)) {
                fos.write(jsonArray.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            Log.i(TAG, "Saved " + questions.size() + " quizzes to " + quizFile.getName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save quizzes: " + courseTitle, e);
        }
    }

    /**
     * Load quizzes for a specific subject
     */
    public synchronized List<QuizQuestion> getQuizzesForSubject(String subject) {
        List<QuizQuestion> allQuizzes = new ArrayList<>();
        if (baseDir == null) return allQuizzes;

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return allQuizzes;

        File[] quizFiles = subjectDir.listFiles((d, name) -> name.endsWith(".json"));
        if (quizFiles == null) return allQuizzes;

        for (File file : quizFiles) {
            allQuizzes.addAll(loadQuizzesFromFile(file));
        }

        return allQuizzes;
    }

    /**
     * Load quizzes for a specific document within a subject
     */
    public synchronized List<QuizQuestion> getQuizzesForDocument(String subject, String courseTitle) {
        if (baseDir == null) return new ArrayList<>();

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return new ArrayList<>();

        File quizFile = new File(subjectDir, sanitizeFilename(courseTitle) + ".json");
        if (!quizFile.exists()) {
            Log.w(TAG, "No quiz file found for: " + courseTitle);
            return new ArrayList<>();
        }

        return loadQuizzesFromFile(quizFile);
    }

    /**
     * Load all quizzes from all subjects
     */
    public synchronized List<QuizQuestion> getAllQuizzes() {
        List<QuizQuestion> allQuizzes = new ArrayList<>();
        if (baseDir == null) return allQuizzes;

        File[] subjectDirs = baseDir.listFiles(File::isDirectory);
        if (subjectDirs == null) return allQuizzes;

        for (File subjectDir : subjectDirs) {
            File[] quizFiles = subjectDir.listFiles((d, name) -> name.endsWith(".json"));
            if (quizFiles != null) {
                for (File file : quizFiles) {
                    allQuizzes.addAll(loadQuizzesFromFile(file));
                }
            }
        }

        return allQuizzes;
    }

    /**
     * Get random sample of quizzes
     */
    public synchronized List<QuizQuestion> getRandomQuizzes(int count) {
        List<QuizQuestion> all = getAllQuizzes();
        Collections.shuffle(all);
        return all.subList(0, Math.min(count, all.size()));
    }

    /**
     * Get list of quiz sources (document titles) for a subject
     */
    public synchronized List<String> getQuizSourcesForSubject(String subject) {
        List<String> sources = new ArrayList<>();
        if (baseDir == null) return sources;

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return sources;

        File[] quizFiles = subjectDir.listFiles((d, name) -> name.endsWith(".json"));
        if (quizFiles == null) return sources;

        for (File file : quizFiles) {
            // Remove .json extension and un-sanitize filename
            String name = file.getName().replace(".json", "").replace("_", " ");
            sources.add(name);
        }

        return sources;
    }

    /**
     * Delete quizzes for a specific document
     */
    public synchronized boolean deleteQuizzesForDocument(String subject, String courseTitle) {
        if (baseDir == null) {
            Log.w(TAG, "QuizBank not initialized");
            return false;
        }

        try {
            File subjectDir = new File(baseDir, subject.toLowerCase());
            if (!subjectDir.exists()) return false;

            File quizFile = new File(subjectDir, sanitizeFilename(courseTitle) + ".json");
            if (quizFile.exists()) {
                boolean deleted = quizFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted quizzes for " + courseTitle);
                }
                return deleted;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete quizzes: " + courseTitle, e);
        }
        return false;
    }

    /**
     * Get count of questions for a specific document
     */
    public synchronized int getQuizCountForDocument(String subject, String courseTitle) {
        if (baseDir == null) return 0;

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return 0;

        File quizFile = new File(subjectDir, sanitizeFilename(courseTitle) + ".json");
        if (!quizFile.exists()) return 0;

        List<QuizQuestion> quizzes = loadQuizzesFromFile(quizFile);
        return quizzes.size();
    }

    /**
     * Get random sample for a specific subject
     */
    public synchronized List<QuizQuestion> getRandomQuizzesForSubject(String subject, int count) {
        List<QuizQuestion> all = getQuizzesForSubject(subject);
        Collections.shuffle(all);
        return all.subList(0, Math.min(count, all.size()));
    }
    
    /**
     * Clear all quiz data (useful for debugging or resetting)
     */
    public synchronized boolean clearAllQuizzes() {
        if (baseDir == null) {
            Log.w(TAG, "QuizBank not initialized");
            return false;
        }
        
        try {
            return deleteRecursive(baseDir);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear all quizzes", e);
            return false;
        }
    }
    
    /**
     * Recursively delete directory and contents
     */
    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        boolean deleted = fileOrDirectory.delete();
        if (deleted) {
            Log.i(TAG, "Deleted: " + fileOrDirectory.getName());
        }
        return deleted;
    }

    private List<QuizQuestion> loadQuizzesFromFile(File file) {
        List<QuizQuestion> quizzes = new ArrayList<>();
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            
            // Validate JSON before parsing
            if (jsonStr.trim().isEmpty()) {
                Log.w(TAG, "Empty quiz file: " + file.getName());
                return quizzes;
            }
            
            JSONArray jsonArray = new JSONArray(jsonStr);

            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    
                    // Validate required fields
                    if (!obj.has("question") || !obj.has("options") || !obj.has("correctAnswer")) {
                        Log.w(TAG, "Skipping invalid quiz question in " + file.getName() + " at index " + i);
                        continue;
                    }
                    
                    String question = obj.getString("question");
                    JSONArray optionsArray = obj.getJSONArray("options");
                    
                    if (optionsArray.length() < 2) {
                        Log.w(TAG, "Skipping quiz with insufficient options in " + file.getName());
                        continue;
                    }
                    
                    String[] options = new String[optionsArray.length()];
                    for (int j = 0; j < optionsArray.length(); j++) {
                        options[j] = optionsArray.getString(j);
                    }
                    int correctAnswer = obj.getInt("correctAnswer");
                    
                    if (correctAnswer < 0 || correctAnswer >= options.length) {
                        Log.w(TAG, "Invalid correct answer index in " + file.getName());
                        continue;
                    }

                    quizzes.add(new QuizQuestion(question, options, correctAnswer));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing quiz question at index " + i + " in " + file.getName(), e);
                    // Continue with next question instead of failing entire file
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read quiz file: " + file.getName(), e);
        } catch (JSONException e) {
            Log.e(TAG, "Corrupted JSON in quiz file: " + file.getName() + " - Consider deleting this file", e);
            // Optionally: auto-delete corrupted file
            try {
                file.delete();
                Log.i(TAG, "Deleted corrupted quiz file: " + file.getName());
            } catch (Exception deleteEx) {
                Log.e(TAG, "Failed to delete corrupted file", deleteEx);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading quiz file: " + file.getName(), e);
        }
        return quizzes;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
