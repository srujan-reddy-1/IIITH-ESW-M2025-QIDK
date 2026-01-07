package com.quicinc.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SubjectsActivity extends AppCompatActivity {

    private RecyclerView subjectsRecyclerView;
    private SubjectAdapter subjectAdapter;
    private List<Subject> subjects;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subjects);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("SubjectsData", MODE_PRIVATE);

        initializeViews();
        loadSubjects();
        setupRecyclerView();
        setupClickListeners();
    }

    private void initializeViews() {
        subjectsRecyclerView = findViewById(R.id.subjects_recycler_view);
    }

    private void loadSubjects() {
        String subjectsJson = sharedPreferences.getString("subjects_list", null);
        
        if (subjectsJson != null) {
            // Load subjects from SharedPreferences
            subjects = loadSubjectsFromJson(subjectsJson);
            // Clean up any unwanted subjects
            cleanupUnwantedSubjects();
            // Add any missing default subjects
            addMissingDefaultSubjects();
            // Update colors for default subjects
            updateDefaultSubjectColors();
        } else {
            // Initialize default subjects for first time
            initializeDefaultSubjects();
            saveSubjects();
        }
    }
    
    private void cleanupUnwantedSubjects() {
        // Remove subjects that shouldn't be there (like PDF-generated subjects)
        subjects.removeIf(subject -> 
            subject.getName().toLowerCase().contains("osn_l") || 
            subject.getName().toLowerCase().contains("pdf") ||
            subject.getName().toLowerCase().startsWith("doc_")
        );
        saveSubjects();
    }
    
    private void updateDefaultSubjectColors() {
        // Update colors for default subjects to ensure they use the new color scheme
        for (Subject subject : subjects) {
            if (subject.getName().equals("Data Structures and Algorithms")) {
                subject.setColorResId(R.color.dsa_color);
            } else if (subject.getName().equals("Operating Systems and Networking")) {
                subject.setColorResId(R.color.osn_color);
            } else if (subject.getName().equals("Probability and Statistics")) {
                subject.setColorResId(R.color.iss_color);
            } else if (subject.getName().equals("Automata Theory")) {
                subject.setColorResId(R.color.automata_color);
            } else if (subject.getName().equals("Data and Application")) {
                subject.setColorResId(R.color.data_app_color);
            } else if (subject.getName().equals("Embedded Systems Workshop")) {
                subject.setColorResId(R.color.embedded_color);
            } else if (subject.getName().equals("Science-1")) {
                subject.setColorResId(R.color.science1_color);
            } else if (subject.getName().equals("Algorithm Analysis and Design")) {
                subject.setColorResId(R.color.algorithm_analysis_color);
            }
        }
        // Save the updated colors
        saveSubjects();
    }
    
    private void addMissingDefaultSubjects() {
        // Check if new subjects are missing and add them
        boolean hasEmbedded = false;
        boolean hasScience1 = false;
        boolean hasAlgorithmAnalysis = false;
        
        for (Subject subject : subjects) {
            if (subject.getName().equals("Embedded Systems Workshop")) {
                hasEmbedded = true;
            }
            if (subject.getName().equals("Science-1")) {
                hasScience1 = true;
            }
            if (subject.getName().equals("Algorithm Analysis and Design")) {
                hasAlgorithmAnalysis = true;
            }
        }
        
        if (!hasEmbedded) {
            subjects.add(new Subject(
                    "Embedded Systems Workshop",
                    "Microcontrollers, Real-time Systems, Hardware Programming, IoT",
                    R.color.embedded_color,
                    87,
                    30,
                    20
            ));
        }
        
        if (!hasScience1) {
            subjects.add(new Subject(
                    "Science-1",
                    "Physics, Chemistry, Biology, Scientific Methods",
                    R.color.science1_color,
                    85,
                    30,
                    20
            ));
        }
        
        if (!hasAlgorithmAnalysis) {
            subjects.add(new Subject(
                    "Algorithm Analysis and Design",
                    "Complexity Analysis, Dynamic Programming, Greedy Algorithms, Divide and Conquer",
                    R.color.algorithm_analysis_color,
                    89,
                    30,
                    20
            ));
        }
        
        if (!hasEmbedded || !hasScience1 || !hasAlgorithmAnalysis) {
            saveSubjects();
        }
    }
    
    private void initializeDefaultSubjects() {
        subjects = new ArrayList<>();

        subjects.add(new Subject(
                "Data Structures and Algorithms",
                "Arrays, Linked Lists, Trees, Graphs, Sorting, Searching",
                R.color.dsa_color,
                90,
                30,
                20
        ));

        subjects.add(new Subject(
                "Operating Systems and Networking",
                "Processes, Threads, Memory Management, TCP/IP, OSI Model",
                R.color.osn_color,
                85,
                30,
                20
        ));

        subjects.add(new Subject(
                "Probability and Statistics",
                "Distributions, Hypothesis Testing, Regression, Confidence Intervals",
                R.color.iss_color,
                80,
                30,
                20
        ));

        subjects.add(new Subject(
                "Automata Theory",
                "Finite Automata, Turing Machines, Formal Languages",
                R.color.automata_color,
                88,
                30,
                20
        ));

        subjects.add(new Subject(
                "Data and Application",
                "Database Management, Web Development, Application Design",
                R.color.data_app_color,
                82,
                30,
                20
        ));

        subjects.add(new Subject(
                "Embedded Systems Workshop",
                "Microcontrollers, Real-time Systems, Hardware Programming, IoT",
                R.color.embedded_color,
                87,
                30,
                20
        ));

        subjects.add(new Subject(
                "Science-1",
                "Physics, Chemistry, Biology, Scientific Methods",
                R.color.science1_color,
                85,
                30,
                20
        ));

        subjects.add(new Subject(
                "Algorithm Analysis and Design",
                "Complexity Analysis, Dynamic Programming, Greedy Algorithms, Divide and Conquer",
                R.color.algorithm_analysis_color,
                89,
                30,
                20
        ));
    }
    
    private List<Subject> loadSubjectsFromJson(String json) {
        List<Subject> loadedSubjects = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject subjectObj = jsonArray.getJSONObject(i);
                Subject subject = new Subject(
                    subjectObj.getString("name"),
                    subjectObj.getString("description"),
                    subjectObj.getInt("colorResource"),
                    subjectObj.getInt("progress"),
                    subjectObj.getInt("questionsAnswered"),
                    subjectObj.getInt("flashcardsStudied"),
                    subjectObj.optString("pdfUri", null) // Load PDF URI or null if not present
                );
                loadedSubjects.add(subject);
            }
        } catch (JSONException e) {
            // If JSON parsing fails, return default subjects
            initializeDefaultSubjects();
            return subjects;
        }
        return loadedSubjects;
    }
    
    private void saveSubjects() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Subject subject : subjects) {
                JSONObject subjectObj = new JSONObject();
                subjectObj.put("name", subject.getName());
                subjectObj.put("description", subject.getDescription());
                subjectObj.put("colorResource", subject.getColorResId());
                subjectObj.put("progress", subject.getProgress());
                subjectObj.put("questionsAnswered", subject.getQuestionsAnswered());
                subjectObj.put("flashcardsStudied", subject.getFlashcardsStudied());
                subjectObj.put("pdfUri", subject.getPdfUri() != null ? subject.getPdfUri() : "");
                jsonArray.put(subjectObj);
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("subjects_list", jsonArray.toString());
            editor.apply();
            
            // Update subject count for progress tracking
            ProgressTracker.updateSubjectCount(this, subjects.size());
        } catch (JSONException e) {
            // Handle JSON error
            e.printStackTrace();
        }
    }

    private void setupRecyclerView() {
        subjectAdapter = new SubjectAdapter(subjects, new SubjectAdapter.OnSubjectClickListener() {
            @Override
            public void onSubjectClick(Subject subject) {
                // Navigate to subject detail or start study session
                Intent intent = new Intent(SubjectsActivity.this, SubjectDetailActivity.class);
                intent.putExtra("subject_name", subject.getName());
                intent.putExtra("subject_description", subject.getDescription());
                intent.putExtra("subject_color", subject.getColorResId());
                startActivity(intent);
            }
        });
        
        // Set long-click listener for delete
        subjectAdapter.setOnSubjectLongClickListener(new SubjectAdapter.OnSubjectLongClickListener() {
            @Override
            public void onSubjectLongClick(Subject subject, int position) {
                showDeleteDialog(subject, position);
            }
        });

        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        subjectsRecyclerView.setLayoutManager(gridLayoutManager);
        subjectsRecyclerView.setAdapter(subjectAdapter);
    }
    
    private void showDeleteDialog(Subject subject, int position) {
        // Only allow deletion of PDF-based subjects, not default subjects
        if (isDefaultSubject(subject.getName())) {
            Toast.makeText(this, "Cannot delete default subjects", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Subject")
            .setMessage("Are you sure you want to delete \"" + subject.getName() + "\"?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Remove from list
                subjects.remove(position);
                subjectAdapter.notifyItemRemoved(position);
                subjectAdapter.notifyItemRangeChanged(position, subjects.size());
                
                // Save updated list
                saveSubjects();
                
                Toast.makeText(this, "Deleted: " + subject.getName(), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupClickListeners() {
        // Upload PDF button - creates new subject from uploaded PDF
        android.widget.ImageButton uploadButton = findViewById(R.id.upload_pdf_button);
        if (uploadButton != null) {
            uploadButton.setOnClickListener(v -> showSubjectNameDialog());
        }
        
        // Back button is optional - only set listener if it exists
//        View backButton = findViewById(R.id.back_button);
//        if (backButton != null) {
//            backButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    finish();
//                }
//            });
//        }

        // Add subject button - currently disabled as per user request
        // User wants PDF upload within each subject, not creating new subjects
//        View addSubjectButton = findViewById(R.id.add_subject_button);
//        if (addSubjectButton != null) {
//            addSubjectButton.setVisibility(View.GONE);
//        }
    }
    
    private void showSubjectNameDialog() {
        // Create an EditText for the subject name input
        final EditText input = new EditText(this);
        input.setHint("Enter subject name");
        input.setPadding(50, 40, 50, 40);
        
        // Create the AlertDialog
        new AlertDialog.Builder(this)
                .setTitle("Create New Subject")
                .setMessage("Enter a name for the new subject:")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String subjectName = input.getText().toString().trim();
                    
                    // Validate input
                    if (subjectName.isEmpty()) {
                        Toast.makeText(this, "Subject name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Check if subject with this name already exists
                    for (Subject subject : subjects) {
                        if (subject.getName().equalsIgnoreCase(subjectName)) {
                            Toast.makeText(this, "A subject with this name already exists", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    
                    // Create the subject directly without PDF upload
                    createNewSubject(subjectName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    private void createNewSubject(String subjectName) {
        // Use the same color as default subjects (blue color)
        int subjectColor = R.color.dsa_color;
        
        // Create new subject with default values
        Subject newSubject = new Subject(
                subjectName,
                "Custom subject",
                subjectColor,
                0,  // Default progress
                0,  // Default questions
                0   // Default flashcards
        );
        
        // Add to subjects list
        subjects.add(newSubject);
        
        // Save to persistent storage
        saveSubjects();
        
        // Refresh the RecyclerView
        if (subjectAdapter != null) {
            subjectAdapter.notifyItemInserted(subjects.size() - 1);
        }
        
        Toast.makeText(this, "Created new subject: " + subjectName, Toast.LENGTH_SHORT).show();
        
        // Automatically open the new subject
        openSubjectDetail(newSubject);
    }

    
    /**
     * Helper method to open subject detail
     */
    private void openSubjectDetail(Subject subject) {
        Intent intent = new Intent(SubjectsActivity.this, SubjectDetailActivity.class);
        intent.putExtra("subject_name", subject.getName());
        intent.putExtra("subject_description", subject.getDescription());
        intent.putExtra("subject_color", subject.getColorResId());
        startActivity(intent);
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try {
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                // If content resolver fails, try to get from path
                String path = uri.getPath();
                if (path != null) {
                    fileName = path.substring(path.lastIndexOf('/') + 1);
                }
            }
        } else if (uri.getScheme().equals("file")) {
            String path = uri.getPath();
            if (path != null) {
                fileName = path.substring(path.lastIndexOf('/') + 1);
            }
        }
        return fileName;
    }
    
    private boolean isDefaultSubject(String subjectName) {
        // Check if this is one of the default built-in subjects
        return subjectName.equals("Data Structures and Algorithms") ||
               subjectName.equals("Operating Systems and Networking") ||
               subjectName.equals("Probability and Statistics") ||
               subjectName.equals("Automata Theory") ||
               subjectName.equals("Data and Application");
    }
}