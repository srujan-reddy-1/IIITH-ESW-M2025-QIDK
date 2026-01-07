// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Conversation extends AppCompatActivity {

    ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>(1000);
    private boolean sessionTracked = false;  // Track if we've counted this session
    private String userName = ""; // Store user's name
    private WorkingGenieWrapper genieWrapper; // AI model wrapper
    private ContextWindowManager contextManager; // Context window manager
    private String currentSubjectContext; // Track which subject this conversation is for
    private String currentWeakTopic; // Track weak topic if opened from weak topics section

    // PDF functionality
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private PDFExtractionService pdfExtractionService;
    private DocumentManager documentManager;

    public static final String cConversationActivityKeyHtpConfig = "htp_config_path";
    public static final String cConversationActivityKeyModelName = "model_dir_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat);
        
        // Initialize PDF services
        initializePDFServices();
        
        RecyclerView recyclerView = findViewById(R.id.chat_recycler_view);
        Message_RecyclerViewAdapter adapter = new Message_RecyclerViewAdapter(this, messages);
        recyclerView.setAdapter(adapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        
        // Store adapter reference for later use
        final Message_RecyclerViewAdapter finalAdapter = adapter;
        
        // Optimize RecyclerView for smooth streaming
        recyclerView.setHasFixedSize(false);
        recyclerView.setItemViewCacheSize(20); // Cache more views for smoother scrolling
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        com.google.android.material.floatingactionbutton.FloatingActionButton sendUserMsgButton = 
            (com.google.android.material.floatingactionbutton.FloatingActionButton) findViewById(R.id.send_button);
        com.google.android.material.textfield.TextInputEditText userMsg = 
            (com.google.android.material.textfield.TextInputEditText) findViewById(R.id.user_input);
        
        // Verify button exists
        if (sendUserMsgButton == null) {
            Log.e("ChatApp", "Send button is NULL!");
            Toast.makeText(this, "Error: Send button not found", Toast.LENGTH_SHORT).show();
        } else {
            Log.d("ChatApp", "Send button initialized successfully");
        }
        
        // Setup keyboard send action
        if (userMsg != null) {
            userMsg.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    if (sendUserMsgButton != null) {
                        sendUserMsgButton.performClick();
                    }
                    return true;
                }
                return false;
            });
        }
        
        // Setup modern UI elements
        ImageButton backButton = findViewById(R.id.back_button);
        View pdfProcessingStatus = findViewById(R.id.pdf_processing_status);
        TextView statusText = findViewById(R.id.status_text);
        
        // Setup back button navigation
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // Update status text to show AI readiness
        if (statusText != null) {
            statusText.setText("Online • Ready to help");
        }
        
        // PDF Status close button
        com.google.android.material.button.MaterialButton closePdfStatus = findViewById(R.id.close_pdf_status);
        if (closePdfStatus != null) {
            closePdfStatus.setOnClickListener(view -> {
                if (pdfProcessingStatus != null) {
                    pdfProcessingStatus.setVisibility(View.GONE);
                }
            });
        }
        
        // Quick action chip buttons
        com.google.android.material.chip.Chip explainButton = findViewById(R.id.explain_button);
        com.google.android.material.chip.Chip quizButton = findViewById(R.id.quiz_me_button);
        com.google.android.material.chip.Chip summarizeButton = findViewById(R.id.summarize_button);
        com.google.android.material.chip.Chip helpButton = findViewById(R.id.help_button);
        
        explainButton.setOnClickListener(view -> {
            // Include weak topic name if available
            String explainMessage;
            if (currentWeakTopic != null && !currentWeakTopic.trim().isEmpty()) {
                explainMessage = "✨ Please explain " + currentWeakTopic + " in detail";
            } else {
                explainMessage = "✨ Please explain this concept in detail";
            }
            userMsg.setText(explainMessage);
            sendUserMsgButton.performClick();
        });
        
        quizButton.setOnClickListener(view -> {
            // Include weak topic name if available
            String quizMessage;
            if (currentWeakTopic != null && !currentWeakTopic.trim().isEmpty()) {
                quizMessage = "🎯 Create a quiz for me on " + currentWeakTopic;
            } else {
                quizMessage = "🎯 Create a quiz for me on this topic";
            }
            userMsg.setText(quizMessage);
            sendUserMsgButton.performClick();
        });
        
        summarizeButton.setOnClickListener(view -> {
            // Include weak topic name if available
            String summarizeMessage;
            if (currentWeakTopic != null && !currentWeakTopic.trim().isEmpty()) {
                summarizeMessage = "📝 Please summarize the key points about " + currentWeakTopic;
            } else {
                summarizeMessage = "📝 Please summarize the key points";
            }
            userMsg.setText(summarizeMessage);
            sendUserMsgButton.performClick();
        });
        
        helpButton.setOnClickListener(view -> {
            // Include weak topic name if available
            String helpMessage;
            if (currentWeakTopic != null && !currentWeakTopic.trim().isEmpty()) {
                helpMessage = "💡 I need help understanding " + currentWeakTopic + " better";
            } else {
                helpMessage = "💡 I need help understanding this better";
            }
            userMsg.setText(helpMessage);
            sendUserMsgButton.performClick();
        });
        
        // Subject chip selection
        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.subject_chip_group);
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (!checkedIds.isEmpty()) {
                    com.google.android.material.chip.Chip selectedChip = findViewById(checkedIds.get(0));
                    if (selectedChip != null) {
                        String subject = selectedChip.getText().toString();
                        Toast.makeText(this, "Selected: " + subject, Toast.LENGTH_SHORT).show();
                        // Update context for AI responses
                    }
                }
            });
        }

        try {
            // Make QNN libraries discoverable
            String nativeLibPath = getApplicationContext().getApplicationInfo().nativeLibraryDir;
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibPath, true);
            Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true);

            // Get information from MainActivity regarding
            //  - Model to run
            //  - HTP config to use
            Bundle bundle = getIntent().getExtras();
            if (bundle == null) {
                Log.e("ChatApp", "Error getting additional info from bundle.");
                Toast.makeText(this, "Unexpected error observed. Exiting app.", Toast.LENGTH_LONG).show();
                finish();
            }

            String htpExtensionsDir = bundle.getString(cConversationActivityKeyHtpConfig);
            String modelName = bundle.getString(cConversationActivityKeyModelName);
            String externalCacheDir = this.getExternalCacheDir().getAbsolutePath().toString();
            String modelDir = Paths.get(externalCacheDir, "models", modelName).toString();

            // Load user name from SharedPreferences
            SharedPreferences sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);
            userName = sharedPreferences.getString("user_name", "");
            
            // Get subject context from intent (if available)
            currentSubjectContext = getIntent().getStringExtra("subject_context");
            
            // Get weak topic from intent (if available)
            currentWeakTopic = getIntent().getStringExtra("weak_topic");
            
            // Load Model using Working Genie wrapper for development
            genieWrapper = new WorkingGenieWrapper(modelDir, htpExtensionsDir, this);
            Log.i("ChatApp", "Working Genie wrapper initialized for development testing.");
            
            // Initialize context window manager
            contextManager = ContextWindowManager.getInstance(this, genieWrapper);
            Log.i("ChatApp", "Context window manager initialized for intelligent document retrieval.");
            
            // Check if this is global chat and process all documents
            boolean isGlobalChat = getIntent().getBooleanExtra("is_global_chat", false);
            String pdfContext = getIntent().getStringExtra("pdf_context");
            String subjectContext = getIntent().getStringExtra("subject_context");
            
            // Clear selected document if this is home page chat (no subject context and no PDF context)
            // Only keep document context if explicitly provided from subject detail page
            if (isGlobalChat || (subjectContext == null && pdfContext == null)) {
                Log.d("ChatApp", "Home page chat - clearing any previously selected document");
                if (documentManager != null) {
                    documentManager.clearSelectedDocument();
                }
            }
            
            if (isGlobalChat) {
                Log.d("ChatApp", "Global chat mode - processing all documents for context");
                processAllDocumentsForGlobalChat();
            }

            messages.add(new ChatMessage(getPersonalizedWelcomeMessage(), MessageSender.BOT));
            
            // Check if there's an initial message (e.g., from weak topic)
            String initialMessage = getIntent().getStringExtra("initial_message");
            if (initialMessage != null && !initialMessage.trim().isEmpty()) {
                // Add initial message as a bot message to guide the user
                messages.add(new ChatMessage(initialMessage, MessageSender.BOT));
                // Notify adapter of the new message
                finalAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.post(() -> recyclerView.scrollToPosition(messages.size() - 1));
            }
            
            // Update UI based on any existing document context
            updateDocumentUI();

            Log.d("ChatApp", "Setting up send button click listener...");
            
            // Get response from Bot once user message is sent
            if (sendUserMsgButton != null) {
                sendUserMsgButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d("ChatApp", "Send button clicked");
                        
                        String userInputMsg = userMsg.getText().toString().trim();
                        Log.d("ChatApp", "User input: " + userInputMsg);
                        
                        if (!userInputMsg.isEmpty()) {
                            // Track chat session on first message
                            if (!sessionTracked) {
                                ProgressTracker.trackChatSession(Conversation.this);
                                sessionTracked = true;
                            }
                        
                        // Reset user message box
                        userMsg.setText("");

                        // Insert user message in the conversation
                        adapter.addMessage(new ChatMessage(userInputMsg, MessageSender.USER));
                        adapter.notifyItemInserted(adapter.getItemCount() - 1);

                        int botResponseMsgIndex = adapter.getItemCount();
                        recyclerView.smoothScrollToPosition(botResponseMsgIndex);

                        // Check if it's a greeting first
                        String personalizedGreeting = getPersonalizedGreeting(userInputMsg);
                        if (personalizedGreeting != null) {
                            // Respond with personalized greeting immediately
                            adapter.addMessage(new ChatMessage(personalizedGreeting, MessageSender.BOT));
                            adapter.notifyItemInserted(adapter.getItemCount() - 1);
                            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                        } else {
                            // Add simple thinking message in italics (no animation to prevent flickering)
                            adapter.addMessage(new ChatMessage("Thinking...", MessageSender.BOT));
                            adapter.notifyItemInserted(adapter.getItemCount() - 1);
                            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                            
                            // Process with AI model
                            ExecutorService service = Executors.newSingleThreadExecutor();
                            service.execute(new Runnable() {
                                private final StringBuilder fullResponse = new StringBuilder();
                                private final Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
                                private long responseStartTime = System.currentTimeMillis();
                                private int lastResponseLength = 0;
                                
                                private final Runnable updateUIRunnable = new Runnable() {
                                    private String lastUpdatedText = "";
                                    private boolean isFirstUpdate = true;
                                    
                                    @Override
                                    public void run() {
                                        String currentResponse;
                                        int currentLength;
                                        synchronized (fullResponse) {
                                            currentResponse = fullResponse.toString();
                                            currentLength = currentResponse.length();
                                        }
                                        
                                        // Only update if content has changed and has meaningful content
                                        if (!currentResponse.equals(lastUpdatedText) && currentResponse.trim().length() > 0) {
                                            lastUpdatedText = currentResponse;
                                            
                                            // Calculate words per second for performance tracking
                                            if (currentLength > lastResponseLength) {
                                                long elapsedMs = System.currentTimeMillis() - responseStartTime;
                                                if (elapsedMs > 0) {
                                                    int newChars = currentLength - lastResponseLength;
                                                    double charsPerSec = (newChars * 1000.0) / elapsedMs;
                                                    double wordsPerSec = charsPerSec / 5.0; // Approximate 5 chars per word
                                                    
                                                    // Log performance metrics (only occasionally to avoid spam)
                                                    if (currentLength % 100 == 0 || elapsedMs % 2000 == 0) {
                                                        Log.d("ChatApp", String.format("Streaming: %d chars, %.1f chars/sec, ~%.1f words/sec", 
                                                            currentLength, charsPerSec, wordsPerSec));
                                                    }
                                                }
                                                lastResponseLength = currentLength;
                                            }
                                            
                                            // Replace the thinking message with current response
                                            adapter.replaceBotMessage(currentResponse);
                                            int lastPosition = adapter.getItemCount() - 1;
                                            if (lastPosition >= 0) {
                                                // Use payload to avoid full rebind during streaming
                                                adapter.notifyItemChanged(lastPosition, "content_update");
                                                
                                                // Smooth scroll to bottom - use scrollToPosition for instant updates
                                                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                                                if (layoutManager != null) {
                                                    int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
                                                    // Only auto-scroll if user is near the bottom (within 2 items)
                                                    if (lastVisiblePosition >= lastPosition - 2 || isFirstUpdate) {
                                                        // Use scrollToPosition for instant, smooth scrolling during streaming
                                                        recyclerView.post(() -> {
                                                            if (recyclerView.getLayoutManager() != null && lastPosition >= 0) {
                                                                recyclerView.scrollToPosition(lastPosition);
                                                            }
                                                        });
                                                    }
                                                }
                                            }
                                            
                                            isFirstUpdate = false;
                                        }
                                    }
                                };
                                
                                private long lastUpdateTime = 0;
                                private static final long UPDATE_INTERVAL_MS = 100; // Update every 100ms for smoother streaming (~10 updates/sec)
                                
                                @Override
                                public void run() {
                                    // Reset performance tracking
                                    responseStartTime = System.currentTimeMillis();
                                    lastResponseLength = 0;
                                    
                                    // Create prompt with document context if available
                                    long promptStartTime = System.currentTimeMillis();
                                    String finalPrompt = createPromptWithContext(userInputMsg);
                                    long promptTime = System.currentTimeMillis() - promptStartTime;
                                    
                                    int promptTokens = TokenBudgetEnforcer.estimateTokens(finalPrompt);
                                    Log.d("ChatApp", String.format("Prompt created in %dms: %d tokens, %d chars", 
                                        promptTime, promptTokens, finalPrompt.length()));
                                    
                                    genieWrapper.getResponseForPrompt(finalPrompt, new StringCallback() {
                                    @Override
                                    public void onNewString(String response) {
                                        // Clean the response to remove [END] markers
                                        String cleanedResponse = cleanChatResponse(response);
                                        
                                        // Update the full response
                                        synchronized (fullResponse) {
                                            fullResponse.setLength(0);
                                            fullResponse.append(cleanedResponse);
                                        }
                                        
                                        // Throttle UI updates for smooth streaming
                                        long currentTime = System.currentTimeMillis();
                                        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                                            lastUpdateTime = currentTime;
                                            
                                            // Remove any pending updates and post new one
                                            uiUpdateHandler.removeCallbacks(updateUIRunnable);
                                            uiUpdateHandler.post(updateUIRunnable);
                                        } else {
                                            // Schedule delayed update if not time yet
                                            uiUpdateHandler.removeCallbacks(updateUIRunnable);
                                            uiUpdateHandler.postDelayed(updateUIRunnable, UPDATE_INTERVAL_MS - (currentTime - lastUpdateTime));
                                        }
                                    }
                                });
                                
                                // Ensure final update is posted after streaming completes
                                uiUpdateHandler.postDelayed(() -> {
                                    uiUpdateHandler.removeCallbacks(updateUIRunnable);
                                    updateUIRunnable.run();
                                    
                                    // Final scroll to ensure we're at the bottom
                                    recyclerView.post(() -> {
                                        int lastPosition = adapter.getItemCount() - 1;
                                        if (lastPosition >= 0 && recyclerView.getLayoutManager() != null) {
                                            recyclerView.scrollToPosition(lastPosition);
                                        }
                                    });
                                    
                                    // Log final performance metrics
                                    long totalTime = System.currentTimeMillis() - responseStartTime;
                                    synchronized (fullResponse) {
                                        int finalLength = fullResponse.length();
                                        int wordCount = finalLength / 5; // Approximate
                                        double wordsPerSec = (wordCount * 1000.0) / totalTime;
                                        Log.i("ChatApp", String.format("Response complete: %d chars, %d words, %d ms, ~%.1f words/sec", 
                                            finalLength, wordCount, totalTime, wordsPerSec));
                                    }
                                }, UPDATE_INTERVAL_MS + 50);
                            }
                        });
                        }

                        // Scroll to last message
                        recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                    } else {
                        Log.d("ChatApp", "User input is empty, not sending");
                        Toast.makeText(Conversation.this, "Please enter a message", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            Log.d("ChatApp", "Send button click listener set up successfully");
        } else {
            Log.e("ChatApp", "Send button is null, cannot set up click listener!");
            Toast.makeText(this, "Error: Send button not found", Toast.LENGTH_LONG).show();
        }

        } catch (Exception e) {
            Log.e("ChatApp", "Error during conversation with Chatbot: " + e.toString());
            Toast.makeText(this, "Unexpected error observed. Exiting app.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Initialize PDF-related services
     */
    private void initializePDFServices() {
        // Initialize document manager
        documentManager = DocumentManager.getInstance(this);
        
        // PDF extraction service will be initialized after genieWrapper is ready
        // This is handled in onCreate after model loading
        
        // Initialize PDF picker launcher
        pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri pdfUri = result.getData().getData();
                    if (pdfUri != null) {
                        processPDFUpload(pdfUri);
                    }
                }
            }
        );
    }
    
    /**
     * Launch PDF file picker
     */
    private void launchPDFPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            pdfPickerLauncher.launch(Intent.createChooser(intent, "Select PDF Document"));
        } catch (Exception e) {
            Toast.makeText(this, "No file manager app found", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Process uploaded PDF file
     */
    private void processPDFUpload(Uri pdfUri) {
        // Initialize PDF extraction service with AI wrapper if not already done
        if (pdfExtractionService == null && genieWrapper != null) {
            pdfExtractionService = new PDFExtractionService(this, genieWrapper);
        } else if (pdfExtractionService == null) {
            pdfExtractionService = new PDFExtractionService(this);
        }
        
        // Show PDF processing status
        View pdfProcessingStatus = findViewById(R.id.pdf_processing_status);
        TextView pdfStatusText = findViewById(R.id.pdf_status_text);
        
        if (pdfProcessingStatus != null) {
            pdfProcessingStatus.setVisibility(View.VISIBLE);
        }
        
        pdfExtractionService.extractTextFromPDF(pdfUri, new PDFExtractionService.PDFExtractionCallback() {
            @Override
            public void onProgress(String status) {
                runOnUiThread(() -> {
                    if (pdfStatusText != null) {
                        pdfStatusText.setText(status);
                    }
                });
            }
            
            @Override
            public void onSuccess(Document document) {
                runOnUiThread(() -> {
                    // Set the subject context for this document (if available)
                    if (currentSubjectContext != null && !currentSubjectContext.isEmpty()) {
                        document.setSubjectName(currentSubjectContext);
                    }
                    
                    // Add document to manager
                    documentManager.addDocument(document);
                    
                    // Set as selected document for chat context
                    documentManager.setSelectedDocument(document.getId());
                    
                    // Show success message
                    Toast.makeText(Conversation.this, 
                        "PDF '" + document.getDisplayName() + "' loaded successfully! " +
                        "(" + document.getWordCount() + " words)", Toast.LENGTH_LONG).show();
                    
                    // Update UI to show document is loaded
                    updateDocumentUI();
                });
            }
            
            @Override
            public void onChunkingComplete(int chunkCount) {
                runOnUiThread(() -> {
                    if (pdfStatusText != null) {
                        if (chunkCount > 1) {
                            pdfStatusText.setText("Document processed into " + chunkCount + " sections for intelligent search");
                        } else {
                            pdfStatusText.setText("Document ready for questions");
                        }
                    }
                    
                    // Hide processing status after a delay
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (pdfProcessingStatus != null) {
                            pdfProcessingStatus.setVisibility(View.GONE);
                        }
                    }, 2000);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    // Hide processing status
                    if (pdfProcessingStatus != null) {
                        pdfProcessingStatus.setVisibility(View.GONE);
                    }
                    
                    // Show error message
                    Toast.makeText(Conversation.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * Update UI based on document state
     */
    private void updateDocumentUI() {
        // Check if this is global chat mode
        boolean isGlobalChat = getIntent().getBooleanExtra("is_global_chat", false);
        String pdfContext = getIntent().getStringExtra("pdf_context");
        String subjectContext = getIntent().getStringExtra("subject_context");
        TextView statusText = findViewById(R.id.status_text);
        
        if (isGlobalChat) {
            // Global chat - show general status, no document selected
            if (statusText != null) {
                statusText.setText("Atom AI • All Subjects Available");
            }
        } else if (pdfContext != null && !pdfContext.isEmpty()) {
            // Subject-specific chat with PDF context - show the PDF
            if (statusText != null) {
                statusText.setText("Document: " + pdfContext + " • Ready to help");
            }
        } else if (subjectContext != null && !subjectContext.isEmpty()) {
            // Subject-specific chat without specific PDF
            Document selectedDoc = documentManager != null ? documentManager.getSelectedDocument() : null;
            if (selectedDoc != null && statusText != null) {
                statusText.setText("Document: " + selectedDoc.getDisplayName() + " • Ready to help");
            } else if (statusText != null) {
                statusText.setText("Subject: " + subjectContext + " • Ready to help");
            }
        } else {
            // Home page chat - no document context
            if (statusText != null) {
                statusText.setText("Online • Ready to help");
            }
        }
    }
    
    /**
     * Create prompt with document context if available
     * Returns a fully formatted Llama 3 prompt ready for the model
     */
    private String createPromptWithContext(String userQuestion) {
        // Get user preferences for personalized tutoring
        String systemInstruction = getUserPreferenceInstruction();
        
        // Check if this is global chat (from homepage with all subjects)
        boolean isGlobalChat = getIntent().getBooleanExtra("is_global_chat", false);
        
        Log.d("ChatApp", "Creating prompt - isGlobalChat: " + isGlobalChat);
        
        String contextContent = "";
        String userInput = userQuestion;
        
        if (isGlobalChat) {
            // Global chat - get context from all documents
            try {
                if (contextManager != null) {
                    String globalContext = contextManager.getGlobalRelevantContext(userQuestion);
                    
                    Log.d("ChatApp", "Global context retrieved: " + (globalContext != null ? globalContext.length() + " chars" : "null"));
                    
                    if (globalContext != null && !globalContext.trim().isEmpty()) {
                        // Build structured context block
                        StringBuilder contextWithInstruction = new StringBuilder();
                        contextWithInstruction.append(globalContext).append("\n\n");
                        contextWithInstruction.append("Question: ").append(userQuestion).append("\n\n");
                        contextWithInstruction.append("Instructions: Answer based on the provided study materials from all subjects. ");
                        contextWithInstruction.append("If the answer is not in the materials, say so clearly. ");
                        contextWithInstruction.append("Do not ask for additional materials - use what's provided above.");
                        contextContent = contextWithInstruction.toString();
                    } else {
                        Log.w("ChatApp", "Global context is empty, using question without context");
                    }
                } else {
                    Log.w("ChatApp", "Context manager is null");
                }
            } catch (Exception e) {
                Log.e("ChatApp", "Error getting global context", e);
            }
        } else {
            // Check if this is home page chat (should not use document context)
            String pdfContext = getIntent().getStringExtra("pdf_context");
            String subjectContext = getIntent().getStringExtra("subject_context");
            // Use stored weak topic (from onCreate) instead of reading from intent each time
            String weakTopic = currentWeakTopic;
            
            // Only use document context if explicitly provided from subject detail page
            if (subjectContext == null && pdfContext == null) {
                // Home page chat - no document context
                Log.d("ChatApp", "Home page chat - using question without document context");
                // Format as Llama 3 prompt without context
                StringBuilder llamaPrompt = new StringBuilder();
                llamaPrompt.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n");
                llamaPrompt.append(systemInstruction);
                llamaPrompt.append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n");
                llamaPrompt.append(userQuestion);
                llamaPrompt.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n");
                return llamaPrompt.toString();
            }
            
            // Subject-specific chat - use selected document context
            if (documentManager != null && documentManager.hasSelectedDocument()) {
                Document selectedDoc = documentManager.getSelectedDocument();
                if (selectedDoc != null) {
                    // Use context window manager for intelligent retrieval
                    String relevantContent;
                    try {
                        if (contextManager != null) {
                            // If weak topic is set, prioritize it in the query
                            String queryForContext = userQuestion;
                            if (weakTopic != null && !weakTopic.trim().isEmpty()) {
                                // Combine weak topic with user question for better context retrieval
                                queryForContext = weakTopic + " " + userQuestion;
                                Log.d("ChatApp", "Using weak topic in context query: " + queryForContext);
                            }
                            
                            // Get relevant context based on the query (with weak topic if available)
                            relevantContent = contextManager.getRelevantContext(selectedDoc.getId(), queryForContext);
                            
                            // If context manager returns empty, fall back to the old method
                            if (relevantContent == null || relevantContent.trim().isEmpty()) {
                                Log.w("ChatApp", "Context manager returned empty, using fallback");
                                String fullContent = selectedDoc.getTextContent();
                                // Use weak topic in fallback search too
                                String searchQuery = weakTopic != null && !weakTopic.trim().isEmpty() 
                                    ? weakTopic : userQuestion;
                                relevantContent = getRelevantContent(fullContent, searchQuery);
                            }
                        } else {
                            Log.w("ChatApp", "Context manager not initialized, using fallback method");
                            String fullContent = selectedDoc.getTextContent();
                            // Use weak topic in fallback search too
                            String searchQuery = weakTopic != null && !weakTopic.trim().isEmpty() 
                                ? weakTopic : userQuestion;
                            relevantContent = getRelevantContent(fullContent, searchQuery);
                        }
                    } catch (Exception e) {
                        Log.w("ChatApp", "Error using context window manager, falling back to simple method", e);
                        String fullContent = selectedDoc.getTextContent();
                        // Use weak topic in fallback search too
                        String searchQuery = weakTopic != null && !weakTopic.trim().isEmpty() 
                            ? weakTopic : userQuestion;
                        relevantContent = getRelevantContent(fullContent, searchQuery);
                    }
                    
                    // Build context block with clear structure
                    StringBuilder contextBlock = new StringBuilder();
                    contextBlock.append("Document: ").append(selectedDoc.getDisplayName()).append("\n\n");
                    
                    // Add weak topic context if available
                    if (weakTopic != null && !weakTopic.trim().isEmpty()) {
                        contextBlock.append("Focus Topic: ").append(weakTopic).append("\n");
                        contextBlock.append("The user wants to learn about this specific topic from the document.\n\n");
                    }
                    
                    contextBlock.append("Relevant Content:\n").append(relevantContent).append("\n\n");
                    contextBlock.append("Question: ").append(userQuestion).append("\n\n");
                    contextBlock.append("Instructions: Answer based on the provided document content. ");
                    if (weakTopic != null && !weakTopic.trim().isEmpty()) {
                        contextBlock.append("Focus on explaining the topic '").append(weakTopic).append("' in detail. ");
                    }
                    contextBlock.append("If the answer is not in the document, say so clearly. ");
                    contextBlock.append("Do not ask for additional materials - use what's provided above.");
                    contextContent = contextBlock.toString();
                }
            }
        }
        
        // Enforce token limit before formatting
        String finalSystemInstruction = systemInstruction;
        String finalContextContent = contextContent;
        String finalUserInput = userInput;
        
        // Use TokenBudgetEnforcer to ensure we stay within limits
        String enforcedPrompt = TokenBudgetEnforcer.enforceTokenLimit(finalSystemInstruction, finalContextContent, finalUserInput);
        
        // Now format as Llama 3 prompt
        // Check if already formatted (shouldn't be, but safety check)
        if (enforcedPrompt.contains("<|begin_of_text|>")) {
            Log.w("ChatApp", "Prompt already formatted, using as-is");
            return enforcedPrompt;
        }
        
        // Format as Llama 3 prompt
        StringBuilder llamaPrompt = new StringBuilder();
        llamaPrompt.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n");
        llamaPrompt.append(finalSystemInstruction);
        llamaPrompt.append("<|eot_id|>");
        
        if (!finalContextContent.isEmpty()) {
            llamaPrompt.append("<|start_header_id|>user<|end_header_id|>\n\n");
            llamaPrompt.append(finalContextContent);
            llamaPrompt.append("<|eot_id|>");
        }
        
        llamaPrompt.append("<|start_header_id|>user<|end_header_id|>\n\n");
        llamaPrompt.append(finalUserInput);
        llamaPrompt.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n");
        
        Log.d("ChatApp", "Formatted Llama 3 prompt: " + llamaPrompt.length() + " chars");
        return llamaPrompt.toString();
    }
    
    /**
     * Get personalized instruction based on user's learning preferences
     * Returns plain text instruction (will be formatted into Llama 3 format by WorkingGenieWrapper)
     */
    private String getUserPreferenceInstruction() {
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        String difficultyLevel = prefs.getString("difficulty_level", "intermediate");
        String preparingFor = prefs.getString("preparing_for", "");
        
        StringBuilder instruction = new StringBuilder("You are Atom, an AI tutor. ");
        instruction.append("IMPORTANT: When the user asks a question, ANSWER it directly with a helpful response. ");
        instruction.append("Do NOT output numbered lists (like '1.', '2.', '11.', etc.), topics, or bullet points. ");
        instruction.append("Do NOT categorize questions or list subject areas. ");
        instruction.append("Provide natural, conversational answers in paragraph or sentence form. ");
        instruction.append("For example, if asked 'What is 2+7?', answer '9' or '2+7 equals 9' - NOT '11. basic arithmetic'. ");
        
        // Add difficulty level instruction with appropriate answer length
        switch (difficultyLevel.toLowerCase()) {
            case "beginner":
                instruction.append("The user is a beginner learner. ");
                instruction.append("Provide detailed, comprehensive explanations with simple terms and basic examples. ");
                instruction.append("Break down concepts into easy-to-understand parts with step-by-step explanations. ");
                instruction.append("Use analogies and real-world examples to help understanding. ");
                instruction.append("For simple questions (like basic math), give a brief direct answer followed by a short explanation. ");
                instruction.append("For complex topics, provide thorough explanations (2-4 paragraphs) to ensure understanding. ");
                break;
            case "advanced":
                instruction.append("The user is an advanced learner. ");
                instruction.append("Provide concise, direct answers. ");
                instruction.append("You can use technical terminology and assume strong foundational knowledge. ");
                instruction.append("For simple questions, give a brief, direct answer (1-2 sentences). ");
                instruction.append("For complex topics, provide focused explanations (1-2 paragraphs maximum). ");
                instruction.append("Avoid unnecessary elaboration - get straight to the point. ");
                break;
            case "intermediate":
            default:
                instruction.append("The user is an intermediate learner. ");
                instruction.append("Provide balanced explanations with clear examples. ");
                instruction.append("Include both practical applications and underlying concepts. ");
                instruction.append("Use appropriate technical terms but explain them when needed. ");
                instruction.append("For simple questions, give a direct answer with a brief explanation (1-2 sentences). ");
                instruction.append("For complex topics, provide moderate explanations (1-3 paragraphs). ");
                break;
        }
        
        // Add preparation goal context
        if (preparingFor != null && !preparingFor.trim().isEmpty()) {
            instruction.append("The user is preparing for: ").append(preparingFor.trim()).append(". ");
            instruction.append("Tailor your responses to help them succeed in this goal. ");
        }
        
        // Add general instruction about matching answer length to question complexity
        instruction.append("Match your answer length to the question complexity - simple questions deserve simple answers. ");
        instruction.append("Always answer the question directly - do not list topics or numbered items unless the user specifically asks for a list. ");
        
        return instruction.toString();
    }
    
    /**
     * Extract relevant content based on the user's question
     */
    private String getRelevantContent(String fullContent, String userQuestion) {
        String lowerQuestion = userQuestion.toLowerCase();
        
        // For author-related questions, look for author information
        if (lowerQuestion.contains("author") || lowerQuestion.contains("written by") || 
            lowerQuestion.contains("who wrote") || lowerQuestion.contains("created by")) {
            return extractAuthorContent(fullContent);
        }
        
        // For summary questions, use beginning and key sections
        if (lowerQuestion.contains("summary") || lowerQuestion.contains("summarize") || 
            lowerQuestion.contains("overview") || lowerQuestion.contains("key points")) {
            return extractSummaryContent(fullContent);
        }
        
        // For specific topic questions, try to find relevant sections
        if (lowerQuestion.contains("what is") || lowerQuestion.contains("explain") || 
            lowerQuestion.contains("about")) {
            return extractTopicContent(fullContent, userQuestion);
        }
        
        // Default: use first portion
        return fullContent.length() > 1200 ? fullContent.substring(0, 1200) + "..." : fullContent;
    }
    
    private String extractAuthorContent(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        
        // Look for author information in first few lines and throughout document
        for (int i = 0; i < Math.min(20, lines.length); i++) {
            String line = lines[i].toLowerCase();
            if (line.contains("author") || line.contains("by ") || line.contains("written")) {
                result.append(lines[i]).append("\n");
            }
        }
        
        // If no author found in beginning, search throughout document
        if (result.length() == 0) {
            for (String line : lines) {
                String lowerLine = line.toLowerCase();
                if (lowerLine.contains("author") || lowerLine.contains("written by") || 
                    lowerLine.contains("created by")) {
                    result.append(line).append("\n");
                    if (result.length() > 500) break;
                }
            }
        }
        
        // If still no author info, return beginning of document
        if (result.length() == 0) {
            return content.length() > 800 ? content.substring(0, 800) + "..." : content;
        }
        
        return result.toString().trim();
    }
    
    private String extractSummaryContent(String content) {
        // For summary, use beginning, middle, and end portions
        int length = content.length();
        if (length <= 1200) {
            return content;
        }
        
        String beginning = content.substring(0, Math.min(400, length));
        String middle = content.substring(length / 2 - 200, Math.min(length / 2 + 200, length));
        String end = content.substring(Math.max(length - 400, length / 2), length);
        
        return beginning + "\n...\n" + middle + "\n...\n" + end;
    }
    
    private String extractTopicContent(String content, String question) {
        // Try to find sections relevant to the question keywords
        String[] questionWords = question.toLowerCase().split("\\s+");
        String[] lines = content.split("\n");
        StringBuilder relevantContent = new StringBuilder();
        
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            for (String word : questionWords) {
                if (word.length() > 3 && lowerLine.contains(word)) {
                    relevantContent.append(line).append("\n");
                    if (relevantContent.length() > 1000) break;
                }
            }
            if (relevantContent.length() > 1000) break;
        }
        
        // If no relevant content found, use beginning
        if (relevantContent.length() == 0) {
            return content.length() > 1200 ? content.substring(0, 1200) + "..." : content;
        }
        
        return relevantContent.toString();
    }
    
    /**
     * Get personalized welcome message based on user's name
     */
    private String getPersonalizedWelcomeMessage() {
        StringBuilder message = new StringBuilder();
        
        if (!userName.isEmpty() && !userName.equals("Study Master")) {
            message.append("Hi ").append(userName).append("! I'm Atom, your AI tutor.");
        } else {
            message.append("Hi! I'm Atom, your AI tutor.");
        }
        
        // Check if this is global chat (from homepage) or subject-specific
        boolean isGlobalChat = getIntent().getBooleanExtra("is_global_chat", false);
        String pdfContext = getIntent().getStringExtra("pdf_context");
        String subjectContext = getIntent().getStringExtra("subject_context");
        
        if (isGlobalChat) {
            // Global chat with access to all subjects
            message.append(" I have access to all your subjects and can help you with anything you're studying.");
            message.append(" Feel free to ask me questions from any of your materials!");
        } else if (pdfContext != null && !pdfContext.isEmpty()) {
            // Subject-specific with PDF context
            message.append(" I have loaded '").append(pdfContext).append("' as context.");
            if (currentWeakTopic != null && !currentWeakTopic.trim().isEmpty()) {
                message.append(" You're focusing on the topic: ").append(currentWeakTopic).append(".");
            }
            message.append(" What would you like to know about this document?");
        } else if (subjectContext != null && !subjectContext.isEmpty()) {
            // Subject-specific without specific PDF
            message.append(" I'm ready to help you with ").append(subjectContext).append(".");
            message.append(" What would you like to study today?");
        } else {
            message.append(" I'm here to help you learn and understand any subject. What would you like to study today?");
        }
        
        return message.toString();
    }
    
    /**
     * Check if user message is a greeting and return personalized response
     */
    private String getPersonalizedGreeting(String userMessage) {
        String lowerMessage = userMessage.toLowerCase().trim();
        
        // Check for various greeting patterns
        if (lowerMessage.equals("hi") || lowerMessage.equals("hello") || 
            lowerMessage.equals("hey") || lowerMessage.equals("good morning") || 
            lowerMessage.equals("good afternoon") || lowerMessage.equals("good evening") ||
            lowerMessage.startsWith("hi ") || lowerMessage.startsWith("hello ") ||
            lowerMessage.startsWith("hey ")) {
            
            if (!userName.isEmpty() && !userName.equals("Study Master")) {
                return "Hi " + userName + "! Great to see you again. How can I help you with your studies today?";
            } else {
                return "Hi there! How can I help you with your studies today?";
            }
        }
        
        return null; // Not a greeting
    }
    
    /**
     * Clean chat response to remove [END] markers and other artifacts
     */
    private String cleanChatResponse(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        
        // Remove [END] marker and anything after it
        int endIndex = response.indexOf("[END]");
        if (endIndex != -1) {
            response = response.substring(0, endIndex);
        }
        
        // Also handle case-insensitive variants
        response = response.replaceAll("(?i)\\[END\\].*?$", "");
        
        // Trim trailing whitespace
        return response.trim();
    }
    
    /**
     * Process all documents for global chat context
     */
    private void processAllDocumentsForGlobalChat() {
        if (documentManager == null || contextManager == null) {
            Log.w("ChatApp", "Cannot process documents - managers not initialized");
            return;
        }
        
        List<Document> allDocuments = documentManager.getAllDocuments();
        Log.d("ChatApp", "Found " + allDocuments.size() + " documents to process for global context");
        
        if (allDocuments.isEmpty()) {
            Log.w("ChatApp", "No documents available for global context");
            return;
        }
        
        // Process each document in the background
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int processedCount = 0;
            for (Document doc : allDocuments) {
                try {
                    String textContent = doc.getTextContent();
                    if (textContent != null && !textContent.trim().isEmpty()) {
                        Log.d("ChatApp", "Processing document: " + doc.getDisplayName() + 
                              " (" + textContent.length() + " chars)");
                        
                        // Process synchronously for now (callback not needed for global init)
                        contextManager.processDocument(doc.getId(), textContent, 
                            new ContextWindowManager.ProcessCallback() {
                                @Override
                                public void onSuccess(int chunkCount, boolean needsSummarization) {
                                    Log.d("ChatApp", "Processed " + doc.getDisplayName() + 
                                          ": " + chunkCount + " chunks");
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e("ChatApp", "Error processing " + doc.getDisplayName() + 
                                          ": " + error);
                                }
                            });
                        processedCount++;
                    }
                } catch (Exception e) {
                    Log.e("ChatApp", "Error processing document " + doc.getDisplayName(), e);
                }
            }
            
            final int finalCount = processedCount;
            runOnUiThread(() -> {
                Log.i("ChatApp", "Global context initialized with " + finalCount + " documents");
            });
        });
        executor.shutdown();
    }
}
