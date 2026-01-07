// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WorkingGenieWrapper: Development wrapper that works around permission issues
 * Uses app's private directory for file operations while calling Genie from original location
 */
public class WorkingGenieWrapper {
    private static final String TAG = "WorkingGenieWrapper";
    private static final String GENIE_BUNDLE_PATH = "/data/local/tmp/genie_bundle";
    private static final String GENIE_EXECUTABLE = "./genie-t2t-run";
    
    private boolean isInitialized = false;
    private ExecutorService executorService;
    private Context context;
    private File appWorkDir;

    /**
     * WorkingGenieWrapper: Constructor that uses app's private directory for file operations
     */
    public WorkingGenieWrapper(String modelDirPath, String htpConfigPath, Context context) {
        Log.i(TAG, "Initializing Working Genie wrapper for development");
        this.context = context;
        
        // Check if Genie bundle exists
        File genieBundleDir = new File(GENIE_BUNDLE_PATH);
        if (!genieBundleDir.exists()) {
            Log.e(TAG, "ERROR: Genie bundle not found at " + GENIE_BUNDLE_PATH);
            Log.e(TAG, "Please ensure the model files are properly installed");
            // Don't throw exception - let the app continue, it will show error when trying to use AI
        } else {
            Log.i(TAG, "Genie bundle found at: " + GENIE_BUNDLE_PATH);
        }
        
        executorService = Executors.newSingleThreadExecutor();
        
        // Create working directory in app's private space
        appWorkDir = new File(context.getFilesDir(), "genie_work");
        if (!appWorkDir.exists()) {
            appWorkDir.mkdirs();
        }
        
        // Try to set execute permissions on AI model files
        attemptToSetExecutePermissions();
        
        isInitialized = true;
        Log.i(TAG, "Working Genie wrapper initialized, work dir: " + appWorkDir.getAbsolutePath());
    }
    
