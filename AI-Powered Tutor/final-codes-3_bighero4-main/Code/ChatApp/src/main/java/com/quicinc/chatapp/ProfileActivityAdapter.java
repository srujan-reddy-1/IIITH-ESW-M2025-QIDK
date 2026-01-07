package com.quicinc.chatapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivityAdapter extends RecyclerView.Adapter<ProfileActivityAdapter.ActivityViewHolder> {

    private Context context;
    private List<ActivityItem> activities;
    private OnActivityClickListener listener;

    public interface OnActivityClickListener {
        void onActivityClick(JSONObject activityData);
    }

    public ProfileActivityAdapter(Context context, OnActivityClickListener listener) {
        this.context = context;
        this.activities = new ArrayList<>();
        this.listener = listener;
    }

    public void setActivities(JSONArray activitiesJson) {
        activities.clear();
        if (activitiesJson != null) {
            for (int i = 0; i < activitiesJson.length(); i++) {
                try {
                    JSONObject activity = activitiesJson.getJSONObject(i);
                    activities.add(new ActivityItem(activity));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_recent_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        ActivityItem item = activities.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    class ActivityViewHolder extends RecyclerView.ViewHolder {
        ImageView activityIcon;
        TextView activityDescription;
        TextView activityTimestamp;
        TextView activityScore;

        public ActivityViewHolder(@NonNull View itemView) {
            super(itemView);
            activityIcon = itemView.findViewById(R.id.activity_icon);
            activityDescription = itemView.findViewById(R.id.activity_description);
            activityTimestamp = itemView.findViewById(R.id.activity_timestamp);
            activityScore = itemView.findViewById(R.id.activity_score);
        }

        public void bind(ActivityItem item) {
            activityDescription.setText(item.title);
            activityTimestamp.setText(item.date);

            if (item.score >= 0) {
                activityScore.setVisibility(View.VISIBLE);
                activityScore.setText(item.percentage + "%");
                
                // Color code based on performance
                if (item.percentage >= 80) {
                    activityScore.setTextColor(context.getResources().getColor(R.color.success_color));
                } else if (item.percentage >= 60) {
                    activityScore.setTextColor(context.getResources().getColor(R.color.warning_color));
                } else {
                    activityScore.setTextColor(context.getResources().getColor(R.color.error_color));
                }
            } else {
                activityScore.setVisibility(View.GONE);
            }

            // Set icon based on activity type
            if (item.type.equals("quiz")) {
                activityIcon.setImageResource(R.drawable.ic_quiz);
            } else {
                activityIcon.setImageResource(R.drawable.ic_recent);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActivityClick(item.data);
                }
            });
        }
    }

    static class ActivityItem {
        String type;
        String title;
        String date;
        int score;
        int percentage;
        JSONObject data;

        ActivityItem(JSONObject json) {
            try {
                this.type = json.optString("type", "unknown");
                this.title = json.optString("title", "Activity");
                this.date = json.optString("date", "");
                this.score = json.optInt("score", -1);
                this.percentage = json.optInt("percentage", -1);
                this.data = json;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
