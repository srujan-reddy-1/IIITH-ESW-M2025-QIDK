// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GemsAdapter extends RecyclerView.Adapter<GemsAdapter.GemViewHolder> {

    private Context context;
    private List<Gem> gems;

    public GemsAdapter(Context context, List<Gem> gems) {
        this.context = context;
        this.gems = gems;
    }

    @NonNull
    @Override
    public GemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_gem, parent, false);
        return new GemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GemViewHolder holder, int position) {
        Gem gem = gems.get(position);
        
        holder.gemNameText.setText(gem.getName());
        holder.gemDescriptionText.setText(gem.getDescription().isEmpty() ? "No description" : gem.getDescription());
        
        String resourceInfo = "";
        if (!gem.getPdfUri().isEmpty() && !gem.getImageUri().isEmpty()) {
            resourceInfo = "📄 PDF • 🖼️ Image";
        } else if (!gem.getPdfUri().isEmpty()) {
            resourceInfo = "📄 PDF attached";
        } else if (!gem.getImageUri().isEmpty()) {
            resourceInfo = "🖼️ Image attached";
        } else {
            resourceInfo = "💬 Text only";
        }
        holder.gemResourcesText.setText(resourceInfo);
        
        holder.gemCard.setOnClickListener(v -> {
            // Check if gem has attachments
            boolean hasAttachments = !gem.getPdfUri().isEmpty() || !gem.getImageUri().isEmpty();
            
            if (hasAttachments) {
                // Show dialog to choose action
                showActionDialog(gem);
            } else {
                // Direct to chat for text-only gems
                startChat(gem);
            }
        });
    }

    private void showActionDialog(Gem gem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Choose Action")
                .setMessage("What would you like to do with " + gem.getName() + "?")
                .setPositiveButton("Start Chat", (dialog, which) -> startChat(gem))
                .setNegativeButton("Generate Quiz", (dialog, which) -> startQuiz(gem))
                .setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void startChat(Gem gem) {
        Intent intent = new Intent(context, Conversation.class);
        intent.putExtra("mode", "chat");
        intent.putExtra("gem_name", gem.getName());
        if (!gem.getPdfUri().isEmpty()) {
            intent.putExtra("pdf_uri", gem.getPdfUri());
        }
        if (!gem.getImageUri().isEmpty()) {
            intent.putExtra("image_uri", gem.getImageUri());
        }
        context.startActivity(intent);
    }

    private void startQuiz(Gem gem) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra("gem_name", gem.getName());
        if (!gem.getPdfUri().isEmpty()) {
            intent.putExtra("pdf_uri", gem.getPdfUri());
        }
        context.startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return gems.size();
    }

    public void updateGems(List<Gem> newGems) {
        this.gems = newGems;
        notifyDataSetChanged();
    }

    static class GemViewHolder extends RecyclerView.ViewHolder {
        CardView gemCard;
        TextView gemNameText, gemDescriptionText, gemResourcesText;

        public GemViewHolder(@NonNull View itemView) {
            super(itemView);
            gemCard = itemView.findViewById(R.id.gem_card);
            gemNameText = itemView.findViewById(R.id.gem_name_text);
            gemDescriptionText = itemView.findViewById(R.id.gem_description_text);
            gemResourcesText = itemView.findViewById(R.id.gem_resources_text);
        }
    }

    public static class Gem {
        private String name;
        private String description;
        private String pdfUri;
        private String imageUri;

        public Gem(String name, String description, String pdfUri, String imageUri) {
            this.name = name;
            this.description = description;
            this.pdfUri = pdfUri;
            this.imageUri = imageUri;
        }

        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getPdfUri() { return pdfUri; }
        public String getImageUri() { return imageUri; }
        
        public static Gem fromString(String gemData) {
            String[] parts = gemData.split("\\|");
            if (parts.length >= 4) {
                return new Gem(parts[0], parts[1], parts[2], parts[3]);
            }
            return new Gem(parts[0], parts.length > 1 ? parts[1] : "", "", "");
        }
    }
}