    /**
     * Attempt to set execute permissions on AI model files
     */
    private void attemptToSetExecutePermissions() {
        try {
            Log.d(TAG, "Attempting to set execute permissions on AI model files...");
            
            File genieExecutable = new File(GENIE_BUNDLE_PATH, "genie-t2t-run");
            if (genieExecutable.exists() && !genieExecutable.canExecute()) {
                boolean success = genieExecutable.setExecutable(true, false);
                if (success) {
                    Log.i(TAG, "Successfully set execute permission on genie-t2t-run");
                } else {
                    Log.w(TAG, "Could not set execute permission via Java - may need ADB");
                }
            }
            
            // Try to set permissions via shell command as fallback
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
                    "chmod +x " + GENIE_BUNDLE_PATH + "/genie-t2t-run 2>/dev/null");
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Log.i(TAG, "Successfully set permissions via shell command");
                } else {
                    Log.d(TAG, "Shell chmod returned exit code: " + exitCode);
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not set permissions via shell: " + e.getMessage());
            }
            
        } catch (Exception e) {
            Log.d(TAG, "Permission setting attempt completed with note: " + e.getMessage());
        }
    }

    /**
     * getResponseForPrompt: Execute Genie with proper file handling
     */
    public void getResponseForPrompt(String userInput, StringCallback callback) {
        if (!isInitialized) {
            callback.onNewString("I'm starting up. Please wait a moment and try again.");
            return;
        }

        if (userInput == null || userInput.trim().isEmpty()) {
            callback.onNewString("Please enter a question or message.");
            return;
        }

        String sanitizedInput = userInput.replace("\"", "\\\"").replace("'", "\\'").replace("`", "\\`");
        
        executorService.execute(() -> {
            // Declare files at method scope for cleanup
            File promptFile = null;
            File kpiFile = null;
            
            try {
                Log.d(TAG, "Processing user query: " + sanitizedInput);
                
                // Thinking message is now handled in UI layer
                
                // Create unique files for each request to avoid conflicts
                long timestamp = System.currentTimeMillis();
                promptFile = new File(appWorkDir, "prompt_" + timestamp + ".txt");
                kpiFile = new File(appWorkDir, "kpi_" + timestamp + ".txt");
                
                // Clean up any existing files first
                cleanupOldFiles();
                
                // Write the prompt file
                // Check if the input already contains Llama 3 format tags (from Conversation.java)
                // If it does, use it as-is. Otherwise, format it.
                String promptContent;
                if (sanitizedInput.contains("<|begin_of_text|>") || 
                    sanitizedInput.contains("<|start_header_id|>") ||
                    sanitizedInput.contains("<|eot_id|>")) {
                    // Input is already formatted (from Conversation.createPromptWithContext)
                    // Use it directly - it already has system prompt, context, and user input
                    Log.d(TAG, "Using pre-formatted prompt from Conversation");
                    promptContent = sanitizedInput;
                } else {
                    // Input is plain text - format it with basic system prompt
                    // This should rarely happen as Conversation.java always formats prompts
                    Log.d(TAG, "Formatting plain text input with basic system prompt");
                    String systemPrompt = "You are a helpful AI assistant. You must follow instructions precisely and output valid JSON when requested.";
                    promptContent = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" + 
                                   systemPrompt + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n" + 
                                   sanitizedInput + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n";
                }
                
                try (FileWriter writer = new FileWriter(promptFile)) {
                    writer.write(promptContent);
                }
                
                Log.d(TAG, "Created prompt file: " + promptFile.getAbsolutePath());
                
                // Build command that uses our prompt file but runs from Genie directory with proper library path
                String command = "cd " + GENIE_BUNDLE_PATH + " && " + 
                               "export LD_LIBRARY_PATH=" + GENIE_BUNDLE_PATH + ":$LD_LIBRARY_PATH && " +
                               GENIE_EXECUTABLE + " -c genie_config.json --prompt_file " + 
                               promptFile.getAbsolutePath() + " --profile " + kpiFile.getAbsolutePath();
                
                Log.d(TAG, "Executing command: " + command);
                
                ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
                Process process = processBuilder.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                StringBuilder fullOutput = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();
                String line;
                boolean responseStarted = false;
                boolean insideResponse = false;
                
                // Create a thread to read errors separately
                Thread errorThread = new Thread(() -> {
                    try {
                        String errorLine;
                        BufferedReader er = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        while ((errorLine = er.readLine()) != null) {
                            errorOutput.append(errorLine).append("\n");
                            Log.w(TAG, "Genie error: " + errorLine);
                        }
                        er.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error stream", e);
                    }
                });
                errorThread.start();
                
                // Read output and stream it in real-time
                StringBuilder streamingBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    fullOutput.append(line).append("\n");
                    Log.d(TAG, "Genie output: " + line);
                    
                    // Check if we've hit the begin marker
                    if (line.contains("[BEGIN]:")) {
                        insideResponse = true;
                        responseStarted = true;
                        int beginIndex = line.indexOf("[BEGIN]:");
                        if (beginIndex != -1) {
                            String content = line.substring(beginIndex + "[BEGIN]:".length());
                            if (!content.trim().isEmpty()) {
                                streamingBuffer.append(content);
                                callback.onNewString(streamingBuffer.toString());
                            }
                        }
                        continue;
                    }
                    
                    // Check if we've hit the end marker
                    if (line.contains("[END]")) {
                        insideResponse = false;
                        int endIndex = line.indexOf("[END]");
                        if (endIndex > 0 && responseStarted) {
                            String content = line.substring(0, endIndex);
                            if (!content.trim().isEmpty()) {
                                streamingBuffer.append(content);
                            }
                        }
                        // Send final complete response
                        if (responseStarted && streamingBuffer.length() > 0) {
                            callback.onNewString(streamingBuffer.toString().trim());
                        }
                        break;
                    }
                    
                    // If we're inside the response, stream the content
                    if (insideResponse && responseStarted) {
                        streamingBuffer.append(line).append("\n");
                        callback.onNewString(streamingBuffer.toString());
                    }
                }
                
                int exitCode = process.waitFor();
                errorThread.join(1000); // Wait up to 1 second for error thread
                Log.d(TAG, "Genie command exit code: " + exitCode);
                
                reader.close();
                errorReader.close();
                
                // If no streaming happened, fall back to full parsing
                if (!responseStarted && exitCode == 0 && fullOutput.length() > 0) {
                    String response = parseGenieResponse(fullOutput.toString());
                    if (response != null && !response.trim().isEmpty()) {
                        callback.onNewString(response.trim());
                    } else {
                        callback.onNewString("I'm having trouble processing that. Could you please rephrase your question?");
                    }
                } else if (!responseStarted) {
                    String errorMsg = errorOutput.toString();
                    Log.e(TAG, "Genie execution failed with exit code: " + exitCode);
                    
                    if (exitCode == 126) {
                        // Permission denied error
                        Log.e(TAG, "════════════════════════════════════════");
                        Log.e(TAG, "❌ PERMISSION DENIED ERROR (exit code 126)");
                        Log.e(TAG, "The Genie executable does not have execute permissions");
                        Log.e(TAG, "════════════════════════════════════════");
                        Log.e(TAG, "To fix this, run these commands on your computer:");
                        Log.e(TAG, "  adb shell \"chmod +x /data/local/tmp/genie_bundle/genie-t2t-run\"");
                        Log.e(TAG, "  adb shell \"chmod +x /data/local/tmp/genie_bundle/*.sh\"");
                        Log.e(TAG, "════════════════════════════════════════");
                        
                        callback.onNewString("⚠️ AI Model Permission Error\n\n" +
                                           "The AI model needs execution permissions.\n\n" +
                                           "📱 To fix this:\n\n" +
                                           "1. Connect your device to computer via USB\n" +
                                           "2. Open terminal/command prompt\n" +
                                           "3. Run this command:\n\n" +
                                           "adb shell \"chmod +x /data/local/tmp/genie_bundle/genie-t2t-run\"\n\n" +
                                           "4. Restart the app\n\n" +
                                           "If you need help, check the app logs for detailed instructions.");
                    } else if (errorMsg.contains("Permission denied") || errorMsg.contains("execute")) {
                        // Try alternative execution method
                        executeAlternativeMethod(sanitizedInput, callback);
                    } else {
                        callback.onNewString("I'm temporarily unavailable. Please try again in a moment.");
                    }
                }
                
                // Cleanup files after processing
                cleanupFiles(promptFile, kpiFile);
                
            } catch (Exception e) {
                Log.e(TAG, "Working wrapper execution error: " + e.getMessage(), e);
                callback.onNewString("Sorry, I encountered an issue. Please try asking your question again.");
                // Cleanup on error too
                if (promptFile != null && promptFile.exists()) promptFile.delete();
                if (kpiFile != null && kpiFile.exists()) kpiFile.delete();
            }
        });
    }

    /**
     * Try alternative execution method by calling the script directly
     */
    private void executeAlternativeMethod(String input, StringCallback callback) {
        try {
            Log.d(TAG, "Trying alternative execution method");
            
            // Direct call to run_genie.sh with input as parameter
            String command = "cd " + GENIE_BUNDLE_PATH + " && ./run_genie.sh '" + input + "'";
            
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            StringBuilder streamingBuffer = new StringBuilder();
            String line;
            boolean responseStarted = false;
            boolean insideResponse = false;
            
            // Read errors in separate thread
            Thread errorThread = new Thread(() -> {
                try {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorOutput.append(errorLine).append("\n");
                        Log.w(TAG, "Alternative error: " + errorLine);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading alternative error stream", e);
                }
            });
            errorThread.start();
            
            // Stream output in real-time
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                Log.d(TAG, "Alternative output: " + line);
                
                if (line.contains("[BEGIN]:")) {
                    insideResponse = true;
                    responseStarted = true;
                    int beginIndex = line.indexOf("[BEGIN]:");
                    if (beginIndex != -1) {
                        String content = line.substring(beginIndex + "[BEGIN]:".length());
                        if (!content.trim().isEmpty()) {
                            streamingBuffer.append(content);
                            callback.onNewString(streamingBuffer.toString());
                        }
                    }
                    continue;
                }
                
                if (line.contains("[END]")) {
                    insideResponse = false;
                    int endIndex = line.indexOf("[END]");
                    if (endIndex > 0 && responseStarted) {
                        String content = line.substring(0, endIndex);
                        if (!content.trim().isEmpty()) {
                            streamingBuffer.append(content);
                        }
                    }
                    // Send final complete response
                    if (responseStarted && streamingBuffer.length() > 0) {
                        callback.onNewString(streamingBuffer.toString().trim());
                    }
                    break;
                }
                
                if (insideResponse && responseStarted) {
                    streamingBuffer.append(line).append("\n");
                    callback.onNewString(streamingBuffer.toString());
                }
            }
            
            int exitCode = process.waitFor();
            errorThread.join(1000);
            reader.close();
            errorReader.close();
            
            if (!responseStarted && exitCode == 0 && output.length() > 0) {
                String response = parseGenieResponse(output.toString());
                if (response != null && !response.trim().isEmpty()) {
                    callback.onNewString(response.trim());
                    return;
                }
            }
            
            // If we get here, provide a simple user-friendly message
            if (!responseStarted) {
                callback.onNewString("I'm having trouble processing your request right now. Please try again later.");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Alternative method failed: " + e.getMessage(), e);
            callback.onNewString("Sorry, I'm unable to process your request at the moment. Please try again.");
        }
    }

    private String getSELinuxStatus() {
        try {
            ProcessBuilder pb = new ProcessBuilder("getenforce");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String status = reader.readLine();
            reader.close();
            process.waitFor();
            return status != null ? status : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String parseGenieResponse(String shellOutput) {
        try {
            String beginMarker = "[BEGIN]:";
            String endMarker = "[END]";
            
            int beginIndex = shellOutput.indexOf(beginMarker);
            int endIndex = shellOutput.indexOf(endMarker);
            
            if (beginIndex != -1 && endIndex != -1 && endIndex > beginIndex) {
                String response = shellOutput.substring(
                    beginIndex + beginMarker.length(), 
                    endIndex
                ).trim();
                return response;
            } else {
                // Return raw output for debugging
                return shellOutput.trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response", e);
            return null;
        }
    }

    private void simulateStreamingResponse(String response, StringCallback callback) {
        // Send the complete response at once to avoid duplication issues
        callback.onNewString(response);
    }

    /**
     * Clean up old files to prevent accumulation
     */
    private void cleanupOldFiles() {
        try {
            if (appWorkDir.exists()) {
                File[] files = appWorkDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // Delete files older than 5 minutes to prevent accumulation
                        if (file.isFile() && (System.currentTimeMillis() - file.lastModified()) > 300000) {
                            file.delete();
                            Log.d(TAG, "Cleaned up old file: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Clean up specific files after processing
     */
    private void cleanupFiles(File... files) {
        for (File file : files) {
            try {
                if (file != null && file.exists()) {
                    if (file.delete()) {
                        Log.d(TAG, "Cleaned up file: " + file.getName());
                    } else {
                        Log.w(TAG, "Failed to delete file: " + file.getName());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error deleting file " + (file != null ? file.getName() : "null") + ": " + e.getMessage());
            }
        }
    }



    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.i(TAG, "WorkingGenieWrapper shutdown completed");
        }
        // Final cleanup on shutdown
        cleanupOldFiles();
    }
}