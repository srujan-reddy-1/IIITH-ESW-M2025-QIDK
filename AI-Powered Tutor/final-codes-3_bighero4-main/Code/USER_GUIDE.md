# AI Tutor User Guide

A comprehensive guide to using all features of the AI Tutor application.

## Getting Started

### First Launch
When you first open the app, you'll see the home screen with an empty subject library. Start by adding your study materials.

### Setting Up Your Profile
1. Tap the profile icon in the bottom navigation
2. Enter your name in the "Personal Info" section
3. (Optional) Add an email address
4. Tap your profile picture to upload a custom image
5. Your progress and level will update automatically as you study

## Document Library

### Adding Subjects
1. From the home screen, tap "Add Subject"
2. Enter a subject name (e.g., "Operating Systems", "Data Structures")
3. The subject card will appear on your home screen

### Uploading PDFs
1. Tap on any subject card
2. Use the "Upload PDF" button to select documents
3. The app will automatically extract and process the text
4. Multiple PDFs can be uploaded per subject

### Managing Documents
- View all documents in a subject by tapping the subject card
- Documents are automatically organized by subject
- The most recently selected document becomes the active context

## AI Chat Features

### Intelligent Context System
The app uses advanced context management to handle documents of any size:

**Document Processing:**
- Documents are automatically split into chunks of ~1000 tokens
- For large documents, the AI generates concise summaries of each chunk
- Summaries capture key concepts while reducing memory usage

**Query Processing:**
- When you ask a question, the system searches through summaries first
- Relevant chunks are scored based on keyword matching
- The full text of the most relevant chunk is provided to the AI
- This allows fast, accurate responses even with hundreds of pages

**Benefits:**
- Handle large textbooks and lengthy lecture notes efficiently
- Fast response times regardless of document size
- No memory overflow issues
- Intelligent selection of the most relevant information

### Global AI (Atom)
Access from the home screen's "Chat with Atom" card.

**Capabilities:**
- Answers questions using context from ALL your uploaded documents
- Useful for cross-subject queries or general study help
- Shows "Atom AI - All Subjects Available" at the top

**Best Used For:**
- Comparing concepts across subjects
- General study questions
- Reviewing related topics from different courses

### Subject-Specific AI
Access by tapping "Chat" within any subject.

**Capabilities:**
- Focuses on the selected subject's documents
- Provides targeted, context-aware responses
- Shows "Document: [PDF Name] - Ready to help" at the top

**Best Used For:**
- Deep dives into specific topics
- Working through document-specific content
- Focused study sessions on one subject

### Chat Tips
- Ask clear, specific questions for best results
- The AI will use relevant excerpts from your documents
- For large documents, the system automatically generates summaries for faster retrieval
- Responses are streamed in real-time
- Previous conversations are saved for reference
- The context system intelligently selects the most relevant sections from your materials

## Quiz System

### Generating Quizzes
1. Open any subject
2. Tap "Generate Quiz"
3. The app creates 10 multiple-choice questions from your documents
4. Questions are based on the first ~4000 characters of content

### Taking Quizzes
1. Read each question carefully
2. Select one of four answer choices
3. Tap "Submit Answer" to check if you're correct
4. Review explanations for both correct and incorrect answers
5. Your score updates automatically

### Quiz History
- View recent quizzes from your profile's "Recent Quizzes" section
- Track scores and subjects over time
- Use quiz performance to identify areas needing review

## Flashcard System

### Creating Flashcards
1. Open any subject
2. Tap "Generate Flashcards"
3. The app creates 8 flashcards with questions and detailed answers
4. Flashcards are generated from document content

### Using Flashcards
1. Read the question on the front
2. Try to recall the answer
3. Tap the card to flip and see the full explanation
4. Swipe or use arrows to move between cards
5. Review multiple times for better retention

### Flashcard Tips
- Use for spaced repetition learning
- Review flashcards regularly for best results
- Combine with quizzes for comprehensive study

## Progress Tracking

### Statistics Dashboard
Access from the profile screen.

