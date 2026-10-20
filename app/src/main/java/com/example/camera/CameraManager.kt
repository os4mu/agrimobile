package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.cv.DetectionResult
import com.example.cv.YOLODetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager handles CameraX lifecycle binding, preview initialization,
 * and background frame processing for YOLODetector inference.
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var latestFrame: Bitmap? = null

    @Volatile
    private var isAnalyzing = false

    /**
     * Initializes CameraX preview and continuous real-time YOLOv8 frame analyzer.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrameAvailable: ((Bitmap) -> Unit)? = null
    ) {
        startLiveDetectionCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            yoloDetector = null,
            onDetectionResult = null,
            onFrameAvailable = onFrameAvailable
        )
    }

    /**
     * Starts CameraX with continuous background YOLOv8 inference pipeline and live callback.
     */
    fun startLiveDetectionCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        yoloDetector: YOLODetector? = null,
        onDetectionResult: ((DetectionResult) -> Unit)? = null,
        onFrameAvailable: ((Bitmap) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                // Configure Preview Use Case
                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Configure Image Analysis Use Case with STRATEGY_KEEP_ONLY_LATEST
                imageAnalyzer = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            Log.d(TAG, "FRAME_RECEIVED")
                            
                            val frame = processImageProxy(imageProxy)
                            if (frame != null) {
                                latestFrame = frame
                                onFrameAvailable?.invoke(frame)
                            }

                            if (yoloDetector != null && onDetectionResult != null) {
                                if (isAnalyzing) {
                                    Log.d(TAG, "FRAME_SKIPPED_INFERENCE_BUSY")
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                isAnalyzing = true
                                Log.d(TAG, "INFERENCE_STARTED")
                                val startTime = System.currentTimeMillis()

                                try {
                                    if (frame != null) {
                                        val result = yoloDetector.detect(frame)
                                        val inferenceTimeMs = System.currentTimeMillis() - startTime
                                        Log.d(TAG, "INFERENCE_TIME_MS = $inferenceTimeMs")
                                        Log.d(TAG, "DETECTION_COUNT = ${result.boundingBoxes.size}")
                                        Log.d(TAG, "OVERLAY_UPDATED")

                                        mainExecutor.execute {
                                            onDetectionResult.invoke(result)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "YOLO_INFERENCE_ERROR", e)
                                } finally {
                                    isAnalyzing = false
                                    imageProxy.close()
                                }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                Log.d(TAG, "LIVE_YOLO_STARTED")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX live detection preview", e)
            }
        }, mainExecutor)
    }

    /**
     * Converts raw CameraX ImageProxy to a correctly rotated Bitmap.
     */
    private fun processImageProxy(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val rotated = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            Log.d(TAG, "FRAME_SIZE = ${rotated.width}x${rotated.height}")

            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            null
        }
    }

    /**
     * Returns the latest camera frame bitmap or null if not available.
     */
    fun getLatestFrame(): Bitmap? {
        return latestFrame
    }

    /**
     * Executes real YOLO detection using the current camera frame.
     */
    fun processCurrentFrameWithYolo(
        yoloDetector: YOLODetector
    ): DetectionResult {
        val frame = getLatestFrame()
        if (frame == null) {
            Log.w(TAG, "No camera frame available for YOLO inference")
            return DetectionResult(
                productName = "Camera Feed Initializing",
                sku = "N/A",
                confidence = 0f,
                quantity = 0,
                unit = "units",
                boundingBoxes = emptyList(),
                rawDetections = emptyList()
            )
        }
        return yoloDetector.detect(frame)
    }

    /**
     * Toggles flashlight / torch state on camera.
     */
    fun toggleTorch(enabled: Boolean) {
        try {
            camera?.cameraControl?.enableTorch(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch: ${e.message}")
        }
    }

    /**
     * Unbinds camera use cases and shuts down background executor thread.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            latestFrame = null
            Log.d(TAG, "CameraX unbound and executor stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping CameraX", e)
        }
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
