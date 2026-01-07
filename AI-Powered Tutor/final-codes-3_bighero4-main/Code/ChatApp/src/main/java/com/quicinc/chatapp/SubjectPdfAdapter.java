// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying PDF documents in subject detail page
 */
public class SubjectPdfAdapter extends RecyclerView.Adapter<SubjectPdfAdapter.PdfViewHolder> {

    public interface OnPdfClickListener {
        void onPdfSelect(Document document);
        void onPdfDelete(Document document);
    }

    private List<Document> pdfs;
    private OnPdfClickListener listener;
    private Document selectedPdf;

    public SubjectPdfAdapter(List<Document> pdfs, OnPdfClickListener listener) {
        this.pdfs = pdfs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PdfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_pdf, parent, false);
        return new PdfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PdfViewHolder holder, int position) {
        Document document = pdfs.get(position);
        holder.bind(document);
    }

    @Override
    public int getItemCount() {
        return pdfs.size();
    }

    public void setSelectedPdf(Document pdf) {
        this.selectedPdf = pdf;
        notifyDataSetChanged();
    }

    public class PdfViewHolder extends RecyclerView.ViewHolder {
        private TextView pdfName;
        private TextView pdfInfo;
        private RadioButton selectRadio;
        private ImageButton deleteButton;

        public PdfViewHolder(@NonNull View itemView) {
            super(itemView);
            pdfName = itemView.findViewById(R.id.pdf_name);
            pdfInfo = itemView.findViewById(R.id.pdf_info);
            selectRadio = itemView.findViewById(R.id.select_radio);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }

        public void bind(Document document) {
            pdfName.setText(document.getDisplayName());
            pdfInfo.setText(document.getWordCount() + " words • " + 
                           formatFileSize(document.getFileSize()));

            // Set radio button state
            selectRadio.setChecked(selectedPdf != null && 
                                 selectedPdf.getId().equals(document.getId()));

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPdfSelect(document);
                }
            });

            selectRadio.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPdfSelect(document);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPdfDelete(document);
                }
            });
        }

        private String formatFileSize(long bytes) {
            if (bytes == 0) return "Unknown size";
            
            String[] units = {"B", "KB", "MB", "GB"};
            int unitIndex = 0;
            double size = bytes;

            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }

            return String.format("%.1f %s", size, units[unitIndex]);
        }
    }
}