# Quiz Personalization & PDF Size - Quick Start Guide

## 🎯 New Features Overview

### 1. Personalized Quizzes (Adaptive Learning)
Your quizzes now automatically focus on topics where you need improvement!

### 2. Larger PDF Support  
Generate quizzes from much larger PDFs (3-5x more content than before).

---

## 🚀 How to Use Personalization

### Step 1: Build Your Learning History
1. Open the Quiz feature
2. Select any PDF document
3. Generate and complete a quiz
4. Don't worry about your score - mistakes help the system learn!

### Step 2: See Your Weak Areas
After completing 1-2 quizzes:
1. Return to the Quiz setup screen
2. Look for the **orange info box** that shows:
   ```
   🎯 Personalized Quiz:
   📊 Areas to improve (from X quizzes):
   • Topic 1 you struggled with
   • Topic 2 you struggled with
   • Topic 3 you struggled with
   
   The quiz will focus on these topics!
   ```

### Step 3: Get Personalized Quizzes
From now on, every quiz you generate will:
- ✅ Emphasize topics you got wrong previously
- ✅ Help you improve in weak areas
- ✅ Automatically adapt as you improve
- ✅ No configuration needed - it's automatic!

---

## 📄 Using Larger PDFs

### What's New:
- **Before**: Could only use ~1-2 pages of PDF content
- **Now**: Can use **3-5 pages** of PDF content for quiz generation

### Best Practices:
1. **Textbooks**: Extract specific chapters (5-10 pages work great)
2. **Research Papers**: Full papers (10-15 pages) now supported
3. **Study Guides**: Multi-section documents work better
4. **Lecture Notes**: Comprehensive notes generate better quizzes

### Tips for Best Results:
- ✅ PDFs with clear text (not scanned images)
- ✅ Well-structured documents with headings
- ✅ Educational/technical content works best
- ✅ 5-15 pages is the sweet spot

---

## 💡 Example Workflow

### Scenario: Studying Biology

**Week 1:**
1. Upload "Cell Biology Chapter 1" (10 pages)
2. Generate 10 questions on Medium difficulty
3. Score: 6/10 (60%) - struggled with mitochondria and cell membrane

**Week 2:**  
1. Upload "Cell Biology Chapter 2" (10 pages)
2. See personalization message:
   ```
   🎯 Personalized Quiz:
   Areas to improve:
   • the main function of mitochondria
   • cell membrane structure
   • transport across membranes
   ```
3. Generate quiz - notice more questions about these topics!
4. Score: 8/10 (80%) - improving!

**Week 3:**
1. Upload "Cell Biology Chapter 3" (10 pages)  
2. System adapts to new weak areas from Week 2
3. Keep improving! 📈

---

## ❓ FAQ

### Q: How many quizzes before personalization kicks in?
**A:** After just 1 quiz! But it gets better with 3-5 quizzes as patterns emerge.

### Q: Can I turn off personalization?
**A:** It's automatic and non-intrusive. If you have no weak areas (consistently scoring 80%+), you won't see the orange box.

### Q: Does it work with all PDF sizes?
**A:** Yes! Small PDFs (1-2 pages) work like before. Larger PDFs (10+ pages) now provide much better coverage.

### Q: What if I want to reset my learning history?
**A:** Currently, quiz history is per-user. You can create a new user account to start fresh.

### Q: How does it choose which topics to focus on?
**A:** The system:
1. Looks at your last 5 quizzes
2. Identifies quizzes where you scored <80%
3. Extracts topics from questions you got wrong
4. Prioritizes top 3 most-missed topics
5. Tells the AI to focus on those areas

---

## 🎓 Study Tips

### Maximize Learning with Personalization:

1. **Be Honest**: Don't look up answers - wrong answers help you learn
2. **Review History**: Click "View Previous Quizzes" to see patterns
3. **Vary Difficulty**: Start Medium, increase as you improve
4. **Regular Practice**: Take quizzes regularly to track progress
5. **Different Sources**: Use multiple PDFs on same subject

### Getting the Most from Larger PDFs:

1. **Focus on Quality**: More content ≠ better if PDF is poorly formatted
2. **Chapter by Chapter**: Break large textbooks into chapters
3. **Key Sections**: For research papers, extract methodology/results
4. **Clear Scans**: OCR scans work but native PDFs are better

---

## 🔧 Troubleshooting

### "No personalization box showing"
- **Cause**: No weak areas detected (great job!) OR haven't taken any quizzes yet
- **Fix**: Take 1-2 quizzes first

### "Quiz questions seem repetitive"
- **Cause**: Personalization is working - focusing on your weak areas
- **Fix**: This is intentional! Keep practicing until you master them

### "PDF too large" error
- **Cause**: While limits increased, there's still a maximum
- **Fix**: Try splitting PDF into 2-3 sections

### "Quiz not relevant to PDF content"
- **Cause**: PDF might have poor text extraction or mixed content
- **Fix**: Use PDFs with clear, educational content

---

## 📊 What Data is Stored?

Per quiz, we store:
- ✅ Timestamp
- ✅ Difficulty level
- ✅ Score
- ✅ PDF source name
- ✅ Each question with your answer
- ✅ Which questions were correct/incorrect

We **DON'T** store:
- ❌ PDF content itself
- ❌ Personal identifying information (beyond username)
- ❌ Usage analytics

Everything is stored locally on your device using Android SharedPreferences.

---

## 🎉 Enjoy Your Enhanced Learning Experience!

The quiz system now works **for you**, adapting to **your** learning needs. Happy studying! 📚✨
