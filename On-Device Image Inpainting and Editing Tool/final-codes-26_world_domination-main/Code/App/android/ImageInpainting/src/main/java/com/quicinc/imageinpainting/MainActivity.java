// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.imageinpainting;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.quicinc.tflite.AIHubDefaults;
import com.quicinc.tflite.TFLiteHelpers; // This import is now used

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // --- UI Elements ---
    private ImageView sourceImageView;
    private ImageView maskImageView;
    private ImageView resultImageView;
    private Button selectImageButton;
    private Button selectMaskButton;
    private Button runModelButton;
    private TextView inferenceTimeView;
    private Spinner modelSelectorSpinner;
    private Spinner runtimeSelectorSpinner; // ADDED

    // --- Image Handling ---
    private ActivityResultLauncher<Intent> selectImageResultLauncher;
    private ActivityResultLauncher<Intent> selectMaskResultLauncher;
    private Bitmap sourceImage = null;
    private Bitmap maskImage = null;

    // --- Inference Elements ---
    private ImageInpainter imageInpainter;
    private String selectedModel = "Select a Model";
    private String selectedRuntime = "Select a Runtime";

    private final NumberFormat timeFormatter = new DecimalFormat("0.00");
    private final ExecutorService backgroundTaskExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        // --- Initialize UI Elements ---
        sourceImageView = findViewById(R.id.sourceImageView);
        maskImageView = findViewById(R.id.maskImageView);
        resultImageView = findViewById(R.id.resultImageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        selectMaskButton = findViewById(R.id.selectMaskButton);
        runModelButton = findViewById(R.id.runModelButton);
        inferenceTimeView = findViewById(R.id.inferenceTimeView);
        modelSelectorSpinner = findViewById(R.id.modelSelectorSpinner);
        runtimeSelectorSpinner = findViewById(R.id.runtimeSelectorSpinner);

        // --- Setup Model Selector Spinner ---
        ArrayAdapter<CharSequence> modelAdapter = ArrayAdapter.createFromResource(this,
                R.array.model_array, android.R.layout.simple_spinner_item);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSelectorSpinner.setAdapter(modelAdapter);
        modelSelectorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedModel = parent.getItemAtPosition(pos).toString();
                triggerModelLoadCheck(); // Check if we can load a model
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedModel = "Select a Model";
                triggerModelLoadCheck();
            }
        });

        // --- Setup Runtime Selector Spinner ---
        ArrayAdapter<CharSequence> runtimeAdapter = ArrayAdapter.createFromResource(this,
                R.array.runtime_array, android.R.layout.simple_spinner_item);
        runtimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        runtimeSelectorSpinner.setAdapter(runtimeAdapter);
        runtimeSelectorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedRuntime = parent.getItemAtPosition(pos).toString();
                triggerModelLoadCheck(); // Check if we can load a model
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedRuntime = "Select a Runtime";
                triggerModelLoadCheck();
            }
        });


        // --- Setup Image Selection ---
        selectImageResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            sourceImage = uriToBitmap(imageUri);
                            sourceImageView.setImageBitmap(sourceImage);
                            checkInputsAndEnableButton();
                        }
                    }
                });

        selectMaskResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            maskImage = uriToBitmap(imageUri);
                            maskImageView.setImageBitmap(maskImage);
                            checkInputsAndEnableButton();
                        }
                    }
                });

        // --- Setup Button Callbacks ---
        selectImageButton.setOnClickListener(view -> openGalleryFor(selectImageResultLauncher));
        selectMaskButton.setOnClickListener(view -> openGalleryFor(selectMaskResultLauncher));
        runModelButton.setOnClickListener((view) -> updatePredictionDataAsync());

        // No model loaded at start
        runModelButton.setEnabled(false);
    }

    private void openGalleryFor(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcher.launch(intent);
    }

    private Bitmap uriToBitmap(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, src) -> decoder.setMutableRequired(true));
            } else {
                return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void checkInputsAndEnableButton() {
        // Enable button only if images are selected AND a model is loaded
        if (sourceImage != null && maskImage != null && imageInpainter != null) {
            runModelButton.setEnabled(true);
            runModelButton.setAlpha(1.0f);
        } else {
            runModelButton.setEnabled(false);
            runModelButton.setAlpha(0.5f);
        }
    }

    private void closeCurrentInpainter() {
        if (imageInpainter != null) {
            imageInpainter.close();
            imageInpainter = null;
        }
    }

    private void triggerModelLoadCheck() {
        boolean validModel = !selectedModel.equals("Select a Model");
        boolean validRuntime = !selectedRuntime.equals("Select a Runtime");

        if (validModel && validRuntime) {
            // Both are selected, load the model
            loadModelAsync(selectedModel, selectedRuntime);
        } else {
            // One or both are not selected, unload any current model
            loadModelAsync(null, null);
        }
    }

    void loadModelAsync(String modelName, String runtimeName) {
        // 1. Disable UI immediately
        runModelButton.setEnabled(false);
        runModelButton.setAlpha(0.5f);

        // 2. Handle "unselected" case
        if (modelName == null || runtimeName == null) {
            if (!selectedModel.equals("Select a Model") && !selectedRuntime.equals("Select a Runtime")) {
                // This shouldn't be hit if triggerModelLoadCheck is correct, but as a safety
            } else {
                Toast.makeText(this, "Select a model and runtime", Toast.LENGTH_SHORT).show();
            }
            // Post a task to just close any existing model
            backgroundTaskExecutor.execute(this::closeCurrentInpainter);
            return;
        }

        // 3. Show loading toast
        Toast.makeText(this, "Loading " + modelName + " on " + runtimeName + "...", Toast.LENGTH_SHORT).show();
        modelSelectorSpinner.setEnabled(false); // Disable spinners during load
        runtimeSelectorSpinner.setEnabled(false);

        // 4. Launch background task to load model
        backgroundTaskExecutor.execute(() -> {
            // 5. Unload any previous model
            closeCurrentInpainter();

            // 6. Get the asset path for the new model
            String modelAssetPath;
            if (modelName.equals("AOT-GAN")) {
                modelAssetPath = this.getResources().getString(R.string.aot_gan_model_asset);
            } else if (modelName.equals("LaMa")) {
                modelAssetPath = this.getResources().getString(R.string.lama_model_asset);
            } else if (modelName.equals("Hi-Fill")){
                modelAssetPath = this.getResources().getString(R.string.hifill_model_asset);
            }else {
                return; // Should not happen
            }

            // 7. Get the delegate priority order for the new runtime
            // CHANGED: This logic now provides a CPU fallback for NPU and GPU
            TFLiteHelpers.DelegateType[][] delegateOrder;
            switch (runtimeName) {
                case "NPU":
                    // This forces the interpreter to try the NPU first.
                    // If any ops are unsupported, they will fall back to CPU.
                    delegateOrder = new TFLiteHelpers.DelegateType[][]{
                            {TFLiteHelpers.DelegateType.QNN_NPU},
                            {} // Fallback to CPU-only if NPU fails to init
                    };
                    break;
                case "GPU":
                    // This forces the interpreter to try the GPU first.
                    // If any ops are unsupported, they will fall back to CPU.
                    delegateOrder = new TFLiteHelpers.DelegateType[][]{
                            {TFLiteHelpers.DelegateType.GPUv2},
                            {} // Fallback to CPU-only if GPU fails to init
                    };
                    break;
                case "CPU":
                    // This creates an empty delegate list, forcing a CPU (XNNPack) execution.
                    delegateOrder = AIHubDefaults.delegatePriorityOrderForDelegates(new HashSet<>());
                    break;
                default:
                    return; // Should not happen
            }

            // 8. Load the new model
            try {
                imageInpainter = new ImageInpainter(
                        this,
                        modelAssetPath,
                        delegateOrder
                );
            } catch (IOException | NoSuchAlgorithmException e) {
                // Post error to UI thread
                mainLooperHandler.post(() -> {
                    Toast.makeText(this, "Failed to load " + modelName, Toast.LENGTH_LONG).show();
                    modelSelectorSpinner.setEnabled(true); // Re-enable spinners
                    runtimeSelectorSpinner.setEnabled(true);
                });
                throw new RuntimeException(e); // Fails the background thread
            }

            // 9. Post success to UI thread
            mainLooperHandler.post(() -> {
                Toast.makeText(this, modelName + " Loaded", Toast.LENGTH_SHORT).show();
                modelSelectorSpinner.setEnabled(true); // Re-enable spinners
                runtimeSelectorSpinner.setEnabled(true);
                checkInputsAndEnableButton(); // Check if we can enable the run button
            });
        });
    }

    void updatePredictionDataAsync() {
        // Disable buttons to prevent multiple clicks
        runModelButton.setEnabled(false);
        selectImageButton.setEnabled(false);
        selectMaskButton.setEnabled(false);
        modelSelectorSpinner.setEnabled(false);
        runtimeSelectorSpinner.setEnabled(false);

        if (imageInpainter == null) {
            Toast.makeText(this, "Error: Model is not loaded.", Toast.LENGTH_SHORT).show();
            runModelButton.setEnabled(true); // Re-enable
            selectImageButton.setEnabled(true);
            selectMaskButton.setEnabled(true);
            modelSelectorSpinner.setEnabled(true);
            runtimeSelectorSpinner.setEnabled(true);
            return;
        }

        Toast.makeText(this, "Running Inpainting...", Toast.LENGTH_SHORT).show();

        // Run inference in a background thread to avoid blocking the UI
        backgroundTaskExecutor.execute(() -> {
            // Background task: Run the inpainting
            final Bitmap resultBitmap = imageInpainter.inpaintImage(sourceImage, maskImage);
            final long inferenceTime = imageInpainter.getLastInferenceTime();
            final String inferenceTimeText = timeFormatter.format((double) inferenceTime / 1000000);

            // Post back to the main UI thread to update the screen
            mainLooperHandler.post(() -> {
                // In main UI thread: Update the result ImageView and time
                resultImageView.setImageBitmap(resultBitmap);
                inferenceTimeView.setText("Inference Time: " + inferenceTimeText + " ms");

                // Re-enable the buttons
                runModelButton.setEnabled(true);
                selectImageButton.setEnabled(true);
                selectMaskButton.setEnabled(true);
                modelSelectorSpinner.setEnabled(true);
                runtimeSelectorSpinner.setEnabled(true);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCurrentInpainter();
        backgroundTaskExecutor.shutdown(); // Stop the background thread
    }
}