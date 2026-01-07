// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.io.InputStream;

/**
 * PDFExtractionService - Service to extract text from PDF files using PDFBox
 */
public class PDFExtractionService {
    private static final String TAG = "PDFExtractionService";

    public interface PDFExtractionCallback {
        void onSuccess(Document document);
        void onError(String errorMessage);
        void onProgress(String status);
        void onChunkingComplete(int chunkCount);
    }

    private Context context;
    private WorkingGenieWrapper aiWrapper;
    private ContextWindowManager contextManager;

    public PDFExtractionService(Context context, WorkingGenieWrapper aiWrapper) {
        this.context = context;
        this.aiWrapper = aiWrapper;
        // Initialize context manager only if AI wrapper is available
        if (aiWrapper != null) {
            this.contextManager = ContextWindowManager.getInstance(context, aiWrapper);
        } else {
            this.contextManager = null;
        }
    }
    
    // Legacy constructor for backward compatibility
    public PDFExtractionService(Context context) {
        this.context = context;
        this.aiWrapper = null;
        this.contextManager = null;
    }
    
    /**
     * Set AI wrapper and context manager after initialization (for lazy initialization)
     */
    public void setAIWrapper(WorkingGenieWrapper aiWrapper) {
        this.aiWrapper = aiWrapper;
        if (aiWrapper != null) {
            this.contextManager = ContextWindowManager.getInstance(context, aiWrapper);
        }
    }

    /**
     * Extract text from a PDF file
     */
    public void extractTextFromPDF(Uri pdfUri, PDFExtractionCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Reading PDF file...");

                // Get the PDF filename
                String fileName = getFileName(pdfUri);
                Log.d(TAG, "Processing PDF: " + fileName);

                callback.onProgress("Extracting text from PDF...");

                // Extract text from PDF with size limit to prevent OOM
                String extractedText = extractTextContent(pdfUri);
                
                if (extractedText == null || extractedText.trim().isEmpty()) {
                    callback.onError("Could not extract text from PDF. The PDF might be image-based or corrupted.");
                    return;
                }
                
                // Check for extremely large files and warn/truncate if needed
                int maxTextLength = 2_000_000; // ~2MB of text (500k tokens)
                if (extractedText.length() > maxTextLength) {
                    Log.w(TAG, "PDF text is very large (" + extractedText.length() + " chars), truncating to prevent memory issues");
                    extractedText = extractedText.substring(0, maxTextLength) + "\n\n[Document truncated due to size - first " + 
                                   (maxTextLength / 1000) + "k characters shown]";
                    callback.onProgress("Large document detected - processing first portion for performance");
                }

                // Create Document object
                Document document = new Document(fileName, extractedText);
                document.setFilePath(pdfUri.toString());
                
                // Get file size
                try {
                    long fileSize = getFileSize(pdfUri);
                    document.setFileSize(fileSize);
                } catch (Exception e) {
                    Log.w(TAG, "Could not get file size: " + e.getMessage());
                }

                Log.i(TAG, "Successfully extracted text from PDF: " + fileName + 
                          " (" + document.getWordCount() + " words)");

                callback.onProgress("PDF processed successfully!");
                callback.onSuccess(document);
                
                // Process document for context window management (if manager available)
                if (contextManager != null && aiWrapper != null) {
                    callback.onProgress("Processing document for intelligent search...");
                    contextManager.processDocument(document.getId(), extractedText, 
                        new ContextWindowManager.ProcessCallback() {
                            @Override
                            public void onSuccess(int chunkCount, boolean needsSummarization) {
                                Log.i(TAG, "Document chunked into " + chunkCount + " parts");
                                if (needsSummarization) {
                                    callback.onProgress("Generating summaries for intelligent search...");
                                }
                                callback.onChunkingComplete(chunkCount);
                            }

                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Context window processing failed: " + error);
                                // Don't fail the entire operation, just log the error
                            }
                        });
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to process PDF", e);
                callback.onError("Failed to process PDF: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Extract text content from PDF using iText with memory-efficient processing
     */
    private String extractTextContent(Uri pdfUri) throws Exception {
        StringBuilder text = new StringBuilder();
        int maxPages = 500; // Limit to prevent OOM on extremely large PDFs
        int maxCharsPerPage = 10000; // Limit chars per page
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            PdfReader reader = new PdfReader(inputStream);
            int pageCount = reader.getNumberOfPages();
            
            // Limit page count for very large PDFs
            int pagesToProcess = Math.min(pageCount, maxPages);
            if (pageCount > maxPages) {
                Log.w(TAG, "PDF has " + pageCount + " pages, processing first " + maxPages + " pages");
            }
            
            for (int i = 1; i <= pagesToProcess; i++) {
                try {
                    String pageText = PdfTextExtractor.getTextFromPage(reader, i);
                    if (pageText != null && !pageText.trim().isEmpty()) {
                        // Truncate very long pages
                        if (pageText.length() > maxCharsPerPage) {
                            pageText = pageText.substring(0, maxCharsPerPage) + "... [page truncated]";
                        }
                        text.append(pageText);
                        if (i < pagesToProcess) {
                            text.append("\n\n"); // Separate pages
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error extracting page " + i + ": " + e.getMessage());
                    // Continue with next page
                }
            }
            
            reader.close();
            
            // Clean up the extracted text
            return cleanExtractedText(text.toString());
        }
    }

    /**
     * Clean up extracted text by removing extra whitespace and formatting issues
     */
    private String cleanExtractedText(String text) {
        if (text == null) return "";
        
        // Remove excessive whitespace while preserving paragraph structure
        text = text.replaceAll("[ \\t]+", " "); // Multiple spaces/tabs to single space
        text = text.replaceAll("\n[ \\t]+", "\n"); // Remove indentation
        text = text.replaceAll("\n{3,}", "\n\n"); // Multiple newlines to double newline
        text = text.trim();
        
        return text;
    }

    /**
     * Get filename from URI
     */
    private String getFileName(Uri uri) {
        String fileName = "Document";
        
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    String fullName = cursor.getString(nameIndex);
                    if (fullName != null) {
                        fileName = fullName;
                        // Remove the .pdf extension for display
                        if (fileName.toLowerCase().endsWith(".pdf")) {
                            fileName = fileName.substring(0, fileName.length() - 4);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get filename: " + e.getMessage());
        }

        return fileName;
    }

    /**
     * Get file size from URI
     */
    private long getFileSize(Uri uri) {
        long size = 0;
        
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get file size: " + e.getMessage());
        }

        return size;
    }
}