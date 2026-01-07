// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * GenieWrapper: Class to connect JNI GenieWrapper and Java code
 * OPTIMIZED VERSION: Uses persistent genie-app process to eliminate reload overhead
 */
public class GenieWrapper {
    long genieWrapperNativeHandle;

    private String workingDirectory;
    private String configFile;
    private Process persistentProcess = null;
    private BufferedReader persistentReader = null;
    private BufferedWriter persistentWriter = null;
    private Thread errorReaderThread = null;
    private boolean isInitialized = false;
    private final Object processLock = new Object();
    
    /**
     * GenieWrapper: Loads model at provided path with provided htp config
     *
     * @param modelDirPath directory path on system pointing to model bundle
     * @param htpConfigPath HTP config file to use
     */
    GenieWrapper(String modelDirPath, String htpConfigPath) {
        // Skipping native library load since we're using shell commands
        // genieWrapperNativeHandle = loadModel(modelDirPath, htpConfigPath);
        genieWrapperNativeHandle = 0; // Dummy value
        
        // Store paths for use in getResponseForPrompt
        this.workingDirectory = modelDirPath != null ? modelDirPath : "/data/local/tmp/genie_bundle";
        this.configFile = htpConfigPath != null ? htpConfigPath : "genie_config.json";
        
        android.util.Log.d("GenieWrapper", "Initialized with working directory: " + workingDirectory);
        android.util.Log.d("GenieWrapper", "Config file: " + configFile);
        
        // Initialize persistent process
        initPersistentProcess();
    }
    
    /**
     * Initialize a persistent genie-app process that stays alive between queries
     */
    private void initPersistentProcess() {
        synchronized(processLock) {
            try {
                android.util.Log.d("GenieWrapper", "Starting persistent genie-app process...");
                
                String fullCommand = "cd " + workingDirectory + " && " +
                                   "export LD_LIBRARY_PATH=" + workingDirectory + " && " +
                                   "./genie-app -c " + configFile;
                
                String[] command = {"sh", "-c", fullCommand};
                
                persistentProcess = Runtime.getRuntime().exec(command);
                persistentReader = new BufferedReader(
                        new InputStreamReader(persistentProcess.getInputStream()));
                persistentWriter = new BufferedWriter(
                        new OutputStreamWriter(persistentProcess.getOutputStream()));
                
                // Start error reader thread
                errorReaderThread = new Thread(() -> {
                    try {
                        BufferedReader errorReader = new BufferedReader(
                                new InputStreamReader(persistentProcess.getErrorStream()));
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            android.util.Log.w("GenieWrapper", "stderr: " + line);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("GenieWrapper", "Error reader failed: " + e.getMessage());
                    }
                });
                errorReaderThread.start();
                
                // Wait for initialization to complete
                String line;
                long startTime = System.currentTimeMillis();
                while ((line = persistentReader.readLine()) != null) {
                    android.util.Log.d("GenieWrapper", "Init: " + line);
                    
                    // Look for signs that initialization is complete
                    if (line.contains("Allocated") || line.contains(">>")) {
                        isInitialized = true;
                        long initTime = System.currentTimeMillis() - startTime;
                        android.util.Log.i("GenieWrapper", "Persistent process initialized in " + initTime + "ms");
                        break;
                    }
                    
                    // Check for errors
                    if (line.contains("ERROR") || line.contains("FATAL")) {
                        android.util.Log.e("GenieWrapper", "Initialization failed: " + line);
                        isInitialized = false;
                        break;
                    }
                    
                    // Timeout after 10 seconds
                    if (System.currentTimeMillis() - startTime > 10000) {
                        android.util.Log.e("GenieWrapper", "Initialization timeout");
                        isInitialized = false;
                        break;
                    }
                }
                
                if (!isInitialized) {
                    android.util.Log.e("GenieWrapper", "Failed to initialize persistent process, falling back to one-shot mode");
                    cleanupPersistentProcess();
                }
                
            } catch (Exception e) {
                android.util.Log.e("GenieWrapper", "Failed to start persistent process: " + e.getMessage(), e);
                isInitialized = false;
                cleanupPersistentProcess();
            }
        }
    }
    
    /**
     * Cleanup the persistent process
     */
    private void cleanupPersistentProcess() {
        synchronized(processLock) {
            try {
                if (persistentWriter != null) {
                    persistentWriter.close();
                }
                if (persistentReader != null) {
                    persistentReader.close();
                }
                if (persistentProcess != null) {
                    persistentProcess.destroy();
                }
            } catch (Exception e) {
                android.util.Log.e("GenieWrapper", "Error cleaning up process: " + e.getMessage());
            }
            persistentProcess = null;
            persistentReader = null;
            persistentWriter = null;
            isInitialized = false;
        }
    }

