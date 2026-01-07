[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/Ei5Ot7FV)

# AI Tutor - Intelligent Study Assistant

An Android application that provides personalized AI-powered learning assistance with document-based context, intelligent study management, and adaptive learning features. Built with on-device AI using Qualcomm Snapdragon NPU for fast, private, and offline-capable tutoring.

## 📱 Overview

AI Tutor is a comprehensive learning platform that combines AI-powered tutoring with document management, quiz generation, flashcard creation, and personalized learning tracking. The app uses **Llama 3.2 3B Instruct** model running on-device via Qualcomm's Snapdragon Neural Processing Unit (NPU) to provide instant, context-aware responses to student queries without requiring internet connectivity.

## ✨ Key Features

### 🤖 AI-Powered Chat Assistant (Atom)
- **Global AI Mode**: Access knowledge from all uploaded subjects simultaneously for cross-subject queries
- **Subject-Specific AI**: Focused tutoring with dedicated document context for each subject
- **Real-time Streaming**: Responses stream word-by-word for natural conversation flow
- **Context-Aware Answers**: Intelligent document retrieval ensures answers are based on your uploaded materials
- **Personalized Responses**: Adapts answer length and complexity based on user's difficulty level (Beginner/Intermediate/Advanced)
- **Quick Actions**: One-tap buttons for "Explain", "Quiz Me", "Summarize", and "Help"

### 📚 Document Management
- **Multi-Subject Organization**: Organize study materials by subject with custom colors and descriptions
- **PDF Upload & Processing**: Automatic text extraction from PDF documents
- **Intelligent Chunking**: Large documents automatically split into manageable chunks (~1600 tokens)
- **AI-Powered Summarization**: Automatic summary generation for efficient context retrieval
- **Hardcoded Topics**: Predefined key topics for specific documents to ensure accuracy
- **Context Library**: View and manage document summaries and topics
- **Smart Document Selection**: Choose which PDF to use as context when chatting

### 📝 Learning Tools

#### Quiz System
- **AI-Generated Quizzes**: Create custom quizzes from document content with multiple-choice questions
- **Multi-Document Support**: Generate quizzes from multiple PDFs with intelligent content combination
- **Performance Tracking**: Track scores and identify weak areas
- **Quiz Review**: Review past quizzes with detailed explanations
- **Smart Content Sampling**: Proportional content selection from multiple documents while respecting token limits

#### Flashcard System
- **Auto-Generated Flashcards**: Create flashcards with questions and detailed answers from documents
- **Spaced Repetition**: Review flashcards to reinforce learning
- **Navigation Controls**: Easy navigation with "Previous" and "Next" buttons, "Done" on final card

### 🎯 Personalized Learning

#### Weak Topics Tracking
- **Automatic Identification**: Topics where you score below 70% are automatically marked as "weak topics"
- **Revision Management**: Track topics needing revision with failure counts and timestamps
- **Focused Study**: Launch chat sessions focused on specific weak topics
- **Retest Options**: Generate quizzes focused on weak topics for targeted practice
- **Progress Tracking**: Monitor improvement on previously weak topics
- **Topic Management**: Delete individual weak topics or clear all when mastered

#### Progress Analytics
- **Detailed Statistics**: Track quizzes taken, flashcards reviewed, subjects studied
- **Gamified Level System**: Earn levels based on learning activity
- **Achievement Tracking**: Unlock achievements as you progress
- **Recent Activity**: View your recent study sessions and activities
- **Study Goals**: Set and track daily, weekly, and monthly learning objectives

### 👤 User Profile
- **Customizable Profile**: Upload profile picture and set personal information
- **Statistics Dashboard**: View comprehensive learning statistics
- **Level & Titles**: Gamified progression system with personalized titles
- **Settings**: Customize difficulty level, preparation goals, and app preferences
- **Dark Mode**: Comfortable studying in low-light conditions

### 🔧 Technical Features
- **On-Device AI**: All processing happens locally on your device - no internet required
- **NPU Acceleration**: Leverages Qualcomm Snapdragon NPU for fast, efficient inference
- **Token Budget Management**: Intelligent context window management (2048 tokens, safe budget of 1900)
- **Streaming Responses**: Real-time token-by-token response generation
- **Memory Efficient**: Handles large documents without memory overflow
- **Offline Capable**: Works completely offline once models are installed

## 🏗️ Technical Architecture

### AI Processing
- **Model**: Llama 3.2 3B Instruct
- **Runtime**: Qualcomm Genie SDK via shell-based execution
- **Acceleration**: Snapdragon NPU (HTP - Hexagon Tensor Processor)
- **Wrapper**: `WorkingGenieWrapper` for development and deployment
- **Prompt Format**: Llama 3 template with system instructions and user context

### Context Management
- **Intelligent Chunking**: Documents split into ~1600 token chunks for processing
- **Two-Tier System**: 
  - Full text for short documents
  - AI-generated summaries for large documents
