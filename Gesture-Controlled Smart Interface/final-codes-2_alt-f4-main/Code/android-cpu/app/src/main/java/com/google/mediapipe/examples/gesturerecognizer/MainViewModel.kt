/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.gesturerecognizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

// Change to AndroidViewModel and pass application to constructor
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // --- Original Properties ---
    private var _delegate: Int = GestureRecognizerHelper.DELEGATE_CPU
    private var _minHandDetectionConfidence: Float =
        GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
    private var _minHandTrackingConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_TRACKING_CONFIDENCE
    private var _minHandPresenceConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_PRESENCE_CONFIDENCE

    val currentDelegate: Int get() = _delegate // Use the backing property

    val currentMinHandDetectionConfidence: Float
        get() =
            _minHandDetectionConfidence
    val currentMinHandTrackingConfidence: Float
        get() =
            _minHandTrackingConfidence
    val currentMinHandPresenceConfidence: Float
        get() =
            _minHandPresenceConfidence

    // --- LiveData for Gesture Results (Original and Corrected) ---

    // Original LiveData for raw results (used by OverlayView)
    private val _gestureRecognizerResult =
        MutableLiveData<GestureRecognizerResult>()
    val gestureRecognizerResult: LiveData<GestureRecognizerResult>
        get() =
            _gestureRecognizerResult

    // New LiveData for corrected results (used by Adapter)
    private val _correctedGestures = MutableLiveData<List<Pair<String, Float>>>()
    val correctedGestures: LiveData<List<Pair<String, Float>>>
        get() = _correctedGestures

    // --- Autocorrect Instance ---
    internal val gestureAutocorrector = GestureAutocorrector(application)

    // --- Original Functions ---
    fun setDelegate(delegate: Int) {
        _delegate = delegate
    }

    fun setMinHandDetectionConfidence(confidence: Float) {
        _minHandDetectionConfidence = confidence
    }

    fun setMinHandTrackingConfidence(confidence: Float) {
        _minHandTrackingConfidence = confidence
    }

    fun setMinHandPresenceConfidence(confidence: Float) {
        _minHandPresenceConfidence = confidence
    }

    // --- Updated Function to set results ---
    fun setGestureRecognizerResult(result: GestureRecognizerResult) {
        // 1. Post the original result (used by OverlayView)
        _gestureRecognizerResult.postValue(result)

        // 2. Correct the gestures and post to the new LiveData
        val categories = result.gestures().firstOrNull() ?: emptyList()
        val correctedCategoryList = categories.map { category ->
            // Use your autocorrect function
            val correctedName =
                gestureAutocorrector.correctWord(category.categoryName())

            // Create a simple Pair of the corrected name and the score
            Pair(correctedName, category.score())
        }
        _correctedGestures.postValue(correctedCategoryList)
    }

    // --- Updated FACTORY ---
    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainViewModel(application)
            }
        }
    }
}