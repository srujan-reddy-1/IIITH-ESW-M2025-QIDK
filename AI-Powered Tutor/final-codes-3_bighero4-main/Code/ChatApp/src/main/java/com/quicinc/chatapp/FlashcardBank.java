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
 * Manages persistent storage of dynamically generated flashcards from PDF content
 */
public class FlashcardBank {
    private static FlashcardBank instance;
    private File baseDir;
    private static final String TAG = "FlashcardBank";

    private FlashcardBank() {
    }

    public static synchronized FlashcardBank getInstance() {
        if (instance == null) instance = new FlashcardBank();
        return instance;
    }

    public synchronized void init(Context ctx) {
        if (baseDir != null) return; // already initialized
        baseDir = new File(ctx.getFilesDir(), "flashcardbank");
        if (!baseDir.exists()) baseDir.mkdirs();
        Log.i(TAG, "FlashcardBank initialized at: " + baseDir.getAbsolutePath());
    }

    /**
     * Save flashcards for a subject/course
     */
    public synchronized void saveFlashcards(String subject, String courseTitle, List<Flashcard> flashcards) {
        if (baseDir == null) {
            Log.w(TAG, "FlashcardBank not initialized");
            return;
        }

        try {
            File subjectDir = new File(baseDir, subject.toLowerCase());
            if (!subjectDir.exists()) subjectDir.mkdirs();

            String sanitizedName = sanitizeFilename(courseTitle);
            File flashcardFile = new File(subjectDir, sanitizedName + ".json");

            Log.i(TAG, "Saving flashcards: subject=" + subject + ", courseTitle=" + courseTitle + 
                      ", sanitized=" + sanitizedName + ", file=" + flashcardFile.getAbsolutePath());

            JSONArray jsonArray = new JSONArray();
            for (Flashcard fc : flashcards) {
                JSONObject obj = new JSONObject();
                obj.put("term", fc.getFront());
                obj.put("definition", fc.getBack());
                obj.put("subject", fc.getSubject());
                jsonArray.put(obj);
            }

            try (FileOutputStream fos = new FileOutputStream(flashcardFile)) {
                fos.write(jsonArray.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            Log.i(TAG, "Saved " + flashcards.size() + " flashcards to " + flashcardFile.getName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save flashcards: " + courseTitle, e);
        }
    }

    /**
     * Load flashcards for a specific subject
     */
    public synchronized List<Flashcard> getFlashcardsForSubject(String subject) {
        List<Flashcard> allFlashcards = new ArrayList<>();
        if (baseDir == null) return allFlashcards;

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return allFlashcards;

        File[] flashcardFiles = subjectDir.listFiles((d, name) -> name.endsWith(".json"));
        if (flashcardFiles == null) return allFlashcards;

        for (File file : flashcardFiles) {
            allFlashcards.addAll(loadFlashcardsFromFile(file));
        }

        return allFlashcards;
    }

    /**
     * Load flashcards for a specific document within a subject
     */
    public synchronized List<Flashcard> getFlashcardsForDocument(String subject, String courseTitle) {
        if (baseDir == null) return new ArrayList<>();

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return new ArrayList<>();

        File flashcardFile = new File(subjectDir, sanitizeFilename(courseTitle) + ".json");
        if (!flashcardFile.exists()) {
            Log.w(TAG, "No flashcard file found for: " + courseTitle);
            return new ArrayList<>();
        }

        return loadFlashcardsFromFile(flashcardFile);
    }

    /**
     * Load all flashcards from all subjects
     */
    public synchronized List<Flashcard> getAllFlashcards() {
        List<Flashcard> allFlashcards = new ArrayList<>();
        if (baseDir == null) return allFlashcards;

        File[] subjectDirs = baseDir.listFiles(File::isDirectory);
        if (subjectDirs == null) return allFlashcards;

        for (File subjectDir : subjectDirs) {
            File[] flashcardFiles = subjectDir.listFiles((d, name) -> name.endsWith(".json"));
            if (flashcardFiles != null) {
                for (File file : flashcardFiles) {
                    allFlashcards.addAll(loadFlashcardsFromFile(file));
                }
            }
        }

        return allFlashcards;
    }

    /**
     * Get random sample of flashcards
     */
    public synchronized List<Flashcard> getRandomFlashcards(int count) {
        List<Flashcard> all = getAllFlashcards();
        Collections.shuffle(all);
        return all.subList(0, Math.min(count, all.size()));
    }

    /**
     * Delete flashcards for a specific document
     */
    public synchronized boolean deleteFlashcardsForDocument(String subject, String courseTitle) {
        if (baseDir == null) {
            Log.w(TAG, "FlashcardBank not initialized");
            return false;
        }

        try {
            File subjectDir = new File(baseDir, subject.toLowerCase());
            if (!subjectDir.exists()) return false;

            File flashcardFile = new File(subjectDir, sanitizeFilename(courseTitle) + ".json");
            if (flashcardFile.exists()) {
                boolean deleted = flashcardFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted flashcards for " + courseTitle);
                }
                return deleted;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete flashcards: " + courseTitle, e);
        }
        return false;
    }

    /**
     * Get count of flashcards for a specific document
     */
    public synchronized int getFlashcardCountForDocument(String subject, String courseTitle) {
        if (baseDir == null) return 0;

        File subjectDir = new File(baseDir, subject.toLowerCase());
        if (!subjectDir.exists()) return 0;

        File flashcardFile = new File(subjectDir, sanitizeFilename(courseTitle) + ".json");
        if (!flashcardFile.exists()) return 0;

        List<Flashcard> flashcards = loadFlashcardsFromFile(flashcardFile);
        return flashcards.size();
    }
    
    /**
     * Clear all flashcard data (useful for debugging or resetting)
     */
    public synchronized boolean clearAllFlashcards() {
        if (baseDir == null) {
            Log.w(TAG, "FlashcardBank not initialized");
            return false;
        }
        
        try {
            return deleteRecursive(baseDir);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear all flashcards", e);
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

    private List<Flashcard> loadFlashcardsFromFile(File file) {
        List<Flashcard> flashcards = new ArrayList<>();
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            
            // Validate JSON before parsing
            if (jsonStr.trim().isEmpty()) {
                Log.w(TAG, "Empty flashcard file: " + file.getName());
                return flashcards;
            }
            
            JSONArray jsonArray = new JSONArray(jsonStr);

            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    
                    // Validate required fields
                    if (!obj.has("term") || !obj.has("definition")) {
                        Log.w(TAG, "Skipping invalid flashcard in " + file.getName() + " at index " + i);
                        continue;
                    }
                    
                    String term = obj.getString("term");
                    String definition = obj.getString("definition");
                    String subject = obj.optString("subject", "");
                    
                    if (term.trim().isEmpty() || definition.trim().isEmpty()) {
                        Log.w(TAG, "Skipping flashcard with empty content in " + file.getName());
                        continue;
                    }

                    flashcards.add(new Flashcard(term, definition, subject));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing flashcard at index " + i + " in " + file.getName(), e);
                    // Continue with next flashcard instead of failing entire file
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read flashcard file: " + file.getName(), e);
        } catch (JSONException e) {
            Log.e(TAG, "Corrupted JSON in flashcard file: " + file.getName() + " - Consider deleting this file", e);
            // Optionally: auto-delete corrupted file
            try {
                file.delete();
                Log.i(TAG, "Deleted corrupted flashcard file: " + file.getName());
            } catch (Exception deleteEx) {
                Log.e(TAG, "Failed to delete corrupted file", deleteEx);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading flashcard file: " + file.getName(), e);
        }
        return flashcards;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
