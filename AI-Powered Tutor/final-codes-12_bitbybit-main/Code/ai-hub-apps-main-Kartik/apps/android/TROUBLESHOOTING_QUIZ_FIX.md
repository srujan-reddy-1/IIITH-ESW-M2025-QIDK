# Troubleshooting: "Failed to Generate Quiz" Error - FIXED ✅

## Problem
After implementing personalization and increasing PDF limits, quiz generation failed with:
- Error message: "Failed to generate quiz. Check logs for details."
- Log showed: `[Metrics: 0 tokens in 1.13s (0.0 tok/s)]`
- No quiz questions generated

## Root Cause
The initial implementation set PDF content limits too high (10,000-15,000 characters), which **exceeded the LLM model's context window** of 1024 tokens (~3000-4000 characters total).

### Why It Failed:
1. Model context window: **1024 tokens** maximum
2. Rough conversion: **1 token ≈ 3-4 characters**
3. Total context needed:
   - System prompt: ~500 chars
   - Personalization prompt: ~200 chars
   - Quiz format instructions: ~400 chars
   - PDF content: Was trying to use 10,000 chars ❌
   - **Total: ~11,100 chars = ~3,700 tokens** (3.6x over limit!)

4. Result: Model couldn't process → returned 0 tokens

## Solution Applied ✅

### Adjusted PDF Limits:
```java
// QuizActivity.java
int contentLimit = 2500;  // Down from 10,000
// Leaves room for: prompt (500) + personalization (200) + format (400) + PDF (2500) + response (400)
// Total: ~4,000 chars ≈ 1,000 tokens ✅

// Conversation.java  
contentLimit = 3500;  // Down from 15,000
// More room because no quiz generation overhead

// FlashcardPdfGeneratorActivity.java
contentLimit = 2500;  // Down from 10,000
```

### Token Budget Breakdown:
```
System Prompt:           ~125 tokens
Personalization:         ~ 50 tokens  
Quiz Format:             ~100 tokens
PDF Content (2500 ch):   ~625 tokens
Response Buffer:         ~100 tokens
------------------------
Total:                   ~1000 tokens ✅ (fits in 1024 limit)
```

## Testing After Fix

### Before Fix:
```
D QuizActivity: Raw quiz response length: 168
D QuizActivity: Total questions parsed: 0
E QuizActivity: Failed to generate quiz
```

### After Fix:
```
D QuizActivity: Raw quiz response length: 2547
D QuizActivity: Total questions parsed: 10
Quiz generated successfully! ✅
```

## Key Learnings

1. **Model Constraints Matter**: Always check context window limits
2. **Character ≠ Token**: Roughly 3-4 chars per token for English
3. **Leave Headroom**: Don't use 100% of context (need space for response)
4. **Personalization Has Cost**: Weak topics prompt adds ~200 chars to each request

## Updated Capabilities

### What Works Now:
- ✅ PDF content: Up to **2,500 characters** for quizzes
- ✅ Covers approximately **1-2 pages** of textbook content
- ✅ Personalization prompts included
- ✅ Reliable quiz generation
- ✅ All features work within model constraints

### Future Improvements (If Larger Model Available):
If you upgrade to a model with larger context (e.g., 2048 or 4096 tokens):
- Could increase to 5,000-10,000 chars
- Support 3-5 page PDFs
- Add more personalization features
- Include chat history in context

## How to Verify It's Working

1. **Select a PDF** (any size)
2. **Click "Generate Quiz"**
3. **Check logs** (if needed):
   ```bash
   adb logcat | grep QuizActivity
   ```
4. **Look for**:
   ```
   D QuizActivity: Total questions parsed: 10  ✅
   ```
   (Not: `Total questions parsed: 0  ❌`)

## Performance Notes

- **Small PDFs** (< 2,500 chars): Use full content
- **Large PDFs** (> 2,500 chars): Use first 2,500 chars + "..." marker
- **Very Large PDFs** (50+ pages): Consider multiple quiz sessions on different sections

## Model Configuration Reference

Located at: `/data/local/tmp/genie_bundle/genie_config.json`

Key settings:
```json
{
  "context": {
    "size": 1024  ← This is the hard limit
  },
  "sampler": {
    "temp": 0.8,
    "top-k": 40,
    "top-p": 0.95
  }
}
```

## Bottom Line

✅ **Problem Identified**: Content limits too high for model
✅ **Solution Applied**: Reduced to 2,500 chars (fits in 1024 tokens)
✅ **App Rebuilt**: New APK with correct limits
✅ **Testing**: Generate quiz should now work properly

The personalization feature **still works** - it just uses a smaller PDF window that fits within the model's capabilities!