    /**
     * getResponseForPrompt: Generates response for provided user input
     *
     * @param userInput user input to generate response for
     * @param callback callback to tunnel each generated token to
     */
    public void getResponseForPrompt(String userInput, StringCallback callback) {
        // If persistent process is available, use it for zero-reload queries
        if (isInitialized && persistentProcess != null && persistentProcess.isAlive()) {
            getResponsePersistent(userInput, callback);
        } else {
            // Fallback to one-shot mode if persistent process failed
            android.util.Log.w("GenieWrapper", "Persistent process not available, using one-shot mode");
            getResponseOneShot(userInput, callback);
        }
    }
    
    /**
     * Use the persistent process (FAST - no reload overhead!)
     */
    private void getResponsePersistent(String userInput, StringCallback callback) {
        synchronized(processLock) {
            try {
                long startTime = System.currentTimeMillis();
                long firstTokenTime = 0;
                int tokenCount = 0;
                
                // Build the prompt with Llama 3 template
                String prompt = "<|begin_of_text|><|start_header_id|>user<|end_header_id|>"
                              + "\n\n" + userInput
                              + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>";
                
                android.util.Log.d("GenieWrapper", "Sending prompt to persistent process");
                
                // Send prompt to the persistent genie-app process
                persistentWriter.write(prompt + "\n");
                persistentWriter.flush();
                
                // Read response
                String line;
                boolean beginFound = false;
                
                while ((line = persistentReader.readLine()) != null) {
                    android.util.Log.d("GenieWrapper", "Response: " + line);
                    
                    if (line.contains("[BEGIN]:")) {
                        beginFound = true;
                        line = line.substring(line.indexOf("[BEGIN]:") + "[BEGIN]:".length());
                    }
                    
                    if (beginFound) {
                        if (line.contains("[END]")) {
                            String remaining = line.substring(0, line.indexOf("[END]"));
                            if (!remaining.isEmpty()) {
                                String[] words = remaining.split("\\s+");
                                for (String word : words) {
                                    if (!word.isEmpty()) {
                                        if (firstTokenTime == 0) {
                                            firstTokenTime = System.currentTimeMillis();
                                        }
                                        callback.onNewString(word + " ");
                                        tokenCount++;
                                    }
                                }
                            }
                            break;
                        }
                        
                        // Stream tokens
                        String[] words = line.split("\\s+");
                        for (String word : words) {
                            if (!word.isEmpty()) {
                                if (firstTokenTime == 0) {
                                    firstTokenTime = System.currentTimeMillis();
                                }
                                callback.onNewString(word + " ");
                                tokenCount++;
                            }
                        }
                    }
                }
                
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                long timeToFirstToken = firstTokenTime > 0 ? (firstTokenTime - startTime) : 0;
                double tokensPerSecond = tokenCount > 0 && totalTime > 0 ? 
                        (tokenCount * 1000.0) / totalTime : 0;
                
                android.util.Log.i("GenieWrapper", String.format("PERSISTENT MODE - Tokens: %d, Speed: %.2f tok/s, TTFT: %dms",
                        tokenCount, tokensPerSecond, timeToFirstToken));
                
                String metrics = String.format("\n\n[⚡ Persistent Mode: %d tokens, %.1f tok/s, TTFT: %dms]",
                        tokenCount, tokensPerSecond, timeToFirstToken);
                callback.onNewString(metrics);
                
            } catch (Exception e) {
                android.util.Log.e("GenieWrapper", "Persistent mode failed: " + e.getMessage(), e);
                callback.onNewString("\n\nError in persistent mode: " + e.getMessage());
                // Mark as not initialized to trigger fallback next time
                isInitialized = false;
            }
        }
    }
    
