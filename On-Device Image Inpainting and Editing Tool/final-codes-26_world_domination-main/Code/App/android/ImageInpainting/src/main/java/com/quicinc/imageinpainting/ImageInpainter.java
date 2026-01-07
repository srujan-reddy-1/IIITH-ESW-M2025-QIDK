// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.imageinpainting;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;

import com.quicinc.ImageProcessing;
import com.quicinc.tflite.AIHubDefaults;
import com.quicinc.tflite.TFLiteHelpers;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class ImageInpainter implements AutoCloseable {
    private static final String TAG = "ImageInpainter";
    private final Interpreter tfLiteInterpreter;
    private final Map<TFLiteHelpers.DelegateType, Delegate> tfLiteDelegateStore;

    private final int[] inputImageShape;
    private final int[] inputMaskShape;
    private final int[] outputImageShape;

    private long preprocessingTime;
    private long postprocessingTime;

    public ImageInpainter(Context context, String modelPath) throws IOException, NoSuchAlgorithmException {
        this(context, modelPath, AIHubDefaults.delegatePriorityOrder);
    }

    public ImageInpainter(Context context, String modelPath, TFLiteHelpers.DelegateType[][] delegatePriorityOrder) throws IOException, NoSuchAlgorithmException {

        Pair<MappedByteBuffer, String> modelAndHash = TFLiteHelpers.loadModelFile(context.getAssets(), modelPath);
        Pair<Interpreter, Map<TFLiteHelpers.DelegateType, Delegate>> iResult = TFLiteHelpers.CreateInterpreterAndDelegatesFromOptions(
                modelAndHash.first,
                delegatePriorityOrder,
                AIHubDefaults.numCPUThreads,
                context.getApplicationInfo().nativeLibraryDir,
                context.getCacheDir().getAbsolutePath(),
                modelAndHash.second
        );
        tfLiteInterpreter = iResult.first;
        tfLiteDelegateStore = iResult.second;

        // --- Model Validation ---
        assert tfLiteInterpreter.getInputTensorCount() == 2;
        assert tfLiteInterpreter.getOutputTensorCount() == 1;

        Tensor inputImageTensor = tfLiteInterpreter.getInputTensor(0);
        inputImageShape = inputImageTensor.shape();
        assert inputImageShape.length == 4 && inputImageShape[0] == 1 && inputImageShape[3] == 3;

        Tensor inputMaskTensor = tfLiteInterpreter.getInputTensor(1);
        inputMaskShape = inputMaskTensor.shape();
        assert inputMaskShape.length == 4 && inputMaskShape[0] == 1 && inputMaskShape[3] == 1;

        Tensor outputImageTensor = tfLiteInterpreter.getOutputTensor(0);
        outputImageShape = outputImageTensor.shape();
        assert outputImageShape.length == 4 && outputImageShape[0] == 1 && outputImageShape[3] == 3;
    }

    /**
     * CORRECTED: The pre- and post-processing logic now correctly normalizes and
     * de-normalizes pixel values to the [0.0, 1.0] range as required by the LaMa model.
     */
    private Object[] preprocess(Bitmap image, Bitmap mask) {
        long prepStartTime = System.nanoTime();

        // --- Process Input Image ---
        Bitmap resizedImg = ImageProcessing.resizeAndPadMaintainAspectRatio(image, inputImageShape[2], inputImageShape[1], 0);
        int[] imagePixels = new int[inputImageShape[1] * inputImageShape[2]];
        resizedImg.getPixels(imagePixels, 0, resizedImg.getWidth(), 0, 0, resizedImg.getWidth(), resizedImg.getHeight());

        ByteBuffer imageInputBuffer = ByteBuffer.allocateDirect(1 * inputImageShape[1] * inputImageShape[2] * 3 * 4); // 4 bytes for float
        imageInputBuffer.order(ByteOrder.nativeOrder());

        for (int pixelValue : imagePixels) {
            // Normalize to [0, 1] range
            imageInputBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f); // R
            imageInputBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);  // G
            imageInputBuffer.putFloat((pixelValue & 0xFF) / 255.0f);         // B
        }

        // --- Process Input Mask ---
        Bitmap resizedMask = ImageProcessing.resizeAndPadMaintainAspectRatio(mask, inputMaskShape[2], inputMaskShape[1], 0);
        int[] maskPixels = new int[inputMaskShape[1] * inputMaskShape[2]];
        resizedMask.getPixels(maskPixels, 0, resizedMask.getWidth(), 0, 0, resizedMask.getWidth(), resizedMask.getHeight());

        ByteBuffer maskInputBuffer = ByteBuffer.allocateDirect(1 * inputMaskShape[1] * inputMaskShape[2] * 1 * 4);
        maskInputBuffer.order(ByteOrder.nativeOrder());

        for (int pixelValue : maskPixels) {
            // Normalize to [0, 1] range using the Red channel as grayscale
            maskInputBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
        }

        preprocessingTime = System.nanoTime() - prepStartTime;
        Log.d(TAG, "Preprocessing Time: " + preprocessingTime / 1000000 + " ms");

        return new Object[]{imageInputBuffer, maskInputBuffer};
    }

    private Bitmap postprocess(ByteBuffer outputBuffer) {
        long postStartTime = System.nanoTime();
        outputBuffer.rewind();
        FloatBuffer floatOutputBuffer = outputBuffer.asFloatBuffer();

        int height = outputImageShape[1];
        int width = outputImageShape[2];
        Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];

        for (int i = 0; i < pixels.length; i++) {
            // De-normalize from [0, 1] range to [0, 255]
            int r = (int) (floatOutputBuffer.get() * 255.0f);
            int g = (int) (floatOutputBuffer.get() * 255.0f);
            int b = (int) (floatOutputBuffer.get() * 255.0f);

            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            pixels[i] = Color.rgb(r, g, b);
        }

        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        postprocessingTime = System.nanoTime() - postStartTime;
        Log.d(TAG, "Postprocessing Time: " + postprocessingTime / 1000000 + " ms");
        return resultBitmap;
    }

    public Bitmap inpaintImage(Bitmap image, Bitmap mask) {
        Object[] inputs = preprocess(image, mask);

        Map<Integer, Object> outputs = new HashMap<>();
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(1 * outputImageShape[1] * outputImageShape[2] * 3 * 4);
        outputBuffer.order(ByteOrder.nativeOrder());
        outputs.put(0, outputBuffer);

        tfLiteInterpreter.runForMultipleInputsOutputs(inputs, outputs);

        return postprocess(outputBuffer);
    }

    @Override
    public void close() {
        if (tfLiteInterpreter != null) tfLiteInterpreter.close();
        for (Delegate delegate : tfLiteDelegateStore.values()) {
            delegate.close();
        }
    }

    public long getLastInferenceTime() {
        return tfLiteInterpreter.getLastNativeInferenceDurationNanoseconds();
    }
}