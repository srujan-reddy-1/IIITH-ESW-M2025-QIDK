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
 * Service for generating quiz questions from PDF content using LLM
 */
public class QuizGenerationService {
    private static final String TAG = "QuizGenerationService";
    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    
    public interface QuizGenerationCallback {
        void onProgress(String message);
        void onSuccess(int questionCount);
        void onError(String errorMessage);
    }
    
    public QuizGenerationService(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Generate quiz questions from document content and save to QuizBank
     */
    public void generateQuizzes(String subject, String documentTitle, String documentContent, 
                                QuizGenerationCallback callback) {
        executorService.execute(() -> {
            WorkingGenieWrapper genieWrapper = null;
            try {
                Log.i(TAG, "════════════════════════════════════════");
                Log.i(TAG, "🎯 STARTING QUIZ GENERATION");
                Log.i(TAG, "Subject: " + subject);
                Log.i(TAG, "Document: " + documentTitle);
                Log.i(TAG, "Content length: " + documentContent.length() + " chars");
                Log.i(TAG, "════════════════════════════════════════");
                
                // Notify progress
                postProgress(callback, "Processing document...");
                
                // Validate that genie_bundle exists
                File genieBundleDir = new File("/data/local/tmp/genie_bundle");
                if (!genieBundleDir.exists()) {
                    Log.e(TAG, "❌ Genie bundle directory not found at: " + genieBundleDir.getAbsolutePath());
                    postError(callback, "AI model not properly installed. Please reinstall the app.");
                    return;
                }
                
                File genieConfig = new File(genieBundleDir, "genie_config.json");
                if (!genieConfig.exists()) {
                    Log.e(TAG, "Genie config file not found at: " + genieConfig.getAbsolutePath());
                    postError(callback, "AI model configuration missing. Please reinstall the app.");
                    return;
                }
                
                // Initialize LLM wrapper first (needed for context window manager)
                File externalCacheDir = context.getExternalCacheDir();
                if (externalCacheDir == null) {
                    Log.e(TAG, "External cache directory is null");
                    postError(callback, "Storage error. Please check app permissions.");
                    return;
                }
                
                String modelDir = Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm").toString();
                String htpExtConfigPath = Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm", "htp_backend_ext_config.json").toString();
                
                genieWrapper = new WorkingGenieWrapper(modelDir, htpExtConfigPath, context);
                
                // DON'T use context window manager for quiz generation - too slow and loses content
                // Just use the document content directly
                postProgress(callback, "Preparing content for quiz generation...");
                
                // Enforce token budget on the document content
                // Reserve ~500 tokens for the quiz prompt template, leaving ~1400 for content
                // Total context window is 2048, safe budget is 1900, so we use 1400 for content + 500 for prompt = 1900
                int maxContentTokens = 1400;
                int contentTokens = TokenBudgetEnforcer.estimateTokens(documentContent);
                String contentForQuiz;
                
                Log.d(TAG, "Content token count: " + contentTokens + " / " + maxContentTokens);
                
                if (contentTokens > maxContentTokens) {
                    Log.i(TAG, "Document content too large (" + contentTokens + " tokens), truncating to " + maxContentTokens);
                    // Truncate intelligently at sentence boundaries
                    int maxChars = maxContentTokens * 4; // 4 chars per token
                    if (documentContent.length() > maxChars) {
                        String truncated = documentContent.substring(0, maxChars);
                        // Try to cut at sentence boundary
                        int lastPeriod = truncated.lastIndexOf('.');
                        int lastNewline = truncated.lastIndexOf('\n');
                        int breakPoint = Math.max(lastPeriod, lastNewline);
                        if (breakPoint > maxChars * 0.8) {
                            contentForQuiz = truncated.substring(0, breakPoint + 1);
                        } else {
                            contentForQuiz = truncated + "...";
                        }
                    } else {
                        contentForQuiz = documentContent;
                    }
                    Log.d(TAG, "Truncated content to " + TokenBudgetEnforcer.estimateTokens(contentForQuiz) + " tokens");
                } else {
                    contentForQuiz = documentContent;
                }
                
                // Generate quiz prompt from document content (QuizParser no longer truncates)
                String quizPrompt = QuizParser.generateQuizPrompt(contentForQuiz);
                
                // Final safety check: enforce overall token limit on the complete prompt
                int promptTokens = TokenBudgetEnforcer.estimateTokens(quizPrompt);
                Log.d(TAG, "Quiz prompt tokens before enforcement: " + promptTokens);
                
                if (promptTokens > 1900) { // MAX_TOTAL_TOKENS
                    Log.w(TAG, "Prompt exceeds budget (" + promptTokens + " tokens), applying final truncation");
                    quizPrompt = TokenBudgetEnforcer.enforceTokenLimit(quizPrompt);
                    Log.d(TAG, "Quiz prompt tokens after enforcement: " + TokenBudgetEnforcer.estimateTokens(quizPrompt));
                }
                
                Log.d(TAG, "Generated quiz prompt with " + TokenBudgetEnforcer.estimateTokens(quizPrompt) + " tokens");
                
                postProgress(callback, "Asking AI to generate questions...");
                
                // Get response from LLM (fully asynchronous - no blocking!)
                final StringBuilder llmResponseBuilder = new StringBuilder();
                final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                
                // Set a timeout that will trigger error callback if AI takes too long
                final Runnable timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        String response = llmResponseBuilder.toString();
                        Log.w(TAG, "⚠ TIMEOUT TRIGGERED after 30 seconds");
                        Log.w(TAG, "Response length at timeout: " + response.length());
                        
                        if (response.trim().isEmpty()) {
                            Log.e(TAG, "Quiz generation timed out - no response from AI");
                            postError(callback, "AI took too long to respond. Please try again.");
                        } else {
                            // We got partial response, try to parse it
                            int answerCount = response.split("Answer:", -1).length - 1;
                            Log.w(TAG, "Quiz generation timed out but got partial response (answers: " + answerCount + "), attempting to parse");
                            Log.d(TAG, "Partial response preview: " + response.substring(0, Math.min(300, response.length())));
                            
                            try {
                                List<QuizQuestion> questions = QuizParser.parseQuizResponse(response);
                                if (!questions.isEmpty()) {
                                    Log.i(TAG, "Parsed " + questions.size() + " questions from partial response");
                                    QuizBank quizBank = QuizBank.getInstance();
                                    quizBank.init(context);
                                    quizBank.saveQuizzes(subject, documentTitle, questions);
                                    postSuccess(callback, questions.size());
                                } else {
                                    Log.e(TAG, "Could not parse any questions from partial response");
                                    postError(callback, "AI response was incomplete. Please try again.");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing partial response", e);
                                postError(callback, "AI response was incomplete. Please try again.");
                            }
                        }
                    }
                };
                
                // Start 30 second timeout
                timeoutHandler.postDelayed(timeoutRunnable, 30000);
                
                genieWrapper.getResponseForPrompt(quizPrompt, new StringCallback() {
                    private boolean responseComplete = false;
                    private int chunkCount = 0;
                    
                    @Override
                    public void onNewString(String str) {
                        chunkCount++;
                        
                        // Don't process if we already completed
                        if (responseComplete) {
                            Log.v(TAG, "Chunk #" + chunkCount + " ignored (already complete)");
                            return;
                        }
                        
                        // CRITICAL FIX: onNewString receives CUMULATIVE response, not incremental chunks
                        // So we REPLACE the content, not append
                        llmResponseBuilder.setLength(0); // Clear previous content
                        llmResponseBuilder.append(str);  // Replace with latest cumulative response
                        
                        // Check if response looks complete (has at least 10 "Answer:" markers = all 10 questions complete)
                        String currentResponse = str; // Use str directly since it's already the full response
                        int answerCount = currentResponse.split("Answer:", -1).length - 1;
                        
                        Log.v(TAG, "Chunk #" + chunkCount + " received: " + str.length() + " chars, Total: " + currentResponse.length() + " chars, Answers found: " + answerCount);
                        
                        if (answerCount >= 10 && currentResponse.length() > 1500) {
                            // Mark as complete so we don't parse again
                            responseComplete = true;
                            
                            Log.i(TAG, "Quiz generation complete! Total chunks: " + chunkCount + ", Total length: " + currentResponse.length() + ", Answers: " + answerCount);
                            
                            // Cancel timeout
                            timeoutHandler.removeCallbacks(timeoutRunnable);
                            
                            // Process the complete response ONCE
                            try {
                                Log.d(TAG, "=== STARTING QUIZ PARSING ===");
                                Log.d(TAG, "Response preview: " + currentResponse.substring(0, Math.min(200, currentResponse.length())));
                                postProgress(callback, "Parsing quiz questions...");
                                
                                List<QuizQuestion> questions = QuizParser.parseQuizResponse(currentResponse);
                                
                                if (questions.isEmpty()) {
                                    Log.e(TAG, "PARSING FAILED: No questions extracted from response");
                                    Log.e(TAG, "Full response was: " + currentResponse);
                                    postError(callback, "Could not parse questions from AI response");
                                    return;
                                }
                                
                                Log.i(TAG, "✓ Successfully parsed " + questions.size() + " questions");
                                for (int i = 0; i < questions.size(); i++) {
                                    QuizQuestion q = questions.get(i);
                                    Log.d(TAG, "  Q" + (i+1) + ": " + q.getQuestion().substring(0, Math.min(50, q.getQuestion().length())) + "...");
                                    Log.d(TAG, "      Options: " + q.getOptions().length + ", Correct: " + q.getCorrectAnswer());
                                }
                                
                                // Save to QuizBank
                                Log.d(TAG, "Saving to QuizBank: subject=" + subject + ", doc=" + documentTitle);
                                postProgress(callback, "Saving quizzes...");
                                QuizBank quizBank = QuizBank.getInstance();
                                quizBank.init(context);
                                quizBank.saveQuizzes(subject, documentTitle, questions);
                                
                                Log.i(TAG, "✓ Quiz generation SUCCESS: " + questions.size() + " questions saved");
                                
                                // Success!
                                postSuccess(callback, questions.size());
                                
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR during quiz processing", e);
                                Log.e(TAG, "Exception type: " + e.getClass().getName());
                                Log.e(TAG, "Exception message: " + e.getMessage());
                                e.printStackTrace();
                                postError(callback, "Error processing AI response: " + e.getMessage());
                            }
                        } else {
                            Log.v(TAG, "Not complete yet - need 10 answers and 1500+ chars (current: " + answerCount + " answers, " + currentResponse.length() + " chars)");
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating quizzes", e);
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred while generating quiz";
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
    
    private void postProgress(QuizGenerationCallback callback, String message) {
        mainHandler.post(() -> callback.onProgress(message));
    }
    
    private void postSuccess(QuizGenerationCallback callback, int count) {
        mainHandler.post(() -> callback.onSuccess(count));
    }
    
    private void postError(QuizGenerationCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}