    /**
     * Fallback one-shot mode (SLOW - reloads model each time)
     */
    private void getResponseOneShot(String userInput, StringCallback callback) {
        try {
            long startTime = System.currentTimeMillis();
            long firstTokenTime = 0;
            int tokenCount = 0;
            
            // Build the prompt with Llama 3 template
            String prompt = "<|begin_of_text|><|start_header_id|>user<|end_header_id|>"
                          + "\n\n" + userInput
                          + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>";

            // Build command that matches the working adb shell command
            // Use the configured working directory and config file
            String fullCommand = "cd " + workingDirectory + " && " +
                               "export LD_LIBRARY_PATH=" + workingDirectory + " && " +
                               "./genie-t2t-run -c " + configFile + " -p '" + prompt.replace("'", "'\\''") + "'";
            
            String[] command = {
                "sh",
                "-c",
                fullCommand
            };

            android.util.Log.d("GenieWrapper", "Executing command: " + fullCommand);
            
            Process process = Runtime.getRuntime().exec(command);

            // Read both stdout and stderr in separate threads
            final StringBuilder errorBuilder = new StringBuilder();
            Thread errorThread = new Thread(() -> {
                try {
                    java.io.BufferedReader errorReader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getErrorStream()));
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        android.util.Log.e("GenieWrapper", "stderr: " + line);
                        errorBuilder.append(line).append("\n");
                    }
                    errorReader.close();
                } catch (java.io.IOException e) {
                    android.util.Log.e("GenieWrapper", "Error reading stderr", e);
                }
            });
            errorThread.start();

            // Read stdout with character-by-character streaming
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                    
            String line;
            boolean beginFound = false;
            StringBuilder currentToken = new StringBuilder();

            // Read stdout line by line, but stream tokens as they complete
            while ((line = reader.readLine()) != null) {
                android.util.Log.d("GenieWrapper", "stdout: " + line);
                
                if (line.contains("[BEGIN]:")) {
                    beginFound = true;
                    line = line.substring(line.indexOf("[BEGIN]:") + "[BEGIN]:".length());
                }
                
                if (beginFound) {
                    if (line.contains("[END]")) {
                        String remaining = line.substring(0, line.indexOf("[END]"));
                        if (!remaining.isEmpty()) {
                            // Send any remaining text before [END]
                            String[] words = remaining.split("\\s+");
                            for (String word : words) {
                                if (!word.isEmpty()) {
                                    if (firstTokenTime == 0) {
                                        firstTokenTime = System.currentTimeMillis();
                                    }
                                    callback.onNewString(word + " ");
                                    tokenCount++;
                                    try { Thread.sleep(10); } catch (InterruptedException e) {}
                                }
                            }
                        }
                        break;
                    }
                    
                    // Stream the line word by word for better visual effect
                    String[] words = line.split("\\s+");
                    for (String word : words) {
                        if (!word.isEmpty()) {
                            if (firstTokenTime == 0) {
                                firstTokenTime = System.currentTimeMillis();
                            }
                            callback.onNewString(word + " ");
                            tokenCount++;
                            // Small delay for visual streaming effect
                            try { Thread.sleep(10); } catch (InterruptedException e) {}
                        }
                    }
                }
            }
            
            reader.close();
            errorThread.join(1000); // Wait for error thread to finish

            // Wait for the process to finish
            int exitCode = process.waitFor();
            long endTime = System.currentTimeMillis();
            
            // Calculate metrics
            long totalTime = endTime - startTime;
            long timeToFirstToken = firstTokenTime > 0 ? (firstTokenTime - startTime) : 0;
            double tokensPerSecond = tokenCount > 0 && totalTime > 0 ? 
                    (tokenCount * 1000.0) / totalTime : 0;

            android.util.Log.d("GenieWrapper", "Process exit code: " + exitCode);
            android.util.Log.d("GenieWrapper", String.format("Metrics - Total: %dms, TTFT: %dms, Tokens: %d, Speed: %.2f tok/s", 
                    totalTime, timeToFirstToken, tokenCount, tokensPerSecond));

            // Send metrics as a final message
            String metrics = String.format("\n\n[Metrics: %d tokens in %.2fs (%.1f tok/s), TTFT: %dms]", 
                    tokenCount, totalTime/1000.0, tokensPerSecond, timeToFirstToken);
            callback.onNewString(metrics);

            // Check for errors
            if (tokenCount == 0 && errorBuilder.length() > 0) {
                callback.onNewString("\n\nError: " + errorBuilder.toString());
            } else if (tokenCount == 0) {
                callback.onNewString("\n\nNo output received. Exit code: " + exitCode);
            }

        } catch (java.io.IOException | InterruptedException e) {
            android.util.Log.e("GenieWrapper", "Exception: " + e.getMessage(), e);
            e.printStackTrace();
            callback.onNewString("Error: " + e.getMessage());
        }
    }

    /**
     * finalize: Free previously loaded model and cleanup persistent process
     */
    @Override
    protected void finalize() {
        cleanupPersistentProcess();
        // Skipping native cleanup since we're using shell commands
        // freeModel(genieWrapperNativeHandle);
    }

    /**
     * loadModel: JNI method to load model using Genie C++ APIs
     *
     * @param modelDirPath directory path on system pointing to model bundle
     * @param htpConfigPath HTP config file to use
     * @return pointer to Genie C++ Wrapper to generate future responses
     */
    private native long loadModel(String modelDirPath, String htpConfigPath);

    /**
     * getResponseForPrompt: JNI method to generate response for provided user input
     *
     * @param nativeHandle native handle captured before with LoadModel
     * @param userInput user input to generate response for
     * @param callback callback to tunnel each generated token to
     */
    private native void getResponseForPrompt(long nativeHandle, String userInput, StringCallback callback);

    /**
     * FreeModel: JNI method to free previously loaded model
     *
     * @param nativeHandle native handle captured before with LoadModel
     */
    private native void freeModel(long nativeHandle);
}