- **Smart Retrieval**: Keyword-based scoring for relevant context selection
- **Global Context**: Cross-subject context retrieval for global AI mode
- **Token Budget Enforcement**: Ensures prompts stay within model limits

### Data Storage
- **SharedPreferences**: User settings, profile data, progress tracking
- **JSON Storage**: Document metadata, weak topics, quiz/flashcard banks
- **File System**: PDF documents stored in app's private directory
- **Memory Management**: Efficient processing to prevent OOM errors

### Key Components
- **DocumentManager**: Handles PDF upload, storage, and retrieval
- **ContextWindowManager**: Intelligent document chunking and context retrieval
- **ContextGenerationService**: AI-powered summarization and topic extraction
- **WeakTopicManager**: Personalized learning tracking and weak topic management
- **QuizGenerationService**: Multi-document quiz creation with smart content sampling
- **FlashcardGenerationService**: Flashcard generation from document content

## 📋 Prerequisites

### Hardware Requirements
- **Device**: Android device with Qualcomm Snapdragon chipset
- **Recommended**: Snapdragon 8 Gen 2, Gen 3, or 8 Elite
- **Minimum**: Android 8.0 (API level 26), but Android 13+ (API 31) recommended
- **RAM**: Minimum 4GB, 6GB+ recommended
- **Storage**: ~2GB free space for models and documents

### Software Requirements
- **Android Studio**: Version 2024.3.1 or newer
- **ADB**: Android Debug Bridge for device deployment
- **Root Access**: Required for initial setup (SELinux configuration)
- **Genie Bundle**: Pre-installed at `/data/local/tmp/genie_bundle` on device

### Model Files
The app requires the Llama 3.2 3B Instruct model files to be installed on the device at:
```
/data/local/tmp/genie_bundle/
├── genie-t2t-run          # Executable
├── *.bin                  # Model binary files
└── genie_config.json      # Model configuration
```

## 🚀 Installation & Setup

### Quick Start (Automated)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd AI_TUTOR2
   ```

2. **Ensure device is connected and rooted**
   ```bash
   adb devices
   adb root
   ```

3. **Run automated deployment script**
   ```bash
   chmod +x deploy_app.sh
   ./deploy_app.sh
   ```

   This script automatically:
   - Checks and configures SELinux to permissive mode
   - Sets proper file permissions for genie bundle
   - Builds the debug APK
   - Installs on device
   - Launches the app

### Manual Setup

1. **Build the APK**
   ```bash
   ./gradlew :ChatApp:assembleDebug
   ```

2. **Configure device (requires root)**
   ```bash
   adb root
   adb shell setenforce 0
   adb shell chmod +x /data/local/tmp/genie_bundle/genie-t2t-run
   ```

3. **Install APK**
   ```bash
   adb install -r ChatApp/build/outputs/apk/debug/ChatApp-debug.apk
   ```

### Model Installation

The Genie bundle with model files must be installed at `/data/local/tmp/genie_bundle/` on the device. This is typically done via:
- Pre-installation on QDC (Qualcomm Developer Cloud) devices
- Manual installation via ADB with root access
- Device-specific setup scripts

## 📁 Project Structure

```
AI_TUTOR2/
├── ChatApp/
│   ├── src/main/
│   │   ├── java/com/quicinc/chatapp/
│   │   │   ├── MainActivity.java              # Home screen with subjects
│   │   │   ├── Conversation.java              # AI chat interface
│   │   │   ├── SubjectDetailActivity.java     # Subject management & PDFs
│   │   │   ├── QuizActivity.java              # Quiz taking interface
│   │   │   ├── FlashcardActivity.java         # Flashcard review
│   │   │   ├── ProfileActivity.java           # User profile & stats
│   │   │   ├── SettingsActivity.java          # App settings
│   │   │   ├── DocumentManager.java           # PDF document handling
│   │   │   ├── ContextWindowManager.java      # Intelligent context retrieval
│   │   │   ├── ContextGenerationService.java  # AI summarization & topics
│   │   │   ├── WeakTopicManager.java          # Personalized learning tracking
│   │   │   ├── QuizGenerationService.java     # Quiz creation
│   │   │   ├── FlashcardGenerationService.java # Flashcard creation
│   │   │   ├── WorkingGenieWrapper.java       # AI model interface
│   │   │   └── ...                            # Other utilities
│   │   ├── res/                               # UI layouts and resources
│   │   ├── assets/                            # Model configs and HTP configs
│   │   └── cpp/                               # Native C++ code (JNI)
│   │       ├── GenieLib.cpp                   # JNI interface
│   │       ├── GenieWrapper.cpp               # Genie SDK wrapper
│   │       └── PromptHandler.cpp              # Llama 3 prompt formatting
│   ├── build.gradle                           # Build configuration
│   └── lint-baseline.xml                      # Lint configuration
├── deploy_app.sh                              # Automated deployment script
├── quick_deploy.sh                            # Quick deployment wrapper
├── DEPLOYMENT.md                              # Detailed deployment guide
├── USER_GUIDE.md                              # User documentation
└── README.md                                  # This file
```

## 🎮 Usage Guide

### Getting Started

1. **First Launch**: The app opens to the home screen with an empty subject library
2. **Add Subjects**: Tap "Add Subject" to create your first subject
3. **Upload PDFs**: Open a subject and tap "Upload PDF" to add study materials
4. **Start Chatting**: Tap "Chat with Atom" for global AI or "Ask Atom" within a subject for focused help

### Document Processing

- PDFs are automatically processed when uploaded
- Large documents are chunked and summarized for efficient retrieval
- Key topics are extracted (hardcoded for known documents, AI-generated for others)
- Processing happens in the background - you can continue using the app

### AI Chat Features

- **Global Mode**: Ask questions that span multiple subjects
- **Subject Mode**: Get focused answers based on specific PDF context
- **Weak Topic Mode**: Launch chat from weak topics for targeted revision
- **Quick Actions**: Use "Explain", "Quiz Me", "Summarize", or "Help" buttons

### Quiz & Flashcards

- **Generate Quiz**: Creates 10 multiple-choice questions from your documents
- **Take Quiz**: Answer questions and see immediate feedback
- **Review Results**: Track performance and identify weak topics
- **Flashcards**: Generate 8 flashcards for spaced repetition learning

### Weak Topics

- Automatically tracked when quiz score < 70%
- View in subject detail page under "Topics Needing Revision"
- Click topic to launch focused chat session
- Use "Retest All" to generate quiz on weak topics
- Delete topics when mastered

## 🔧 Development

### Building from Source

```bash
# Clone repository
git clone <repository-url>
cd AI_TUTOR2

