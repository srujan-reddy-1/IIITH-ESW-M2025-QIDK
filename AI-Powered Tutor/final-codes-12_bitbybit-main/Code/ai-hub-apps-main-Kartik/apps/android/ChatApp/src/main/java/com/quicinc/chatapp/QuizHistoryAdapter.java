// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * QuizHistoryAdapter - Adapter for displaying quiz results in history
 */
public class QuizHistoryAdapter extends RecyclerView.Adapter<QuizHistoryAdapter.ViewHolder> {

    private Context context;
    private List<QuizResult> quizResults;
    private OnQuizClickListener clickListener;

    public interface OnQuizClickListener {
        void onQuizClick(QuizResult quizResult);
    }

    public QuizHistoryAdapter(Context context, List<QuizResult> quizResults, OnQuizClickListener clickListener) {
        this.context = context;
        this.quizResults = quizResults;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quiz_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuizResult result = quizResults.get(position);

        holder.gradeText.setText(result.getGrade());
        holder.scoreText.setText(String.format("Score: %d/%d (%.1f%%)", 
            result.score, result.totalQuestions, result.getPercentage()));
        holder.difficultyText.setText("Difficulty: " + result.difficulty);
        holder.timestampText.setText(result.timestamp);

        // Set grade background color based on performance
        int backgroundColor;
        if (result.getPercentage() >= 90) {
            backgroundColor = context.getResources().getColor(android.R.color.holo_green_dark, null);
        } else if (result.getPercentage() >= 70) {
            backgroundColor = context.getResources().getColor(android.R.color.holo_blue_dark, null);
        } else if (result.getPercentage() >= 50) {
            backgroundColor = context.getResources().getColor(android.R.color.holo_orange_dark, null);
        } else {
            backgroundColor = context.getResources().getColor(android.R.color.holo_red_dark, null);
        }
        holder.gradeText.setBackgroundColor(backgroundColor);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onQuizClick(result);
            }
        });
    }

    @Override
    public int getItemCount() {
        return quizResults.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView gradeText, scoreText, difficultyText, timestampText;

        ViewHolder(View itemView) {
            super(itemView);
            gradeText = itemView.findViewById(R.id.quiz_grade_text);
            scoreText = itemView.findViewById(R.id.quiz_score_text);
            difficultyText = itemView.findViewById(R.id.quiz_difficulty_text);
            timestampText = itemView.findViewById(R.id.quiz_timestamp_text);
        }
    }
}
