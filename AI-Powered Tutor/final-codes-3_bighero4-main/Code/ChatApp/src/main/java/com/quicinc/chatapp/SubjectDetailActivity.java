package com.quicinc.chatapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SubjectDetailActivity extends AppCompatActivity {

    private String subjectName;
    private String subjectDescription;
    private int subjectColor;
    private int subjectProgress;
    
    private RecentActivityAdapter recentActivityAdapter;
    private RecentActivityManager activityManager;
    private RecyclerView recentActivityRecyclerView;
    private TextView noActivityText;
    
    // PDF Management
    private DocumentManager documentManager;
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private PDFExtractionService pdfExtractionService;
    private List<Document> subjectPdfs;
    private Document selectedPdf;
    private LinearLayout pdfListContainer;
    private TextView noPdfsMessage;
    private LinearLayout contextListContainer;
    private TextView noContextMessage;
    private LinearLayout allTopicsSection;
    private LinearLayout allTopicsContainer;
    private LinearLayout weakTopicsSection;
    private LinearLayout weakTopicsHeader;
    private LinearLayout weakTopicsContent;
    private LinearLayout weakTopicsContainer;
    private TextView noWeakTopicsMessage;
    private TextView weakTopicsCount;
    private ImageView weakTopicsExpandIcon;
    private WeakTopicManager weakTopicManager;
    private final Map<String, View> contextEntryViews = new HashMap<>();
    private final Set<String> contextsInProgress = new HashSet<>();
    private SubjectTopicsManager topicsManager;
    private ContextGenerationService contextGenerationService;
    private WorkingGenieWrapper genieWrapper;
    private ContextWindowManager contextWindowManager;
    
    // Required for launching Conversation activity
    private String htpConfigPath;
    private String modelName;

    // Track if activity is in foreground
    private boolean isActivityInForeground = false;
    private static final int MAX_TOPICS_DISPLAY = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);

        // Get data from intent
        subjectName = getIntent().getStringExtra("subject_name");
        subjectDescription = getIntent().getStringExtra("subject_description");
        subjectColor = getIntent().getIntExtra("subject_color", R.color.primary_blue);
        subjectProgress = getIntent().getIntExtra("subject_progress", 0);

        // Initialize conversation parameters
        initializeConversationParams();
        
        activityManager = new RecentActivityManager(this);
        
        // Initialize PDF management
        initializePDFServices();
    initializeContextServices();
        
        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadRecentActivity();
        
                // Delay PDF loading slightly to ensure UI is fully ready
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                loadSubjectPdfs();
                // Also refresh context library after PDFs are loaded
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        refreshContextLibrary();
                    }
                }, 200);
            }
        }, 100);
        
        // Check if PDF should be auto-uploaded
        String autoUploadPdfUri = getIntent().getStringExtra("auto_upload_pdf_uri");
        if (autoUploadPdfUri != null) {
            // Delay slightly to ensure UI is ready
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                Uri pdfUri = Uri.parse(autoUploadPdfUri);
                processPDFUpload(pdfUri);
            }, 500);
        }
    }

    private void initializeViews() {
        TextView nameText = findViewById(R.id.subject_name);
        TextView descriptionText = findViewById(R.id.subject_description);
        pdfListContainer = findViewById(R.id.pdf_list_container);
        noPdfsMessage = findViewById(R.id.no_pdfs_message);
    contextListContainer = findViewById(R.id.context_list_container);
    noContextMessage = findViewById(R.id.no_context_message);
    allTopicsSection = findViewById(R.id.all_topics_section);
    allTopicsContainer = findViewById(R.id.all_topics_container);
    weakTopicsSection = findViewById(R.id.weak_topics_section);
    weakTopicsHeader = findViewById(R.id.weak_topics_header);
    weakTopicsContent = findViewById(R.id.weak_topics_content);
    weakTopicsContainer = findViewById(R.id.weak_topics_container);
    noWeakTopicsMessage = findViewById(R.id.no_weak_topics_message);
    weakTopicsCount = findViewById(R.id.weak_topics_count);
    weakTopicsExpandIcon = findViewById(R.id.weak_topics_expand_icon);
    weakTopicManager = new WeakTopicManager(this);
    
    // Setup weak topics dropdown
    setupWeakTopicsDropdown();
        
        recentActivityRecyclerView = findViewById(R.id.recent_activity_recycler_view);
        noActivityText = findViewById(R.id.no_activity_text);

        // Setup back button navigation
        android.widget.ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        nameText.setText(subjectName);
        descriptionText.setText(getModernDescription());
        
        // Apply fixed colors to icons (not subject-specific)
        try {
            ImageView askAtomIcon = findViewById(R.id.ask_atom_icon);
            ImageView quizIcon = findViewById(R.id.quiz_icon);
            ImageView flashcardsIcon = findViewById(R.id.flashcards_icon);
            ImageView documentIcon = findViewById(R.id.document_icon);
            ImageView activityIcon = findViewById(R.id.activity_icon);
            ImageView contextIcon = findViewById(R.id.context_icon);
            
            // Fixed colors for each icon
            if (askAtomIcon != null) {
                int atomColor = getResources().getColor(R.color.background_dark, null); // Green
                askAtomIcon.setColorFilter(atomColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (quizIcon != null) {
                int quizColor = getResources().getColor(R.color.background_dark, null); // Blue
                quizIcon.setColorFilter(quizColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (flashcardsIcon != null) {
                int flashColor = getResources().getColor(R.color.background_dark, null); // Purple
                flashcardsIcon.setColorFilter(flashColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (documentIcon != null) {
                int docColor = getResources().getColor(R.color.background_dark, null); // Orange
                documentIcon.setColorFilter(docColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (activityIcon != null) {
                int activityColor = getResources().getColor(R.color.background_dark, null); // Pink
                activityIcon.setColorFilter(activityColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (contextIcon != null) {
                int contextColor = getResources().getColor(R.color.background_dark, null);
                contextIcon.setColorFilter(contextColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception e) {
            android.util.Log.e("SubjectDetail", "Error applying colors", e);
        }
        
        // Initialize new UI elements
    setupActivityCollapse();
    updateTopicsOverview();
    }
    
    private String getModernDescription() {
        // Return modern, production-level descriptions
        switch (subjectName) {
            case "Data Structures and Algorithms":
                return "Master efficient problem-solving techniques";
            case "Operating Systems and Networking":
                return "Understand system architecture and network protocols";
            case "Probability and Statistics":
                return "Analyze data and make informed decisions";
            case "Automata Theory":
                return "Explore computational models and formal languages";
            case "Data and Application":
                return "Build robust applications and manage data";
            default:
                return "Enhance your knowledge and skills";
        }
    }
    

    
    private void setupActivityCollapse() {
        LinearLayout activityHeader = findViewById(R.id.activity_header);
        LinearLayout activityContent = findViewById(R.id.activity_content);
        ImageView expandIcon = findViewById(R.id.activity_expand_icon);
        
        final boolean[] isExpanded = {false};
        
        activityHeader.setOnClickListener(v -> {
            if (isExpanded[0]) {
                // Collapse
                activityContent.setVisibility(View.GONE);
                expandIcon.setRotation(0);
                isExpanded[0] = false;
            } else {
                // Expand
                activityContent.setVisibility(View.VISIBLE);
                expandIcon.setRotation(180);
                isExpanded[0] = true;
            }
        });
        
        // Update activity summary
        updateActivitySummary();
    }
    
    /**
     * Setup weak topics dropdown collapse/expand
     */
    private void setupWeakTopicsDropdown() {
        if (weakTopicsHeader == null || weakTopicsContent == null || weakTopicsExpandIcon == null) {
            return;
        }
        
        final boolean[] isExpanded = {false};
        
        weakTopicsHeader.setOnClickListener(v -> {
            if (isExpanded[0]) {
                // Collapse
                weakTopicsContent.setVisibility(View.GONE);
                weakTopicsExpandIcon.setRotation(0);
                isExpanded[0] = false;
            } else {
                // Expand
                weakTopicsContent.setVisibility(View.VISIBLE);
                weakTopicsExpandIcon.setRotation(180);
                isExpanded[0] = true;
            }
        });
    }
    
    private void setupRecyclerView() {
        List<RecentActivity> activities = activityManager.getActivitiesForSubject(subjectName);
        
        // Limit to 3 most recent activities
        List<RecentActivity> limitedActivities = activities.size() > 3 
            ? activities.subList(0, 3) 
            : activities;
        
        recentActivityAdapter = new RecentActivityAdapter(limitedActivities);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recentActivityRecyclerView.setLayoutManager(layoutManager);
        recentActivityRecyclerView.setAdapter(recentActivityAdapter);
    }
    
    private void loadRecentActivity() {
        List<RecentActivity> activities = activityManager.getActivitiesForSubject(subjectName);
        
        if (activities.isEmpty()) {
            recentActivityRecyclerView.setVisibility(View.GONE);
            noActivityText.setVisibility(View.VISIBLE);
        } else {
            recentActivityRecyclerView.setVisibility(View.VISIBLE);
            noActivityText.setVisibility(View.GONE);
            
            // Limit to 3 most recent activities
            List<RecentActivity> limitedActivities = activities.size() > 3 
                ? activities.subList(0, 3) 
                : activities;
            
            recentActivityAdapter.updateActivities(limitedActivities);
        }
        
        // Update activity summary
        updateActivitySummary();
    }
    
    /**
     * Initialize PDF-related services
     */
    private void initializePDFServices() {
        // Initialize document manager
        documentManager = DocumentManager.getInstance(this);
        
        // Initialize PDF extraction service (will set AI wrapper later if needed)
        pdfExtractionService = new PDFExtractionService(this);
        
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
        
        subjectPdfs = new ArrayList<>();
    }

    private void initializeContextServices() {
        topicsManager = new SubjectTopicsManager(this);
        try {
            File externalCacheDir = getExternalCacheDir();
            if (externalCacheDir != null) {
                File modelsDir = new File(externalCacheDir, "models");
                File modelDir = new File(modelsDir, "llm");
                if (!modelDir.exists()) {
                    modelDir.mkdirs();
                }
                String modelDirPath = modelDir.getAbsolutePath();
                
                // Initialize genie wrapper with proper error handling
                try {
                    genieWrapper = new WorkingGenieWrapper(modelDirPath, htpConfigPath, this);
                    contextWindowManager = ContextWindowManager.getInstance(this, genieWrapper);
                    
                    // Update PDF extraction service with AI wrapper for context processing
                    if (pdfExtractionService != null) {
                        pdfExtractionService.setAIWrapper(genieWrapper);
                    }
                } catch (Exception e) {
                    Log.w("SubjectDetailActivity", "Genie wrapper initialization failed (may be expected): " + e.getMessage());
                    genieWrapper = null;
                    contextWindowManager = null;
                }
            }
        } catch (Exception e) {
            Log.e("SubjectDetailActivity", "Failed to initialize context services", e);
            genieWrapper = null;
            contextWindowManager = null;
        }

        // Initialize context generation service (will work with or without AI wrapper)
        contextGenerationService = new ContextGenerationService(this, genieWrapper, contextWindowManager);
    }
    
    /**
     * Initialize parameters needed for Conversation activity
     */
    private void initializeConversationParams() {
        try {
            // Get the HTP config path (same logic as MainActivity)
            java.io.File externalCacheDir = this.getExternalCacheDir();
            java.nio.file.Path htpExtConfigPath = java.nio.file.Paths.get(externalCacheDir.getAbsolutePath(), "models", "llm", "htp_backend_ext_config.json");
            
            this.htpConfigPath = htpExtConfigPath.toString();
            this.modelName = "llm";
            
            android.util.Log.d("SubjectDetailActivity", "Initialized conversation params: " + htpConfigPath);
        } catch (Exception e) {
            android.util.Log.e("SubjectDetailActivity", "Error initializing conversation params", e);
            // Set defaults
            this.htpConfigPath = "";
            this.modelName = "llm";
        }
    }
    

    
    /**
     * Load PDFs for this subject
     */
    private void loadSubjectPdfs() {
        loadSubjectPdfs(true);
    }
    
    /**
     * Load PDFs for this subject
     * @param updateUI whether to update the UI after loading
     */
    private void loadSubjectPdfs(boolean updateUI) {
        subjectPdfs.clear();
        
        // Clean up any duplicates first
        documentManager.cleanupDuplicates();
        
        // Get all documents and filter by subject name (stored as metadata)
        List<Document> allDocs = documentManager.getAllDocuments();
        for (Document doc : allDocs) {
            // Check if document is associated with this subject
            if (isDocumentForSubject(doc, subjectName)) {
                subjectPdfs.add(doc);
            }
        }
        
        if (updateUI) {
            updateDocumentDropdown();
            refreshContextLibrary();
        }
    }
    
    /**
     * Check if document belongs to this subject
     */
    private boolean isDocumentForSubject(Document doc, String subjectName) {
        // Check if the document has a subject name set and if it matches
        if (doc.getSubjectName() != null) {
            return doc.getSubjectName().equals(subjectName);
        }
        // For legacy documents without subject name, don't show them in any specific subject
        return false;
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
     * Process uploaded PDF file with improved error handling and large file support
     */
    private void processPDFUpload(Uri pdfUri) {
        // Show initial progress
        Toast.makeText(this, "Processing PDF...", Toast.LENGTH_SHORT).show();
        
        pdfExtractionService.extractTextFromPDF(pdfUri, new PDFExtractionService.PDFExtractionCallback() {
            @Override
            public void onProgress(String status) {
                runOnUiThread(() -> {
                    Toast.makeText(SubjectDetailActivity.this, status, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onSuccess(Document document) {
                runOnUiThread(() -> {
                    // Check if activity is still valid
                    if (isFinishing() || isDestroyed()) {
                        Log.w("SubjectDetailActivity", "Activity finishing, skipping PDF processing");
                        return;
                    }
                    
                    try {
                        // Set the subject name for this document
                        document.setSubjectName(subjectName);
                        
                        // Check if a document with the same name already exists
                        Document existingDoc = null;
                        for (Document doc : subjectPdfs) {
                            if (doc.getDisplayName().equals(document.getDisplayName())) {
                                existingDoc = doc;
                                break;
                            }
                        }
                        
                        boolean isReplacement = existingDoc != null;
                        
                        if (isReplacement) {
                            // Remove old document from subject PDFs list
                            subjectPdfs.remove(existingDoc);
                            
                            // Clean up context data for old document
                            try {
                                if (contextWindowManager != null) {
                                    contextWindowManager.deleteDocumentChunks(existingDoc.getId());
                                    contextWindowManager.deleteDocumentChunks(existingDoc.getDisplayName());
                                }
                            } catch (Exception e) {
                                android.util.Log.e("SubjectDetailActivity", "Error cleaning up old context: " + e.getMessage());
                            }
                        }
                        
                        // Add to document manager (will replace if exists)
                        documentManager.addDocument(document);
                        
                        // Reload PDFs to ensure consistency
                        loadSubjectPdfs(true);
                        
                        // Automatically select the newly uploaded document
                        selectedPdf = document;
                        documentManager.setSelectedDocument(document.getId());
                        
                        // Force immediate UI refresh
                        forceRefreshPdfList();
                        
                        String message = isReplacement ? 
                            "PDF '" + document.getDisplayName() + "' replaced!" :
                            "PDF '" + document.getDisplayName() + "' added successfully!";
                        
                        Toast.makeText(SubjectDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                        
                        // Update Ask Atom button to show selected document
                        updateAskAtomButton();
                        
                        // NO AUTO-GENERATION - User must manually generate quizzes/flashcards
                        // This gives users control over when to generate content
                            
                    } catch (Exception e) {
                        Log.e("SubjectDetailActivity", "Error processing PDF success", e);
                        Toast.makeText(SubjectDetailActivity.this, 
                            "PDF added but error updating UI: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onChunkingComplete(int chunkCount) {
                runOnUiThread(() -> {
                    if (chunkCount > 1) {
                        Log.i("SubjectDetailActivity", "Document processed into " + chunkCount + " sections");
                    }
                });
            }
        });
    }
    
    /**
     * Generate quiz questions from document content using AI
     */
    private void generateQuizzesFromDocument(Document document) {
        // Check if activity is still valid before starting
        if (isFinishing() || isDestroyed()) {
            Log.w("SubjectDetailActivity", "Activity finishing, skipping quiz generation");
            return;
        }
        
        // Just show a simple toast - no blocking dialog
        Toast.makeText(this, "Generating quizzes in background...", Toast.LENGTH_SHORT).show();
        
        // Mark quiz generation as in progress
        SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("generating_" + subjectName, true)
            .putLong("gen_start_time_" + subjectName, System.currentTimeMillis())
            .apply();
        
        QuizGenerationService quizService = new QuizGenerationService(this);
        
        quizService.generateQuizzes(subjectName, document.getDisplayName(), 
            document.getTextContent(), new QuizGenerationService.QuizGenerationCallback() {
            
            @Override
            public void onProgress(String message) {
                // Silent progress - no UI updates to avoid blocking
                Log.d("SubjectDetailActivity", "Quiz generation progress: " + message);
            }
            
            @Override
            public void onSuccess(int questionCount) {
                // Mark quiz generation as complete
                SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
                prefs.edit()
                    .putBoolean("generating_" + subjectName, false)
                    .remove("gen_start_time_" + subjectName)
                    .apply();
                
                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> {
                        Toast.makeText(SubjectDetailActivity.this, 
                            "✓ Generated " + questionCount + " quiz questions", 
                            Toast.LENGTH_SHORT).show();
                        
                        // Record activity
                        activityManager.addActivity(subjectName, 
                            "Generated " + questionCount + " quizzes from " + document.getDisplayName(), 
                            RecentActivity.ActivityType.QUIZ);
                        loadRecentActivity();
                    });
                    
                    // After quizzes, generate flashcards too
                    generateFlashcardsFromDocument(document);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                // Mark quiz generation as complete (failed)
                SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
                prefs.edit()
                    .putBoolean("generating_" + subjectName, false)
                    .remove("gen_start_time_" + subjectName)
                    .apply();
                
                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> {
                        Toast.makeText(SubjectDetailActivity.this, 
                            "Quiz generation failed, trying flashcards...", 
                            Toast.LENGTH_SHORT).show();
                    });
                    
                    // Still try to generate flashcards even if quiz generation failed
                    generateFlashcardsFromDocument(document);
                }
            }
        });
    }

    /**
     * Generate flashcards from document content using AI
     */
    private void generateFlashcardsFromDocument(Document document) {
        // Check if activity is still valid before starting
        if (isFinishing() || isDestroyed()) {
            Log.w("SubjectDetailActivity", "Activity finishing, skipping flashcard generation");
            return;
        }
        
        FlashcardGenerationService flashcardService = new FlashcardGenerationService(this);
        
        flashcardService.generateFlashcards(subjectName, document.getDisplayName(), 
            document.getTextContent(), new FlashcardGenerationService.FlashcardGenerationCallback() {
            
            @Override
            public void onProgress(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(SubjectDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onSuccess(int flashcardCount) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Generated " + flashcardCount + " flashcards from " + document.getDisplayName(), 
                        Toast.LENGTH_LONG).show();
                    
                    // Record activity
                    activityManager.addActivity(subjectName, 
                        "Generated " + flashcardCount + " flashcards from " + document.getDisplayName(), 
                        RecentActivity.ActivityType.FLASHCARDS);
                    loadRecentActivity();
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Flashcard generation failed: " + errorMessage, 
                        Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Delete the currently selected document and its associated quizzes
     */
    private void deleteSelectedDocument() {
        if (selectedPdf == null) {
            Toast.makeText(this, "Please select a document to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        final Document docToDelete = selectedPdf;
        
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Document")
            .setMessage("Delete '" + docToDelete.getDisplayName() + "' and all associated quizzes and flashcards?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Clear topics from Document object before deleting
                docToDelete.setContextTopics(new ArrayList<>());
                docToDelete.setContextSummary("");
                docToDelete.setContextGeneratedAt(0);
                documentManager.updateDocument(docToDelete);
                
                // Delete from DocumentManager
                documentManager.deleteDocument(docToDelete.getId());
                
                // Delete associated quizzes from QuizBank
                QuizBank quizBank = QuizBank.getInstance();
                quizBank.init(this);
                boolean quizzesDeleted = quizBank.deleteQuizzesForDocument(subjectName, docToDelete.getDisplayName());
                
                // Delete associated flashcards from FlashcardBank
                FlashcardBank flashcardBank = FlashcardBank.getInstance();
                flashcardBank.init(this);
                boolean flashcardsDeleted = flashcardBank.deleteFlashcardsForDocument(subjectName, docToDelete.getDisplayName());
                
                // Delete context window chunks
                try {
                    if (contextWindowManager != null) {
                        contextWindowManager.deleteDocumentChunks(docToDelete.getId());
                        contextWindowManager.deleteDocumentChunks(docToDelete.getDisplayName());
                    }
                } catch (Exception e) {
                    android.util.Log.e("SubjectDetailActivity", "Error deleting context chunks: " + e.getMessage());
                }
                
                // Remove from local list
                subjectPdfs.remove(docToDelete);
                if (selectedPdf != null && selectedPdf.getId().equals(docToDelete.getId())) {
                    selectedPdf = null;
                }
                
                // Delete topics from SubjectTopicsManager (by both ID and name for safety)
                if (topicsManager != null) {
                    topicsManager.deleteTopicsForDocument(subjectName, docToDelete.getId());
                    // Also try deleting by document name in case ID doesn't match
                    topicsManager.deleteTopicsForDocument(subjectName, docToDelete.getDisplayName());
                }
                
                // Update UI safely
                forceRefreshPdfList();
                refreshContextLibrary();
                updateTopicsOverview();
                
                String message = "Document deleted";
                if (quizzesDeleted || flashcardsDeleted) {
                    message += " along with associated content";
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                
                // Record activity
                activityManager.addActivity(subjectName, 
                    "Deleted document: " + docToDelete.getDisplayName(), 
                    RecentActivity.ActivityType.VIEW);
                loadRecentActivity();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Update Ask Atom subtitle text based on selected PDF
     */
    private void updateAskAtomButton() {
        TextView askAtomSubtitle = findViewById(R.id.ask_atom_subtitle);
        if (askAtomSubtitle != null) {
            if (selectedPdf != null) {
                askAtomSubtitle.setText("Ask about " + selectedPdf.getDisplayName());
            } else {
                askAtomSubtitle.setText("Get instant help from AI");
            }
        }
    }
    
    /**
     * Force refresh the PDF list UI with proper state checking
     */
    private void forceRefreshPdfList() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (!isActivityInForeground) {
                // onResume will refresh once the user returns
                return;
            }
            updateDocumentDropdown();
        });
    }

    /**
     * Update the document dropdown with available PDFs
     */
    private void updateDocumentDropdown() {
        runOnUiThread(() -> {
            try {
                if (pdfListContainer == null || noPdfsMessage == null) {
                    Log.w("SubjectDetailActivity", "PDF list views not initialized yet");
                    return;
                }

                Log.d("SubjectDetailActivity", "Updating PDF list. Count: " + (subjectPdfs != null ? subjectPdfs.size() : "null"));

                pdfListContainer.removeAllViews();
                pdfListContainer.invalidate();

                if (subjectPdfs == null || subjectPdfs.isEmpty()) {
                    noPdfsMessage.setVisibility(View.VISIBLE);
                    pdfListContainer.addView(noPdfsMessage);
                    selectedPdf = null;
                } else {
                    noPdfsMessage.setVisibility(View.GONE);

                    final int initialShowCount = 2;
                    int toShow = Math.min(initialShowCount, subjectPdfs.size());

                    for (int i = 0; i < toShow; i++) {
                        Document doc = subjectPdfs.get(i);
                        addPdfItemView(pdfListContainer, doc);
                    }

                    if (subjectPdfs.size() > initialShowCount) {
                        LinearLayout showMoreButton = new LinearLayout(SubjectDetailActivity.this);
                        showMoreButton.setOrientation(LinearLayout.HORIZONTAL);
                        showMoreButton.setGravity(android.view.Gravity.CENTER);
                        showMoreButton.setPadding(12, 12, 12, 12);
                        showMoreButton.setBackground(getDrawable(android.R.drawable.list_selector_background));
                        showMoreButton.setClickable(true);
                        showMoreButton.setFocusable(true);

                        TextView showMoreText = new TextView(SubjectDetailActivity.this);
                        showMoreText.setText("▼ Show " + (subjectPdfs.size() - initialShowCount) + " more");
                        showMoreText.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_primary50));
                        showMoreText.setTextSize(14);
                        showMoreText.setTypeface(null, android.graphics.Typeface.BOLD);
                        showMoreButton.addView(showMoreText);

                        LinearLayout showLessButton = new LinearLayout(SubjectDetailActivity.this);
                        showLessButton.setOrientation(LinearLayout.HORIZONTAL);
                        showLessButton.setGravity(android.view.Gravity.CENTER);
                        showLessButton.setPadding(12, 12, 12, 12);
                        showLessButton.setBackground(getDrawable(android.R.drawable.list_selector_background));
                        showLessButton.setClickable(true);
                        showLessButton.setFocusable(true);
                        showLessButton.setVisibility(View.GONE);

                        TextView showLessText = new TextView(SubjectDetailActivity.this);
                        showLessText.setText("▲ Show less");
                        showLessText.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_primary50));
                        showLessText.setTextSize(14);
                        showLessText.setTypeface(null, android.graphics.Typeface.BOLD);
                        showLessButton.addView(showLessText);

                        pdfListContainer.addView(showMoreButton);

                        showMoreButton.setOnClickListener(v -> {
                            showMoreButton.setVisibility(View.GONE);
                            for (int i = initialShowCount; i < subjectPdfs.size(); i++) {
                                Document doc = subjectPdfs.get(i);
                                addPdfItemView(pdfListContainer, doc);
                            }
                            pdfListContainer.addView(showLessButton);
                            showLessButton.setVisibility(View.VISIBLE);
                        });

                        showLessButton.setOnClickListener(v -> {
                            int childCount = pdfListContainer.getChildCount();
                            for (int i = childCount - 1; i >= initialShowCount + 1; i--) {
                                pdfListContainer.removeViewAt(i);
                            }
                            showMoreButton.setVisibility(View.VISIBLE);
                        });
                    }

                    if (selectedPdf == null && !subjectPdfs.isEmpty()) {
                        selectedPdf = subjectPdfs.get(0);
                    }
                }

                updateAskAtomButton();
                refreshContextLibrary();
            } catch (Exception e) {
                Log.e("SubjectDetailActivity", "Error updating PDF list", e);
            }
        });
    }

    private void refreshContextLibrary() {
        runOnUiThread(() -> {
            if (contextListContainer == null || subjectPdfs == null) {
                Log.w("SubjectDetailActivity", "Context library views not initialized");
                return;
            }

            try {
                contextListContainer.removeAllViews();
                contextEntryViews.clear();

                if (subjectPdfs.isEmpty()) {
                    if (noContextMessage != null) {
                        noContextMessage.setVisibility(View.VISIBLE);
                        contextListContainer.addView(noContextMessage);
                    }
                    updateTopicsOverview();
                    return;
                }

                if (noContextMessage != null) {
                    noContextMessage.setVisibility(View.GONE);
                }

                // Create context entries for each document
                for (Document doc : subjectPdfs) {
                    try {
                        View entryView = getLayoutInflater().inflate(R.layout.item_context_entry, contextListContainer, false);
                        if (entryView != null) {
                            contextListContainer.addView(entryView);
                            contextEntryViews.put(doc.getId(), entryView);
                            bindContextEntryView(entryView, doc);
                        }
                    } catch (Exception e) {
                        Log.e("SubjectDetailActivity", "Error creating context entry for " + doc.getDisplayName(), e);
                    }
                }

                updateTopicsOverview();
                updateAllTopicsSection();
                updateWeakTopicsSection();
            } catch (Exception e) {
                Log.e("SubjectDetailActivity", "Error refreshing context library", e);
            }
        });
    }
    
    /**
     * Update weak topics section with topics needing revision
     */
    private void updateWeakTopicsSection() {
        runOnUiThread(() -> {
            if (weakTopicsSection == null || weakTopicsContainer == null || weakTopicManager == null) {
                return;
            }
            
            try {
                weakTopicsContainer.removeAllViews();
                
                // Get weak topics for this subject
                List<WeakTopicManager.WeakTopicEntry> weakTopics = weakTopicManager.getWeakTopicsForSubject(subjectName);
                
                // Filter to only show topics needing revision
                List<WeakTopicManager.WeakTopicEntry> needsRevision = new ArrayList<>();
                for (WeakTopicManager.WeakTopicEntry entry : weakTopics) {
                    if (entry.needsRevision) {
                        needsRevision.add(entry);
                    }
                }
                
                if (needsRevision.isEmpty()) {
                    weakTopicsSection.setVisibility(View.GONE);
                    if (noWeakTopicsMessage != null) {
                        noWeakTopicsMessage.setVisibility(View.GONE);
                    }
                } else {
                    weakTopicsSection.setVisibility(View.VISIBLE);
                    if (noWeakTopicsMessage != null) {
                        noWeakTopicsMessage.setVisibility(View.GONE);
                    }
                    
                    // Update count
                    if (weakTopicsCount != null) {
                        weakTopicsCount.setText(String.valueOf(needsRevision.size()));
                    }
                    
                    // Clear and display weak topics as simple list
                    weakTopicsContainer.removeAllViews();
                    for (WeakTopicManager.WeakTopicEntry entry : needsRevision) {
                        View topicView = createSimpleWeakTopicView(entry);
                        weakTopicsContainer.addView(topicView);
                    }
                }
                
                // Setup retest button
                Button retestButton = findViewById(R.id.retest_weak_topics_button);
                if (retestButton != null) {
                    retestButton.setOnClickListener(v -> {
                        if (needsRevision.isEmpty()) {
                            Toast.makeText(this, "No weak topics to retest", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        launchRetestForWeakTopics(needsRevision);
                    });
                }
                
                // Setup delete all button
                Button deleteAllButton = findViewById(R.id.delete_all_weak_topics_button);
                if (deleteAllButton != null) {
                    deleteAllButton.setOnClickListener(v -> {
                        if (needsRevision.isEmpty()) {
                            Toast.makeText(this, "No weak topics to delete", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showDeleteAllConfirmation(needsRevision);
                    });
                }
                
            } catch (Exception e) {
                Log.e("SubjectDetailActivity", "Error updating weak topics section", e);
            }
        });
    }
    
    /**
     * Create a simple view for a weak topic entry (plain text list with delete button)
     */
    private View createSimpleWeakTopicView(WeakTopicManager.WeakTopicEntry entry) {
        // Create horizontal layout for topic and delete button
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(16, 12, 16, 12);
        rowLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Create TextView for topic name
        TextView topicView = new TextView(this);
        topicView.setText(entry.topic);
        topicView.setTextSize(14);
        topicView.setTextColor(getResources().getColor(android.R.color.black, getTheme()));
        topicView.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topicView.setClickable(true);
        topicView.setFocusable(true);
        topicView.setBackgroundResource(android.R.drawable.list_selector_background);
        
        // Add click listener to open chat
        topicView.setOnClickListener(v -> {
            launchChatForWeakTopic(entry);
        });
        
        // Create delete icon button (small)
        ImageButton deleteButton = new ImageButton(this);
        deleteButton.setImageResource(R.drawable.ic_close);
        deleteButton.setBackgroundResource(android.R.drawable.btn_default);
        deleteButton.setPadding(8, 8, 8, 8);
        deleteButton.setColorFilter(getResources().getColor(R.color.error_color, getTheme()));
        deleteButton.setLayoutParams(new LinearLayout.LayoutParams(
            36, 36));
        deleteButton.setContentDescription("Delete topic");
        deleteButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        
        // Add click listener to delete
        deleteButton.setOnClickListener(v -> {
            showDeleteConfirmation(entry);
        });
        
        // Add views to row
        rowLayout.addView(topicView);
        rowLayout.addView(deleteButton);
        
        // Add divider
        View divider = new View(this);
        ViewGroup.LayoutParams dividerParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        
        // Create container
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(rowLayout);
        container.addView(divider);
        
        return container;
    }
    
    /**
     * Show confirmation dialog before deleting a single weak topic
     */
    private void showDeleteConfirmation(WeakTopicManager.WeakTopicEntry entry) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Delete Weak Topic")
            .setMessage("Are you sure you want to remove \"" + entry.topic + "\" from weak topics?")
            .setPositiveButton("Delete", (dialog, which) -> {
                weakTopicManager.removeWeakTopic(entry.topic, entry.documentId, entry.subjectName);
                Toast.makeText(this, "Topic removed", Toast.LENGTH_SHORT).show();
                updateWeakTopicsSection();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Show confirmation dialog before deleting all weak topics
     */
    private void showDeleteAllConfirmation(List<WeakTopicManager.WeakTopicEntry> topics) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Delete All Weak Topics")
            .setMessage("Are you sure you want to remove all " + topics.size() + " weak topics? This cannot be undone.")
            .setPositiveButton("Delete All", (dialog, which) -> {
                for (WeakTopicManager.WeakTopicEntry entry : topics) {
                    weakTopicManager.removeWeakTopic(entry.topic, entry.documentId, entry.subjectName);
                }
                Toast.makeText(this, "All weak topics removed", Toast.LENGTH_SHORT).show();
                updateWeakTopicsSection();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Launch chat with weak topic for focused revision
     */
    private void launchChatForWeakTopic(WeakTopicManager.WeakTopicEntry entry) {
        try {
            // Mark as reviewed
            weakTopicManager.markTopicAsReviewed(entry.topic, entry.documentId, entry.subjectName);
            
            Intent intent = new Intent(this, Conversation.class);
            intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, htpConfigPath);
            intent.putExtra(Conversation.cConversationActivityKeyModelName, modelName);
            intent.putExtra("subject_context", entry.subjectName);
            intent.putExtra("weak_topic", entry.topic);
            intent.putExtra("pdf_context", entry.documentName);
            // Add initial message about the topic
            intent.putExtra("initial_message", "What do you want to know about this topic: " + entry.topic + "?");
            
            startActivity(intent);
            
            // Update UI
            updateWeakTopicsSection();
        } catch (Exception e) {
            Log.e("SubjectDetailActivity", "Error launching chat for weak topic", e);
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Launch retest quiz for weak topics
     */
    private void launchRetestForWeakTopics(List<WeakTopicManager.WeakTopicEntry> weakTopics) {
        if (weakTopics == null || weakTopics.isEmpty()) {
            Toast.makeText(this, "No weak topics to retest", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Collect document IDs for weak topics
        Set<String> documentIds = new HashSet<>();
        for (WeakTopicManager.WeakTopicEntry entry : weakTopics) {
            if (entry.documentId != null) {
                documentIds.add(entry.documentId);
            }
        }
        
        if (documentIds.isEmpty()) {
            Toast.makeText(this, "No documents found for weak topics", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Find documents
        List<Document> documents = new ArrayList<>();
        for (String docId : documentIds) {
            Document doc = documentManager.getDocument(docId);
            if (doc != null) {
                documents.add(doc);
            }
        }
        
        if (documents.isEmpty()) {
            Toast.makeText(this, "Documents not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Launch quiz generation for these documents with focus on weak topics
        Toast.makeText(this, "Generating retest quiz for " + weakTopics.size() + " weak topic(s)...", 
                      Toast.LENGTH_LONG).show();
        
        // Use the existing quiz generation but mark it as a retest
        Intent intent = new Intent(this, QuizActivity.class);
        intent.putExtra("subject", subjectName);
        intent.putExtra("is_retest", true);
        intent.putExtra("weak_topics_count", weakTopics.size());
        
        // If single document, use it
        if (documents.size() == 1) {
            intent.putExtra("document_title", documents.get(0).getDisplayName());
        }
        
        startActivity(intent);
    }

    private void updateAllTopicsSection() {
        runOnUiThread(() -> {
            if (allTopicsSection == null || allTopicsContainer == null || subjectPdfs == null) {
                return;
            }

            allTopicsContainer.removeAllViews();

            // Count documents that have topics
            List<Document> docsWithTopics = new ArrayList<>();
            for (Document doc : subjectPdfs) {
                if (doc.getContextTopics() != null && !doc.getContextTopics().isEmpty()) {
                    docsWithTopics.add(doc);
                }
            }

            if (docsWithTopics.isEmpty()) {
                allTopicsSection.setVisibility(View.GONE);
            } else {
                allTopicsSection.setVisibility(View.VISIBLE);
                
                for (Document doc : docsWithTopics) {
                    View topicItem = getLayoutInflater().inflate(R.layout.item_topic_list, allTopicsContainer, false);
                    
                    TextView lectureName = topicItem.findViewById(R.id.topic_lecture_name);
                    TextView topicCount = topicItem.findViewById(R.id.topic_count);
                    
                    // Extract lecture number from document name (e.g., "DSM_L01.pdf" -> "L1")
                    String displayName = extractLectureNumber(doc.getDisplayName());
                    lectureName.setText(displayName);
                    
                    int count = doc.getContextTopics().size();
                    topicCount.setText(count + " topic" + (count == 1 ? "" : "s"));
                    
                    topicItem.setOnClickListener(v -> showTopicsDialog(doc));
                    allTopicsContainer.addView(topicItem);
                }
            }
        });
    }

    private String extractLectureNumber(String fileName) {
        // Extract lecture number from filenames like "DSM_L01.pdf", "AT_L04.pdf", etc.
        Pattern pattern = Pattern.compile(".*_?L(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            String number = matcher.group(1);
            // Remove leading zeros
            int lectureNum = Integer.parseInt(number);
            return "L" + lectureNum;
        }
        
        // Fallback: use first 3 characters of filename
        return fileName.length() > 3 ? fileName.substring(0, 3) : fileName;
    }

    private void showTopicsDialog(Document doc) {
        if (doc.getContextTopics() == null || doc.getContextTopics().isEmpty()) {
            return;
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 32, 48, 32);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(doc.getDisplayName() + " Topics");
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(getResources().getColor(android.R.color.black, getTheme()));
        container.addView(titleView);

        // Add some spacing
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32));
        container.addView(spacer);

        // Add topics as chips
        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setChipSpacingHorizontal(16);
        chipGroup.setChipSpacingVertical(12);
        
        for (String topic : doc.getContextTopics()) {
            if (topic == null || topic.trim().isEmpty()) continue;
            
            Chip chip = new Chip(this);
            chip.setText(topic.trim());
            chip.setChipBackgroundColorResource(R.color.primary_blue);
            chip.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
            chip.setCheckable(false);
            chip.setClickable(false);
            chipGroup.addView(chip);
        }
        
        container.addView(chipGroup);
        scrollView.addView(container);

        new android.app.AlertDialog.Builder(this)
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showDocumentDetails(Document doc) {
        boolean hasContext = doc.getContextSummary() != null && !doc.getContextSummary().isEmpty();
        boolean hasTopics = doc.getContextTopics() != null && !doc.getContextTopics().isEmpty();
        
        if (!hasContext && !hasTopics) {
            Toast.makeText(this, "No context generated. Tap the refresh icon to generate.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_document_details, null);
        
        // Initialize views
        TextView titleView = dialogView.findViewById(R.id.dialog_doc_title);
        TextView summaryText = dialogView.findViewById(R.id.dialog_summary_text);
        TextView topicsHeader = dialogView.findViewById(R.id.dialog_topics_header);
        ChipGroup topicsGroup = dialogView.findViewById(R.id.dialog_topics_chip_group);
        View summarySection = dialogView.findViewById(R.id.dialog_summary_section);
        View topicsSection = dialogView.findViewById(R.id.dialog_topics_section);
        MaterialButton closeButton = dialogView.findViewById(R.id.dialog_close_button);
        MaterialButton regenerateButton = dialogView.findViewById(R.id.dialog_regenerate_button);

        // Set content
        titleView.setText(doc.getDisplayName());

        if (hasContext) {
            summarySection.setVisibility(View.VISIBLE);
            summaryText.setText(cleanDisplaySummary(doc.getContextSummary()));
        } else {
            summarySection.setVisibility(View.GONE);
        }

        if (hasTopics) {
            topicsSection.setVisibility(View.VISIBLE);
            topicsHeader.setText("Key Topics (" + doc.getContextTopics().size() + ")");
            
            topicsGroup.removeAllViews();
            for (String topic : doc.getContextTopics()) {
                if (topic == null || topic.trim().isEmpty()) continue;
                
                Chip chip = new Chip(this);
                chip.setText(topic.trim());
                chip.setChipBackgroundColorResource(R.color.primary_blue);
                chip.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                chip.setCheckable(false);
                chip.setClickable(false);
                topicsGroup.addView(chip);
            }
        } else {
            topicsSection.setVisibility(View.GONE);
        }

        // Create dialog
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Set button listeners
        closeButton.setOnClickListener(v -> dialog.dismiss());
        regenerateButton.setOnClickListener(v -> {
            dialog.dismiss();
            forceRegenerateContext(doc);
        });

        dialog.show();
        
        // Set dialog window size
        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            int height = (int) (dm.heightPixels * 0.85); // 85% of screen height
            int width = (int) (dm.widthPixels * 0.95);   // 95% of screen width
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
    
    /**
     * Clean summary text for display by removing remaining administrative content
     */
    private String cleanDisplaySummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return "No summary available";
        }
        
        // Remove common administrative patterns that may have slipped through
        String cleaned = summary;
        
        // Remove course codes like CS101, ECE 201, etc.
        cleaned = cleaned.replaceAll("(?i)[A-Z]{2,4}\\s*\\d{3,4}[A-Z]?\\s*", "");
        
        // Remove professor/instructor mentions
        cleaned = cleaned.replaceAll("(?i)(professor|prof\\.?|dr\\.?|instructor)\\s+[A-Z][a-z]+\\s*[A-Z]?[a-z]*\\s*", "");
        
        // Remove lecture/week numbers
        cleaned = cleaned.replaceAll("(?i)(lecture|week|chapter)\\s*#?\\d+\\s*", "");
        
        // Remove dates
        cleaned = cleaned.replaceAll("(?i)\\d{1,2}/\\d{1,2}/\\d{2,4}\\s*", "");
        cleaned = cleaned.replaceAll("(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2},?\\s*\\d{0,4}\\s*", "");
        
        // Remove semester info
        cleaned = cleaned.replaceAll("(?i)(spring|fall|summer|winter)\\s*(semester)?\\s*\\d{0,4}\\s*", "");
        
        // Clean up formatting
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[,\\.:\\s]+", "");
        cleaned = cleaned.replaceAll("\\.\\s*\\.", ".");
        
        return cleaned.isEmpty() ? "No summary available" : cleaned;
    }
    
    /**
     * Force regenerate context for a document
     */
    private void forceRegenerateContext(Document doc) {
        if (contextGenerationService == null) {
            Toast.makeText(this, "Context service not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (contextsInProgress.contains(doc.getId())) {
            Toast.makeText(this, "Already generating context", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Regenerating context for " + doc.getDisplayName(), Toast.LENGTH_SHORT).show();
        
        contextsInProgress.add(doc.getId());
        View entryView = contextEntryViews.get(doc.getId());
        if (entryView != null) {
            ProgressBar progressBar = entryView.findViewById(R.id.context_progress);
            MaterialButton actionButton = entryView.findViewById(R.id.context_generate_button);
            TextView status = entryView.findViewById(R.id.context_doc_status);
            progressBar.setVisibility(View.VISIBLE);
            actionButton.setEnabled(false);
            actionButton.setText("Generating...");
            status.setText("Regenerating...");
        }
        
        // Force regenerate with true
        contextGenerationService.generateContext(subjectName, doc, new ContextGenerationService.ContextGenerationCallback() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> {
                    if (entryView != null) {
                        TextView status = entryView.findViewById(R.id.context_doc_status);
                        status.setText(message);
                    }
                });
            }

            @Override
            public void onSuccess(ContextGenerationService.ContextResult result) {
                contextsInProgress.remove(doc.getId());
                doc.setContextSummary(result.getSummary());
                List<String> resultTopics = result.getTopics() != null
                        ? new ArrayList<>(result.getTopics())
                        : new ArrayList<>();
                doc.setContextTopics(resultTopics);
                doc.setContextGeneratedAt(System.currentTimeMillis());
                documentManager.updateDocument(doc);
                if (topicsManager != null) {
                    topicsManager.replaceTopicsForDocument(subjectName, doc, result.getTopics());
                }
                runOnUiThread(() -> {
                    if (entryView != null) {
                        bindContextEntryView(entryView, doc);
                    }
                    updateTopicsOverview();
                    Toast.makeText(SubjectDetailActivity.this,
                            "Context regenerated for " + doc.getDisplayName(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                contextsInProgress.remove(doc.getId());
                runOnUiThread(() -> {
                    if (entryView != null) {
                        ProgressBar progressBar = entryView.findViewById(R.id.context_progress);
                        MaterialButton actionButton = entryView.findViewById(R.id.context_generate_button);
                        TextView status = entryView.findViewById(R.id.context_doc_status);
                        progressBar.setVisibility(View.GONE);
                        actionButton.setEnabled(true);
                        actionButton.setText("Generate Context");
                        status.setText("Failed");
                    }
                    Toast.makeText(SubjectDetailActivity.this, 
                            errorMessage != null ? errorMessage : "Failed to regenerate", 
                            Toast.LENGTH_SHORT).show();
                });
            }
        }, true);  // Force regenerate
    }

    private void updateTopicsOverview() {
        // Summary card functionality removed as requested
    }

    private void bindContextEntryView(View entryView, Document doc) {
        TextView title = entryView.findViewById(R.id.context_doc_title);
        TextView statusDot = entryView.findViewById(R.id.context_doc_status);
        MaterialButton actionButton = entryView.findViewById(R.id.context_generate_button);
        ProgressBar progressBar = entryView.findViewById(R.id.context_progress);
        
        // Set the document title
        String displayName = !TextUtils.isEmpty(doc.getDisplayName()) 
            ? doc.getDisplayName() 
            : "Unknown Document";
        title.setText(displayName);
        
        // Set status and button based on context summary state
        boolean hasContext = !TextUtils.isEmpty(doc.getContextSummary());
        boolean inProgress = contextsInProgress.contains(doc.getId());
        
        if (inProgress) {
            statusDot.setText("●");
            statusDot.setTextColor(getColor(R.color.primary_blue));
            actionButton.setIconResource(R.drawable.ic_recent);
            progressBar.setVisibility(View.VISIBLE);
            actionButton.setEnabled(false);
            // No click listener while generating
            actionButton.setOnClickListener(null);
        } else if (hasContext) {
            statusDot.setText("●");
            statusDot.setTextColor(getColor(R.color.primary_blue));
            // Use arrow icon to indicate "View"
            actionButton.setIconResource(R.drawable.ic_arrow_forward);
            progressBar.setVisibility(View.GONE);
            actionButton.setEnabled(true);
            // Click opens details
            actionButton.setOnClickListener(v -> showDocumentDetails(doc));
        } else {
            statusDot.setText("●");
            statusDot.setTextColor(getColor(android.R.color.darker_gray));
            actionButton.setIconResource(R.drawable.ic_recent);
            progressBar.setVisibility(View.GONE);
            actionButton.setEnabled(true);
            // Click starts generation
            actionButton.setOnClickListener(v -> startContextGeneration(doc));
        }
        
        // Title always opens details (which might be empty/prompt to generate)
        title.setOnClickListener(v -> showDocumentDetails(doc));
    }





    private void populateTopicChips(ChipGroup group, List<String> topics) {
        if (group == null) {
            return;
        }
        group.removeAllViews();
        if (topics == null || topics.isEmpty()) {
            group.setVisibility(View.GONE);
            return;
        }
        group.setVisibility(View.VISIBLE);
        for (String topic : topics) {
            if (topic == null || topic.trim().isEmpty()) {
                continue;
            }
            Chip chip = new Chip(this);
            chip.setText(topic.trim());
            chip.setCheckable(false);
            chip.setClickable(false);
            group.addView(chip);
        }
    }

    private String buildContextStatusText(Document doc) {
        long timestamp = doc.getContextGeneratedAt();
        if (timestamp <= 0) {
            return "Context ready";
        }
        Date date = new Date(timestamp);
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        return "Updated " + formatter.format(date);
    }

    private void startContextGeneration(Document document) {
        if (contextGenerationService == null) {
            Toast.makeText(this, "Context service not ready. Try reopening the subject.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (document == null) {
            Log.w("SubjectDetailActivity", "Cannot generate context - document is null");
            return;
        }
        
        // Check if document has content
        if (document.getTextContent() == null || document.getTextContent().trim().isEmpty()) {
            Toast.makeText(this, "Document has no text content. Please upload a readable PDF.", Toast.LENGTH_LONG).show();
            return;
        }

        if (contextsInProgress.contains(document.getId())) {
            Log.w("SubjectDetailActivity", "Ignoring duplicate context request for " + document.getDisplayName());
            Toast.makeText(this, "Already generating context for " + document.getDisplayName(), Toast.LENGTH_SHORT).show();
            return;
        }

        contextsInProgress.add(document.getId());
        View entryView = contextEntryViews.get(document.getId());
        if (entryView != null) {
            ProgressBar progressBar = entryView.findViewById(R.id.context_progress);
            MaterialButton actionButton = entryView.findViewById(R.id.context_generate_button);
            TextView status = entryView.findViewById(R.id.context_doc_status);
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (actionButton != null) {
                actionButton.setEnabled(false);
                actionButton.setText("Generating...");
            }
            if (status != null) status.setText("Generating context...");
        }

        // Use existing context if available, otherwise generate new
        // Pass false to use cached context if it exists
        contextGenerationService.generateContext(subjectName, document, 
            new ContextGenerationService.ContextGenerationCallback() {
            @Override
            public void onProgress(String message) {
                if (entryView == null || message == null) {
                    return;
                }
                runOnUiThread(() -> {
                    TextView status = entryView.findViewById(R.id.context_doc_status);
                    status.setText(message);
                });
            }

            @Override
            public void onSuccess(ContextGenerationService.ContextResult result) {
                contextsInProgress.remove(document.getId());
                document.setContextSummary(result.getSummary());
                List<String> resultTopics = result.getTopics() != null
                        ? new ArrayList<>(result.getTopics())
                        : new ArrayList<>();
                document.setContextTopics(resultTopics);
                document.setContextGeneratedAt(System.currentTimeMillis());
                documentManager.updateDocument(document);
                if (topicsManager != null) {
                    topicsManager.replaceTopicsForDocument(subjectName, document, result.getTopics());
                }
                runOnUiThread(() -> {
                    if (entryView != null) {
                        bindContextEntryView(entryView, document);
                    }
                    updateTopicsOverview();
                    if (isActivityInForeground && !isFinishing() && !isDestroyed()) {
                        Toast.makeText(SubjectDetailActivity.this,
                                "Context ready for " + document.getDisplayName(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d("SubjectDetailActivity", "Context ready toast suppressed because activity not in foreground");
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                contextsInProgress.remove(document.getId());
                runOnUiThread(() -> {
                    if (entryView != null) {
                        ProgressBar progressBar = entryView.findViewById(R.id.context_progress);
                        MaterialButton actionButton = entryView.findViewById(R.id.context_generate_button);
                        TextView status = entryView.findViewById(R.id.context_doc_status);
                        progressBar.setVisibility(View.GONE);
                        actionButton.setEnabled(true);
                        actionButton.setText("Generate Context");
                        status.setText(errorMessage != null ? errorMessage : "Unable to generate context.");
                    }
                    if (errorMessage != null && isActivityInForeground && !isFinishing() && !isDestroyed()) {
                        Toast.makeText(SubjectDetailActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, false);  // Don't force regenerate - use existing context if available
        
        Log.d("SubjectDetailActivity", "Started context generation for: " + document.getDisplayName());
    }

    /**
     * Helper method to add a PDF item view to a container
     */
    private void addPdfItemView(LinearLayout container, Document doc) {
        View pdfItemView = getLayoutInflater().inflate(R.layout.item_pdf_document, container, false);
        
        TextView pdfName = pdfItemView.findViewById(R.id.pdf_name);
        TextView pdfSize = pdfItemView.findViewById(R.id.pdf_size);
        ImageButton deleteButton = pdfItemView.findViewById(R.id.pdf_delete_icon);
        
        pdfName.setText(doc.getDisplayName());
        pdfSize.setText(formatFileSize(doc.getFileSize()));
        
        // Set click listener for the item (select for Chat)
        pdfItemView.setOnClickListener(v -> {
            selectedPdf = doc;
            Toast.makeText(this, "Selected: " + doc.getDisplayName(), Toast.LENGTH_SHORT).show();
            updateAskAtomButton();
        });
        
        // Set delete button listener
        deleteButton.setOnClickListener(v -> {
            deleteDocument(doc);
        });
        
        container.addView(pdfItemView);
    }
    
    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Delete a specific document
     */
    private void deleteDocument(Document doc) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Delete Document")
            .setMessage("Delete '" + doc.getDisplayName() + "' and all associated content?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Clear topics from Document object before deleting
                doc.setContextTopics(new ArrayList<>());
                doc.setContextSummary("");
                doc.setContextGeneratedAt(0);
                documentManager.updateDocument(doc);
                
                // Delete from DocumentManager
                documentManager.deleteDocument(doc.getId());
                
                // Delete associated content
                QuizBank.getInstance().deleteQuizzesForDocument(subjectName, doc.getDisplayName());
                FlashcardBank.getInstance().deleteFlashcardsForDocument(subjectName, doc.getDisplayName());
                
                // Delete topics from SubjectTopicsManager (by both ID and name for safety)
                if (topicsManager != null) {
                    topicsManager.deleteTopicsForDocument(subjectName, doc.getId());
                    // Also try deleting by document name in case ID doesn't match
                    topicsManager.deleteTopicsForDocument(subjectName, doc.getDisplayName());
                }
                
                // Delete context chunks
                if (contextWindowManager != null) {
                    contextWindowManager.deleteDocumentChunks(doc.getId());
                    contextWindowManager.deleteDocumentChunks(doc.getDisplayName());
                }
                
                // Clear from context entry views
                contextEntryViews.remove(doc.getId());
                
                // Remove from local list
                subjectPdfs.remove(doc);
                if (selectedPdf != null && selectedPdf.getId().equals(doc.getId())) {
                    selectedPdf = null;
                }
                
                // Reload PDF list from storage to ensure consistency (without UI update)
                loadSubjectPdfs(false);
                
                // Force immediate UI refresh
                forceRefreshPdfList();
                refreshContextLibrary();
                updateTopicsOverview();
                updateAllTopicsSection();
                
                Toast.makeText(this, "Document deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Update activity summary text
     */
    private void updateActivitySummary() {
        TextView activitySummary = findViewById(R.id.activity_summary);
        
        List<RecentActivity> activities = activityManager.getActivitiesForSubject(subjectName);
        int activityCount = activities.size();
        
        if (activityCount == 0) {
            activitySummary.setText("No activity yet");
        } else if (activityCount == 1) {
            activitySummary.setText("1 activity");
        } else {
            // Since timestamp is a string, let's use a simpler approach
            // Count recent activities (we'll show total for now)
            activitySummary.setText(activityCount + " total activities");
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.chat_with_atom_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show PDF selection dialog if there are PDFs available
                if (subjectPdfs != null && !subjectPdfs.isEmpty()) {
                    showPdfSelectionDialog();
                } else {
                    // No PDFs available, open chat without PDF context
                    launchChatWithPdf(null);
                }
            }
        });

        findViewById(R.id.take_quiz_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if user has uploaded any PDFs
                if (subjectPdfs.isEmpty()) {
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Please upload a PDF document first to generate quiz questions!", 
                        Toast.LENGTH_LONG).show();
                    return;
                }
                
                // Show document selection dialog
                showDocumentSelectionForQuiz();
            }
        });

        findViewById(R.id.study_flashcards_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if user has uploaded any PDFs
                if (subjectPdfs.isEmpty()) {
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Please upload a PDF document first to generate flashcards!", 
                        Toast.LENGTH_LONG).show();
                    return;
                }
                
                // Show document selection dialog
                showDocumentSelectionForFlashcards();
            }
        });

        // PDF Upload button
        findViewById(R.id.pdf_upload_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchPDFPicker();
            }
        });
    }
    
    /**
     * Show dialog to select which PDF to ask about
     */
    private void showPdfSelectionDialog() {
        if (subjectPdfs == null || subjectPdfs.isEmpty()) {
            // No PDFs, launch without context
            launchChatWithPdf(null);
            return;
        }
        
        // Create array of PDF names
        String[] pdfNames = new String[subjectPdfs.size() + 1]; // +1 for "No PDF" option
        pdfNames[0] = "Ask without PDF context";
        for (int i = 0; i < subjectPdfs.size(); i++) {
            pdfNames[i + 1] = subjectPdfs.get(i).getDisplayName();
        }
        
        // Create dialog
        new android.app.AlertDialog.Builder(this)
            .setTitle("Choose PDF to ask about")
            .setItems(pdfNames, (dialog, which) -> {
                if (which == 0) {
                    // User chose "No PDF"
                    launchChatWithPdf(null);
                } else {
                    // User chose a PDF (index - 1 because first item is "No PDF")
                    Document selectedDoc = subjectPdfs.get(which - 1);
                    launchChatWithPdf(selectedDoc);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Launch chat with selected PDF (or without if pdf is null)
     */
    private void launchChatWithPdf(Document pdf) {
        String activityDesc;
        
        if (pdf != null) {
            // Set the selected PDF as context in DocumentManager
            documentManager.setSelectedDocument(pdf.getId());
            activityDesc = "Asked Atom about " + pdf.getDisplayName();
            
            Toast.makeText(SubjectDetailActivity.this, 
                "Loading " + pdf.getDisplayName() + " as context...", 
                Toast.LENGTH_SHORT).show();
        } else {
            // Clear any previous context
            documentManager.clearSelectedDocument();
            activityDesc = "Asked Atom about " + subjectName + " concepts";
        }
        
        // Add activity to recent activities
        activityManager.addActivity(subjectName, activityDesc, RecentActivity.ActivityType.CHAT);
        
        // Track as last global activity
        LastActivityTracker.saveChatActivity(SubjectDetailActivity.this, subjectName, htpConfigPath, modelName);
        
        Intent intent = new Intent(SubjectDetailActivity.this, Conversation.class);
        // Add required parameters for Conversation activity
        intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, htpConfigPath);
        intent.putExtra(Conversation.cConversationActivityKeyModelName, modelName);
        // Add context parameters
        intent.putExtra("subject_context", subjectName);
        if (pdf != null) {
            intent.putExtra("pdf_context", pdf.getDisplayName());
        }
        startActivity(intent);
        
        // Refresh recent activities
        loadRecentActivity();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isActivityInForeground = true;
        // Refresh recent activities when returning to this activity
        loadRecentActivity();
        // Refresh weak topics section
        updateWeakTopicsSection();
        // Refresh PDF list in case anything changed while away
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isActivityInForeground && !isFinishing() && !isDestroyed()) {
                updateDocumentDropdown();
            }
        }, 200);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityInForeground = false;
        // Clear any pending callbacks to prevent memory leaks
        // This helps prevent operations from continuing after activity is destroyed
        Log.d("SubjectDetailActivity", "Activity destroyed, cleaning up resources");
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityInForeground = false;
        // Activity is going into background, log for debugging
        Log.d("SubjectDetailActivity", "Activity paused");
    }    // Public method to add activity from other activities (like quiz completion)
    public static void addActivityForSubject(Context context, String subjectName, String description, 
                                           RecentActivity.ActivityType type, String score) {
        RecentActivityManager manager = new RecentActivityManager(context);
        manager.addActivity(subjectName, description, type, score);
    }
    
    public static void addActivityForSubject(Context context, String subjectName, String description, 
                                           RecentActivity.ActivityType type) {
        addActivityForSubject(context, subjectName, description, type, null);
    }

    private boolean isDefaultSubject(String subjectName) {
        // Check if this is one of the default built-in subjects
        return subjectName.equals("Data Structures and Algorithms") ||
               subjectName.equals("Operating Systems and Networking") ||
               subjectName.equals("Probability and Statistics") ||
               subjectName.equals("Automata Theory") ||
               subjectName.equals("Data and Application");
    }

    private String getSubjectPdfUri(String subjectName) {
        // Get the stored subjects from SharedPreferences to find the PDF URI
        SharedPreferences sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE);
        String subjectsJson = sharedPreferences.getString("subjects_list", null);
        
        // Add debug logging
        android.util.Log.d("SubjectDetail", "Looking for PDF for subject: " + subjectName);
        android.util.Log.d("SubjectDetail", "Subjects JSON: " + subjectsJson);
        
        if (subjectsJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(subjectsJson);
                android.util.Log.d("SubjectDetail", "Found " + jsonArray.length() + " subjects in storage");
                
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject subjectObj = jsonArray.getJSONObject(i);
                    String storedName = subjectObj.getString("name");
                    String pdfUri = subjectObj.optString("pdfUri", null);
                    
                    android.util.Log.d("SubjectDetail", "Subject " + i + ": " + storedName + ", PDF: " + pdfUri);
                    
                    if (subjectName.equals(storedName)) {
                        android.util.Log.d("SubjectDetail", "Found matching subject, returning PDF URI: " + pdfUri);
                        return pdfUri;
                    }
                }
            } catch (JSONException e) {
                android.util.Log.e("SubjectDetail", "Error parsing JSON: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            android.util.Log.d("SubjectDetail", "No subjects JSON found in SharedPreferences");
        }
        android.util.Log.d("SubjectDetail", "No PDF found for subject: " + subjectName);
        return null;
    }

    private void openPdfViewer(String pdfUriString) {
        try {
            Uri pdfUri = Uri.parse(pdfUriString);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Try to open with chooser to give user options
            Intent chooser = Intent.createChooser(intent, "Open PDF with");
            
            // Check if there's an app that can handle PDF viewing
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                // Fallback: try to open as any document
                Intent fallbackIntent = new Intent(Intent.ACTION_VIEW);
                fallbackIntent.setData(pdfUri);
                fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                if (fallbackIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(Intent.createChooser(fallbackIntent, "Open with"));
                } else {
                    Toast.makeText(this, "No PDF viewer app found. Please install a PDF viewer like Adobe Acrobat Reader or Google Drive.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SubjectDetail", "Error opening PDF: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show document selection dialog for quiz generation
     */
    private void showDocumentSelectionForQuiz() {
        DocumentSelectionDialog dialog = new DocumentSelectionDialog(
            this,
            subjectPdfs,
            "Generate Quiz",
            "Generate Quiz",
            selectedDocuments -> {
                // User has selected documents, now generate quiz
                generateQuizFromSelectedDocuments(selectedDocuments);
            }
        );
        dialog.show();
    }
    
    /**
     * Show document selection dialog for flashcard generation
     */
    private void showDocumentSelectionForFlashcards() {
        DocumentSelectionDialog dialog = new DocumentSelectionDialog(
            this,
            subjectPdfs,
            "Generate Flashcards",
            "Generate Flashcards",
            selectedDocuments -> {
                // User has selected documents, now generate flashcards
                generateFlashcardsFromSelectedDocuments(selectedDocuments);
            }
        );
        dialog.show();
    }
    
    /**
     * Generate quiz from selected documents
     */
    private void generateQuizFromSelectedDocuments(List<Document> selectedDocuments) {
        if (selectedDocuments.isEmpty()) {
            Toast.makeText(this, "No documents selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Generating quiz questions from " + selectedDocuments.size() + " document(s)...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Smartly combine content from all selected documents with token budget enforcement
        // Reserve ~500 tokens for prompt template, leaving ~1400 for content
        int maxContentTokens = 1400;
        int CHARS_PER_TOKEN = 4;
        
        StringBuilder combinedContent = new StringBuilder();
        String documentTitles = "";
        int totalTokens = 0;
        
        // First pass: calculate total tokens
        for (Document doc : selectedDocuments) {
            if (doc.getTextContent() != null) {
                totalTokens += TokenBudgetEnforcer.estimateTokens(doc.getTextContent());
            }
        }
        
        Log.d("SubjectDetailActivity", "Total tokens from " + selectedDocuments.size() + " documents: " + totalTokens);
        
        // If total exceeds budget, intelligently sample from each document
        if (totalTokens > maxContentTokens) {
            Log.i("SubjectDetailActivity", "Content exceeds token budget, sampling proportionally from each document");
            
            // Calculate tokens per document (proportional sampling)
            int tokensPerDoc = maxContentTokens / selectedDocuments.size();
            // Reserve some tokens for separators
            int availableTokensPerDoc = (int)(tokensPerDoc * 0.95);
            
            for (int i = 0; i < selectedDocuments.size(); i++) {
                Document doc = selectedDocuments.get(i);
                String content = doc.getTextContent();
                
                if (content != null && !content.trim().isEmpty()) {
                    int docTokens = TokenBudgetEnforcer.estimateTokens(content);
                    
                    if (docTokens > availableTokensPerDoc) {
                        // Truncate this document's content
                        int maxChars = availableTokensPerDoc * CHARS_PER_TOKEN;
                        String truncated = content.substring(0, Math.min(maxChars, content.length()));
                        // Try to cut at sentence boundary
                        int lastPeriod = truncated.lastIndexOf('.');
                        if (lastPeriod > maxChars * 0.8) {
                            truncated = truncated.substring(0, lastPeriod + 1);
                        }
                        combinedContent.append("=== From: ").append(doc.getDisplayName()).append(" ===\n");
                        combinedContent.append(truncated).append("\n\n");
                        Log.d("SubjectDetailActivity", "Truncated " + doc.getDisplayName() + " from " + docTokens + " to ~" + availableTokensPerDoc + " tokens");
                    } else {
                        // Use full content
                        combinedContent.append("=== From: ").append(doc.getDisplayName()).append(" ===\n");
                        combinedContent.append(content).append("\n\n");
                    }
                }
                
                // Build document titles
                if (i == 0) {
                    documentTitles = doc.getDisplayName();
                } else if (i == selectedDocuments.size() - 1) {
                    documentTitles += " and " + doc.getDisplayName();
                } else {
                    documentTitles += ", " + doc.getDisplayName();
                }
            }
        } else {
            // All content fits, combine normally
            for (int i = 0; i < selectedDocuments.size(); i++) {
                Document doc = selectedDocuments.get(i);
                String content = doc.getTextContent();
                
                if (content != null && !content.trim().isEmpty()) {
                    combinedContent.append("=== From: ").append(doc.getDisplayName()).append(" ===\n");
                    combinedContent.append(content).append("\n\n");
                }
                
                if (i == 0) {
                    documentTitles = doc.getDisplayName();
                } else if (i == selectedDocuments.size() - 1) {
                    documentTitles += " and " + doc.getDisplayName();
                } else {
                    documentTitles += ", " + doc.getDisplayName();
                }
            }
        }
        
        // Final safety check: enforce token limit on combined content
        String finalCombinedContent = combinedContent.toString();
        int finalTokens = TokenBudgetEnforcer.estimateTokens(finalCombinedContent);
        if (finalTokens > maxContentTokens) {
            Log.w("SubjectDetailActivity", "Combined content still exceeds budget (" + finalTokens + " tokens), applying final truncation");
            // Use enforceTokenLimit which will truncate properly
            finalCombinedContent = TokenBudgetEnforcer.enforceTokenLimit(finalCombinedContent);
            // Further ensure it's within maxContentTokens
            int finalCheck = TokenBudgetEnforcer.estimateTokens(finalCombinedContent);
            if (finalCheck > maxContentTokens) {
                // Manual truncation as last resort
                int maxChars = maxContentTokens * CHARS_PER_TOKEN;
                if (finalCombinedContent.length() > maxChars) {
                    String truncated = finalCombinedContent.substring(0, maxChars);
                    int lastPeriod = truncated.lastIndexOf('.');
                    if (lastPeriod > maxChars * 0.8) {
                        finalCombinedContent = truncated.substring(0, lastPeriod + 1);
                    } else {
                        finalCombinedContent = truncated + "...";
                    }
                }
            }
        }
        
        final String finalDocumentTitles = documentTitles;
        final String finalContentForQuiz = finalCombinedContent;
        
        Log.d("SubjectDetailActivity", "Final combined content: " + TokenBudgetEnforcer.estimateTokens(finalContentForQuiz) + " tokens");
        
        // Mark quiz generation as in progress
        SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("generating_" + subjectName, true)
            .putLong("gen_start_time_" + subjectName, System.currentTimeMillis())
            .apply();
        
        QuizGenerationService quizService = new QuizGenerationService(this);
        
        quizService.generateQuizzes(subjectName, finalDocumentTitles, 
            finalContentForQuiz, new QuizGenerationService.QuizGenerationCallback() {
            
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.setMessage(message);
                    }
                });
            }
            
            @Override
            public void onSuccess(int questionCount) {
                // Mark quiz generation as complete
                SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
                prefs.edit()
                    .putBoolean("generating_" + subjectName, false)
                    .remove("gen_start_time_" + subjectName)
                    .apply();
                
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    
                    Toast.makeText(SubjectDetailActivity.this, 
                        "✓ Generated " + questionCount + " quiz questions!", 
                        Toast.LENGTH_SHORT).show();
                    
                    // Record activity
                    activityManager.addActivity(subjectName, 
                        "Generated quiz from " + finalDocumentTitles, 
                        RecentActivity.ActivityType.QUIZ);
                    loadRecentActivity();
                    
                    // Track as last global activity
                    LastActivityTracker.saveQuizActivity(SubjectDetailActivity.this, subjectName);
                    
                    // Navigate to quiz activity
                    Intent intent = new Intent(SubjectDetailActivity.this, QuizActivity.class);
                    intent.putExtra("subject", subjectName);
                    intent.putExtra("document_title", finalDocumentTitles);
                    startActivity(intent);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                // Mark quiz generation as complete (failed)
                SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
                prefs.edit()
                    .putBoolean("generating_" + subjectName, false)
                    .remove("gen_start_time_" + subjectName)
                    .apply();
                
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Quiz generation failed: " + errorMessage, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * Generate flashcards from selected documents
     */
    private void generateFlashcardsFromSelectedDocuments(List<Document> selectedDocuments) {
        if (selectedDocuments.isEmpty()) {
            Toast.makeText(this, "No documents selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Generating flashcards from " + selectedDocuments.size() + " document(s)...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Combine content from all selected documents
        StringBuilder combinedContent = new StringBuilder();
        String documentTitles = "";
        
        for (int i = 0; i < selectedDocuments.size(); i++) {
            Document doc = selectedDocuments.get(i);
            combinedContent.append(doc.getTextContent()).append("\n\n");
            
            if (i == 0) {
                documentTitles = doc.getDisplayName();
            } else if (i == selectedDocuments.size() - 1) {
                documentTitles += " and " + doc.getDisplayName();
            } else {
                documentTitles += ", " + doc.getDisplayName();
            }
        }
        
        final String finalDocumentTitles = documentTitles;
        
        FlashcardGenerationService flashcardService = new FlashcardGenerationService(this);
        
        flashcardService.generateFlashcards(subjectName, finalDocumentTitles, 
            combinedContent.toString(), new FlashcardGenerationService.FlashcardGenerationCallback() {
            
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.setMessage(message);
                    }
                });
            }
            
            @Override
            public void onSuccess(int flashcardCount) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    
                    Toast.makeText(SubjectDetailActivity.this, 
                        "✓ Generated " + flashcardCount + " flashcards!", 
                        Toast.LENGTH_SHORT).show();
                    
                    // Record activity
                    activityManager.addActivity(subjectName, 
                        "Generated flashcards from " + finalDocumentTitles, 
                        RecentActivity.ActivityType.FLASHCARDS);
                    loadRecentActivity();
                    
                    // Track as last global activity
                    LastActivityTracker.saveFlashcardActivity(SubjectDetailActivity.this, subjectName);
                    
                    // Navigate to flashcard activity
                    Intent intent = new Intent(SubjectDetailActivity.this, FlashcardActivity.class);
                    intent.putExtra("subject", subjectName);
                    intent.putExtra("document_title", finalDocumentTitles);
                    startActivity(intent);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    
                    Toast.makeText(SubjectDetailActivity.this, 
                        "Flashcard generation failed: " + errorMessage, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}