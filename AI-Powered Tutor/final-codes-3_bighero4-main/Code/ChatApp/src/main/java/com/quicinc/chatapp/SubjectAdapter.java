package com.quicinc.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private List<Subject> subjects;
    private OnSubjectClickListener clickListener;
    private OnSubjectLongClickListener longClickListener;

    public interface OnSubjectClickListener {
        void onSubjectClick(Subject subject);
    }
    
    public interface OnSubjectLongClickListener {
        void onSubjectLongClick(Subject subject, int position);
    }

    public SubjectAdapter(List<Subject> subjects, OnSubjectClickListener clickListener) {
        this.subjects = subjects;
        this.clickListener = clickListener;
    }
    
    public void setOnSubjectLongClickListener(OnSubjectLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        holder.bind(subject, clickListener, longClickListener, position);
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardView;
        private LinearLayout headerLayout;
        private TextView iconText;
        private TextView nameText;

        public SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.subject_card);
            headerLayout = itemView.findViewById(R.id.subject_header);
            iconText = itemView.findViewById(R.id.subject_icon);
            nameText = itemView.findViewById(R.id.subject_name);
        }

        public void bind(Subject subject, OnSubjectClickListener clickListener, 
                         OnSubjectLongClickListener longClickListener, int position) {
            nameText.setText(subject.getName());
            
            // Set icon emoji based on subject name
            String icon = getSubjectIcon(subject.getName());
            iconText.setText(icon);
            
            // Set header background color
            int color = cardView.getContext().getResources().getColor(subject.getColorResId());
            headerLayout.setBackgroundColor(color);

            // Click listeners
            cardView.setOnClickListener(v -> clickListener.onSubjectClick(subject));
            
            // Long click listener for delete
            cardView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onSubjectLongClick(subject, position);
                    return true;
                }
                return false;
            });
        }
        
        /**
         * Get emoji icon for subject
         */
        private String getSubjectIcon(String subjectName) {
            String lower = subjectName.toLowerCase();
            
            if (lower.contains("data structures") || lower.contains("algorithms")) {
                return "🔢";
            } else if (lower.contains("operating") || lower.contains("network")) {
                return "💻";
            } else if (lower.contains("probability") || lower.contains("statistics")) {
                return "📊";
            } else if (lower.contains("automata") || lower.contains("theory")) {
                return "🤖";
            } else if (lower.contains("data") && lower.contains("application")) {
                return "💾";
            } else if (lower.contains("math")) {
                return "🧮";
            } else if (lower.contains("security")) {
                return "🔐";
            } else if (lower.contains("machine learning") || lower.contains("ml")) {
                return "🤖";
            } else if (lower.contains("artificial intelligence") || lower.contains("ai")) {
                return "🧠";
            } else if (lower.contains("physics")) {
                return "⚛️";
            } else if (lower.contains("chemistry")) {
                return "🧪";
            } else if (lower.contains("biology")) {
                return "🧬";
            } else if (lower.contains("history")) {
                return "📜";
            } else if (lower.contains("geography")) {
                return "🗺️";
            } else if (lower.contains("literature") || lower.contains("english")) {
                return "📚";
            } else if (lower.contains("economics")) {
                return "💰";
            } else if (lower.contains("business")) {
                return "💼";
            } else if (lower.contains("law")) {
                return "⚖️";
            } else {
                return "📖"; // Default book icon
            }
        }
    }
}