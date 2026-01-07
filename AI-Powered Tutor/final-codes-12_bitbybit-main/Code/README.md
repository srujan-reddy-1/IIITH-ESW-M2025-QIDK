# Code Structure

## Project Overview

This repository contains the implementation of an **AI-powered Educational Android Application** featuring:
- **ChatBot with LLM Integration**: Conversational AI using Qualcomm's AI Runtime SDK (QAIRT) and Genie library
- **Quiz System**: Interactive quiz functionality with personalization features
- **MongoDB Integration**: Persistent data storage for user profiles, chat history, and quiz data
- **Computer Vision Demos**: Multiple AI/ML demonstration apps including image classification, object detection, semantic segmentation, and super resolution

## Repository Structure

```
Code/
├── README.md                           # This file
└── ai-hub-apps-main-Kartik/           # Main application directory
    ├── apps/
    │   ├── android/                    # Android applications
    │   │   ├── ChatApp/               # Main ChatBot application with MongoDB
    │   │   ├── ImageClassification/   # Image classification demo
    │   │   ├── ObjectDetection/       # Object detection demo
    │   │   ├── SemanticSegmentation/  # Semantic segmentation demo
    │   │   ├── SuperResolution/       # Super resolution demo
    │   │   ├── WhisperKit/            # Voice recognition (placeholder)
    │   │   ├── build.gradle           # Project-level build configuration
    │   │   ├── settings.gradle        # Gradle settings
    │   │   └── *.md                   # Documentation files
    │   ├── windows/                    # Windows C++ and Python demos
    │   │   ├── cpp/                   # C++ implementations
    │   │   └── python/                # Python implementations
    │   └── versions.yaml              # Version configuration
    ├── python/                         # Python utilities and scripts
    ├── tutorials/                      # Tutorial and example code
    └── version_info.py                # Version information

Demos/                                  # Demo presentations and materials
Presentation/                           # Project presentations
Report/                                 # Project reports and documentation
```

## Main Application: ChatApp

### Technology Stack
- **Platform**: Android (SDK 31-34)
- **Build System**: Gradle 8.9.1+
- **AI Framework**: Qualcomm AI Runtime SDK (QAIRT)
- **LLM Library**: Genie (for on-device language model inference)
- **Database**: MongoDB Realm 10.18.0 (Local with optional cloud sync)
- **Programming Language**: Java

### Key Features

#### 1. **AI-Powered ChatBot**
- On-device LLM inference using Qualcomm's Genie library
- Support for multiple Snapdragon SoCs (8 Elite, 8 Gen3, 8 Gen2)
- Real-time conversational AI with context awareness
- Chat history persistence via MongoDB

#### 2. **User Authentication System**
- User registration and login functionality
- Secure password storage with MongoDB Realm
- Session management using SharedPreferences
- Email validation and user profile management

#### 3. **MongoDB Integration**
The app uses MongoDB Realm for local data persistence:
- **User Data**: Registration info, credentials, profile data
- **Chat Sessions**: Conversation tracking with timestamps
- **Chat Messages**: Complete message history with user/bot identification
- **Quiz Data**: (In development) User progress and quiz responses

#### 4. **Quiz System**
- Interactive quiz functionality
- Personalized quiz generation
- Performance tracking and analytics
- Data persistence for progress tracking

### ChatApp Architecture

```
ChatApp/
├── src/main/
│   ├── java/com/quicinc/chatapp/
│   │   ├── MainActivity.java           # Entry point, model loading
│   │   ├── Conversation.java          # Chat UI and MongoDB integration
│   │   ├── LoginActivity.java         # User login screen
│   │   ├── RegisterActivity.java      # User registration screen
│   │   ├── ChatApplication.java       # Application class (Realm init)
│   │   ├── database/                  # MongoDB package
│   │   │   ├── DatabaseManager.java  # Singleton for DB operations
│   │   │   └── models/               # Realm data models
│   │   │       ├── UserModel.java    # User schema
│   │   │       ├── ChatSessionModel.java  # Chat session schema
│   │   │       └── ChatMessageModel.java  # Message schema
│   │   ├── GenieWrapper.java         # LLM interface wrapper
│   │   ├── ChatMessage.java          # Message data class
│   │   └── Message_RecyclerViewAdapter.java  # Chat UI adapter
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml     # Main screen layout
│   │   │   ├── activity_login.xml    # Login screen layout
│   │   │   ├── activity_register.xml # Registration screen layout
│   │   │   ├── chat.xml              # Chat interface layout
│   │   │   └── chat_row.xml          # Individual message layout
│   │   └── ...                       # Other resources
│   ├── AndroidManifest.xml           # App configuration
│   └── assets/                       # Model files and configurations
└── build.gradle                       # App-level build config

```

### MongoDB Data Models

#### UserModel
```java
- userId (String, PrimaryKey)
- email (String, Required)
- password (String, Required)
- username (String)
- registrationDate (Date)
- lastLoginDate (Date)
```

#### ChatSessionModel
```java
- sessionId (String, PrimaryKey)
- userId (String)
- sessionTitle (String)
- createdDate (Date)
- lastModifiedDate (Date)
```

#### ChatMessageModel
```java
- messageId (String, PrimaryKey)
- sessionId (String)
- content (String)
- isUser (boolean)
- timestamp (Date)
```

### Database Operations

The `DatabaseManager` singleton provides:
- `registerUser()` - Create new user account
- `loginUser()` - Authenticate user credentials
- `isEmailRegistered()` - Check email availability
- `createChatSession()` - Start new chat conversation
- `saveChatMessage()` - Persist chat messages
- `getUserChatSessions()` - Retrieve user's chat history
- `getSessionMessages()` - Get messages for specific session
- `deleteChatSession()` - Remove chat session