# Build debug APK
./gradlew :ChatApp:assembleDebug

# Build and install
./gradlew :ChatApp:installDebug
```

### Key Configuration

- **Min SDK**: 31 (Android 13)
- **Target SDK**: Defined in `gradle.properties`
- **Compile SDK**: Defined in `gradle.properties`
- **Java Version**: Defined in `gradle.properties`

### Debugging

- Enable ADB logging: `adb logcat | grep ChatApp`
- Check Genie execution: `adb shell ls -la /data/local/tmp/genie_bundle/`
- Verify SELinux: `adb shell getenforce` (should be "Permissive")

## 🐛 Troubleshooting

### AI Not Responding
- Verify Genie bundle exists: `adb shell ls /data/local/tmp/genie_bundle/`
- Check SELinux mode: `adb shell getenforce` (should be "Permissive")
- Verify executable permissions: `adb shell ls -la /data/local/tmp/genie_bundle/genie-t2t-run`
- Check logs: `adb logcat | grep WorkingGenieWrapper`

### Document Processing Issues
- Ensure PDF has extractable text (not just images)
- Check PDF is not password-protected
- Verify sufficient storage space
- Check logs: `adb logcat | grep PDFExtractionService`

### Quiz/Flashcard Generation Fails
- Ensure document has sufficient content (at least a few pages)
- Check that document processing completed successfully
- Verify document contains educational text, not pure graphics

### Deployment Issues
- Ensure device is rooted: `adb root`
- Set SELinux to permissive: `adb shell setenforce 0`
- Check ADB connection: `adb devices`
- Use automated script: `./deploy_app.sh`

## 📄 License

Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
SPDX-License-Identifier: BSD-3-Clause

## 🙏 Acknowledgments

- Built using **Qualcomm AI Hub** and **Genie SDK** for on-device AI inference
- Powered by **Llama 3.2 3B Instruct** model
- Leverages **Snapdragon NPU** for hardware-accelerated inference
- Uses **QAIRT SDK** (Qualcomm AI Runtime SDK) for model execution

## 📚 Additional Documentation

- **USER_GUIDE.md**: Comprehensive user guide with detailed feature explanations
- **DEPLOYMENT.md**: Detailed deployment instructions and troubleshooting
- **SECURITY.md**: Security considerations and best practices
- **QIDK_TECHNICAL_REPORT.md**: Technical deep-dive into implementation

---

**Note**: This app requires a Qualcomm Snapdragon device with NPU support and root access for initial setup. For best results, use devices from Qualcomm Developer Cloud (QDC) or devices with verified Snapdragon 8 Gen 2/Gen 3/8 Elite chipsets.

---

## 📁 Repository Structure

**NOTE**: 

1. The `Demos` folder shall contain videos and Images or Readme file with link to the video(public link).
2. The `Presentation` folder shall contain **pdf** version of the presentations used for the final evaluation.
3. The `Code` folder shall contain **all**, **and any**, code used for the project.