**Metrics Tracked:**
- Total quizzes taken
- Flashcards reviewed
- Number of subjects studied
- Study time accumulation
- Chat sessions completed

### Level System
Your level increases based on:
- Subjects added (20 XP each)
- Quizzes taken (5 XP each)
- Flashcards reviewed (2 XP each)
- Study hours (10 XP each)
- Chat sessions (3 XP each)

**Performance Bonuses:**
- 20% XP boost for 80%+ average quiz score
- 30% XP boost for 90%+ average with 5+ quizzes

**Level Titles:**
- Level 1-5: Learning Explorer
- Level 6-10: Study Enthusiast
- Level 11-15: Knowledge Seeker
- Level 16-20: Dedicated Scholar
- Level 21-30: Academic Champion
- Level 31-40: Master Learner
- Level 41-50: Study Virtuoso
- Level 51+: Legend

### Detailed Progress
Tap "View Detailed Progress" to see:
- Subject-by-subject breakdown
- Time spent per subject
- Quiz performance trends
- Study consistency metrics

## Study Goals

### Setting Goals
1. Go to Profile > Study Goals
2. Set daily, weekly, or monthly targets
3. Choose goals for:
   - Study hours
   - Quizzes to complete
   - Flashcards to review
   - Subjects to cover

### Tracking Goals
- Progress updates automatically as you study
- Visual indicators show completion status
- Receive motivation when goals are achieved
- Adjust goals as needed for realistic targets

## Settings and Customization

### Display Settings
- Toggle dark mode for comfortable night studying
- Theme persists across app restarts

### Profile Customization
- Update your name anytime
- Change profile picture
- View and manage your statistics

### About Phone
- View device information
- Check app version
- Access system details

## Tips for Effective Use

### Document Management
- Upload complete lecture notes or textbook chapters
- Keep documents organized by subject
- Update documents as you progress through courses

### Study Workflow
1. Upload new course materials as PDFs
2. Generate quizzes to test initial understanding
3. Use AI chat for detailed explanations
4. Create flashcards for regular review
5. Track progress and adjust study patterns

### Optimizing AI Responses
- Use specific questions rather than vague ones
- Reference specific topics from your documents
- For complex topics, break into smaller questions
- Use subject-specific chat for focused queries
- Use global AI when comparing across subjects

### Memory and Performance
- The app processes documents in the background
- Large documents are automatically chunked and summarized for efficient retrieval
- First query after opening may take a moment while context is prepared
- Subsequent responses are faster due to intelligent caching
- Quiz and flashcard generation uses optimized text extraction (first 3000-4000 chars)
- Context window manager handles documents of any size without memory issues

## Troubleshooting

### Document Not Loading
- Ensure PDF is not password-protected
- Check that PDF contains extractable text (not just images)
- Try uploading a different version of the document

### AI Not Responding
- Check that documents have been uploaded
- Verify the document processing completed
- Try restarting the app if issues persist

### Quiz/Flashcard Generation Issues
- Ensure the document has sufficient content
- Documents should be at least a few pages long
- Content should be educational text, not pure graphics

### Context Not Available
- For global AI, ensure documents are uploaded in multiple subjects
- For subject AI, select a document before starting chat
- Wait for document processing to complete after upload

## Best Practices

### Study Session Planning
1. Start with quiz to assess current knowledge
2. Review incorrect answers carefully
3. Use AI chat for deeper understanding
4. Create flashcards for key concepts
5. Review flashcards regularly over time

### Document Organization
- Name subjects clearly (e.g., "CS201 - Data Structures")
- Upload related materials to the same subject
- Keep documents updated as course progresses

### Tracking Progress
- Check profile weekly to monitor trends
- Set realistic study goals based on your schedule
- Adjust study patterns based on quiz performance
- Use detailed progress to identify weak areas

## Privacy and Data

### Local Storage
- All data is stored locally on your device
- Documents and AI models run on-device
- No data is sent to external servers

### Data Management
- Profile data saved in app preferences
- Documents stored in app-specific directories
- Clear app data to reset if needed

## Support and Feedback

For issues or suggestions, please refer to the project repository or contact the development team.
