# AI Tutor: On-Device AI-Powered Learning Platform

**Project Documentation**

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Project Overview](#project-overview)
3. [System Architecture](#system-architecture)
4. [Features and Functionality](#features-and-functionality)
5. [Technical Implementation](#technical-implementation)
6. [User Guide](#user-guide)
7. [Installation and Setup](#installation-and-setup)
8. [Testing and Performance](#testing-and-performance)


---

## Executive Summary

**AI Tutor** is an advanced Android application designed to revolutionize personalized learning through on-device artificial intelligence. Built using Qualcomm's Snapdragon Neural Processing Unit (NPU) and the Llama 3.2 3B Instruct model, the application provides students with an intelligent tutoring system that operates entirely offline, ensuring data privacy and instant responses.

The application addresses the growing need for personalized, accessible, and private educational assistance by combining document-based learning, AI-powered question answering, adaptive quiz generation, and comprehensive progress tracking. Unlike cloud-based alternatives, Atom AI Tutor processes all data locally on the device, eliminating concerns about data privacy and internet connectivity.

### Key Achievements
- **On-Device AI Processing**: Complete AI inference on Snapdragon NPU with zero cloud dependency
- **Context-Aware Learning**: Intelligent document processing and retrieval for accurate, relevant answers
- **Adaptive Learning System**: Automatic weak topic identification and personalized study recommendations
- **Multi-Modal Learning Tools**: Integrated chat, quizzes, flashcards, and progress tracking
- **Privacy-First Design**: All personal data and study materials remain on device

---

## Project Overview

### Motivation

Traditional learning methods often lack personalization and immediate feedback. Students struggle with:
- Limited access to one-on-one tutoring
- Inability to get instant answers to questions
- Difficulty identifying and addressing knowledge gaps
- Lack of adaptive learning paths
- Privacy concerns with cloud-based educational tools

Atom AI Tutor was developed to address these challenges by providing:
1. **Instant Access**: 24/7 availability of an AI tutor without internet dependency
2. **Personalization**: Adaptive learning based on individual performance and weak topics
3. **Privacy**: Complete data sovereignty with on-device processing
4. **Comprehensive Tools**: Unified platform for reading, questioning, testing, and tracking progress

### Target Users

- **High School Students**: Preparing for board exams and competitive tests
- **College Students**: Studying technical subjects requiring deep understanding
- **Self-Learners**: Individuals pursuing independent study of various topics
- **Privacy-Conscious Users**: Those concerned about educational data privacy

### Objectives

1. Develop a fully functional on-device AI tutoring system
2. Implement intelligent document processing and context retrieval
3. Create adaptive learning mechanisms with weak topic tracking
4. Design an intuitive, user-friendly interface
5. Ensure robust performance on Snapdragon-powered devices
6. Maintain complete offline functionality

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    User Interface Layer                     │
│  (Activities, Fragments, RecyclerViews, Material Design)    │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                 Application Logic Layer                     │
│  • Document Management    • Progress Tracking               │
│  • Context Processing     • Quiz/Flashcard Generation       │
│  • Weak Topic Management  • User Profile Management         │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                  AI Processing Layer                        │
│  • Genie Wrapper (JNI Interface)                            │
│  • Prompt Handler (C++)                                     │
│  • Context Window Manager                                   │
│  • Token Budget Management                                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                 Hardware Acceleration                       │
│  • Qualcomm Snapdragon NPU (HTP)                            │
│  • Llama 3.2 3B Instruct Model                              │
│  • QAIRT SDK Runtime                                        │
└─────────────────────────────────────────────────────────────┘
```

### Component Architecture

#### 1. User Interface Components
- **MainActivity**: Home screen with subject library
- **SubjectDetailActivity**: Document management and subject-specific features
- **Conversation**: AI chat interface with streaming responses
- **QuizActivity**: Interactive quiz taking with immediate feedback
- **FlashcardActivity**: Spaced repetition learning interface
- **ProfileActivity**: User statistics and progress visualization
- **DetailedProgressActivity**: Comprehensive analytics dashboard

#### 2. Core Service Components
- **DocumentManager**: Handles PDF upload, storage, and retrieval
- **ContextWindowManager**: Manages document chunking and intelligent retrieval
- **ContextGenerationService**: AI-powered summarization and topic extraction
- **WeakTopicManager**: Tracks learning gaps and revision needs
- **QuizGenerationService**: Creates contextual quizzes from documents
- **FlashcardGenerationService**: Generates review flashcards

#### 3. AI Processing Components
- **WorkingGenieWrapper**: Java interface to Genie SDK
- **GenieWrapper (C++)**: Native implementation of model execution
- **PromptHandler (C++)**: Llama 3 prompt formatting and system instructions
- **GenieLib (JNI)**: Bridge between Java and C++ layers

#### 4. Data Management
- **SharedPreferences**: User settings, profile data, preferences
- **JSON Files**: Document metadata, quiz banks, flashcard data
- **File System**: PDF storage, model binaries, cache

### Data Flow

#### Document Processing Flow
```
PDF Upload → Text Extraction → Chunking (1600 tokens) → 
AI Summarization → Topic Extraction → Storage → 
Context Library Update
```

#### AI Query Processing Flow
```
User Query → Context Retrieval (keyword scoring) → 
Prompt Assembly (system + context + query) → 
Token Budget Check → Model Inference (NPU) → 
Streaming Response → UI Update
```

#### Adaptive Learning Flow
```
Quiz/Activity → Performance Evaluation → Weak Topic Detection (<70%) → 
Update Weak Topics → Revision Recommendations → 
Focused Study → Retest → Progress Tracking
```

---

## Features and Functionality

### 1. AI Chat Assistant (Atom)

**Global AI Mode**
- Access knowledge across all uploaded documents
- Cross-subject queries with intelligent context retrieval
- Comprehensive answers drawing from multiple sources

**Subject-Specific Mode**
- Focused tutoring within selected subject
- Document-specific context for accurate responses
- Specialized assistance based on uploaded materials

**Chat Features**
- Real-time streaming responses (word-by-word)
- Quick action buttons (Explain, Quiz Me, Summarize, Help)
- Markdown rendering for formatted text
- Message history persistence
- Profile picture integration

**Technical Details**
- Context window: 2048 tokens (1900 safe budget)
- Keyword-based context scoring (+10 per match)
- Intelligent prompt assembly with system instructions
- Anti-hallucination measures ("Based on the provided context...")

### 2. Document Management System

**PDF Processing**
- Automatic text extraction using PDFBox
- Support for multi-page documents
- Intelligent chunking (~1600 tokens per chunk)
- Duplicate detection and prevention

**Context Generation**
- AI-powered summarization for large documents
- Automatic topic extraction
- Hardcoded topics for known educational materials
- Context library for easy access

**Organization**
- Subject-based categorization
- Custom colors and descriptions per subject
- Document search and filtering
- Recent uploads tracking

### 3. Quiz System

**Quiz Generation**
- AI-generated multiple-choice questions (10 questions)
- Multi-document support with proportional sampling
- Difficulty adaptation based on user level
- Token budget-aware content selection

**Quiz Taking**
- Interactive question-by-answer interface
- Immediate feedback on selection
- Explanation for each answer
- Progress indicator

**Performance Tracking**
- Automatic score calculation
- Weak topic identification (<70%)
- Quiz history and review
- Performance trends over time

### 4. Flashcard System

**Flashcard Generation**
- AI-created question-answer pairs (8 cards)
- Document-based content extraction
- Comprehensive explanations
- Topic-focused generation

**Review Interface**
- Card-by-card navigation
- Question-Answer flip interaction
- Previous/Next controls
- Review progress tracking

### 5. Weak Topics Management

**Automatic Detection**
- Quiz performance monitoring
- Threshold-based identification (score < 70%)
- Failure count tracking
- Timestamp recording for revision scheduling

**Management Features**
- Collapsible weak topics section
- Individual topic deletion
- Bulk deletion option
- Direct chat launch for focused revision
- Retest generation for weak topics only

**Progress Tracking**
- Improvement monitoring
- Auto-removal after consistent performance
- Historical weak topics archive
- Success rate calculation

### 6. Progress Analytics

**Statistics Dashboard**
- Total quizzes taken
- Average quiz scores
- Flashcards reviewed
- Study sessions completed
- Time spent studying

**Gamification**
- Level system based on activity
- Achievement unlocking
- Personalized titles
- Progress milestones

**Activity Tracking**
- Recent activity feed
- Subject-wise breakdown
- Daily/Weekly/Monthly views
- Goal setting and tracking

### 7. User Profile Management

**Profile Customization**
- Profile picture upload and crop
- Name and personal information
- Study goals and targets
- Difficulty level selection

**Settings**
- Dark mode support
- Difficulty adjustment (Beginner/Intermediate/Advanced)
- Preparation goals (Board Exams, Competitive, College, Self-Study)
- Notification preferences

---

## Technical Implementation

### AI Model Integration

**Model Specification**
- Model: Llama 3.2 3B Instruct
- Quantization: INT4 for optimal performance
- Context Length: 2048 tokens
- Deployment: Qualcomm Genie SDK

**Prompt Engineering**
```
<|begin_of_text|><|start_header_id|>system<|end_header_id|>

You are Atom, an AI tutor helping students learn. [System instructions...]

<|start_header_id|>user<|end_header_id|>

Context: [Retrieved document chunks...]

Question: [User query]

<|start_header_id|>assistant<|end_header_id|>
```

**Optimization Techniques**
- NPU hardware acceleration via HTP backend
- Token budget management to prevent context overflow
- Streaming inference for responsive UX
- Caching frequently accessed contexts
- Batch processing for quiz/flashcard generation

### Context Window Management

**Chunking Strategy**
```java
MAX_CHUNK_TOKENS = 1600
OVERLAP_TOKENS = 200
```

**Intelligent Retrieval Algorithm**
1. Extract keywords from user query
2. Score each chunk based on keyword frequency (+10 per match)
3. Select top-scoring chunks
4. Enforce token budget (1900 tokens max)
5. Assemble prompt with highest-value context

**Two-Tier Context System**
- **Tier 1**: Full text for documents <2000 tokens
- **Tier 2**: AI-generated summaries for larger documents

### Document Processing Pipeline

**Text Extraction**
```java
PDFExtractionService:
1. Open PDF with PDFBox
2. Iterate through pages
3. Extract text content
4. Normalize whitespace
5. Detect and handle multi-column layouts
```

**Summarization Process**
```java
ContextGenerationService:
1. Check document size
2. If > 2000 tokens:
   a. Generate AI summary
   b. Extract key topics
   c. Store in context library
3. Else: Store full text
```

### Weak Topic Algorithm

**Detection Logic**
```java
if (quizScore < 70%) {
    weakTopicManager.addWeakTopic(
        topic, 
        score, 
        timestamp,
        failureCount++
    );
}
```

**Improvement Tracking**
```java
if (consecutiveImprovements >= 2 && score >= 70%) {
    weakTopicManager.removeWeakTopic(topic);
    achievementManager.unlock("Weakness Conquered");
}
```

### Performance Optimizations

**Memory Management**
- Lazy loading for document lists
- RecyclerView with ViewHolder pattern
- Bitmap recycling for images
- LRU cache for frequently accessed data

**UI Responsiveness**
- Background threads for AI inference
- Handler-based UI updates
- Progress indicators during processing
- Debounced user input

**Battery Efficiency**
- NPU acceleration reduces CPU load
- Batch processing for multiple operations
- Wake lock management
- Background service optimization

---

## User Guide

### Getting Started

**1. First Launch**
- Application opens to home screen
- Empty subject library displayed
- Floating action button to add subjects

**2. Creating Subjects**
- Tap "+" button
- Enter subject name (e.g., "Physics", "Mathematics")
- Select color scheme
- Add optional description
- Save subject

**3. Adding Study Materials**
- Open a subject
- Tap "Upload Document" button
- Select PDF from device storage
- Wait for processing (background)
- Document appears in library

### Using AI Chat

**Global Mode**
- Access from home screen "Chat with Atom"
- Ask questions across all subjects
- Best for interdisciplinary queries

**Subject Mode**
- Enter subject → "Ask Atom AI"
- Questions answered using subject documents
- More focused and accurate responses

**Quick Actions**
- **Explain**: Detailed explanation of concepts
- **Quiz Me**: Generate quiz on current topic
- **Summarize**: Concise summary of content
- **Help**: General assistance

### Taking Quizzes

**Generation**
1. Open subject
2. Tap "Quiz" card
3. Select document(s) for quiz
4. Quiz generates automatically

**Answering**
1. Read question carefully
2. Select one of four options
3. Receive immediate feedback
4. View explanation
5. Continue to next question

**Review**
1. Complete all 10 questions
2. View final score
3. See correct/incorrect breakdown
4. Review weak topics identified

### Using Flashcards

**Creating Flashcards**
1. Open subject
2. Tap "Flashcards" card
3. Select document
4. 8 flashcards generate automatically

**Reviewing**
1. Read question
2. Think of answer
3. Tap card to reveal answer
4. Use Previous/Next to navigate
5. "Done" on last card

### Managing Weak Topics

**Viewing Weak Topics**
1. Open subject
2. Scroll to "Topics Needing Revision"
3. Tap header to expand
4. View list of weak topics

**Revision Strategies**
1. Tap topic → launches focused chat
2. "Retest All" → generates weak topics quiz
3. Delete icon → remove individual topic
4. Bulk delete → clear all weak topics

### Tracking Progress

**Viewing Statistics**
1. Home screen → Profile icon
2. View dashboard with:
   - Current level
   - Quizzes completed
   - Average scores
   - Study streaks
   - Recent activity

**Setting Goals**
1. Profile → Study Goals
2. Set daily/weekly targets
3. Track completion
4. Earn achievements

---

## Installation and Setup

### Prerequisites

**Hardware Requirements**
- Android device with Snapdragon 8 Gen 2, Gen 3, or 8 Elite
- Minimum 4GB RAM (6GB+ recommended)
- 2GB free storage space
- Root access for initial setup

**Software Requirements**
- Android 13+ (API 31+)
- Android Studio 2024.3.1+
- ADB (Android Debug Bridge)
- Git with Git-LFS enabled

### Development Setup

**1. Clone Repository**
```bash
git clone https://github.com/quic/ai-hub-apps.git
cd ai-hub-apps/apps/android/ChatApp
```

**2. Install Dependencies**
```bash
pip install qai-hub
pip install "qai-hub-models[llama-v3-2-3b-instruct]"
```

**3. Export Model**
```bash
python -m qai_hub_models.models.llama_v3_2_3b_instruct.export \
    --device "Snapdragon 8 Elite QRD" \
    --output-dir genie_bundle \
    --skip-profiling \
    --skip-inferencing
```

**4. Prepare Assets**
```bash
# Copy model binaries
cp genie_bundle/*.bin src/main/assets/models/llm/

# Download tokenizer
# Place tokenizer.json in src/main/assets/models/llm/
```

**5. Configure Build**
- Update `build.gradle` with QAIRT SDK path
- Sync Gradle files
- Build project

### Device Deployment

**1. Prepare Device**
```bash
adb root
adb shell setenforce 0
adb shell chmod +x /data/local/tmp/genie_bundle/genie-t2t-run
```

**2. Build and Install**
```bash
./gradlew :ChatApp:assembleDebug
adb install -r ChatApp/build/outputs/apk/debug/ChatApp-debug.apk
```

**3. Launch Application**
- Open from app drawer
- Grant necessary permissions
- Begin using

### Automated Deployment

**Using Deploy Script**
```bash
chmod +x deploy_app.sh
./deploy_app.sh
```

The script automatically:
- Checks device connectivity
- Configures SELinux
- Sets file permissions
- Builds APK
- Installs on device
- Launches application

---

## Testing and Performance

### Performance Metrics

**AI Inference Speed**
- First token latency: ~500ms
- Subsequent tokens: ~50ms per token
- Full response (100 tokens): ~5-6 seconds

**Document Processing**
- PDF extraction: ~2-3 seconds per page
- Summarization: ~10-15 seconds per document
- Topic extraction: ~5-8 seconds

**Quiz Generation**
- 10 questions: ~30-40 seconds
- Multi-document: +10 seconds per additional document

**Memory Usage**
- Baseline: ~200MB
- Active AI inference: ~600MB
- Peak usage: ~800MB

### Known Limitations

1. **Device Compatibility**: Requires Snapdragon NPU
2. **PDF Limitations**: Text-based PDFs only, no scanned images
3. **Context Length**: 2048 token limit restricts very long documents
4. **Processing Time**: Large documents require significant processing
5. **Language Support**: English only (model limitation)

---
