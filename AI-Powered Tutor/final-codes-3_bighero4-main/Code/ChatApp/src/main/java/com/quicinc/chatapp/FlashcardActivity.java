package com.quicinc.chatapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class FlashcardActivity extends AppCompatActivity {

    private TextView frontText;
    private TextView backText;
    private LinearLayout frontLayout;
    private LinearLayout backLayout;
    private TextView cardCounter;
    private TextView subjectNameTextView;
    // Removed difficulty buttons - no longer needed
    private Button previousButton;
    private Button nextButton;
    // Removed difficulty layout - no longer needed

    private List<Flashcard> flashcards;
    private int currentCardIndex = 0;
    private boolean isShowingFront = true;

    private String subjectName;
    private android.os.Handler flashcardCheckHandler;
    private Runnable flashcardCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);

        // Get subject name from intent
        subjectName = getIntent().getStringExtra("subject");
        if (subjectName == null) {
            subjectName = "General";
        }

        initializeViews();
        
        // Check if flashcard generation is in progress
        // Only check for document-specific mode
        String documentTitle = getIntent().getStringExtra("document_title");
        if (documentTitle != null && !documentTitle.isEmpty() && isFlashcardGenerationInProgress()) {
            showGeneratingFlashcardMessage();
            return;
        }
        
        initializeFlashcards();
        setupClickListeners();
        displayCurrentCard();
        
        // Start polling for new flashcards if we don't have any yet
        if (flashcards.isEmpty()) {
            startFlashcardPolling();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload flashcards when activity resumes (in case new ones were generated)
        reloadFlashcardsIfNeeded();
        
        // Restart polling if we still don't have flashcards
        if (flashcards.isEmpty()) {
            startFlashcardPolling();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop polling when activity pauses to save battery
        stopFlashcardPolling();
    }

    private void startFlashcardPolling() {
        if (flashcardCheckHandler != null) {
            return; // Already polling
        }
        
        flashcardCheckHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        flashcardCheckRunnable = new Runnable() {
            @Override
            public void run() {
                reloadFlashcardsIfNeeded();
                
                // Keep polling every 3 seconds if we still don't have flashcards
                if (flashcards.isEmpty() && flashcardCheckHandler != null) {
                    flashcardCheckHandler.postDelayed(this, 3000);
                } else {
                    // Stop polling once we have flashcards
                    stopFlashcardPolling();
                }
            }
        };
        
        // Start polling after 2 seconds
        flashcardCheckHandler.postDelayed(flashcardCheckRunnable, 2000);
        android.util.Log.i("FlashcardActivity", "Started polling for new flashcards");
    }

    private void stopFlashcardPolling() {
        if (flashcardCheckHandler != null && flashcardCheckRunnable != null) {
            flashcardCheckHandler.removeCallbacks(flashcardCheckRunnable);
            flashcardCheckHandler = null;
            flashcardCheckRunnable = null;
            android.util.Log.i("FlashcardActivity", "Stopped polling for flashcards");
        }
    }

    private void reloadFlashcardsIfNeeded() {
        try {
            List<Flashcard> newFlashcards = new ArrayList<>();
            String documentTitle = getIntent().getStringExtra("document_title");
            boolean isInteractiveMode = !getIntent().hasExtra("subject");
            
            if (isInteractiveMode) {
                newFlashcards = FlashcardBank.getInstance().getRandomFlashcards(10);
            } else if (documentTitle != null && !documentTitle.isEmpty()) {
                newFlashcards = FlashcardBank.getInstance().getFlashcardsForDocument(subjectName, documentTitle);
            } else {
                newFlashcards = FlashcardBank.getInstance().getFlashcardsForSubject(subjectName);
            }
            
            // Only reload if we found new flashcards
            // Check if content changed by comparing first flashcard's front text
            boolean contentChanged = flashcards.isEmpty() || 
                                   newFlashcards.size() != flashcards.size() ||
                                   !newFlashcards.get(0).getFront().equals(flashcards.get(0).getFront());
            
            if (!newFlashcards.isEmpty() && contentChanged) {
                flashcards = newFlashcards;
                currentCardIndex = 0;
                isShowingFront = true;
                displayCurrentCard();
                
                // Update UI to show flashcards are now available
                if (previousButton != null && nextButton != null) {
                    previousButton.setEnabled(true);
                    nextButton.setEnabled(true);
                }
                
                android.util.Log.i("FlashcardActivity", "Reloaded " + flashcards.size() + " flashcards");
                Toast.makeText(this, "Loaded " + flashcards.size() + " flashcards!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("FlashcardActivity", "Error reloading flashcards", e);
        }
    }

    private void initializeViews() {
        try {
            frontText = (TextView) findViewById(R.id.front_text);
            backText = (TextView) findViewById(R.id.back_text);
            frontLayout = (LinearLayout) findViewById(R.id.front_layout);
            backLayout = (LinearLayout) findViewById(R.id.back_layout);
            cardCounter = (TextView) findViewById(R.id.card_counter);
            subjectNameTextView = (TextView) findViewById(R.id.flashcard_subject_name);
            // Removed difficulty button initialization - no longer in layout
            previousButton = (Button) findViewById(R.id.previous_button);
            nextButton = (Button) findViewById(R.id.next_button);
            // Removed difficulty layout initialization - no longer in layout
        } catch (Exception e) {
            // Handle view initialization errors
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeFlashcards() {
        flashcards = new ArrayList<>();
        
        try {
            // Initialize FlashcardBank
            FlashcardBank.getInstance().init(this);
            
            // Get document title if passed from SubjectDetailActivity
            String documentTitle = getIntent().getStringExtra("document_title");
            
            // Check if this is interactive mode (launched from homepage)
            boolean isInteractiveMode = !getIntent().hasExtra("subject");
            
            if (isInteractiveMode) {
                // Interactive mode: Load 10 random flashcards from all generated flashcards
                flashcards = FlashcardBank.getInstance().getRandomFlashcards(10);
                
                if (flashcards.isEmpty()) {
                    showNoFlashcardsAvailable();
                    return;
                }
            } else if (documentTitle != null && !documentTitle.isEmpty()) {
                // Document-specific mode: Load flashcards only for this document
                flashcards = FlashcardBank.getInstance().getFlashcardsForDocument(subjectName, documentTitle);
                
                if (flashcards.isEmpty()) {
                    // No generated flashcards for this document - show waiting message
                    showNoFlashcardsAvailable();
                    return;
                }
            } else {
                // Subject mode: Load all flashcards for this subject
                flashcards = FlashcardBank.getInstance().getFlashcardsForSubject(subjectName);
                
                if (flashcards.isEmpty()) {
                    // No generated flashcards for this subject - show waiting message
                    showNoFlashcardsAvailable();
                    return;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("FlashcardActivity", "Error loading flashcards", e);
            showNoFlashcardsAvailable();
        }
    }
    
    private void showNoFlashcardsAvailable() {
        frontText.setText("No Flashcards Yet");
        backText.setText("Upload some PDFs to generate flashcards!");
        cardCounter.setText("0 / 0");
        // Removed difficulty layout visibility - no longer in layout
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
    }
    
    private void loadHardcodedFlashcards() {
        // Load flashcards based on subject
        switch (subjectName.toLowerCase()) {
            case "math":
            case "mathematics":
                loadMathFlashcards();
                break;
            case "science":
            case "physics":
                loadScienceFlashcards();
                break;
            case "history":
                loadHistoryFlashcards();
                break;
            case "english":
            case "literature":
                loadEnglishFlashcards();
                break;
            case "biology":
                loadBiologyFlashcards();
                break;
            case "chemistry":
                loadChemistryFlashcards();
                break;
            case "dsa":
            case "data structures":
            case "algorithms":
            case "data structures and algorithms":
                loadDSAFlashcards();
                break;
            case "osn":
            case "operating systems":
            case "networking":
            case "operating systems and networking":
                loadOSNFlashcards();
                break;
            case "probability":
            case "statistics":
            case "probability and statistics":
                loadProbabilityStatisticsFlashcards();
                break;
            case "automata":
            case "automata theory":
            case "finite automata":
            case "formal languages":
                loadAutomataFlashcards();
                break;
            case "data and application":
            case "database management":
            case "web development":
            case "application design":
                loadDataApplicationFlashcards();
                break;
            default:
                loadGeneralFlashcards();
                break;
        }
    }

    private void loadMathFlashcards() {
        flashcards.add(new Flashcard(
            "What is the Pythagorean theorem?",
            "a² + b² = c²\n\nIn a right triangle, the square of the hypotenuse equals the sum of squares of the other two sides.",
            "Math"
        ));
        
        flashcards.add(new Flashcard(
            "What is the quadratic formula?",
            "x = (-b ± √(b² - 4ac)) / 2a\n\nUsed to solve quadratic equations of the form ax² + bx + c = 0",
            "Math"
        ));
        
        flashcards.add(new Flashcard(
            "What is the derivative of sin(x)?",
            "cos(x)\n\nThe derivative of sine is cosine.",
            "Math"
        ));

        flashcards.add(new Flashcard(
            "What is the integral of x²?",
            "x³/3 + C\n\nWhere C is the constant of integration.",
            "Math"
        ));
    }

    private void loadScienceFlashcards() {
        flashcards.add(new Flashcard(
            "What is Newton's Second Law?",
            "F = ma\n\nForce equals mass times acceleration.",
            "Science"
        ));
        
        flashcards.add(new Flashcard(
            "What is the speed of light?",
            "299,792,458 m/s\n\nThis is the speed of light in a vacuum, often denoted as 'c'.",
            "Science"
        ));

        flashcards.add(new Flashcard(
            "What is gravity?",
            "A fundamental force that attracts objects with mass toward each other.\n\nOn Earth, it accelerates objects at 9.8 m/s².",
            "Science"
        ));
    }

    private void loadHistoryFlashcards() {
        flashcards.add(new Flashcard(
            "When did World War II end?",
            "September 2, 1945\n\nJapan formally surrendered aboard the USS Missouri in Tokyo Bay.",
            "History"
        ));
        
        flashcards.add(new Flashcard(
            "Who was the first President of the United States?",
            "George Washington\n\nHe served from 1789 to 1797 and established many presidential precedents.",
            "History"
        ));

        flashcards.add(new Flashcard(
            "What year did the Berlin Wall fall?",
            "1989\n\nThe fall of the Berlin Wall on November 9, 1989, marked the beginning of German reunification.",
            "History"
        ));
    }

    private void loadEnglishFlashcards() {
        flashcards.add(new Flashcard(
            "What is a metaphor?",
            "A figure of speech that directly compares two unlike things without using 'like' or 'as'.\n\nExample: 'Life is a journey'",
            "English"
        ));
        
        flashcards.add(new Flashcard(
            "What is alliteration?",
            "The repetition of initial consonant sounds in successive words.\n\nExample: 'Peter Piper picked a peck of pickled peppers'",
            "English"
        ));

        flashcards.add(new Flashcard(
            "What is a haiku?",
            "A traditional Japanese poem with three lines following a 5-7-5 syllable pattern.\n\nOften captures a moment in nature.",
            "English"
        ));
    }

    private void loadBiologyFlashcards() {
        flashcards.add(new Flashcard(
            "What is photosynthesis?",
            "The process by which plants convert sunlight, carbon dioxide, and water into glucose and oxygen.\n\n6CO₂ + 6H₂O + light → C₆H₁₂O₆ + 6O₂",
            "Biology"
        ));
        
        flashcards.add(new Flashcard(
            "What is DNA?",
            "Deoxyribonucleic acid - the molecule that carries genetic information in living organisms.\n\nStructured as a double helix with four nucleotide bases: A, T, G, C.",
            "Biology"
        ));

        flashcards.add(new Flashcard(
            "What is mitosis?",
            "The process of cell division that produces two identical diploid cells from one parent cell.\n\nPhases: Prophase, Metaphase, Anaphase, Telophase.",
            "Biology"
        ));
    }

    private void loadChemistryFlashcards() {
        flashcards.add(new Flashcard(
            "What is the chemical formula for water?",
            "H₂O\n\nTwo hydrogen atoms bonded to one oxygen atom.",
            "Chemistry"
        ));
        
        flashcards.add(new Flashcard(
            "What is Avogadro's number?",
            "6.022 × 10²³\n\nThe number of particles (atoms, molecules) in one mole of a substance.",
            "Chemistry"
        ));

        flashcards.add(new Flashcard(
            "What is the periodic table?",
            "A systematic arrangement of chemical elements ordered by atomic number.\n\nElements with similar properties are grouped in columns called families.",
            "Chemistry"
        ));
    }

    private void loadDSAFlashcards() {
        flashcards.add(new Flashcard(
            "What is Big O Notation and Why is it Important?",
            "Big O notation describes an algorithm's worst-case time complexity as input size grows. It helps compare algorithm efficiency, predict performance on large datasets, and identify bottlenecks.\n\nUnderstanding Big O allows developers to choose optimal algorithms for specific problems and system constraints.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain the Difference Between Array and Linked List",
            "Arrays store elements in contiguous memory with O(1) random access but O(n) insertion/deletion. Linked lists use scattered memory with O(n) access but O(1) insertion/deletion if position is known.\n\nArrays are cache-friendly; linked lists provide dynamic sizing without reallocation.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is a Stack and Name its Applications",
            "A stack follows LIFO (Last In First Out) principle.\n\nApplications include function call management, undo/redo functionality, expression evaluation, backtracking algorithms, and browser history. Stacks efficiently handle problems requiring reversal or nested structures.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain Hash Tables and Hash Collisions",
            "Hash tables use hash functions to map keys to array indices for O(1) average lookup. Collisions occur when different keys hash to same index.\n\nHandling methods include chaining (linked lists) and open addressing (probing). Quality hash functions minimize collisions.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is Binary Search and Its Prerequisites?",
            "Binary search repeatedly divides sorted data in half, achieving O(log n) complexity. It requires data to be sorted beforehand.\n\nIt's more efficient than linear search for large datasets but needs comparable elements. Works only on indexed, ordered collections.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain the Difference Between DFS and BFS",
            "DFS (Depth-First Search) uses a stack, explores deeply before backtracking. BFS (Breadth-First Search) uses a queue, explores level by level.\n\nDFS uses less memory but may find longer paths; BFS finds shortest paths in unweighted graphs. Applications differ by problem needs.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is a Binary Search Tree?",
            "A BST is a binary tree where left child values are smaller and right child values are larger than parent. Average operations (search, insert, delete) are O(log n).\n\nUnbalanced trees degrade to O(n). Used for sorted data storage and range queries.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain Recursion and Base Case Importance",
            "Recursion solves problems by having functions call themselves with simpler inputs. Base cases prevent infinite recursion by defining stopping conditions.\n\nWithout proper base cases, stack overflow occurs. Recursion is elegant for problems with recursive structure but uses extra memory.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is Dynamic Programming?",
            "Dynamic programming optimizes problems with overlapping subproblems and optimal substructure by storing computed results. Two approaches: memoization (top-down) and tabulation (bottom-up).\n\nReduces exponential time to polynomial time. Examples: Fibonacci, knapsack, longest common subsequence.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain Sorting Algorithms and Their Time Complexities",
            "Common sorts include bubble (O(n²)), merge (O(n log n)), quick (O(n log n) avg), and heap (O(n log n)).\n\nBubble is simple but slow; merge is stable but needs extra space; quick is fastest average. Choice depends on data characteristics and stability requirements.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is a Graph and How is it Represented?",
            "A graph consists of vertices connected by edges (directed/undirected, weighted/unweighted). Representations include adjacency matrix (O(1) lookup, O(v²) space) and adjacency list (saves space, efficient traversal).\n\nChoice depends on graph density and operation frequency.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain the Concept of Time and Space Complexity Trade-offs",
            "Algorithms often exchange time for space or vice versa. Memoization uses extra memory to reduce computation time. Compression trades CPU cycles for storage.\n\nDevelopers choose based on constraints: memory-limited systems prioritize time optimization; compute-limited systems prioritize space efficiency.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is a Priority Queue?",
            "A priority queue stores elements with associated priorities, always returning highest/lowest priority element first. Implemented using heaps for O(log n) insertion and deletion.\n\nApplications include Dijkstra's algorithm, task scheduling, and huffman coding. Differs from regular queues (FIFO).",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain Greedy Algorithms",
            "Greedy algorithms make locally optimal choices hoping to find global optimum. Simple and efficient but don't guarantee optimal solutions.\n\nExamples: activity selection, huffman coding, Dijkstra's. Works well for problems with greedy choice property and optimal substructure.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is a Trie Data Structure?",
            "A trie is a tree storing strings where each node represents a character. Provides O(m) lookup for string of length m, independent of dataset size.\n\nUsed for autocomplete, spell-checking, and IP routing. Trades space efficiency for fast prefix-based searches.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain Backtracking and Its Applications",
            "Backtracking explores all potential solutions, abandoning branches that violate constraints. Uses recursion with state management.\n\nApplications: N-Queens, sudoku solving, maze solving, permutations. Efficient for constraint satisfaction problems where search space can be pruned effectively.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is Graph Coloring?",
            "Graph coloring assigns minimum colors to vertices so adjacent vertices have different colors. Useful for scheduling, register allocation, and map coloring.\n\nNP-complete for general graphs but solvable for specific types. Greedy approaches provide reasonable approximations.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain Divide and Conquer Strategy",
            "Divide and conquer breaks problems into smaller subproblems, solves independently, then combines results. Examples: merge sort, quick sort, binary search.\n\nOften achieves O(n log n) complexity. Requires problem decomposition ability and recombination strategy.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "What is Topological Sorting?",
            "Topological sorting orders directed acyclic graph (DAG) vertices linearly where edges go from earlier to later vertices. Used for task scheduling, dependency resolution, and course prerequisites.\n\nComputed via DFS or Kahn's algorithm. Not applicable to cyclic graphs.",
            "DSA"
        ));

        flashcards.add(new Flashcard(
            "Explain the Concept of Space Complexity",
            "Space complexity measures memory required by an algorithm beyond input. Includes auxiliary space for variables and data structures. Analyzed similarly to time complexity with Big O notation.\n\nConcerns: recursion depth (stack overflow), large allocations, memory leaks in production systems.",
            "DSA"
        ));
    }

    private void loadOSNFlashcards() {
        flashcards.add(new Flashcard(
            "What is an Operating System and Its Main Functions?",
            "An OS manages hardware and software resources, providing interface between user and hardware. Main functions: process management, memory management, file system management, device management, and security.\n\nIt enables multitasking, resource sharing, and protects system stability while providing user-friendly environment.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain Process vs Thread",
            "A process is independent program instance with separate memory space and resources. Threads share process memory but have independent execution.\n\nCreating threads is faster and cheaper than processes; context switching is quicker. But threads require synchronization to avoid data corruption and race conditions.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is Process Scheduling and Scheduling Algorithms?",
            "Process scheduling determines which process runs on CPU at given time. Algorithms include FCFS (simple, starvation), SJF (optimal average wait, preemption issues), Round Robin (fair, time slice based).\n\nChoice impacts throughput, turnaround time, and system fairness. Priority scheduling and multilevel queues provide additional options.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain Deadlock and Its Four Necessary Conditions",
            "Deadlock occurs when processes wait indefinitely for resources held by others. Four necessary conditions: mutual exclusion, hold and wait, no preemption, and circular wait.\n\nHandled via prevention (eliminate conditions), avoidance (banker's algorithm), detection, or recovery. Prevention is most practical approach.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is Virtual Memory?",
            "Virtual memory allows processes to use memory larger than physical RAM using disk storage. Uses paging or segmentation to map virtual to physical addresses.\n\nEnables multitasking and process isolation. Trade-off: extends capacity but slower than physical memory due to disk access overhead.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain Cache Memory and Its Hierarchy",
            "Cache stores frequently accessed data closer to CPU for faster access. Hierarchy: L1 (smallest, fastest, on-core), L2 (larger, slower), L3 (largest, shared).\n\nUses locality principles: temporal (reuse recent data) and spatial (nearby data). Reduces memory access time significantly but adds complexity.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is Semaphore and Its Types?",
            "Semaphore is synchronization primitive using counter controlling resource access. Binary semaphore (0/1) works like mutex for mutual exclusion.\n\nCounting semaphore manages multiple identical resources. Prevents race conditions and enables inter-process communication but can cause deadlock if misused.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain Mutual Exclusion and Critical Section",
            "Mutual exclusion ensures only one process accesses shared resource simultaneously. Critical section is code accessing shared resources.\n\nSolutions: locks, semaphores, monitors. Prevents data corruption and race conditions but can reduce performance through serialization and potential deadlocks.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is Memory Paging?",
            "Paging divides physical memory into fixed-size frames and virtual memory into pages. Maps virtual addresses to physical addresses via page table.\n\nEliminates external fragmentation and allows non-contiguous memory allocation. Trade-offs: internal fragmentation, page table overhead, and TLB requirement for efficiency.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain OSI Model Layers",
            "OSI has 7 layers: Physical (bits), Data Link (frames), Network (packets/IP), Transport (TCP/UDP), Session, Presentation, Application.\n\nEach layer adds headers (encapsulation) and communicates with peers. Provides standardized communication framework but TCP/IP model (4 layers) is more practical in implementation.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is TCP/IP Model and Its Layers?",
            "TCP/IP model has 4 layers: Link (hardware), Internet (IP), Transport (TCP/UDP), Application. More practical than OSI, used in actual internet.\n\nTCP ensures reliable delivery with error checking; UDP is faster but unreliable. Foundation of modern networking and web communication.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain IP Addressing and Subnetting",
            "IP addresses identify devices on networks. IPv4: 32-bit (xxx.xxx.xxx.xxx), IPv6: 128-bit. Subnetting divides networks using subnet masks for efficient routing and security.\n\nCIDR notation simplifies representation. Enables hierarchical addressing, reduces routing table size, and improves network organization.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is DNS and How Does It Work?",
            "DNS translates domain names to IP addresses. Client queries recursive resolver, which queries root nameserver, TLD nameserver, then authoritative nameserver.\n\nResults cached at each level for efficiency. Essential for web accessibility; human-readable names mapped to machine addresses hierarchically.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain TCP Three-Way Handshake",
            "TCP connection establishment: SYN (client sends), SYN-ACK (server responds), ACK (client acknowledges). Ensures both parties ready for communication and establishes sequence numbers.\n\nAfter handshake, data transmission begins. FIN sequence terminates connection. Provides reliable connection foundation.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is Congestion Control in Networks?",
            "Congestion occurs when network traffic exceeds capacity. TCP uses congestion control: slow start (exponential growth), congestion avoidance (linear), fast recovery.\n\nMonitors packet loss and round-trip time. Prevents network collapse and ensures fair bandwidth sharing among flows.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain Routing and Routing Protocols",
            "Routing forwards packets to destinations through networks. Static routing uses fixed paths; dynamic routing adjusts based on conditions.\n\nProtocols: RIP (distance vector), OSPF (link state), BGP (exterior). Trade-offs between simplicity, overhead, convergence time, and routing optimality.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is NAT (Network Address Translation)?",
            "NAT maps private IP addresses to public IPs for internet communication. Enables multiple devices sharing single public IP. Provides security by hiding internal network structure.\n\nTrade-offs: complicates peer-to-peer communication, increases latency slightly, not scalable for IPv6.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain File Systems and Inode Structure",
            "File systems organize data on storage devices. Inodes store file metadata (permissions, ownership, timestamps) and block pointers. Allocates disk space via contiguous, linked, or indexed allocation.\n\nTrade-offs: fragmentation, access speed, reliability. Examples: FAT32, NTFS, ext4, APFS.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "What is Interrupt Handling?",
            "Interrupts notify CPU of events (hardware/software) requiring immediate attention. CPU saves current state, services interrupt, restores state. Priority-based handling manages multiple interrupts.\n\nEssential for responsive systems, I/O operations, and multitasking but adds complexity and context-switching overhead.",
            "OSN"
        ));

        flashcards.add(new Flashcard(
            "Explain Socket Programming and Socket Types?",
            "Sockets enable inter-process communication over networks. Types: TCP sockets (stream, reliable, connection-oriented), UDP sockets (datagram, unreliable, connectionless).\n\nClient-server model uses bind, listen, connect, accept, send, receive. Foundation for network applications: web servers, email, streaming.",
            "OSN"
        ));
    }

    private void loadProbabilityStatisticsFlashcards() {
        flashcards.add(new Flashcard(
            "What is Probability?",
            "Measures likelihood (0 to 1).\n\n• P(A) = favorable/total\n• P(A or B) = P(A) + P(B) - P(A∩B)\n• P(not A) = 1 - P(A)",
            "Probability"
        ));

        flashcards.add(new Flashcard(
            "What is Conditional Probability?",
            "Probability of A given B occurred.\n\nP(A|B) = P(A∩B) / P(B)\n\nBayes: P(A|B) = P(B|A) × P(A) / P(B)\n\nUsed in diagnosis, ML, filtering.",
            "Probability"
        ));

        flashcards.add(new Flashcard(
            "Discrete vs Continuous Distributions?",
            "Discrete: Countable outcomes (binomial, Poisson)\nContinuous: Uncountable (normal, exponential)\n\nDiscrete uses PMF, Continuous uses PDF\nDiscrete sums, Continuous integrates",
            "Probability"
        ));

        flashcards.add(new Flashcard(
            "What is Normal Distribution?",
            "Bell-shaped, symmetric curve.\nMean (μ) and std dev (σ)\n\n68% within 1σ\n95% within 2σ\n99.7% within 3σ\n\nUsed in hypothesis testing, CI.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Standard Deviation?",
            "Measures data spread around mean.\n\nVariance: σ² = Σ(x - μ)² / n\nStd Dev: σ = √variance\n\nHigher values = more dispersion\nUsed in risk assessment, quality control.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Hypothesis Testing?",
            "Tests if evidence supports null hypothesis (H₀).\n\nType I: Reject true H₀ (false positive)\nType II: Accept false H₀ (false negative)\n\nα controls Type I, Power controls Type II.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is P-value?",
            "Probability of observing data if H₀ true.\n\nLower p-value = stronger evidence against H₀\nTypical threshold: 0.05\n\nIf p < 0.05, reject H₀",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What are Confidence Intervals?",
            "Range estimate for population parameter.\n\n95% CI = 95% probability parameter in interval\n\nFormula: estimate ± margin of error\nWider = more uncertainty",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Sampling Distribution and Central Limit Theorem?",
            "Sampling distribution is distribution of sample statistic across repeated samples. Central Limit Theorem states sampling distribution of means approaches normal distribution regardless of population distribution (with large n).\n\nEnables using normal distribution for inference even with non-normal populations. Foundation for hypothesis testing and confidence intervals.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Correlation?",
            "Measures relationship strength (-1 to 1).\n\n1 = perfect positive\n-1 = perfect negative\n0 = no relationship\n\nCorrelation ≠ Causation",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Regression?",
            "Models relationship between variables.\n\nLinear: y = mx + b\nFinds best-fit line\n\nR² measures strength\nUsed for prediction, forecasting.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Binomial Distribution?",
            "Models n independent trials, probability p.\n\nP(X=k) = C(n,k) × p^k × (1-p)^(n-k)\n\nUsed for pass/fail scenarios\nApproximates normal for large n.",
            "Probability"
        ));

        flashcards.add(new Flashcard(
            "What is Poisson Distribution?",
            "Models event count in fixed interval.\n\nP(X=k) = (e^-λ × λ^k) / k!\n\nFor rare events: accidents, requests\nRate λ constant, events independent.",
            "Probability"
        ));

        flashcards.add(new Flashcard(
            "Explain ANOVA (Analysis of Variance)?",
            "ANOVA tests if means across multiple groups significantly differ. Compares between-group and within-group variance using F-statistic. Null: all groups equal means.\n\nDetermines if differences statistically significant or due to chance. Used for comparing treatment effects, product variants, or multiple conditions simultaneously.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Chi-Square Test?",
            "Chi-square test examines relationship between categorical variables. χ² = Σ(observed - expected)² / expected. Tests independence and goodness of fit.\n\nNull: no relationship between variables. Used in market research, genetics, quality control. Assumes expected frequencies ≥ 5. Important for categorical data analysis.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "Explain Probability Density Function (PDF) and Cumulative Distribution Function (CDF)?",
            "PDF describes probability distribution of continuous variable. CDF is cumulative probability (P(X ≤ x)). PDF integrates to 1; CDF ranges 0 to 1.\n\nPDF shows likelihood of specific values; CDF shows probability up to point. Both essential for continuous probability analysis and statistical calculations.",
            "Probability"
        ));

        flashcards.add(new Flashcard(
            "What is Skewness?",
            "Measures distribution asymmetry.\n\nPositive: right-skewed tail\nNegative: left-skewed tail\n\nKurtosis: tail heaviness\nHelps identify outliers.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What are Sampling Methods?",
            "Random: equal probability\nStratified: divide into groups\nCluster: select groups\nSystematic: fixed intervals\n\nEnsures representative samples.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is MLE?",
            "Maximum Likelihood Estimation finds parameters maximizing likelihood.\n\nSteps: write likelihood, take derivative, solve\n\nProduces unbiased, efficient estimators.",
            "Statistics"
        ));

        flashcards.add(new Flashcard(
            "What is Relative Risk?",
            "Compares event probability between groups.\n\nRR = P(event|exposed) / P(event|unexposed)\n\nRR > 1: increased risk\nRR < 1: decreased risk",
            "Statistics"
        ));
    }

    private void loadAutomataFlashcards() {
        flashcards.add(new Flashcard(
            "What is Automata?",
            "Abstract mathematical model of computation with states and transitions.\n\nUsed in compiler design, pattern matching\nProvides theoretical foundation for computation limits",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "DFA vs NFA?",
            "DFA: exactly one transition per state-symbol\nNFA: zero, one, or multiple transitions\n\nDFA easier to implement\nNFA more expressive and concise",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Finite Automaton?",
            "Mathematical model with:\n• Q (states)\n• Σ (alphabet)\n• δ (transition function)\n• q₀ (initial state)\n• F (final states)",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What are Regular Expressions?",
            "Describe patterns using symbols and operators.\n\nOperators: concatenation, union, Kleene star\nEvery regex ↔ FA\nUsed in text processing, validation",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Pumping Lemma?",
            "If language L is regular, long strings can be 'pumped'.\n\nFormula: if |xyz| ≥ p and xy*z ⊆ L\nUsed to prove languages non-regular\nCannot prove regularity",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Context-Free Grammar?",
            "Variables, terminals, production rules, start symbol.\n\nRules: A → α\nGenerates context-free languages\nUsed in programming language syntax",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Pushdown Automaton?",
            "FA with additional stack memory.\n\nTransitions: read input, pop/push stack\nRecognizes context-free languages\nUsed in parsing and compiler design",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "CFG and PDA Relationship?",
            "CFG and PDA are equivalent.\n\nCFG generates languages\nPDA accepts them\nBoth recognize context-free languages",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Chomsky Hierarchy?",
            "Classifies languages by generative power:\n• Type 0: recursively enumerable\n• Type 1: context-sensitive\n• Type 2: context-free\n• Type 3: regular",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Turing Machine?",
            "Abstract computational model with tape, head, states.\n\nCan simulate any algorithm\nDetermines computability and decidability\nFoundation for complexity theory",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Halting Problem?",
            "Determine if program terminates on input.\n\nProved undecidable by contradiction\nDemonstrates computational limits\nFundamental result in computability theory",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Kleene Star?",
            "L* = {ε, L, LL, LLL, ...}\n\nGenerates all concatenations of L\nProperties: associative, idempotent\nUsed for zero-or-more repetition",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What are Epsilon Transitions?",
            "State change without consuming input.\n\nEnables NFA to move 'for free'\nSimplifies automaton design\nUseful for combining automata",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Thompson's Construction?",
            "Converts regular expression to NFA.\n\nRecursively builds NFAs for subexpressions\nLinear time complexity\nFoundation for regex engines",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is State Minimization?",
            "Reduces DFA states while preserving language.\n\nIdentifies equivalent states\nProduces minimal DFA\nImproves efficiency and memory usage",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "Regular Language Closure?",
            "Regular languages closed under:\n• Union, concatenation, Kleene star\n• Complement, intersection, difference\n\nEnables proving languages regular by composition",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "Decidable vs Undecidable?",
            "Decidable: algorithm gives yes/no for all inputs\nUndecidable: no such algorithm exists\n\nExample: balanced parentheses decidable\nHalting problem undecidable",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Subset Construction?",
            "Converts NFA to DFA systematically.\n\nEach DFA state = subset of NFA states\nCompute epsilon closure\nWorst case: exponential blowup",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Language Complement?",
            "L^c = Σ* - L (all strings not in L)\n\nRegular language complement regular\nContext-free complement not context-free\nSwap final/non-final states in DFA",
            "Automata"
        ));

        flashcards.add(new Flashcard(
            "What is Myhill-Nerode Theorem?",
            "Relates DFA minimality to language equivalence.\n\nLanguage regular iff equivalence classes finite\nBuilds minimal DFA directly\nCharacterizes regular languages",
            "Automata"
        ));
    }

    private void loadDataApplicationFlashcards() {
        flashcards.add(new Flashcard(
            "What is Data Mining?",
            "Extracts patterns and insights from large datasets.\n\nObjectives: discovery, prediction, classification\nTechniques: clustering, decision trees, neural networks\nApplications: marketing, fraud detection",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is ETL Process?",
            "Extract, Transform, Load pipeline.\n\nExtract: retrieve data from sources\nTransform: clean, validate, restructure\nLoad: store in data warehouse\nEnsures data quality and usability",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is Data Warehouse?",
            "Centralizes data from multiple sources for analysis.\n\nCharacteristics: subject-oriented, integrated, time-variant, non-volatile\nOptimized for queries, not transactions",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "OLTP vs OLAP?",
            "OLTP: Online Transaction Processing\n• Real-time transactions\n• Normalized, fast writes\n\nOLAP: Online Analytical Processing\n• Complex queries, historical data\n• Denormalized for analysis",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is NoSQL?",
            "Flexible, schema-less data storage.\n\nTypes: key-value, document, column-family, graph\nAdvantages: horizontal scaling, handles unstructured data\nTrade-offs: eventual consistency",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "Relational vs Non-Relational?",
            "Relational: tables, ACID, structured schema (SQL)\nNon-relational: flexible schema, horizontal scaling\n\nRelational: consistency, complex queries\nNoSQL: scalability, flexibility",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is Big Data (3Vs)?",
            "Characterized by:\n• Volume: massive quantities\n• Variety: diverse formats\n• Velocity: rapid generation\n\nRequires distributed systems (Hadoop, Spark)",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is MapReduce?",
            "Distributes computation across clusters.\n\nMap: processes input, produces key-value pairs\nReduce: aggregates values for each key\nFault-tolerant, scalable for massive datasets",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is Apache Spark?",
            "Distributed computing framework.\n\nUses RDDs and DataFrames\n10-100x faster than MapReduce\nSupports batch, streaming, ML, graph processing",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "Normalization vs Denormalization?",
            "Normalization: reduces redundancy, ensures consistency\nDenormalization: sacrifices normalization for performance\n\nNormalization for OLTP\nDenormalization for OLAP",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is Database Indexing?",
            "Creates structures for fast data retrieval.\n\nReduces query time from O(n) to O(log n)\nTrade-offs: slows writes, consumes storage\nTypes: primary, secondary, composite",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "JSON vs XML?",
            "JSON: compact key-value pairs, faster parsing\nXML: hierarchical tags, verbose, semantic\n\nJSON preferred for web APIs\nXML common in legacy systems",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is Data Validation?",
            "Checks data accuracy, completeness, consistency.\n\nTechniques: range checks, format validation\nPrevents garbage-in-garbage-out\nCritical for reliable analytics",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "Sharding vs Partitioning?",
            "Sharding: distributes data across multiple databases\nPartitioning: divides table within database\n\nBoth improve scalability and performance\nTrade-offs: complexity, consistency",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is API?",
            "Application Programming Interface for communication.\n\nTypes: REST, SOAP, GraphQL\nSpecifies endpoints, request/response formats\nEnables integration, microservices",
            "Application"
        ));

        flashcards.add(new Flashcard(
            "What is REST Architecture?",
            "Representational State Transfer.\n\nHTTP methods: GET, POST, PUT, DELETE\nStateless, cacheable, scalable\nResources identified by URIs",
            "Application"
        ));

        flashcards.add(new Flashcard(
            "What is Microservices?",
            "Decomposes apps into small, independent services.\n\nAdvantages: scalability, flexibility, deployment independence\nChallenges: complexity, consistency, debugging",
            "Application"
        ));

        flashcards.add(new Flashcard(
            "What is Database Replication?",
            "Copies database across multiple servers.\n\nTypes: master-slave, peer-to-peer\nBenefits: availability, fault tolerance, scalability\nTrade-offs: consistency challenges",
            "Data"
        ));

        flashcards.add(new Flashcard(
            "What is Caching?",
            "Stores frequently accessed data in fast storage.\n\nLevels: browser, CDN, application, database\nInvalidation: TTL, event-based, LRU\nImproves performance, reduces latency",
            "Application"
        ));

        flashcards.add(new Flashcard(
            "What is Data Encryption?",
            "Protects data via algorithms (AES, RSA).\n\nTypes: symmetric, asymmetric\nIn-transit (SSL/TLS), at-rest encryption\nEssential for security and compliance",
            "Data"
        ));
    }

    private void loadGeneralFlashcards() {
        flashcards.add(new Flashcard(
            "How is AI Transforming Personalized Learning?",
            "AI creates adaptive platforms that customize learning based on student pace and performance. Intelligent tutoring systems provide real-time feedback and targeted help. AI identifies knowledge gaps and recommends resources. Benefits: improved engagement and outcomes. Concerns: data privacy, algorithmic bias, and reduced teacher-student interaction.",
            "General"
        ));
        
        flashcards.add(new Flashcard(
            "What are the Ethical Challenges of AI in Education?",
            "Key challenges include algorithmic bias affecting disadvantaged students, privacy concerns with data collection, academic integrity issues with AI-generated content, and job displacement of educators. AI systems may perpetuate existing inequalities if training data is biased. Over-reliance on AI could reduce critical thinking development. Balancing innovation with fairness, transparency, and student well-being requires careful governance, ethical guidelines, and educator training for responsible implementation.",
            "General"
        ));

        flashcards.add(new Flashcard(
            "How Can AI Support Teachers and Improve Classroom Efficiency?",
            "AI automates administrative tasks like grading, attendance, and lesson planning, freeing teachers for meaningful student interaction. AI-powered tools provide teaching recommendations based on class performance data. Virtual assistants handle student queries outside class hours. However, implementation requires teacher training, infrastructure investment, and careful change management. Teachers risk job insecurity and must evolve their roles toward mentoring and critical skill development rather than content delivery.",
            "General"
        ));
    }



    private void setupClickListeners() {
        // Card click to flip
        if (frontLayout != null) {
            frontLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    flipCard();
                }
            });
        }
        
        if (backLayout != null) {
            backLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    flipCard();
                }
            });
        }

        // Navigation buttons
        if (previousButton != null) {
            previousButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPreviousCard();
                }
            });
        }

        if (nextButton != null) {
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showNextCard();
                }
            });
        }

        // Removed difficulty button click listeners - no longer in layout
    }

    private void displayCurrentCard() {
        if (flashcards.isEmpty()) return;

        Flashcard card = flashcards.get(currentCardIndex);
        frontText.setText(card.getFront());
        backText.setText(card.getBack());
        
        cardCounter.setText((currentCardIndex + 1) + " / " + flashcards.size());
        
        // Show subject name if available
        if (card.getSubject() != null && !card.getSubject().isEmpty()) {
            subjectNameTextView.setText(card.getSubject());
            subjectNameTextView.setVisibility(View.VISIBLE);
        } else {
            subjectNameTextView.setVisibility(View.GONE);
        }
        
        // Increment flashcard review counter
        ProgressTracker.incrementFlashcardsReviewed(this);
        
        // Show front side
        isShowingFront = true;
        frontLayout.setVisibility(View.VISIBLE);
        backLayout.setVisibility(View.GONE);
        // Removed difficulty layout visibility - no longer in layout
        
        updateNavigationButtons();
    }



    private void flipCard() {
        if (isShowingFront) {
            // Show back of card
            if (frontLayout != null) frontLayout.setVisibility(View.GONE);
            if (backLayout != null) backLayout.setVisibility(View.VISIBLE);
            // Removed difficulty layout - no longer in layout
            isShowingFront = false;
        } else {
            // Show front of card
            if (backLayout != null) backLayout.setVisibility(View.GONE);
            // Removed difficulty layout - no longer in layout
            if (frontLayout != null) frontLayout.setVisibility(View.VISIBLE);
            isShowingFront = true;
        }
    }

    private void showPreviousCard() {
        if (currentCardIndex > 0) {
            currentCardIndex--;
            displayCurrentCard();
        }
    }

    private void showNextCard() {
        if (currentCardIndex < flashcards.size() - 1) {
            currentCardIndex++;
            displayCurrentCard();
        } else {
            // On last card, "Done" button was clicked - finish activity
            finish();
        }
    }

    private void updateNavigationButtons() {
        previousButton.setEnabled(currentCardIndex > 0);
        
        // Check if we're on the last card
        boolean isLastCard = currentCardIndex >= flashcards.size() - 1;
        
        if (isLastCard) {
            // On last card - change button text to "Done" and enable it
            nextButton.setText("Done");
            nextButton.setEnabled(true);
        } else {
            // Not on last card - show "Next" and enable if not at end
            nextButton.setText("Next");
            nextButton.setEnabled(true);
        }
    }

    // Removed markDifficulty method - no longer needed without difficulty buttons
    
    /**
     * Check if flashcard generation is in progress for this subject
     */
    private boolean isFlashcardGenerationInProgress() {
        try {
            if (subjectName == null || subjectName.isEmpty() || subjectName.equalsIgnoreCase("General")) {
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
            android.util.Log.e("FlashcardActivity", "Error checking generation status", e);
            return false; // If error, assume not generating
        }
    }
    
    /**
     * Show a message that flashcards are being generated
     */
    private void showGeneratingFlashcardMessage() {
        // Show generating message in the card
        frontText.setText("🤖 Generating flashcards...");
        backText.setText("Please wait while AI creates flashcards from your uploaded PDFs.\n\nThis may take 20-30 seconds. You'll be notified when ready!");
        cardCounter.setText("Generating...");
        
        // Hide navigation buttons - removed difficulty layout
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        
        // Show front layout, hide back
        frontLayout.setVisibility(View.VISIBLE);
        backLayout.setVisibility(View.GONE);
        
        Toast.makeText(this, "Flashcards are being generated. Please check back in a moment!", Toast.LENGTH_LONG).show();
    }
}