## Demo Applications

### 1. ImageClassification
- **Purpose**: Real-time image classification
- **Model**: MobileNet v1 (quantized)
- **Framework**: TensorFlow Lite
- **Features**: Multi-class image recognition with confidence scores

### 2. ObjectDetection
- **Purpose**: Real-time object detection and localization
- **Framework**: TensorFlow Lite
- **Features**: Bounding box detection, multi-object recognition

### 3. SemanticSegmentation
- **Purpose**: Pixel-level image segmentation
- **Framework**: TensorFlow Lite
- **Features**: Scene understanding, object boundary detection

### 4. SuperResolution
- **Purpose**: Image upscaling and enhancement
- **Framework**: TensorFlow Lite
- **Features**: AI-powered image quality improvement

## Build and Run Instructions

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **Android SDK**: API 31-34
- **Gradle**: 8.9.1 or later
- **Device**: Qualcomm Snapdragon 8 Elite, 8 Gen3, or 8 Gen2
- **MongoDB**: Realm SDK 10.18.0 (included in dependencies)

### Setup Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/Embedded-Systems-Workshop/final-codes-12_bitbybit.git
   cd final-codes-12_bitbybit/Code/ai-hub-apps-main-Kartik/apps/android
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the `android` directory
   - Wait for Gradle sync to complete

3. **Configure Dependencies**
   - MongoDB Realm dependencies are already configured in `build.gradle`
   - Gradle will automatically download required libraries

4. **Build the ChatApp**
   ```bash
   ./gradlew :ChatApp:assembleDebug
   ```

5. **Install on Device**
   - Connect your Snapdragon-powered Android device
   - Enable USB debugging
   - Run from Android Studio or use:
   ```bash
   ./gradlew :ChatApp:installDebug
   ```

### First Run

1. The app will launch to the **Login Screen**
2. Click "Register" to create a new account
3. Fill in username, email, and password
4. After registration, login with your credentials
5. The app will load the LLM model (may take a few seconds)
6. Start chatting with the AI assistant
7. All conversations are automatically saved to MongoDB

## Configuration Files

### build.gradle (Project)
- MongoDB Realm plugin: `io.realm:realm-gradle-plugin:10.18.0`
- Gradle distribution URL

### build.gradle (ChatApp)
- MongoDB Realm dependencies
- RecyclerView and DrawerLayout for UI
- Compile and target SDK versions
- Native library configurations

### AndroidManifest.xml
- Application name: `.ChatApplication` (for Realm initialization)
- Activities: LoginActivity (launcher), RegisterActivity, MainActivity, Conversation
- Native library requirements: libadsprpc.so, libcdsprpc.so
- Permissions: As required by QAIRT SDK

## Development Notes

### MongoDB Realm Configuration
- **Database Name**: `chatapp.realm`
- **Schema Version**: 1
- **Migration Strategy**: `deleteRealmIfMigrationNeeded()` (development mode)
- **Thread Safety**: Realm instances are thread-safe but not shareable across threads

### Session Management
- User sessions are managed via `SharedPreferences`
- Session data: `userId`, `username`, `email`
- LoginActivity checks for existing session on app start

### AI Model Loading
- Models are stored in `assets/models/` directory
- HTP (Hexagon Tensor Processor) configurations per SoC
- Runtime environment variables set for QNN library discovery

## Troubleshooting

### Common Issues

1. **Gradle Build Fails**
   - Ensure Gradle wrapper has execution permissions: `chmod +x gradlew`
   - Clear Gradle cache: `./gradlew clean`

2. **Realm Migration Errors**
   - Current configuration deletes database on schema changes
   - For production, implement proper migration strategy

3. **Model Loading Errors**
   - Verify device SoC is supported (SM8750, SM8650, QCS8550)
   - Check model files exist in assets directory
   - Ensure sufficient storage space

4. **MongoDB Sync Issues**
   - Current implementation uses local Realm only
   - For cloud sync, configure MongoDB Atlas App Services

## Documentation

- **MONGODB_INTEGRATION_GUIDE.md**: Detailed MongoDB setup guide
- **QUIZ_PERSONALIZATION_IMPLEMENTATION.md**: Quiz system documentation
- **TROUBLESHOOTING_QUIZ_FIX.md**: Quiz-related issue resolution
- **FEATURE_FIXES_SUMMARY.md**: Recent bug fixes and improvements

## Contributing

This is an educational project for the Embedded Systems Workshop. For questions or improvements:
1. Create an issue in the GitHub repository
2. Submit a pull request with detailed description
3. Follow the existing code style and structure

## License

This project uses components with different licenses:
- **Qualcomm AI Hub Apps**: BSD-3-Clause License
- **MongoDB Realm**: Apache 2.0 License
- **TensorFlow Lite**: Apache 2.0 License

See individual component directories for specific license information.

## Project Status

✅ **Completed**:
- User authentication system
- MongoDB Realm integration
- Basic chat functionality
- Data models and DatabaseManager
- Login/Register UI and logic

🚧 **In Progress**:
- Quiz data persistence
- Cloud sync configuration
- Advanced chat features (edit, delete sessions)
- User profile management

📋 **Planned**:
- Flashcard system integration
- Study analytics dashboard
- Multi-modal AI capabilities
- Offline mode improvements

## Contact

**Project**: Embedded Systems Workshop - AI Educational App  
**Repository**: https://github.com/Embedded-Systems-Workshop/final-codes-12_bitbybit  
**Team**: BitByBit 
**Team Number** 12
**Members** Kartik , Anushka , Pranjal , Pariza

---

**Note**: This project demonstrates integration of on-device AI with modern mobile app development practices, focusing on educational applications and user data persistence.
