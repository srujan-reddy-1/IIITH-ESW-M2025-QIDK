// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying flashcard sets in a RecyclerView
 */
public class FlashcardSetsAdapter extends RecyclerView.Adapter<FlashcardSetsAdapter.ViewHolder> {

    private Context context;
    private List<FlashcardSet> flashcardSets;

    public FlashcardSetsAdapter(Context context, List<FlashcardSet> flashcardSets) {
        this.context = context;
        this.flashcardSets = flashcardSets;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_flashcard_set, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlashcardSet set = flashcardSets.get(position);

        holder.titleText.setText(set.getTitle());
        holder.descriptionText.setText(set.getDescription());
        holder.cardCountText.setText(set.getFlashcardCount() + " cards");
        
        // Format date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(set.getCreatedTimestamp()));
        holder.dateText.setText(dateStr);

        // Source badge
        if ("PDF".equals(set.getSourceType())) {
            holder.sourceText.setText("📄 From PDF");
        } else {
            holder.sourceText.setText("✏️ Custom");
        }

        // Click to study
        holder.cardView.setOnClickListener(v -> {
            Intent intent = new Intent(context, FlashcardStudyActivity.class);
            intent.putExtra("flashcard_set", set);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return flashcardSets.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView titleText;
        TextView descriptionText;
        TextView cardCountText;
        TextView dateText;
        TextView sourceText;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            titleText = itemView.findViewById(R.id.set_title);
            descriptionText = itemView.findViewById(R.id.set_description);
            cardCountText = itemView.findViewById(R.id.card_count);
            dateText = itemView.findViewById(R.id.date_text);
            sourceText = itemView.findViewById(R.id.source_text);
        }
    }
}
