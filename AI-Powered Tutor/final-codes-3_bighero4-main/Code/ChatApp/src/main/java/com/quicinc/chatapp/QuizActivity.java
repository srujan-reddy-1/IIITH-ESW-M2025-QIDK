// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class QuizActivity extends AppCompatActivity {

    private TextView questionText, questionNumberText, timerText, scoreText, resultMessage, resultScoreText, resultPercentageText, subjectTitle;
    private ProgressBar quizProgress;
    private RadioGroup optionsGroup;
    private Button previousButton, nextButton, submitButton, retakeButton, backToMainButton, reviewAnswersButton, backToResultsButton;
    private LinearLayout quizContent, resultLayout, loadingLayout, reviewLayout;
    private androidx.recyclerview.widget.RecyclerView reviewRecyclerView;

    private List<QuizQuestion> questions;
    private int currentQuestionIndex = 0;
    private int score = 0;
    private CountDownTimer timer;
    private long timeRemaining = 600000; // 10 minutes in milliseconds
    private boolean quizCompleted = false;
    private String subjectName = "Quiz";
    private String documentTitle = null; // For document-specific quizzes

    private static final String TAG = "QuizActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        // Get subject name from intent
        if (getIntent().hasExtra("subject")) {
            subjectName = getIntent().getStringExtra("subject");
        }
        
        // Get document title from intent (if launching from specific document)
        if (getIntent().hasExtra("document_title")) {
            documentTitle = getIntent().getStringExtra("document_title");
        }

        initializeViews();
        if (quizProgress == null) {
            Log.e(TAG, "quiz_progress not found in layout. Check activity_quiz.xml");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_SHORT).show();
            return; // Exit if critical component is missing
        }
        
        // Check if quiz generation is in progress
        // Only check for document-specific mode, not for interactive or general subject mode
        if (documentTitle != null && !documentTitle.isEmpty() && isQuizGenerationInProgress()) {
            showGeneratingQuizMessage();
            return;
        }
        
        initializeQuestions();
        setupClickListeners();
        startQuiz();
    }

    private void initializeViews() {
        questionText = findViewById(R.id.question_text);
        questionNumberText = findViewById(R.id.question_number);
        timerText = findViewById(R.id.timer_text);
        scoreText = findViewById(R.id.score_text);
        subjectTitle = findViewById(R.id.subject_title);
        quizProgress = findViewById(R.id.quiz_progress);
        optionsGroup = findViewById(R.id.options_group);
        previousButton = findViewById(R.id.previous_button);
        nextButton = findViewById(R.id.next_button);
        submitButton = findViewById(R.id.submit_button);
        quizContent = findViewById(R.id.quiz_content);
        loadingLayout = findViewById(R.id.loading_layout);
        resultLayout = findViewById(R.id.result_layout);
        resultMessage = findViewById(R.id.result_message);
        resultScoreText = findViewById(R.id.result_score_text);
        resultPercentageText = findViewById(R.id.result_percentage_text);
        retakeButton = findViewById(R.id.retake_button);
        backToMainButton = findViewById(R.id.back_to_main_button);
        reviewAnswersButton = findViewById(R.id.review_answers_button);
        reviewLayout = findViewById(R.id.review_layout);
        reviewRecyclerView = findViewById(R.id.review_recycler_view);
        backToResultsButton = findViewById(R.id.back_to_results_button);
        
        // Set subject title
        if (subjectTitle != null) {
            String title;
            
            if (documentTitle != null && !documentTitle.isEmpty()) {
                // Show document-specific title
                title = documentTitle + " Quiz";
            } else {
                String shortName = getShortSubjectName(subjectName);
                Log.d("QuizActivity", "Subject name received: '" + subjectName + "'");
                Log.d("QuizActivity", "Short name generated: '" + shortName + "'");
                
                if (shortName.equals("Interactive Quizzes") || shortName.toLowerCase().contains("quiz")) {
                    // If it already contains "Quiz" or is "Interactive Quizzes", use as-is
                    title = shortName;
                } else {
                    // Otherwise, append " Quiz"
                    title = shortName + " Quiz";
                }
            }
            
            subjectTitle.setText(title);
            Log.d("QuizActivity", "Set title to: " + title);
        }
    }

    private String getShortSubjectName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "Interactive Quizzes";
        
        switch (fullName.toLowerCase()) {
            case "data structures and algorithms":
                return "DSA";
            case "operating systems and networking":
                return "OSN";
            case "probability and statistics":
                return "Probability & Statistics";
            case "automata theory":
                return "Automata";
            case "data and application":
                return "Data & App";
            case "information systems security":
                return "ISS";
            case "machine learning":
                return "ML";
            case "artificial intelligence":
                return "AI";
            default:
                return fullName;
        }
    }

    private void initializeQuestions() {
        questions = new ArrayList<>();
        
        // First, try to load questions from QuizBank
        try {
            QuizBank quizBank = QuizBank.getInstance();
            quizBank.init(this);
            
            // Check if this is interactive mode (no subject passed from MainActivity)
            boolean isInteractiveMode = !getIntent().hasExtra("subject");
            
            if (isInteractiveMode) {
                // Interactive mode: get 10 random questions from ALL generated quizzes
                List<QuizQuestion> bankQuestions = quizBank.getRandomQuizzes(10);
                if (!bankQuestions.isEmpty()) {
                    questions.addAll(bankQuestions);
                    Log.d(TAG, "Loaded " + questions.size() + " random questions from QuizBank (interactive mode)");
                } else {
                    // No generated quizzes available - show message instead of hardcoded
                    Log.w(TAG, "No generated quizzes available for interactive mode");
                    showNoQuizzesAvailable();
                    return;
                }
            } else if (documentTitle != null && !documentTitle.isEmpty()) {
                // Document-specific mode: get questions only for this specific document
                List<QuizQuestion> bankQuestions = quizBank.getQuizzesForDocument(subjectName, documentTitle);
                if (!bankQuestions.isEmpty()) {
                    questions.addAll(bankQuestions);
                    Log.d(TAG, "Loaded " + questions.size() + " questions from document: " + documentTitle);
                } else {
                    Log.w(TAG, "No questions found for document: " + documentTitle);
                }
            } else {
                // Subject-specific mode: get questions for this subject (all documents)
                List<QuizQuestion> bankQuestions = quizBank.getQuizzesForSubject(subjectName);
                if (!bankQuestions.isEmpty()) {
                    questions.addAll(bankQuestions);
                    Log.d(TAG, "Loaded " + questions.size() + " questions from QuizBank for " + subjectName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading questions from QuizBank", e);
        }
        
        // If QuizBank is empty (but NOT interactive mode), fall back to hardcoded questions
        boolean isInteractiveMode = !getIntent().hasExtra("subject");
        if (questions.isEmpty() && !isInteractiveMode) {
            Log.d(TAG, "QuizBank empty, using hardcoded questions");
            if (subjectName == null) {
                initializeGeneralQuestions();
            } else {
                String subject = subjectName.toLowerCase();
                if (subject.contains("data structures") || subject.contains("algorithms") || subject.contains("dsa")) {
                    initializeDSAQuestions();
                } else if (subject.contains("operating systems") || subject.contains("networking") || subject.contains("osn")) {
                    initializeOSNQuestions();
                } else if (subject.contains("probability") || subject.contains("statistics")) {
                    initializeProbabilityStatisticsQuestions();
                } else if (subject.contains("automata") || subject.contains("formal languages")) {
                    initializeAutomataQuestions();
                } else if (subject.contains("data and application") || subject.contains("database") || subject.contains("web development")) {
                    initializeDataApplicationQuestions();
                } else if (subject.contains("math") || subject.contains("mathematics")) {
                    initializeMathQuestions();
                } else {
                    // Default general questions for uploaded PDFs or unknown subjects
                    initializeGeneralQuestions();
                }
            }
        }
    }

    private void initializeDSAQuestions() {
        questions.add(new QuizQuestion("What is the time complexity of binary search?", 
            new String[]{"O(n)", "O(log n)", "O(n²)", "O(1)"}, 1));
        questions.add(new QuizQuestion("Which data structure uses LIFO principle?", 
            new String[]{"Queue", "Stack", "Array", "Tree"}, 1));
        questions.add(new QuizQuestion("What is the worst-case time complexity of QuickSort?", 
            new String[]{"O(n log n)", "O(n²)", "O(log n)", "O(n)"}, 1));
        questions.add(new QuizQuestion("In a binary tree, what is the maximum number of nodes at level n?", 
            new String[]{"2^n", "2^(n-1)", "2^(n+1)", "n²"}, 0));
        questions.add(new QuizQuestion("Which traversal of a binary tree visits root first?", 
            new String[]{"Inorder", "Preorder", "Postorder", "Level order"}, 1));
        questions.add(new QuizQuestion("What is the space complexity of merge sort?", 
            new String[]{"O(1)", "O(log n)", "O(n)", "O(n²)"}, 2));
        questions.add(new QuizQuestion("Which data structure is best for implementing recursion?", 
            new String[]{"Queue", "Stack", "Array", "Linked List"}, 1));
        questions.add(new QuizQuestion("What is the average case time complexity of hash table insertion?", 
            new String[]{"O(1)", "O(log n)", "O(n)", "O(n²)"}, 0));
        questions.add(new QuizQuestion("In a complete binary tree with n nodes, what is the height?", 
            new String[]{"log n", "n", "n/2", "√n"}, 0));
        questions.add(new QuizQuestion("Which algorithm is used to find shortest path in weighted graphs?", 
            new String[]{"BFS", "DFS", "Dijkstra", "Binary Search"}, 2));
    }

    private void initializeOSQuestions() {
        questions.add(new QuizQuestion("What is the main function of an operating system?", 
            new String[]{"Manage hardware", "Run applications", "Provide user interface", "All of the above"}, 3));
        questions.add(new QuizQuestion("Which scheduling algorithm gives minimum waiting time?", 
            new String[]{"FCFS", "SJF", "Round Robin", "Priority"}, 1));
        questions.add(new QuizQuestion("What is a deadlock in operating systems?", 
            new String[]{"Process termination", "Memory leak", "Circular wait condition", "CPU overload"}, 2));
        questions.add(new QuizQuestion("What is virtual memory?", 
            new String[]{"Physical RAM", "Secondary storage used as RAM", "Cache memory", "ROM"}, 1));
        questions.add(new QuizQuestion("Which command is used to list files in Unix/Linux?", 
            new String[]{"dir", "ls", "list", "show"}, 1));
        questions.add(new QuizQuestion("What is a process in operating systems?", 
            new String[]{"Running program", "System call", "Memory address", "File descriptor"}, 0));
        questions.add(new QuizQuestion("What is the purpose of system calls?", 
            new String[]{"User-kernel communication", "Process creation", "File operations", "All of the above"}, 3));
        questions.add(new QuizQuestion("Which memory management technique allows non-contiguous allocation?", 
            new String[]{"Contiguous allocation", "Paging", "Segmentation", "Both B and C"}, 3));
        questions.add(new QuizQuestion("What is thrashing in virtual memory?", 
            new String[]{"Low CPU utilization due to excessive paging", "Memory corruption", "Process deadlock", "File system error"}, 0));
        questions.add(new QuizQuestion("Which of the following is not a process state?", 
            new String[]{"Ready", "Running", "Waiting", "Compiled"}, 3));
    }

    private void initializeDBMSQuestions() {
        questions.add(new QuizQuestion("What does ACID stand for in database systems?", 
            new String[]{"Atomicity, Consistency, Isolation, Durability", "Access, Control, Integration, Design", "Analysis, Creation, Implementation, Deployment", "Authentication, Certification, Identification, Declaration"}, 0));
        questions.add(new QuizQuestion("Which normal form eliminates partial dependencies?", 
            new String[]{"1NF", "2NF", "3NF", "BCNF"}, 1));
        questions.add(new QuizQuestion("What is a primary key?", 
            new String[]{"Unique identifier for a record", "Foreign key reference", "Index on a table", "Backup key"}, 0));
        questions.add(new QuizQuestion("Which SQL command is used to retrieve data?", 
            new String[]{"INSERT", "UPDATE", "DELETE", "SELECT"}, 3));
        questions.add(new QuizQuestion("What is denormalization?", 
            new String[]{"Breaking tables apart", "Combining normalized tables", "Creating indexes", "Data backup"}, 1));
        questions.add(new QuizQuestion("Which join returns all records from both tables?", 
            new String[]{"INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN"}, 3));
        questions.add(new QuizQuestion("What is a transaction in database?", 
            new String[]{"Single SQL statement", "Group of related operations", "Database backup", "User session"}, 1));
        questions.add(new QuizQuestion("Which constraint ensures referential integrity?", 
            new String[]{"PRIMARY KEY", "FOREIGN KEY", "UNIQUE", "CHECK"}, 1));
        questions.add(new QuizQuestion("What is the purpose of indexing?", 
            new String[]{"Data security", "Faster data retrieval", "Data backup", "Data compression"}, 1));
        questions.add(new QuizQuestion("Which isolation level prevents dirty reads?", 
            new String[]{"READ UNCOMMITTED", "READ COMMITTED", "REPEATABLE READ", "SERIALIZABLE"}, 1));
    }

    private void initializeNetworkQuestions() {
        questions.add(new QuizQuestion("What does TCP stand for?", 
            new String[]{"Transfer Control Protocol", "Transmission Control Protocol", "Transport Communication Protocol", "Terminal Control Protocol"}, 1));
        questions.add(new QuizQuestion("Which layer handles routing in the OSI model?", 
            new String[]{"Physical", "Data Link", "Network", "Transport"}, 2));
        questions.add(new QuizQuestion("What is the default port for HTTP?", 
            new String[]{"21", "23", "80", "443"}, 2));
        questions.add(new QuizQuestion("Which protocol is connectionless?", 
            new String[]{"TCP", "UDP", "HTTP", "FTP"}, 1));
        questions.add(new QuizQuestion("What does DNS stand for?", 
            new String[]{"Domain Name System", "Data Network Service", "Dynamic Network Setup", "Digital Name Server"}, 0));
        questions.add(new QuizQuestion("Which device operates at the Network layer?", 
            new String[]{"Hub", "Switch", "Router", "Bridge"}, 2));
        questions.add(new QuizQuestion("What is the maximum length of an Ethernet frame?", 
            new String[]{"1024 bytes", "1518 bytes", "2048 bytes", "4096 bytes"}, 1));
        questions.add(new QuizQuestion("Which protocol is used for secure web browsing?", 
            new String[]{"HTTP", "HTTPS", "FTP", "SMTP"}, 1));
        questions.add(new QuizQuestion("What is subnetting?", 
            new String[]{"Dividing a network into smaller networks", "Connecting networks", "Network security", "Data encryption"}, 0));
        questions.add(new QuizQuestion("Which command is used to test network connectivity?", 
            new String[]{"ipconfig", "netstat", "ping", "tracert"}, 2));
    }

    private void initializeSoftwareEngineeringQuestions() {
        questions.add(new QuizQuestion("What is the first phase of SDLC?", 
            new String[]{"Design", "Implementation", "Requirements Analysis", "Testing"}, 2));
        questions.add(new QuizQuestion("Which model follows iterative approach?", 
            new String[]{"Waterfall", "Spiral", "V-Model", "Big Bang"}, 1));
        questions.add(new QuizQuestion("What is white box testing?", 
            new String[]{"Testing without code knowledge", "Testing with code knowledge", "User acceptance testing", "Performance testing"}, 1));
        questions.add(new QuizQuestion("What does UML stand for?", 
            new String[]{"Unified Modeling Language", "Universal Markup Language", "User Management Logic", "Unified Management Language"}, 0));
        questions.add(new QuizQuestion("Which principle focuses on code reusability?", 
            new String[]{"DRY", "KISS", "YAGNI", "SOLID"}, 0));
        questions.add(new QuizQuestion("What is refactoring?", 
            new String[]{"Adding new features", "Improving code structure", "Bug fixing", "Performance optimization"}, 1));
        questions.add(new QuizQuestion("Which testing is done by end users?", 
            new String[]{"Unit testing", "Integration testing", "System testing", "Acceptance testing"}, 3));
        questions.add(new QuizQuestion("What is version control?", 
            new String[]{"Code compilation", "Managing code changes", "Code execution", "Code documentation"}, 1));
        questions.add(new QuizQuestion("Which metric measures code complexity?", 
            new String[]{"LOC", "Cyclomatic Complexity", "Defect Density", "Code Coverage"}, 1));
        questions.add(new QuizQuestion("What is agile methodology?", 
            new String[]{"Sequential development", "Iterative development", "Prototype development", "Incremental development"}, 1));
    }

    private void initializeMathQuestions() {
        questions.add(new QuizQuestion("What is 15 + 27?", 
            new String[]{"40", "42", "44", "46"}, 1));
        questions.add(new QuizQuestion("What is the square root of 64?", 
            new String[]{"6", "7", "8", "9"}, 2));
        questions.add(new QuizQuestion("What is 12 × 8?", 
            new String[]{"84", "92", "96", "104"}, 2));
        questions.add(new QuizQuestion("What is 144 ÷ 12?", 
            new String[]{"10", "11", "12", "13"}, 2));
        questions.add(new QuizQuestion("What is 2³ (2 to the power of 3)?", 
            new String[]{"6", "8", "9", "12"}, 1));
        questions.add(new QuizQuestion("What is the derivative of x²?", 
            new String[]{"x", "2x", "x²", "2"}, 1));
        questions.add(new QuizQuestion("What is sin(90°)?", 
            new String[]{"0", "1", "-1", "0.5"}, 1));
        questions.add(new QuizQuestion("What is the area of a circle with radius 5?", 
            new String[]{"25π", "10π", "5π", "15π"}, 0));
        questions.add(new QuizQuestion("What is log₁₀(100)?", 
            new String[]{"1", "2", "10", "100"}, 1));
        questions.add(new QuizQuestion("What is the sum of angles in a triangle?", 
            new String[]{"90°", "180°", "270°", "360°"}, 1));
    }

    private void initializeGeneralQuestions() {
        questions.add(new QuizQuestion("What does CPU stand for?", 
            new String[]{"Central Processing Unit", "Computer Processing Unit", "Central Program Unit", "Computer Program Unit"}, 0));
        questions.add(new QuizQuestion("Which of the following is a programming language?", 
            new String[]{"HTML", "CSS", "Python", "HTTP"}, 2));
        questions.add(new QuizQuestion("What is the binary representation of 8?", 
            new String[]{"1000", "1010", "1100", "0100"}, 0));
        questions.add(new QuizQuestion("Which company developed Java?", 
            new String[]{"Microsoft", "Sun Microsystems", "Apple", "IBM"}, 1));
        questions.add(new QuizQuestion("What does WWW stand for?", 
            new String[]{"World Wide Web", "World Web World", "Wide World Web", "Web World Wide"}, 0));
        questions.add(new QuizQuestion("Which is not an input device?", 
            new String[]{"Keyboard", "Mouse", "Monitor", "Microphone"}, 2));
        questions.add(new QuizQuestion("What is the main component of a computer?", 
            new String[]{"Monitor", "Keyboard", "Motherboard", "Speaker"}, 2));
        questions.add(new QuizQuestion("Which protocol is used for email?", 
            new String[]{"HTTP", "SMTP", "FTP", "TCP"}, 1));
        questions.add(new QuizQuestion("What does RAM stand for?", 
            new String[]{"Random Access Memory", "Read Access Memory", "Rapid Access Memory", "Real Access Memory"}, 0));
        questions.add(new QuizQuestion("Which is a type of computer virus?", 
            new String[]{"Trojan", "Worm", "Spyware", "All of the above"}, 3));
    }

    private void initializeOSNQuestions() {
        questions.add(new QuizQuestion("What is an Operating System?", 
            new String[]{"Hardware component", "Software that manages hardware", "Programming language", "Database"}, 1));
        questions.add(new QuizQuestion("What is a process in OS?", 
            new String[]{"Running program", "Static program", "Hardware device", "Network protocol"}, 0));
        questions.add(new QuizQuestion("Which scheduling algorithm is fair?", 
            new String[]{"FCFS", "Round Robin", "SJF", "Priority"}, 1));
        questions.add(new QuizQuestion("What causes deadlock?", 
            new String[]{"Mutual exclusion", "Hold and wait", "Circular wait", "All of the above"}, 3));
        questions.add(new QuizQuestion("TCP stands for?", 
            new String[]{"Transfer Control Protocol", "Transmission Control Protocol", "Transport Control Protocol", "Technical Control Protocol"}, 1));
    }

    private void initializeProbabilityStatisticsQuestions() {
        questions.add(new QuizQuestion("What is probability range?", 
            new String[]{"0 to 100", "0 to 1", "-1 to 1", "1 to 10"}, 1));
        questions.add(new QuizQuestion("What is P(not A) if P(A) = 0.3?", 
            new String[]{"0.3", "0.7", "1.0", "0.0"}, 1));
        questions.add(new QuizQuestion("Normal distribution is also called?", 
            new String[]{"Bell curve", "Gaussian distribution", "Standard distribution", "All of the above"}, 3));
        questions.add(new QuizQuestion("What is the mean of [2, 4, 6]?", 
            new String[]{"3", "4", "5", "6"}, 1));
        questions.add(new QuizQuestion("Standard deviation measures?", 
            new String[]{"Central tendency", "Data spread", "Correlation", "Regression"}, 1));
    }

    private void initializeAutomataQuestions() {
        questions.add(new QuizQuestion("What is a finite automaton?", 
            new String[]{"Mathematical model", "Programming language", "Database", "Network protocol"}, 0));
        questions.add(new QuizQuestion("DFA stands for?", 
            new String[]{"Direct Finite Automaton", "Deterministic Finite Automaton", "Data Flow Analysis", "Dynamic Function Array"}, 1));
        questions.add(new QuizQuestion("Kleene star (*) represents?", 
            new String[]{"One occurrence", "Zero or more occurrences", "Exactly two", "One or more"}, 1));
        questions.add(new QuizQuestion("Which is NOT a regular language operation?", 
            new String[]{"Union", "Concatenation", "Intersection", "None - all are regular"}, 3));
        questions.add(new QuizQuestion("Turing machine can simulate?", 
            new String[]{"Any algorithm", "Only simple calculations", "Database queries", "Network protocols"}, 0));
    }

    private void initializeDataApplicationQuestions() {
        questions.add(new QuizQuestion("What is ETL in data processing?", 
            new String[]{"Extract, Transform, Load", "Enter, Test, Launch", "Edit, Transfer, Link", "Export, Track, List"}, 0));
        questions.add(new QuizQuestion("NoSQL databases are?", 
            new String[]{"Relational only", "Schema-flexible", "Always faster", "Only for big data"}, 1));
        questions.add(new QuizQuestion("What is OLAP?", 
            new String[]{"Online Transaction Processing", "Online Analytical Processing", "Offline Application Processing", "Open Logic Application Protocol"}, 1));
        questions.add(new QuizQuestion("REST API uses which protocol?", 
            new String[]{"FTP", "SMTP", "HTTP", "TCP"}, 2));
        questions.add(new QuizQuestion("What is Big Data characterized by?", 
            new String[]{"Volume only", "Velocity only", "Variety only", "Volume, Velocity, Variety"}, 3));
    }

    private void setupClickListeners() {
        nextButton.setOnClickListener(v -> {
            saveCurrentAnswer();
            if (currentQuestionIndex < questions.size() - 1) {
                currentQuestionIndex++;
                displayQuestion();
            } else if (!quizCompleted) {
                finishQuiz();
            }
        });

        previousButton.setOnClickListener(v -> {
            saveCurrentAnswer();
            if (currentQuestionIndex > 0) {
                currentQuestionIndex--;
                displayQuestion();
            }
        });

        submitButton.setOnClickListener(v -> {
            saveCurrentAnswer();
            finishQuiz();
        });

        retakeButton.setOnClickListener(v -> {
            restartQuiz();
        });

        backToMainButton.setOnClickListener(v -> finish());
        
        reviewAnswersButton.setOnClickListener(v -> {
            showReview();
        });
        
        backToResultsButton.setOnClickListener(v -> {
            hideReview();
        });
    }

    private void startQuiz() {
        // Hide loading, show quiz content
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
        }
        if (quizContent != null) {
            quizContent.setVisibility(View.VISIBLE);
        }
        
        displayQuestion();
        startTimer();
        updateUI();
    }

    private void displayQuestion() {
        if (currentQuestionIndex < questions.size()) {
            QuizQuestion question = questions.get(currentQuestionIndex);

            questionText.setText(question.getQuestion());
            questionNumberText.setText(getString(R.string.quiz_progress, currentQuestionIndex + 1, questions.size()));

            // Clear previous options and selection
            optionsGroup.removeAllViews();
            optionsGroup.clearCheck();

            // Add new options
            String[] options = question.getOptions();
            for (int i = 0; i < options.length; i++) {
                RadioButton radioButton = new RadioButton(this);
                radioButton.setText(options[i]);
                radioButton.setId(View.generateViewId()); // Generate unique ID
                radioButton.setTag(i); // Store the option index in the tag
                radioButton.setTextSize(16);
                radioButton.setPadding(16, 16, 16, 16);
                
                // Check if this option was previously selected
                if (question.getSelectedAnswer() == i) {
                    radioButton.setChecked(true);
                }
                
                optionsGroup.addView(radioButton);
            }

            updateProgressBar();
            updateNavigationButtons();
        }
    }

    private void saveCurrentAnswer() {
        if (currentQuestionIndex < questions.size()) {
            int selectedId = optionsGroup.getCheckedRadioButtonId();
            if (selectedId != -1) {
                // Find the RadioButton and get its tag (which contains the option index)
                RadioButton selectedButton = findViewById(selectedId);
                if (selectedButton != null && selectedButton.getTag() != null) {
                    int optionIndex = (Integer) selectedButton.getTag();
                    questions.get(currentQuestionIndex).setSelectedAnswer(optionIndex);
                }
            }
            // If no option is selected, the question remains unattempted (selectedAnswer = -1)
        }
    }

    private void updateProgressBar() {
        if (quizProgress != null) {
            int progress = (int) (((float) (currentQuestionIndex + 1) / questions.size()) * 100);
            quizProgress.setProgress(progress);
        } else {
            Log.w(TAG, "quizProgress is null, cannot update progress");
        }
    }

    private void updateNavigationButtons() {
        previousButton.setEnabled(currentQuestionIndex > 0);
        if (currentQuestionIndex == questions.size() - 1 && !quizCompleted) {
            nextButton.setVisibility(View.GONE);
            submitButton.setVisibility(View.VISIBLE);
        } else {
            nextButton.setVisibility(View.VISIBLE);
            submitButton.setVisibility(View.GONE);
        }
    }

    private void updateUI() {
        scoreText.setText(getString(R.string.quiz_score, score, questions.size()));
    }

    private void startTimer() {
        timer = new CountDownTimer(timeRemaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemaining = millisUntilFinished;
                long minutes = millisUntilFinished / 60000;
                long seconds = (millisUntilFinished % 60000) / 1000;
                timerText.setText(getString(R.string.quiz_timer, String.format("%02d:%02d", minutes, seconds)));
            }

            @Override
            public void onFinish() {
                Toast.makeText(QuizActivity.this, "Time's up!", Toast.LENGTH_SHORT).show();
                finishQuiz();
            }
        }.start();
    }

    private void finishQuiz() {
        if (quizCompleted) return;

        quizCompleted = true;
        if (timer != null) {
            timer.cancel();
        }

        calculateScore();
        
        // Track quiz completion and score for progress
        ProgressTracker.incrementQuizzesTaken(this);
        ProgressTracker.trackQuizScore(this, score, questions.size());
        
        showResults();
    }

    private void calculateScore() {
        score = 0;
        for (QuizQuestion question : questions) {
            if (question.isCorrect()) {
                score++;
            }
        }
    }

    private void showResults() {
        quizContent.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
        reviewLayout.setVisibility(View.GONE);

        int percentage = (int) (((float) score / questions.size()) * 100);
        resultScoreText.setText(getString(R.string.quiz_score, score, questions.size()));
        resultPercentageText.setText(percentage + "%");

        if (percentage >= 80) {
            resultMessage.setText("Excellent work! 🎉");
            resultMessage.setTextColor(getResources().getColor(R.color.success_color));
        } else if (percentage >= 60) {
            resultMessage.setText("Good job! Keep practicing! 👍");
            resultMessage.setTextColor(getResources().getColor(R.color.warning_color));
        } else {
            resultMessage.setText("Keep studying and try again! 📚");
            resultMessage.setTextColor(getResources().getColor(R.color.error_color));
        }
        
        WeakTopicManager weakTopicManager = new WeakTopicManager(this);
        
        // Mark weak topics from incorrect answers (if score < 70%)
        if (percentage < 70) {
            weakTopicManager.markWeakTopicsFromQuiz(subjectName, documentTitle, questions, score, questions.size());
            
            int incorrectCount = questions.size() - score;
            if (incorrectCount > 0) {
                // Show message about weak topics
                String weakTopicsMsg = incorrectCount + " topic" + (incorrectCount > 1 ? "s" : "") + 
                                      " marked for revision. Review them in the subject page.";
                Toast.makeText(this, weakTopicsMsg, Toast.LENGTH_LONG).show();
            }
        } else {
            // If score is good (>= 70%), mark correct answers as improved
            for (QuizQuestion question : questions) {
                if (question.isCorrect()) {
                    String topic = extractTopicFromQuestion(question.getQuestion());
                    if (topic != null && !topic.trim().isEmpty()) {
                        // Find document ID
                        String documentId = null;
                        if (documentTitle != null && !documentTitle.isEmpty()) {
                            DocumentManager docManager = DocumentManager.getInstance(this);
                            List<Document> allDocs = docManager.getAllDocuments();
                            for (Document doc : allDocs) {
                                if (doc.getDisplayName().equals(documentTitle) || 
                                    doc.getDisplayName().contains(documentTitle) ||
                                    documentTitle.contains(doc.getDisplayName())) {
                                    documentId = doc.getId();
                                    break;
                                }
                            }
                        }
                        weakTopicManager.markTopicAsImproved(topic.trim(), documentId, subjectName);
                    }
                }
            }
        }
        
        // Save quiz result to recent activity
        saveQuizToRecentActivity(score, questions.size(), percentage);
    }
    
    /**
     * Show detailed answer review
     */
    private void showReview() {
        resultLayout.setVisibility(View.GONE);
        reviewLayout.setVisibility(View.VISIBLE);
        
        // Setup RecyclerView with review adapter
        QuizReviewAdapter adapter = new QuizReviewAdapter(this, questions);
        reviewRecyclerView.setAdapter(adapter);
        reviewRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
    }
    
    /**
     * Hide review and go back to results
     */
    private void hideReview() {
        reviewLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
    }
    
    /**
     * Extract topic from question text (helper method)
     */
    private String extractTopicFromQuestion(String question) {
        if (question == null || question.isEmpty()) {
            return null;
        }
        
        // Remove question marks and common question words
        String cleaned = question.replaceAll("[?]", "").trim();
        
        // Try to extract key terms
        String[] patterns = {
            "(?i)what is (.+?)\\?",
            "(?i)explain (.+?)\\?",
            "(?i)define (.+?)\\?",
            "(?i)describe (.+?)\\?",
            "(?i)what are (.+?)\\?",
            "(?i)how does (.+?)\\?",
            "(?i)what does (.+?)\\?",
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(cleaned);
            if (m.find()) {
                String topic = m.group(1).trim();
                topic = topic.replaceAll("\\s+", " ");
                if (topic.length() > 50) {
                    topic = topic.substring(0, 50).trim();
                }
                return topic;
            }
        }
        
        // Fallback: extract first few meaningful words
        String[] words = cleaned.split("\\s+");
        if (words.length >= 2) {
            int startIdx = 0;
            String[] skipWords = {"what", "is", "are", "the", "a", "an", "how", "does", "do", "explain", "define", "describe"};
            for (int i = 0; i < words.length; i++) {
                boolean shouldSkip = false;
                for (String skip : skipWords) {
                    if (words[i].equalsIgnoreCase(skip)) {
                        shouldSkip = true;
                        break;
                    }
                }
                if (!shouldSkip) {
                    startIdx = i;
                    break;
                }
            }
            
            StringBuilder topic = new StringBuilder();
            int wordCount = 0;
            for (int i = startIdx; i < words.length && wordCount < 4; i++) {
                if (words[i].length() > 2) {
                    if (topic.length() > 0) topic.append(" ");
                    topic.append(words[i]);
                    wordCount++;
                }
            }
            
            if (topic.length() > 0) {
                return topic.toString();
            }
        }
        
        if (cleaned.length() > 30) {
            return cleaned.substring(0, 30).trim() + "...";
        }
        return cleaned.trim();
    }
    
    /**
     * Save quiz result to recent activity for user profile
     */
    private void saveQuizToRecentActivity(int score, int total, int percentage) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("RecentActivity", MODE_PRIVATE);
            
            // Get existing activities (stored as JSON array)
            String activitiesJson = prefs.getString("activities", "[]");
            org.json.JSONArray activities = new org.json.JSONArray(activitiesJson);
            
            // Create questions array with all details
            org.json.JSONArray questionsArray = new org.json.JSONArray();
            for (QuizQuestion question : questions) {
                org.json.JSONObject questionObj = new org.json.JSONObject();
                questionObj.put("question", question.getQuestion());
                
                // Store all options
                org.json.JSONArray optionsArray = new org.json.JSONArray();
                for (String option : question.getOptions()) {
                    optionsArray.put(option);
                }
                questionObj.put("options", optionsArray);
                questionObj.put("correctAnswer", question.getCorrectAnswer());
                questionObj.put("selectedAnswer", question.getSelectedAnswer());
                questionObj.put("isCorrect", question.isCorrect());
                
                questionsArray.put(questionObj);
            }
            
            // Create new activity entry
            org.json.JSONObject activity = new org.json.JSONObject();
            activity.put("type", "quiz");
            activity.put("subject", subjectName);
            activity.put("title", documentTitle != null ? documentTitle : subjectName);
            activity.put("score", score);
            activity.put("total", total);
            activity.put("percentage", percentage);
            activity.put("timestamp", System.currentTimeMillis());
            activity.put("date", new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                .format(new java.util.Date()));
            activity.put("questions", questionsArray);  // Store full quiz data
            
            // Add to beginning of array (most recent first)
            activities.put(0, activity);
            
            // Keep only last 20 activities
            if (activities.length() > 20) {
                activities.remove(activities.length() - 1);
            }
            
            // Save back to preferences
            prefs.edit().putString("activities", activities.toString()).apply();
            
            Log.d(TAG, "Saved quiz to recent activity: " + score + "/" + total + " with " + questions.size() + " questions");
        } catch (Exception e) {
            Log.e(TAG, "Error saving quiz to recent activity", e);
        }
    }

    private void restartQuiz() {
        currentQuestionIndex = 0;
        score = 0;
        timeRemaining = 600000;
        quizCompleted = false;

        for (QuizQuestion question : questions) {
            question.setSelectedAnswer(-1);
        }

        quizContent.setVisibility(View.VISIBLE);
        resultLayout.setVisibility(View.GONE);
        startQuiz();
    }
    
    /**
     * Check if quiz generation is in progress for this subject
     */
    private boolean isQuizGenerationInProgress() {
        try {
            if (subjectName == null || subjectName.isEmpty()) {
                return false;
            }
            
            android.content.SharedPreferences prefs = getSharedPreferences("quiz_generation", MODE_PRIVATE);
            boolean isGenerating = prefs.getBoolean("generating_" + subjectName, false);
            
            // If flag is stuck for more than 90 seconds, clear it automatically
            if (isGenerating) {
                long lastGenTime = prefs.getLong("gen_start_time_" + subjectName, 0);
                long currentTime = System.currentTimeMillis();
                if (lastGenTime > 0 && (currentTime - lastGenTime) > 90000) { // 90 seconds
                    // Clear stuck flag
                    prefs.edit()
                        .putBoolean("generating_" + subjectName, false)
                        .remove("gen_start_time_" + subjectName)
                        .apply();
                    return false;
                }
            }
            
            return isGenerating;
        } catch (Exception e) {
            Log.e(TAG, "Error checking generation status", e);
            return false; // If error, assume not generating
        }
    }
    
    /**
     * Show a message that quiz is being generated
     */
    private void showGeneratingQuizMessage() {
        // Hide quiz content
        quizContent.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
        
        // Show generating message
        resultMessage.setText("🤖 Generating quiz questions...");
        resultMessage.setTextColor(getResources().getColor(R.color.primary_blue));
        resultScoreText.setText("Please wait while AI creates quiz questions from your uploaded PDFs.");
        resultPercentageText.setText("This may take 20-30 seconds. You'll be notified when ready!");
        
        // Hide retry button, show back button
        retakeButton.setVisibility(View.GONE);
        backToMainButton.setText("Go Back");
        
        Toast.makeText(this, "Quiz questions are being generated. Please check back in a moment!", Toast.LENGTH_LONG).show();
    }

    /**
     * Show message that no quizzes are available yet
     */
    private void showNoQuizzesAvailable() {
        // Hide quiz content
        quizContent.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
        
        // Show message
        resultMessage.setText("📚 No Quiz Questions Yet");
        resultMessage.setTextColor(getResources().getColor(R.color.primary_blue));
        resultScoreText.setText("Upload PDFs in any subject to generate quiz questions.");
        resultPercentageText.setText("Once you upload documents, AI will automatically create quizzes for you!");
        
        // Hide retry button, show back button
        retakeButton.setVisibility(View.GONE);
        backToMainButton.setText("Go Back");
        backToMainButton.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, "Upload some PDFs first to generate quiz questions!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}