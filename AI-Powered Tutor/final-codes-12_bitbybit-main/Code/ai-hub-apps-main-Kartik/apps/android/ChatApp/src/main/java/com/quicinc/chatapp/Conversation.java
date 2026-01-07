// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.system.Os;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Conversation extends AppCompatActivity {

    ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>(1000);
    private String pdfContext = "";  // Stores extracted PDF text
    private String pdfFileName = "";  // Stores PDF filename
    private String imageContext = "";  // Stores image analysis
    private String imageFileName = "";  // Stores image filename
    private String mode = "chat";  // Mode: "chat", "pdf", or "image"
    private GenieWrapper genieWrapper = null;  // Persistent model instance
    private SharedPreferences preferences;  // For tracking metrics
    private boolean sessionTracked = false;  // Track if we've already counted this session
    private StringBuilder conversationHistory = new StringBuilder();  // Store conversation context
    private static final int MAX_HISTORY_MESSAGES = 6;  // Keep last 6 messages (3 exchanges)

    // Chat session management
    private ChatSessionManager sessionManager;
    private ChatSession currentSession;
    private DrawerLayout drawerLayout;
    private LinearLayout navDrawer;
    private ChatHistoryAdapter chatHistoryAdapter;
    private RecyclerView historyRecyclerView;
    private Message_RecyclerViewAdapter chatAdapter;
    private TextView emptyStateText;

    private static final String cWelcomeMessage = "Hi! How can I help you?\n\nTip: Click the 📄 button to upload a PDF or 🖼️ to analyze an image!";
    private static final String cPdfWelcomeMessage = "Upload a PDF document to analyze and ask questions about it!";
    private static final String cImageWelcomeMessage = "Upload an image to analyze and ask questions about it!";
    public static final String cConversationActivityKeyHtpConfig = "htp_config_path";
    public static final String cConversationActivityKeyModelName = "model_dir_name";

    // File picker launchers
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize preferences for tracking metrics
        preferences = getSharedPreferences("TutorAppPrefs", MODE_PRIVATE);

        // Get mode from intent
        Intent intent = getIntent();
        mode = intent.getStringExtra("mode");
        if (mode == null) {
            mode = "chat";
        }

        // Check for gem name and auto-load content
        String gemName = intent.getStringExtra("gem_name");
        String pdfUriString = intent.getStringExtra("pdf_uri");
        String imageUriString = intent.getStringExtra("image_uri");

        // Initialize PDF Box (required for PDF parsing)
        PDFBoxResourceLoader.init(getApplicationContext());

        setContentView(R.layout.chat_with_drawer);
        
        // Initialize chat session manager
        String username = preferences.getString("current_user_name", "default_user");
        sessionManager = new ChatSessionManager(this, username);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Setup drawer layout
        drawerLayout = findViewById(R.id.drawer_layout);
        navDrawer = findViewById(R.id.nav_drawer);
        
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        
        // Setup drawer content
        setupDrawerContent();
        
        // Load or create current session
        String currentSessionId = sessionManager.getCurrentSessionId();
        if (currentSessionId != null && sessionManager.sessionExists(currentSessionId)) {
            currentSession = sessionManager.loadSession(currentSessionId);
            if (currentSession != null) {
                // Restore messages from session
                messages.clear();
                messages.addAll(currentSession.getMessages());
                pdfContext = currentSession.getPdfContext() != null ? currentSession.getPdfContext() : "";
                pdfFileName = currentSession.getPdfFileName() != null ? currentSession.getPdfFileName() : "";
                imageContext = currentSession.getImageContext() != null ? currentSession.getImageContext() : "";
                imageFileName = currentSession.getImageFileName() != null ? currentSession.getImageFileName() : "";
            }
        } else {
            // Create new session
            currentSession = new ChatSession();
            sessionManager.setCurrentSessionId(currentSession.getSessionId());
        }
        
        // Set title based on mode
        if (getSupportActionBar() != null) {
            if (mode.equals("pdf")) {
                getSupportActionBar().setTitle("📄 PDF Analysis");
            } else if (mode.equals("image")) {
                getSupportActionBar().setTitle("🖼️ Image Analysis");
            } else {
                getSupportActionBar().setTitle("💬 Chat Assistant");
            }
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        RecyclerView recyclerView = findViewById(R.id.chat_recycler_view);
        chatAdapter = new Message_RecyclerViewAdapter(this, messages);
        recyclerView.setAdapter(chatAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ImageButton sendUserMsgButton = (ImageButton) findViewById(R.id.send_button);
        ImageButton pdfButton = (ImageButton) findViewById(R.id.pdf_button);
        ImageButton imageButton = (ImageButton) findViewById(R.id.image_button);
        ImageButton clearPdfButton = (ImageButton) findViewById(R.id.clear_pdf_button);
        TextView userMsg = (TextView) findViewById(R.id.user_input);
        LinearLayout pdfStatusLayout = (LinearLayout) findViewById(R.id.pdf_status_layout);
        TextView pdfStatusText = (TextView) findViewById(R.id.pdf_status_text);

        // Setup PDF picker
        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri pdfUri = result.getData().getData();
                        loadPdfInBackground(pdfUri, pdfStatusLayout, pdfStatusText, chatAdapter);
                    }
                }
        );

        // Setup Image picker
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        loadImageInBackground(imageUri, pdfStatusLayout, pdfStatusText, chatAdapter);
                    }
                }
        );

        // PDF button click - open file picker
        pdfButton.setOnClickListener(v -> {
            Intent intent2 = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent2.setType("application/pdf");
            intent2.addCategory(Intent.CATEGORY_OPENABLE);
            pdfPickerLauncher.launch(intent2);
        });

        // Image button click - open image picker
        imageButton.setOnClickListener(v -> {
            Intent intent2 = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent2.setType("image/*");
            intent2.addCategory(Intent.CATEGORY_OPENABLE);
            imagePickerLauncher.launch(intent2);
        });

        // Clear PDF/Image button
        clearPdfButton.setOnClickListener(v -> {
            pdfContext = "";
            pdfFileName = "";
            imageContext = "";
            imageFileName = "";
            pdfStatusLayout.setVisibility(View.GONE);
            clearConversationHistory();  // Also clear conversation history when changing context
            chatAdapter.addMessage(new ChatMessage("Context cleared. Back to normal chat mode. Conversation history reset.", MessageSender.BOT));
            chatAdapter.notifyItemInserted(chatAdapter.getItemCount() - 1);
            recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        });

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
            
            // MODIFIED: Use uploaded files from /data/local/tmp instead of app cache
            // You can change this path to use different uploaded locations:
            // Option 1: /data/local/tmp/genie_bundle (complete bundle with libs)
            // Option 2: /data/local/tmp/genie_models (models only)
            // Option 3: /data/local/tmp/qairt/lib/aarch64-android (QAIRT SDK location)
            
            String modelDir = "/data/local/tmp/genie_bundle";  // Using complete bundle
            
            // Use the main genie config (contains dialog field)
            htpExtensionsDir = "genie_config.json";  // Main config file with dialog settings
            
            Log.i("ChatApp", "Using model directory: " + modelDir);
            Log.i("ChatApp", "Using config file: " + htpExtensionsDir);

            // Load Model once and keep it in memory
            genieWrapper = new GenieWrapper(modelDir, htpExtensionsDir);
            Log.i("ChatApp", modelName + " Loaded.");

            // Add welcome message based on mode
            if (mode.equals("pdf")) {
                messages.add(new ChatMessage(cPdfWelcomeMessage, MessageSender.BOT));
                // Auto-trigger PDF picker in PDF mode
                pdfButton.performClick();
            } else if (mode.equals("image")) {
                messages.add(new ChatMessage(cImageWelcomeMessage, MessageSender.BOT));
                // Auto-trigger image picker in image mode
                imageButton.performClick();
            } else {
                messages.add(new ChatMessage(cWelcomeMessage, MessageSender.BOT));
            }

            // Auto-load content from gem intents
            if (gemName != null) {
                String welcomeMsg = "Welcome to " + gemName + "!";
                if (pdfUriString != null || imageUriString != null) {
                    welcomeMsg += " Loading your documents...";
                    // Disable send button until content is loaded
                    sendUserMsgButton.setEnabled(false);
                }
                messages.add(new ChatMessage(welcomeMsg, MessageSender.BOT));
                
                // Auto-load PDF if provided
                if (pdfUriString != null && !pdfUriString.isEmpty()) {
                    try {
                        Uri pdfUri = Uri.parse(pdfUriString);
                        loadPdfInBackground(pdfUri, pdfStatusLayout, pdfStatusText, chatAdapter);
                    } catch (Exception e) {
                        Log.e("ChatApp", "Error loading PDF from gem: " + e.getMessage());
                        // Re-enable send button on error
                        sendUserMsgButton.setEnabled(true);
                    }
                }
                
                // Auto-load image if provided
                if (imageUriString != null && !imageUriString.isEmpty()) {
                    try {
                        Uri imageUri = Uri.parse(imageUriString);
                        loadImageInBackground(imageUri, pdfStatusLayout, pdfStatusText, chatAdapter);
                    } catch (Exception e) {
                        Log.e("ChatApp", "Error loading image from gem: " + e.getMessage());
                        // Re-enable send button on error
                        sendUserMsgButton.setEnabled(true);
                    }
                }
            }

            // Get response from Bot once user message is sent
            sendUserMsgButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (userMsg.getTextSize() != 0) {
                        String userInputMsg = userMsg.getText().toString();
                        // Reset user message box
                        userMsg.setText("");

                        // Track chat session on first user message
                        if (!sessionTracked) {
                            int currentCount = preferences.getInt("chat_sessions_count", 0);
                            preferences.edit().putInt("chat_sessions_count", currentCount + 1).apply();
                            sessionTracked = true;
                        }

                        // Insert user message in the conversation
                        chatAdapter.addMessage(new ChatMessage(userInputMsg, MessageSender.USER));
                        chatAdapter.notifyItemInserted(chatAdapter.getItemCount() - 1);

                        int botResponseMsgIndex = chatAdapter.getItemCount();
                        recyclerView.smoothScrollToPosition(botResponseMsgIndex);

                        ExecutorService service = Executors.newSingleThreadExecutor();
                        service.execute(new Runnable() {
                            @Override
                            public void run() {
                                // Build conversation history for context
                                String contextPrompt = buildConversationContext(userInputMsg);
                                
                                genieWrapper.getResponseForPrompt(contextPrompt, new StringCallback() {
                                    @Override
                                    public void onNewString(String response) {
                                        runOnUiThread(() -> {
                                            // Update the last item in the adapter
                                            chatAdapter.updateBotMessage(response);
                                            chatAdapter.notifyItemChanged(botResponseMsgIndex);
                                        });
                                    }
                                });
                                
                                // After response completes, add to history
                                runOnUiThread(() -> {
                                    String botResponse = chatAdapter.getLastBotMessage();
                                    addToConversationHistory(userInputMsg, botResponse);
                                    saveCurrentSession();  // Auto-save after each message
                                });
                            }
                        });

                        // Scroll to last message
                        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                }
            });

        } catch (Exception e) {
            Log.e("ChatApp", "Error during conversation with Chatbot: " + e.toString());
            Toast.makeText(this, "Unexpected error observed. Exiting app.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Load and parse PDF file in background thread
     */
    private void loadPdfInBackground(Uri pdfUri, LinearLayout statusLayout, TextView statusText, Message_RecyclerViewAdapter adapter) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.execute(() -> {
            try {
                // Show loading status
                runOnUiThread(() -> {
                    statusLayout.setVisibility(View.VISIBLE);
                    statusText.setText("Loading PDF...");
                });

                // Extract filename
                String filename = getFileName(pdfUri);
                pdfFileName = filename;

                // Open PDF document
                InputStream inputStream = getContentResolver().openInputStream(pdfUri);
                PDDocument document = PDDocument.load(inputStream);
                
                // Extract text
                PDFTextStripper stripper = new PDFTextStripper();
                String extractedText = stripper.getText(document);
                
                // Format the extracted text for better readability
                extractedText = formatPdfText(extractedText);
                
                // Close document
                document.close();
                inputStream.close();

                // Limit context size to fit model context window (1024 tokens ~= 3000-4000 chars)
                // Using 3500 chars to leave room for conversation history
                if (extractedText.length() > 3500) {
                    pdfContext = extractedText.substring(0, 3500) + "\n\n[...document truncated...]";
                } else {
                    pdfContext = extractedText;
                }

                int pageCount = document.getNumberOfPages();
                int charCount = pdfContext.length();

                // Update UI with formatted success message
                runOnUiThread(() -> {
                    statusText.setText("📄 " + filename + " (" + pageCount + " pages, " + charCount + " chars)");
                    
                    String successMessage = "✅ PDF Loaded Successfully\n\n" +
                                          "📄 File: " + filename + "\n" +
                                          "📊 Pages: " + pageCount + "\n" +
                                          "📝 Characters: " + charCount + "\n\n" +
                                          "💡 You can now ask questions about this document!\n\n" +
                                          "Examples:\n" +
                                          "• Summarize the main points\n" +
                                          "• Explain the formulas\n" +
                                          "• What are the key concepts?";
                    
                    chatAdapter.addMessage(new ChatMessage(successMessage, MessageSender.BOT));
                    chatAdapter.notifyItemInserted(chatAdapter.getItemCount() - 1);
                    
                    // Track document analysis
                    int currentDocs = preferences.getInt("documents_analyzed_count", 0);
                    preferences.edit().putInt("documents_analyzed_count", currentDocs + 1).apply();
                    
                    // Re-enable send button now that content is loaded
                    ImageButton sendUserMsgButton = findViewById(R.id.send_button);
                    sendUserMsgButton.setEnabled(true);
                });

                Log.i("ChatApp", "PDF loaded: " + filename + " (" + pageCount + " pages)");
                Log.i("ChatApp", "PDF context length: " + pdfContext.length());
                Log.i("ChatApp", "PDF context preview: " + (pdfContext.length() > 100 ? pdfContext.substring(0, 100) + "..." : pdfContext));

            } catch (Exception e) {
                Log.e("ChatApp", "Error loading PDF: " + e.toString(), e);
                runOnUiThread(() -> {
                    statusLayout.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    
                    // Re-enable send button even on error
                    ImageButton sendUserMsgButton = findViewById(R.id.send_button);
                    sendUserMsgButton.setEnabled(true);
                });
            }
        });
    }

    /**
     * Load and analyze image in background thread
     */
    private void loadImageInBackground(Uri imageUri, LinearLayout statusLayout, TextView statusText, Message_RecyclerViewAdapter adapter) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.execute(() -> {
            try {
                // Show loading status
                runOnUiThread(() -> {
                    statusLayout.setVisibility(View.VISIBLE);
                    statusText.setText("Analyzing image...");
                });

                // Extract filename
                String filename = getFileName(imageUri);
                imageFileName = filename;

                // Load and analyze the image
                String analysis = analyzeImage(imageUri);
                
                imageContext = analysis;

                // Update UI
                runOnUiThread(() -> {
                    statusText.setText("🖼️ " + filename + " (analyzed)");
                    chatAdapter.addMessage(new ChatMessage("Image loaded successfully: " + filename + 
                                                      "\n\nImage Analysis Complete!" +
                                                      "\n\nI can now answer questions about:" +
                                                      "\n• Text content in the image" +
                                                      "\n• Visual elements and objects" +
                                                      "\n• Scene description" +
                                                      "\n• Colors and composition" +
                                                      "\n\nWhat would you like to know about this image?", 
                                                      MessageSender.BOT));
                    chatAdapter.notifyItemInserted(chatAdapter.getItemCount() - 1);
                    
                    // Track document analysis (images count as documents)
                    int currentDocs = preferences.getInt("documents_analyzed_count", 0);
                    preferences.edit().putInt("documents_analyzed_count", currentDocs + 1).apply();
                    
                    // Re-enable send button now that content is loaded
                    ImageButton sendUserMsgButton = findViewById(R.id.send_button);
                    sendUserMsgButton.setEnabled(true);
                });

                Log.i("ChatApp", "Image loaded: " + filename);
                Log.i("ChatApp", "Image analysis: " + analysis.substring(0, Math.min(200, analysis.length())));

            } catch (Exception e) {
                Log.e("ChatApp", "Error loading image: " + e.toString(), e);
                runOnUiThread(() -> {
                    statusLayout.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    
                    // Re-enable send button even on error
                    ImageButton sendUserMsgButton = findViewById(R.id.send_button);
                    sendUserMsgButton.setEnabled(true);
                });
            }
        });
    }

    private String analyzeImage(Uri imageUri) {
        try {
            // Load image as bitmap for ML Kit
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (bitmap == null) {
                return "Unable to load image for analysis.";
            }
            
            // Get image dimensions
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            String fileName = getFileName(imageUri);
            
            // Create InputImage for ML Kit
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            
            // Initialize ML Kit Text Recognizer
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            
            // StringBuilder to store results
            final StringBuilder analysisResult = new StringBuilder();
            analysisResult.append("📸 Image Analysis:\n");
            analysisResult.append("File: ").append(fileName).append("\n");
            analysisResult.append("Dimensions: ").append(width).append(" x ").append(height).append(" pixels\n");
            analysisResult.append("Aspect Ratio: ").append(width > height ? "Landscape" : height > width ? "Portrait" : "Square").append("\n\n");
            
            // Process image for text recognition (synchronous wait)
            final boolean[] ocrCompleted = {false};
            final String[] extractedText = {""};
            
            recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String text = visionText.getText();
                    extractedText[0] = text;
                    
                    if (text != null && !text.trim().isEmpty()) {
                        analysisResult.append("✅ OCR Text Extraction:\n");
                        analysisResult.append("━━━━━━━━━━━━━━━━━━━━━\n");
                        analysisResult.append(text.trim()).append("\n");
                        analysisResult.append("━━━━━━━━━━━━━━━━━━━━━\n\n");
                        analysisResult.append("📝 Detected ").append(visionText.getTextBlocks().size())
                                     .append(" text blocks with ").append(text.split("\\s+").length)
                                     .append(" words.\n\n");
                        analysisResult.append("💡 You can now ask questions about the text content!");
                    } else {
                        analysisResult.append("ℹ️ No text detected in this image.\n");
                        analysisResult.append("This appears to be a visual image without readable text.\n");
                        analysisResult.append("You can ask about colors, patterns, or visual elements.");
                    }
                    
                    synchronized (ocrCompleted) {
                        ocrCompleted[0] = true;
                        ocrCompleted.notify();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatApp", "OCR failed: " + e.getMessage());
                    analysisResult.append("⚠️ OCR processing failed: ").append(e.getMessage()).append("\n");
                    analysisResult.append("Image loaded but text extraction unavailable.");
                    
                    synchronized (ocrCompleted) {
                        ocrCompleted[0] = true;
                        ocrCompleted.notify();
                    }
                });
            
            // Wait for OCR to complete (max 5 seconds)
            synchronized (ocrCompleted) {
                try {
                    if (!ocrCompleted[0]) {
                        ocrCompleted.wait(5000);
                    }
                } catch (InterruptedException e) {
                    Log.e("ChatApp", "OCR wait interrupted: " + e.getMessage());
                }
            }
            
            // Store extracted text for Llama context
            if (extractedText[0] != null && !extractedText[0].trim().isEmpty()) {
                imageContext = "EXTRACTED TEXT FROM IMAGE:\n" + extractedText[0];
            } else {
                imageContext = "Image: " + fileName + " (no text detected)";
            }
            
            return analysisResult.toString();
            
        } catch (Exception e) {
            Log.e("ChatApp", "Error analyzing image: " + e.getMessage());
            return "❌ Error analyzing image: " + e.getMessage() + 
                   "\nBasic image loaded. You can still ask general questions.";
        }
    }
    
    /**
     * Get filename from URI
     */
    private String getFileName(Uri uri) {
        String filename = "document.pdf";
        if (uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        filename = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (filename == null || filename.isEmpty()) {
            filename = uri.getLastPathSegment();
        }
        return filename != null ? filename : "document.pdf";
    }
    
    /**
     * Format PDF extracted text for better readability
     * - Preserves mathematical formulas and equations
     * - Normalizes whitespace and line breaks
     * - Maintains structure for better AI understanding
     */
    private String formatPdfText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Normalize line endings
        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("\\r", "\n");
        
        // Remove excessive blank lines (more than 2 consecutive)
        text = text.replaceAll("\\n{3,}", "\n\n");
        
        // Remove spaces at the beginning and end of lines
        text = text.replaceAll("(?m)^[ \\t]+", "");
        text = text.replaceAll("(?m)[ \\t]+$", "");
        
        // Preserve formulas and equations by adding line breaks around them
        // Detect common mathematical patterns
        text = text.replaceAll("(?m)^([^\\n]{0,5}[a-zA-Z0-9()\\[\\]]+\\s*[=≈≠<>≤≥]\\s*[^\\n]+)$", "\n$1\n");
        
        // Preserve section headers (short lines that might be titles)
        text = text.replaceAll("(?m)^([A-Z][^.!?\\n]{2,40})$", "\n$1\n");
        
        // Normalize excessive spaces (but preserve single spaces)
        text = text.replaceAll("[ \\t]{2,}", " ");
        
        // Fix broken words at line endings (common in PDFs)
        text = text.replaceAll("-\\n([a-z])", "$1");
        
        // Clean up
        text = text.trim();
        
        return text;
    }
    
    /**
     * Build conversation context prompt including history
     */
    private String buildConversationContext(String currentUserMessage) {
        StringBuilder prompt = new StringBuilder();
        
        // Add PDF or image context if available (prioritize this as base context)
        if (!pdfContext.isEmpty()) {
            prompt.append("Document context:\n").append(pdfContext).append("\n\n");
        } else if (!imageContext.isEmpty()) {
            // Enhanced prompt for OCR-extracted text
            prompt.append("You have access to text extracted from an image via OCR.\n");
            prompt.append(imageContext).append("\n\n");
            prompt.append("Please answer questions about this text accurately. ");
            prompt.append("If asked about specific content, quote directly from the extracted text.\n\n");
        }
        
        // Add conversation history for context
        if (conversationHistory.length() > 0) {
            prompt.append("Previous conversation:\n");
            prompt.append(conversationHistory.toString());
            prompt.append("\n");
        }
        
        // Add current user message
        prompt.append("User: ").append(currentUserMessage).append("\n");
        prompt.append("Assistant:");
        
        // Log for debugging
        Log.d("ChatApp", "Context prompt length: " + prompt.length() + " chars");
        
        return prompt.toString();
    }
    
    /**
     * Add exchange to conversation history
     * Maintains sliding window of recent messages
     */
    private void addToConversationHistory(String userMessage, String botResponse) {
        // Count current messages in history
        int messageCount = conversationHistory.toString().split("User:|Assistant:").length - 1;
        
        // If we exceed limit, remove oldest exchange (2 messages: user + assistant)
        if (messageCount >= MAX_HISTORY_MESSAGES) {
            String historyStr = conversationHistory.toString();
            // Find second occurrence of "User:" to keep from there
            int firstUser = historyStr.indexOf("User:");
            int secondUser = historyStr.indexOf("User:", firstUser + 1);
            if (secondUser > 0) {
                conversationHistory = new StringBuilder(historyStr.substring(secondUser));
            }
        }
        
        // Add new exchange
        conversationHistory.append("User: ").append(userMessage).append("\n");
        conversationHistory.append("Assistant: ").append(botResponse).append("\n");
        
        Log.d("ChatApp", "Conversation history size: " + conversationHistory.length() + " chars");
    }
    
    /**
     * Clear conversation history (useful when switching topics or uploading new PDF)
     */
    private void clearConversationHistory() {
        conversationHistory.setLength(0);
        Log.d("ChatApp", "Conversation history cleared");
    }
    
    /**
     * Setup navigation drawer content
     */
    private void setupDrawerContent() {
        // Find views in drawer
        LinearLayout newChatButton = navDrawer.findViewById(R.id.new_chat_button);
        historyRecyclerView = navDrawer.findViewById(R.id.chat_history_recycler);
        emptyStateText = navDrawer.findViewById(R.id.empty_state_text);
        
        // Setup "New Chat" button
        newChatButton.setOnClickListener(v -> {
            createNewChat();
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        
        // Setup chat history adapter
        chatHistoryAdapter = new ChatHistoryAdapter(new ChatHistoryAdapter.OnChatSessionClickListener() {
            @Override
            public void onChatSessionClick(ChatSession session) {
                loadChatSession(session.getSessionId());
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            
            @Override
            public void onChatSessionLongClick(ChatSession session) {
                // Show delete confirmation
                showDeleteSessionDialog(session);
            }
        });
        
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(chatHistoryAdapter);
        
        // Load chat history
        loadChatHistory();
    }
    
    /**
     * Load chat history into navigation drawer
     */
    private void loadChatHistory() {
        List<ChatSession> sessions = sessionManager.loadAllSessions();
        
        // Filter out current session from list
        List<ChatSession> otherSessions = new ArrayList<>();
        for (ChatSession session : sessions) {
            if (currentSession == null || !session.getSessionId().equals(currentSession.getSessionId())) {
                otherSessions.add(session);
            }
        }
        
        // Update UI
        if (otherSessions.isEmpty()) {
            historyRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            historyRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            chatHistoryAdapter.setChatSessions(otherSessions);
        }
        
        Log.d("ChatApp", "Loaded " + otherSessions.size() + " previous chat sessions");
    }
    
    /**
     * Show delete confirmation dialog
     */
    private void showDeleteSessionDialog(ChatSession session) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Chat")
            .setMessage("Delete \"" + session.getTitle() + "\"?")
            .setPositiveButton("Delete", (dialog, which) -> {
                sessionManager.deleteSession(session.getSessionId());
                loadChatHistory();  // Refresh list
                Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Create a new chat session
     */
    private void createNewChat() {
        // Save current session before creating new one
        saveCurrentSession();
        
        // Clear current chat
        messages.clear();
        chatAdapter.notifyDataSetChanged();
        
        // Clear contexts
        pdfContext = "";
        pdfFileName = "";
        imageContext = "";
        imageFileName = "";
        clearConversationHistory();
        
        // Create new session
        currentSession = new ChatSession();
        sessionManager.setCurrentSessionId(currentSession.getSessionId());
        
        // Add welcome message
        messages.add(new ChatMessage(cWelcomeMessage, MessageSender.BOT));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        
        // Refresh drawer to show previous chat
        loadChatHistory();
        
        Toast.makeText(this, "New chat started", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Save current chat session
     */
    private void saveCurrentSession() {
        if (currentSession != null && !messages.isEmpty()) {
            // Update session data
            currentSession.setMessages(new ArrayList<>(messages));
            currentSession.setPdfContext(pdfContext, pdfFileName);
            currentSession.setImageContext(imageContext, imageFileName);
            currentSession.updateTimestamp();
            
            // Generate title from first user message if still "New Chat"
            if (currentSession.getTitle().equals("New Chat")) {
                currentSession.generateTitleFromFirstMessage();
            }
            
            // Save to storage
            sessionManager.saveSession(currentSession);
            Log.d("ChatApp", "Session saved: " + currentSession.getSessionId());
        }
    }
    
    /**
     * Load a specific chat session
     */
    private void loadChatSession(String sessionId) {
        // Save current session first
        saveCurrentSession();
        
        // Load new session
        ChatSession session = sessionManager.loadSession(sessionId);
        if (session != null) {
            currentSession = session;
            sessionManager.setCurrentSessionId(sessionId);
            
            // Restore messages
            messages.clear();
            messages.addAll(session.getMessages());
            chatAdapter.notifyDataSetChanged();
            
            // Restore contexts
            pdfContext = session.getPdfContext() != null ? session.getPdfContext() : "";
            pdfFileName = session.getPdfFileName() != null ? session.getPdfFileName() : "";
            imageContext = session.getImageContext() != null ? session.getImageContext() : "";
            imageFileName = session.getImageFileName() != null ? session.getImageFileName() : "";
            
            // Rebuild conversation history from messages
            rebuildConversationHistory();
            
            Toast.makeText(this, "Loaded: " + session.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Rebuild conversation history from loaded messages
     */
    private void rebuildConversationHistory() {
        conversationHistory.setLength(0);
        
        // Take last MAX_HISTORY_MESSAGES
        int start = Math.max(0, messages.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.isMessageFromUser()) {
                conversationHistory.append("User: ").append(msg.getMessage()).append("\n");
            } else {
                conversationHistory.append("Assistant: ").append(msg.getMessage()).append("\n");
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Auto-save when app goes to background
        saveCurrentSession();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Save before closing
        saveCurrentSession();
    }
    
    @Override
    public void onBackPressed() {
        // Close drawer if open, otherwise normal back behavior
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

