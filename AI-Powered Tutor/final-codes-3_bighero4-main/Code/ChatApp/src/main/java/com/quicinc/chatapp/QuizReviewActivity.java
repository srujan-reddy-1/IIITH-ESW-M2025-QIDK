package com.quicinc.chatapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to view saved quiz results from recent activity
 */
public class QuizReviewActivity extends AppCompatActivity {
    
    private static final String TAG = "QuizReviewActivity";
    
    private TextView titleText, scoreText, percentageText, dateText;
    private RecyclerView reviewRecyclerView;
    private Button backButton;
    private ImageButton backButtonIcon;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_review);
        
        initializeViews();
        loadQuizData();
        setupClickListeners();
    }
    
    private void initializeViews() {
        titleText = findViewById(R.id.quiz_review_title);
        scoreText = findViewById(R.id.review_score_text);
        percentageText = findViewById(R.id.review_percentage_text);
        dateText = findViewById(R.id.review_date_text);
        reviewRecyclerView = findViewById(R.id.review_recycler_view);
        backButton = findViewById(R.id.back_button);
        backButtonIcon = findViewById(R.id.back_button_icon);
    }
    
    private void loadQuizData() {
        try {
            // Get quiz data from intent
            String quizDataJson = getIntent().getStringExtra("quiz_data");
            if (quizDataJson == null) {
                Log.e(TAG, "No quiz data provided");
                finish();
                return;
            }
            
            JSONObject quizData = new JSONObject(quizDataJson);
            
            // Set header info
            String title = quizData.optString("title", "Quiz Review");
            int score = quizData.optInt("score", 0);
            int total = quizData.optInt("total", 0);
            int percentage = quizData.optInt("percentage", 0);
            String date = quizData.optString("date", "");
            
            titleText.setText(title + " Review");
            scoreText.setText("Score: " + score + "/" + total);
            percentageText.setText(percentage + "%");
            dateText.setText("Taken on " + date);
            
            // Set percentage color
            if (percentage >= 80) {
                percentageText.setTextColor(getResources().getColor(R.color.success_color, null));
            } else if (percentage >= 60) {
                percentageText.setTextColor(getResources().getColor(R.color.warning_color, null));
            } else {
                percentageText.setTextColor(getResources().getColor(R.color.error_color, null));
            }
            
            // Load questions
            JSONArray questionsArray = quizData.optJSONArray("questions");
            if (questionsArray != null) {
                List<QuizQuestion> questions = new ArrayList<>();
                
                for (int i = 0; i < questionsArray.length(); i++) {
                    JSONObject questionObj = questionsArray.getJSONObject(i);
                    
                    String questionText = questionObj.getString("question");
                    
                    // Get options array
                    JSONArray optionsArray = questionObj.getJSONArray("options");
                    String[] options = new String[optionsArray.length()];
                    for (int j = 0; j < optionsArray.length(); j++) {
                        options[j] = optionsArray.getString(j);
                    }
                    
                    int correctAnswer = questionObj.getInt("correctAnswer");
                    int selectedAnswer = questionObj.getInt("selectedAnswer");
                    
                    QuizQuestion question = new QuizQuestion(questionText, options, correctAnswer);
                    question.setSelectedAnswer(selectedAnswer);
                    questions.add(question);
                }
                
                // Setup RecyclerView
                QuizReviewAdapter adapter = new QuizReviewAdapter(this, questions);
                reviewRecyclerView.setAdapter(adapter);
                reviewRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                
                Log.d(TAG, "Loaded " + questions.size() + " questions for review");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading quiz data", e);
            finish();
        }
    }
    
    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        if (backButtonIcon != null) {
            backButtonIcon.setOnClickListener(v -> finish());
        }
    }
}
