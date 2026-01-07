package com.quicinc.chatapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying quiz review results
 */
public class QuizReviewAdapter extends RecyclerView.Adapter<QuizReviewAdapter.ViewHolder> {

    private Context context;
    private List<QuizQuestion> questions;

    public QuizReviewAdapter(Context context, List<QuizQuestion> questions) {
        this.context = context;
        this.questions = questions;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quiz_review, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuizQuestion question = questions.get(position);
        
        // Set question number
        holder.questionNumber.setText("Question " + (position + 1));
        
        // Set question text
        holder.questionText.setText(question.getQuestion());
        
        // Get correct and user's answer text
        String correctAnswerText = question.getOptions()[question.getCorrectAnswer()];
        String userAnswerText;
        int selectedAnswer = question.getSelectedAnswer();
        boolean isAttempted = (selectedAnswer >= 0 && selectedAnswer < question.getOptions().length);
        
        if (isAttempted) {
            userAnswerText = question.getOptions()[selectedAnswer];
        } else {
            userAnswerText = "Not attempted";
        }
        
        // Set answer texts
        holder.correctAnswerText.setText(correctAnswerText);
        holder.yourAnswerText.setText(userAnswerText);
        
        // Set status and colors based on correctness
        if (!isAttempted) {
            // Not attempted
            holder.answerStatus.setText("⊘ Skipped");
            holder.answerStatus.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.warning_color, null));
            holder.yourAnswerLayout.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.warning_light, null));
            holder.yourAnswerText.setTextColor(
                context.getResources().getColor(R.color.warning_color, null));
        } else if (question.isCorrect()) {
            // Correct answer
            holder.answerStatus.setText("✓ Correct");
            holder.answerStatus.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.success_color, null));
            holder.yourAnswerLayout.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.success_light, null));
            holder.yourAnswerText.setTextColor(
                context.getResources().getColor(R.color.success_color, null));
        } else {
            // Incorrect answer
            holder.answerStatus.setText("✗ Incorrect");
            holder.answerStatus.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.error_color, null));
            holder.yourAnswerLayout.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.error_light, null));
            holder.yourAnswerText.setTextColor(
                context.getResources().getColor(R.color.error_color, null));
        }
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView questionNumber;
        TextView answerStatus;
        TextView questionText;
        TextView yourAnswerText;
        TextView correctAnswerText;
        LinearLayout yourAnswerLayout;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            questionNumber = itemView.findViewById(R.id.question_number);
            answerStatus = itemView.findViewById(R.id.answer_status);
            questionText = itemView.findViewById(R.id.review_question_text);
            yourAnswerText = itemView.findViewById(R.id.your_answer_text);
            correctAnswerText = itemView.findViewById(R.id.correct_answer_text);
            yourAnswerLayout = itemView.findViewById(R.id.your_answer_layout);
        }
    }
}
