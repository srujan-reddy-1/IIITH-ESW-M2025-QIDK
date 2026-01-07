package com.quicinc.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RecentActivityAdapter extends RecyclerView.Adapter<RecentActivityAdapter.ViewHolder> {

    private List<RecentActivity> activities;

    public RecentActivityAdapter(List<RecentActivity> activities) {
        this.activities = activities;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentActivity activity = activities.get(position);
        holder.bind(activity);
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    public void updateActivities(List<RecentActivity> newActivities) {
        this.activities = newActivities;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView activityIcon;
        private TextView activityDescription;
        private TextView activityTimestamp;
        private TextView activityScore;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            activityIcon = itemView.findViewById(R.id.activity_icon);
            activityDescription = itemView.findViewById(R.id.activity_description);
            activityTimestamp = itemView.findViewById(R.id.activity_timestamp);
            activityScore = itemView.findViewById(R.id.activity_score);
        }

        public void bind(RecentActivity activity) {
            activityIcon.setImageResource(activity.getIconResId());
            activityDescription.setText(activity.getDescription());
            activityTimestamp.setText(activity.getTimestamp());

            if (activity.getScore() != null && !activity.getScore().isEmpty()) {
                activityScore.setVisibility(View.VISIBLE);
                activityScore.setText(activity.getScore());
            } else {
                activityScore.setVisibility(View.GONE);
            }
        }
    }
}
