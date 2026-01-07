package com.quicinc.chatapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for generating flashcards from PDF content using LLM
 */
public class FlashcardGenerationService {
    private static final String TAG = "FlashcardGenerationService";
    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    
    public interface FlashcardGenerationCallback {
        void onProgress(String message);
        void onSuccess(int flashcardCount);
        void onError(String errorMessage);
    }
    
    public FlashcardGenerationService(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Generate flashcards from document content and save to FlashcardBank
     */
    public void generateFlashcards(String subject, String documentTitle, String documentContent, 
                                   FlashcardGenerationCallback callback) {
        executorService.execute(() -> {
            WorkingGenieWrapper genieWrapper = null;
            try {
                // Notify progress
                postProgress(callback, "Generating flashcards...");
                
                // Validate that genie_bundle exists
                File genieBundleDir = new File("/data/local/tmp/genie_bundle");
                if (!genieBundleDir.exists()) {
                    Log.e(TAG, "Genie bundle directory not found at: " + genieBundleDir.getAbsolutePath());
                    postError(callback, "AI model not properly installed. Please reinstall the app.");
                    return;
                }
                
                File genieConfig = new File(genieBundleDir, "genie_config.json");
                if (!genieConfig.exists()) {
                    Log.e(TAG, "Genie config file not found at: " + genieConfig.getAbsolutePath());
                    postError(callback, "AI model configuration missing. Please reinstall the app.");
                    return;
                }
                
                // Enforce token budget on document content before generating prompt
                // Reserve ~500 tokens for the flashcard prompt template, leaving ~1400 for content
                int maxContentTokens = 1400;
                int contentTokens = TokenBudgetEnforcer.estimateTokens(documentContent);
                String contentForFlashcards;
                
                if (contentTokens > maxContentTokens) {
                    Log.i(TAG, "Document content too large (" + contentTokens + " tokens), truncating to " + maxContentTokens);
                    int maxChars = maxContentTokens * 4; // 4 chars per token
                    contentForFlashcards = documentContent.length() > maxChars 
                        ? documentContent.substring(0, maxChars) 
                        : documentContent;
                } else {
                    contentForFlashcards = documentContent;
                }
                
                // Generate flashcard prompt
                String flashcardPrompt = FlashcardParser.generateFlashcardPrompt(contentForFlashcards);
                
                // Final safety check: enforce overall token limit on the complete prompt
                flashcardPrompt = TokenBudgetEnforcer.enforceTokenLimit(flashcardPrompt);
                
                Log.d(TAG, "Generated flashcard prompt (" + TokenBudgetEnforcer.estimateTokens(flashcardPrompt) + " tokens)");
                
                // Initialize LLM wrapper
                File externalCacheDir = context.getExternalCacheDir();
                if (externalCacheDir == null) {
                    Log.e(TAG, "External cache directory is null");
                    postError(callback, "Storage error. Please check app permissions.");
                    return;
                }
                
                String modelDir = Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm").toString();
                String htpExtConfigPath = Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm", "htp_backend_ext_config.json").toString();
                
                genieWrapper = new WorkingGenieWrapper(modelDir, htpExtConfigPath, context);
                
                postProgress(callback, "Asking AI to generate flashcards...");
                
                // Get response from LLM (fully asynchronous - no blocking!)
                final StringBuilder llmResponseBuilder = new StringBuilder();
                final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                
                // Set a timeout that will trigger error callback if AI takes too long
                final Runnable timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        String response = llmResponseBuilder.toString();
                        if (response.trim().isEmpty()) {
                            Log.e(TAG, "Flashcard generation timed out - no response from AI");
                            postError(callback, "AI took too long to respond. Please try again.");
                        } else {
                            // We got partial response, try to parse it
                            Log.w(TAG, "Flashcard generation timed out but got partial response, attempting to parse");
                            try {
                                List<Flashcard> flashcards = FlashcardParser.parseFlashcardResponse(response, subject);
                                if (!flashcards.isEmpty()) {
                                    FlashcardBank flashcardBank = FlashcardBank.getInstance();
                                    flashcardBank.init(context);
                                    flashcardBank.saveFlashcards(subject, documentTitle, flashcards);
                                    postSuccess(callback, flashcards.size());
                                } else {
                                    postError(callback, "AI response was incomplete. Please try again.");
                                }
                            } catch (Exception e) {
                                postError(callback, "AI response was incomplete. Please try again.");
                            }
                        }
                    }
                };
                
                // Start 30 second timeout
                timeoutHandler.postDelayed(timeoutRunnable, 30000);
                
                genieWrapper.getResponseForPrompt(flashcardPrompt, new StringCallback() {
                    private boolean responseComplete = false;
                    
                    @Override
                    public void onNewString(String str) {
                        // Don't process if we already completed
                        if (responseComplete) {
                            return;
                        }
                        
                        // CRITICAL FIX: onNewString receives CUMULATIVE response, not incremental chunks
                        // So we REPLACE the content, not append
                        llmResponseBuilder.setLength(0); // Clear previous content
                        llmResponseBuilder.append(str);  // Replace with latest cumulative response
                        
                        // Check if response looks complete (has at least 10 flashcard markers for all 10 cards)
                        String currentResponse = str; // Use str directly since it's already the full response
                        int flashcardCount = currentResponse.split("\\d+\\.\\s*\\*\\*", -1).length - 1;
                        
                        if (flashcardCount >= 10 && currentResponse.length() > 1200) {
                            // Mark as complete so we don't parse again
                            responseComplete = true;
                            
                            // Cancel timeout
                            timeoutHandler.removeCallbacks(timeoutRunnable);
                            
                            // Process the complete response ONCE
                            try {
                                Log.d(TAG, "Received complete LLM response (length: " + currentResponse.length() + ", flashcards: " + flashcardCount + ")");
                                postProgress(callback, "Parsing flashcards...");
                                
                                List<Flashcard> flashcards = FlashcardParser.parseFlashcardResponse(currentResponse, subject);
                                
                                if (flashcards.isEmpty()) {
                                    postError(callback, "Could not parse flashcards from AI response");
                                    return;
                                }
                                
                                Log.d(TAG, "Parsed " + flashcards.size() + " flashcards");
                                postProgress(callback, "Saving " + flashcards.size() + " flashcards...");
                                
                                // Save to FlashcardBank
                                FlashcardBank flashcardBank = FlashcardBank.getInstance();
                                flashcardBank.init(context);
                                flashcardBank.saveFlashcards(subject, documentTitle, flashcards);
                                
                                // Success!
                                postSuccess(callback, flashcards.size());
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing flashcard response", e);
                                postError(callback, "Error processing AI response: " + e.getMessage());
                            }
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating flashcards", e);
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred while generating flashcards";
                postError(callback, errorMsg);
            } finally {
                // Cleanup genieWrapper
                if (genieWrapper != null) {
                    try {
                        genieWrapper.shutdown();
                    } catch (Exception e) {
                        Log.w(TAG, "Error during genieWrapper shutdown", e);
                    }
                }
            }
        });
    }
    
    private void postProgress(FlashcardGenerationCallback callback, String message) {
        mainHandler.post(() -> callback.onProgress(message));
    }
    
    private void postSuccess(FlashcardGenerationCallback callback, int count) {
        mainHandler.post(() -> callback.onSuccess(count));
    }
    
    private void postError(FlashcardGenerationCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}
