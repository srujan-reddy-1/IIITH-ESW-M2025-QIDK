package com.google.mediapipe.examples.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerHelper
import com.google.mediapipe.examples.gesturerecognizer.MainViewModel
import com.google.mediapipe.examples.gesturerecognizer.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

class CameraFragment : Fragment(),
    GestureRecognizerHelper.GestureRecognizerListener {

    companion object {
        private const val TAG = "ContiguousGesture"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private val viewModel: MainViewModel by activityViewModels()

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // ================= SENTENCE BUFFERS ===================
    private val visibleBuffer = StringBuilder()
    private val internalBuffer = StringBuilder()

    // ================= LOCK-IN MODEL =======================
    private var lastSeenGesture: String? = null
    private var consecutiveFrames = 0
    private val requiredFrames = 15

    private val confidenceThreshold = 0.85f

    // ================= COOLDOWN ===========================
    private var cooldownActive = false
    private var cooldownEndTime = 0L
    private val cooldownDuration = 500L   // 0.5 seconds

    // ================= LATENCY TRACKING ====================
    private val latencyHistory = LinkedList<Long>()
    private val rollingWindow = 100

    private var spaceRun = 0


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    override fun onResume() {
        super.onResume()

        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(),
                com.google.mediapipe.examples.gesturerecognizer.R.id.fragment_container)
                .navigate(
                    com.google.mediapipe.examples.gesturerecognizer.R.id.action_camera_to_permissions
                )
        }

        backgroundExecutor.execute {
            if (this::gestureRecognizerHelper.isInitialized &&
                gestureRecognizerHelper.isClosed()) {
                gestureRecognizerHelper.setupGestureRecognizer()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (this::gestureRecognizerHelper.isInitialized) {
            gestureRecognizerHelper.clearGestureRecognizer()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        fragmentCameraBinding.viewFinder.post { setUpCamera() }

        backgroundExecutor.execute {
            // MUST MATCH REAL ORDER OF CONSTRUCTOR
            gestureRecognizerHelper = GestureRecognizerHelper(
                viewModel.currentMinHandDetectionConfidence,
                viewModel.currentMinHandTrackingConfidence,
                viewModel.currentMinHandPresenceConfidence,
                viewModel.currentDelegate,
                RunningMode.LIVE_STREAM,
                requireContext(),
                this
            )
        }
    }

    private fun setUpCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also { analyzer ->
                analyzer.setAnalyzer(backgroundExecutor) { proxy ->
                    gestureRecognizerHelper.recognizeLiveStream(proxy)
                }
            }

        provider.unbindAll()
        camera = provider.bindToLifecycle(this, selector, preview, imageAnalyzer)
        preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    // =====================================================================
    //                           RESULT PROCESSING
    // =====================================================================

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        activity?.runOnUiThread {

            val now = System.currentTimeMillis()

            // -------------------- COOLDOWN ------------------------
            if (cooldownActive) {
                if (now < cooldownEndTime) {
                    fragmentCameraBinding.textPrediction.text =
                        "Sentence: $visibleBuffer\nPrediction: "
                    return@runOnUiThread
                }
                cooldownActive = false
                lastSeenGesture = null
                consecutiveFrames = 0
            }

            // -------------------- GET TOP GESTURE ------------------
            val gestureList = resultBundle.results.first().gestures()
            var gesture = "none"

            if (gestureList.isNotEmpty()) {
                val top = gestureList.first().first()
                if (top.score() >= confidenceThreshold) {
                    gesture = top.categoryName()
                }
            }

            // -------------------- LOCK-IN MODEL --------------------
            if (gesture == "none") {
                lastSeenGesture = null
                consecutiveFrames = 0
            } else {
                if (gesture == lastSeenGesture) {
                    consecutiveFrames++
                } else {
                    lastSeenGesture = gesture
                    consecutiveFrames = 1
                }

                if (consecutiveFrames >= requiredFrames) {
                    handleLockedGesture(gesture, now)
                    return@runOnUiThread
                }
            }

            // -------------------- UI UPDATE ------------------------
            val progress = (consecutiveFrames * 100) / requiredFrames
            val predictionText =
                if (lastSeenGesture != null)
                    "${lastSeenGesture} (${progress}%)"
                else
                    "Show a sign"

            fragmentCameraBinding.textPrediction.text =
                "Sentence: $visibleBuffer\nPrediction: $predictionText"

            updateLatency(resultBundle)
            updateOverlay(resultBundle)
        }
    }

    private fun handleLockedGesture(gesture: String, now: Long) {

        when (gesture) {

            // ================= SPACE ====================
            "space" -> {
                spaceRun += 1

                applyAutocorrectOnPreviousWord()

                visibleBuffer.append('_')
                internalBuffer.append(' ')

                when (spaceRun) {
                    2 -> {
                        // convert 2 spaces into a period
                        visibleBuffer.setLength(visibleBuffer.length - 2)
                        visibleBuffer.append('.')

                        internalBuffer.setLength(internalBuffer.length - 2)
                        internalBuffer.append('.')
                    }
                    3 -> {
                        // clear everything
                        visibleBuffer.clear()
                        internalBuffer.clear()
                        spaceRun = 0
                    }
                }
            }

            // ================= DELETE ====================
            "del" -> {
                spaceRun = 0
                if (visibleBuffer.isNotEmpty())
                    visibleBuffer.deleteCharAt(visibleBuffer.length - 1)

                if (internalBuffer.isNotEmpty())
                    internalBuffer.deleteCharAt(internalBuffer.length - 1)
            }

            // ================= LETTER ====================
            else -> {
                spaceRun = 0
                val letter = gesture.uppercase(Locale.US)
                visibleBuffer.append(letter)
                internalBuffer.append(letter)
            }
        }

        // ================= FLASH =======================
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            camera?.cameraControl?.enableTorch(true)
            Handler(Looper.getMainLooper()).postDelayed({
                camera?.cameraControl?.enableTorch(false)
            }, 80)
        }

        // ================= START COOLDOWN ===============
        cooldownActive = true
        cooldownEndTime = now + cooldownDuration

        // reset streak
        lastSeenGesture = null
        consecutiveFrames = 0
    }

    private fun applyAutocorrectOnPreviousWord() {
        val text = internalBuffer.toString()
        val lastSpace = text.lastIndexOf(' ')
        val start = if (lastSpace == -1) 0 else lastSpace + 1

        // extract word only (alphabetic)
        val word = text.substring(start)

        if (word.isNotBlank()) {
            val corrected = viewModel.gestureAutocorrector.correctWord(word)
                .uppercase(Locale.US)

            // replace ONLY the alphabetic word, not spaces
            internalBuffer.replace(start, start + word.length, corrected)
            visibleBuffer.replace(start, start + word.length, corrected)
        }
    }

    private fun countTrailingSpaces(sb: StringBuilder): Int {
        var count = 0
        for (i in sb.length - 1 downTo 0) {
            if (sb[i] == ' ') count++
            else break
        }
        return count
    }

    private fun updateLatency(result: GestureRecognizerHelper.ResultBundle) {
        latencyHistory.add(result.inferenceTime)
        if (latencyHistory.size > rollingWindow) latencyHistory.removeFirst()

        val avg = latencyHistory.average()
        fragmentCameraBinding.textInferenceTime.text = "Avg. Latency: %.0f ms".format(avg)
    }

    private fun updateOverlay(resultBundle: GestureRecognizerHelper.ResultBundle) {
        fragmentCameraBinding.overlay.setResults(
            resultBundle.results.first(),
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            RunningMode.LIVE_STREAM
        )
        fragmentCameraBinding.overlay.invalidate()
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
