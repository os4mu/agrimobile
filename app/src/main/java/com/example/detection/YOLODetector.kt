package com.example.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.cv.ImagePreprocessor
import com.example.cv.LetterboxInfo
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * Data class representing a raw detection returned by YOLO inference.
 */
data class RawDetection(
    val boundingBox: RectF,
    val classIndex: Int,
    val label: String,
    val score: Float,
    val origWidth: Int = 640,
    val origHeight: Int = 640
)

/**
 * YOLODetector handles loading trained YOLOv8 TFLite models (specifically best_float16.tflite),
 * preprocessing camera images to 640x640 via letterboxing, running TFLite Interpreter inference,
 * parsing raw YOLOv8 outputs, confidence filtering, and applying class-aware Non-Maximum Suppression (NMS).
 */
class YOLODetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var cachedLetterboxBitmap: Bitmap? = null
    private var cachedInputBuffer: ByteBuffer? = null
    private var cachedIntValues: IntArray? = null
    private var cachedFloatOutput: Array<Array<FloatArray>>? = null
    var loadedModelName: String? = null
        private set

    var loadError: String? = null
        private set

    var lastInferenceError: String? = null
        private set

    var modelSizeBytes: Long = 0L
        private set

    var inputShapeStr: String = "N/A"
        private set

    var inputTypeStr: String = "N/A"
        private set

    var inputWidth: Int = 640
        private set

    var inputHeight: Int = 640
        private set

    var isNCHW: Boolean = false
        private set

    var outputShapeStr: String = "N/A"
        private set

    var outputTypeStr: String = "N/A"
        private set

    var lastInferenceTimeMs: Long = 0L
        private set

    var lastTopClass: String = "N/A"
        private set

    var lastTopScore: Float = 0f
        private set

    var lastRawCandidateCount: Int = 0
        private set

    var lastNmsCount: Int = 0
        private set

    var inputScale: Float = 0f
        private set

    var inputZeroPoint: Int = 0
        private set

    var outputScale: Float = 0f
        private set

    var outputZeroPoint: Int = 0
        private set

    var lastMinRawScore: Float = 0f
        private set

    var lastMaxRawScore: Float = 0f
        private set

    var lastCandidateCountAfterConfidence: Int = 0
        private set

    var lastTopClassId: Int = -1
        private set

    var labels: List<String> = listOf(
        "brodan_insecticide_100ml",
        "brodan_insecticide_1L",
        "brodan_insecticide_250ml",
        "brodan_insecticide_500ml",
        "fostermilk",
        "majesty_catfood",
        "ronstar_herbicide_1L",
        "ronstar_herbicide_500ml",
        "roundup_herbicide_1L",
        "roundup_herbicide_4L",
        "sp_2.4D_herbicide_amine_500ml",
        "sp_2.4D_herbicide_ester_1L",
        "sp_2.4D_herbicide_ester_250ml",
        "sp_2.4D_herbicide_ester_500ml",
        "swire_ammosul",
        "thunderbird_babystagbooster_brooding",
        "thunderbird_enertone_maintenance",
        "thunderbird_platinum_conditioning",
        "thunderbird_stagdeveloper4plus_ranging",
        "thunderbird_stagdeveloper_ranging",
        "vitminpro_premiumpack"
    )
        private set

    init {
        loadLabels()
        loadModel()
    }

    /**
     * Loads labels dynamically from assets/labels.txt if present.
     */
    private fun loadLabels() {
        try {
            val assetFiles = context.assets.list("") ?: emptyArray()
            val labelFileName = assetFiles.firstOrNull { 
                it.equals("labels.txt", ignoreCase = true) || it.equals("labels", ignoreCase = true)
            }
            if (labelFileName != null) {
                context.assets.open(labelFileName).bufferedReader().useLines { lines ->
                    val customLabels = lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { line ->
                            line.replaceFirst(Regex("^\\d+[\\s:,]+"), "").trim()
                        }
                        .filter { it.isNotEmpty() }
                        .toList()
                    if (customLabels.isNotEmpty()) {
                        labels = customLabels
                        Log.d(TAG, "Loaded ${labels.size} labels from asset '$labelFileName'")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load custom labels file from assets: ${e.message}")
        }
    }

    /**
     * Loads best_float16.tflite robustly from assets and initializes TFLite Interpreter.
     */
    private fun loadModel() {
        try {
            val assetFiles = context.assets.list("") ?: emptyArray()
            
            // Target your exact model file from assets
            val candidateNames = listOf(
                "v2best_float16.tflite",
                "v2best.tflite",
                "v2.tflite"
            ) + assetFiles.filter { it.endsWith(".tflite", ignoreCase = true) }

            var loadedByteBuffer: ByteBuffer? = null
            var matchedName: String? = null
            var sizeBytes: Long = 0L

            for (fileName in candidateNames.distinct()) {
                val pair = loadModelBufferRobust(context, fileName)
                if (pair != null) {
                    loadedByteBuffer = pair.first
                    sizeBytes = pair.second
                    matchedName = fileName
                    break
                }
            }

            if (loadedByteBuffer != null && matchedName != null) {
                val options = Interpreter.Options()
                try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val delegateOptions = compatList.bestOptionsForThisDevice
                        val gpu = GpuDelegate(delegateOptions)
                        options.addDelegate(gpu)
                        gpuDelegate = gpu
                        Log.d(TAG, "TFLite GPU acceleration ENABLED successfully")
                    } else {
                        Log.d(TAG, "GPU delegate not supported on device; using 4 CPU threads")
                        options.setNumThreads(4)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "GPU acceleration fallback to CPU: ${e.message}")
                    options.setNumThreads(4)
                }

                val interp = Interpreter(loadedByteBuffer, options)
                interpreter = interp
                loadedModelName = matchedName
                loadError = null
                modelSizeBytes = sizeBytes

                val inputTensor = interp.getInputTensor(0)
                val outputTensor = interp.getOutputTensor(0)

                val inputQuant = inputTensor.quantizationParams()
                val outputQuant = outputTensor.quantizationParams()

                val inShape = inputTensor.shape()
                inputShapeStr = inShape.contentToString()
                inputTypeStr = inputTensor.dataType().toString()
                inputScale = inputQuant.scale
                inputZeroPoint = inputQuant.zeroPoint

                if (inShape.size == 4) {
                    if (inShape[3] == 3) {
                        // NHWC: [batch, height, width, channels]
                        inputHeight = inShape[1]
                        inputWidth = inShape[2]
                        isNCHW = false
                    } else if (inShape[1] == 3) {
                        // NCHW: [batch, channels, height, width]
                        inputHeight = inShape[2]
                        inputWidth = inShape[3]
                        isNCHW = true
                    } else {
                        inputHeight = inShape[1]
                        inputWidth = inShape[2]
                        isNCHW = false
                    }
                } else {
                    inputHeight = 640
                    inputWidth = 640
                    isNCHW = false
                }

                outputShapeStr = outputTensor.shape().contentToString()
                outputTypeStr = outputTensor.dataType().toString()
                outputScale = outputQuant.scale
                outputZeroPoint = outputQuant.zeroPoint

                Log.d(TAG, "YOLO_MODEL = $matchedName")
                Log.d(TAG, "MODEL_SIZE = $modelSizeBytes")
                Log.d(TAG, "INPUT_SHAPE = $inputShapeStr")
                Log.d(TAG, "INPUT_TYPE = $inputTypeStr")
                Log.d(TAG, "INPUT_SCALE = $inputScale")
                Log.d(TAG, "INPUT_ZERO_POINT = $inputZeroPoint")
                Log.d(TAG, "OUTPUT_SHAPE = $outputShapeStr")
                Log.d(TAG, "OUTPUT_TYPE = $outputTypeStr")
                Log.d(TAG, "OUTPUT_SCALE = $outputScale")
                Log.d(TAG, "OUTPUT_ZERO_POINT = $outputZeroPoint")
                Log.d(TAG, "YOLO MODEL LOADED SUCCESSFULLY")
            } else {
                val err = "No valid .tflite model file could be loaded from assets."
                loadError = err
                Log.e(TAG, "YOLO_MODEL_FOUND = false")
                Log.e(TAG, "YOLO MODEL LOADING FAILED: $err")
            }
        } catch (e: Exception) {
            val stackTrace = Log.getStackTraceString(e)
            loadError = "Model load failed: ${e.message}"
            Log.e(TAG, "YOLO_MODEL_FOUND = false")
            Log.e(TAG, "YOLO MODEL LOADING FAILED\n$stackTrace")
        }
    }

    /**
     * Helper to load ByteBuffer robustly via direct stream reading or asset descriptor.
     */
    private fun loadModelBufferRobust(context: Context, fileName: String): Pair<ByteBuffer, Long>? {
        // Method 1: Direct stream read (works even if compressed by AGP)
        try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()
            if (bytes.isNotEmpty()) {
                val buffer = ByteBuffer.allocateDirect(bytes.size)
                buffer.order(ByteOrder.nativeOrder())
                buffer.put(bytes)
                buffer.rewind()
                Log.d(TAG, "Loaded '$fileName' via direct stream read (${bytes.size} bytes)")
                return Pair(buffer, bytes.size.toLong())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream read failed for '$fileName': ${e.message}")
        }

        // Method 2: AssetFileDescriptor map (uncompressed fallback)
        try {
            val fileDescriptor = context.assets.openFd(fileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            Log.d(TAG, "Loaded '$fileName' via FileChannel map ($declaredLength bytes)")
            return Pair(mappedBuffer, declaredLength)
        } catch (e: Exception) {
            Log.w(TAG, "AssetFileDescriptor map failed for '$fileName': ${e.message}")
        }

        return null
    }

    /**
     * Preprocesses input camera bitmap via letterboxing to 640x640, converts to input RGB buffer,
     * executes inference via TFLite Interpreter, parses YOLOv8 detection boxes, applies confidence
     * filtering (default 0.25f), and applies class-aware Non-Maximum Suppression (NMS).
     */
    @Synchronized
    fun detect(bitmap: Bitmap, confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD): List<RawDetection> {
        val tflite = interpreter
        if (tflite == null) {
            val err = "Cannot run detection: YOLOv8 model is not loaded (loadError=$loadError)"
            Log.e(TAG, err)
            lastInferenceError = err
            return emptyList()
        }

        try {
            Log.d(TAG, "CAMERA_FRAME = ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "BITMAP = ${bitmap.width}x${bitmap.height}")

            if (cachedLetterboxBitmap == null || cachedLetterboxBitmap!!.width != inputWidth || cachedLetterboxBitmap!!.height != inputHeight) {
                cachedLetterboxBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
            }
            val letterboxInfo = ImagePreprocessor.processLetterbox(bitmap, inputWidth, inputHeight, cachedLetterboxBitmap)
            Log.d(TAG, "ORIGINAL_IMAGE = ${letterboxInfo.origWidth}x${letterboxInfo.origHeight}")
            Log.d(TAG, "LETTERBOX_SCALE = ${letterboxInfo.scale}")
            Log.d(TAG, "PAD_X = ${letterboxInfo.padX}")
            Log.d(TAG, "PAD_Y = ${letterboxInfo.padY}")

            val inputTensor = tflite.getInputTensor(0)
            val expectedInputBytes = inputTensor.numBytes()
            val inputBuffer = convertBitmapToByteBuffer(
                letterboxInfo.bitmap,
                inputTensor.dataType(),
                inputTensor.quantizationParams(),
                inputWidth,
                inputHeight,
                isNCHW
            )

            Log.d(TAG, "INPUT_BUFFER_SIZE = ${inputBuffer.capacity()}")
            Log.d(TAG, "EXPECTED_INPUT_BYTES = $expectedInputBytes")

            if (inputBuffer.capacity() != expectedInputBytes) {
                val err = "Input buffer capacity mismatch: capacity=${inputBuffer.capacity()} != expected=$expectedInputBytes"
                Log.e(TAG, err)
                lastInferenceError = err
                return emptyList()
            }

            val outputTensor = tflite.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            val outputDataType = outputTensor.dataType()
            val outputQuant = outputTensor.quantizationParams()

            Log.d(TAG, "OUTPUT_SHAPE = ${outputShape.contentToString()}")
            Log.d(TAG, "OUTPUT_TYPE = $outputDataType")
            Log.d(TAG, "OUTPUT_NUM_BYTES = ${outputTensor.numBytes()}")

            val floatOutput: Array<Array<FloatArray>>
            val startTime = System.currentTimeMillis()

            try {
                if (outputDataType == DataType.INT8 || outputDataType == DataType.UINT8) {
                    val scale = outputQuant.scale
                    val zeroPoint = outputQuant.zeroPoint

                    val rawOutput = Array(outputShape[0]) {
                        Array(outputShape[1]) {
                            ByteArray(outputShape[2])
                        }
                    }
                    tflite.run(inputBuffer, rawOutput)

                    floatOutput = Array(outputShape[0]) { i ->
                        Array(outputShape[1]) { j ->
                            FloatArray(outputShape[2]) { k ->
                                val b = rawOutput[i][j][k]
                                val rawVal = if (outputDataType == DataType.UINT8) (b.toInt() and 0xFF) else b.toInt()
                                if (scale != 0f) (rawVal - zeroPoint) * scale else rawVal.toFloat()
                            }
                        }
                    }
                } else {
                    if (cachedFloatOutput == null || cachedFloatOutput!!.size != outputShape[0] ||
                        cachedFloatOutput!![0].size != outputShape[1] || cachedFloatOutput!![0][0].size != outputShape[2]
                    ) {
                        cachedFloatOutput = Array(outputShape[0]) {
                            Array(outputShape[1]) {
                                FloatArray(outputShape[2])
                            }
                        }
                    }
                    floatOutput = cachedFloatOutput!!
                    tflite.run(inputBuffer, floatOutput)
                }
            } catch (e: Exception) {
                val stackTrace = Log.getStackTraceString(e)
                val err = "YOLO_INFERENCE_ERROR\nType: ${e.javaClass.simpleName}\nMessage: ${e.message}\nStackTrace:\n$stackTrace"
                Log.e(TAG, err)
                lastInferenceError = err
                throw e
            }

            lastInferenceTimeMs = System.currentTimeMillis() - startTime
            lastInferenceError = null
            Log.d(TAG, "INFERENCE_TIME_MS = $lastInferenceTimeMs")

            val rawDetections = parseYoloOutput(floatOutput, confidenceThreshold, letterboxInfo)
            lastRawCandidateCount = rawDetections.size

            val finalDetections = applyRobustNMS(
                rawDetections,
                intraClassIouThreshold = IOU_THRESHOLD,
                crossClassIouThreshold = CROSS_CLASS_IOU_THRESHOLD,
                maxDetections = MAX_DETECTIONS_CAP
            )
            lastNmsCount = finalDetections.size
            Log.d(TAG, "AFTER_NMS = $lastNmsCount")

            return finalDetections
        } catch (e: Exception) {
            val err = "Error in YOLO detection pipeline: ${e.message}"
            Log.e(TAG, err, e)
            lastInferenceError = err
            return emptyList()
        }
    }

    /**
     * Converts a Bitmap to ByteBuffer according to the model's expected shape, DataType, and Quantization.
     */
    private fun convertBitmapToByteBuffer(
        bitmap: Bitmap,
        dataType: DataType,
        quantization: Tensor.QuantizationParams,
        width: Int,
        height: Int,
        isNCHW: Boolean = false
    ): ByteBuffer {
        val bytesPerChannel = when (dataType) {
            DataType.FLOAT32 -> 4
            DataType.INT8, DataType.UINT8 -> 1
            else -> 4
        }
        val requiredCapacity = bytesPerChannel * width * height * 3
        if (cachedInputBuffer == null || cachedInputBuffer!!.capacity() != requiredCapacity) {
            cachedInputBuffer = ByteBuffer.allocateDirect(requiredCapacity).apply {
                order(ByteOrder.nativeOrder())
            }
        }
        val byteBuffer = cachedInputBuffer!!
        byteBuffer.rewind()

        if (cachedIntValues == null || cachedIntValues!!.size != width * height) {
            cachedIntValues = IntArray(width * height)
        }
        val intValues = cachedIntValues!!
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height)

        val scale = if (quantization.scale != 0f) quantization.scale else (1f / 255f)
        val zeroPoint = quantization.zeroPoint

        if (!isNCHW) {
            // NHWC format
            var pixel = 0
            for (i in 0 until height) {
                for (j in 0 until width) {
                    val value = intValues[pixel++]
                    val r = ((value shr 16) and 0xFF) / 255.0f
                    val g = ((value shr 8) and 0xFF) / 255.0f
                    val b = (value and 0xFF) / 255.0f

                    when (dataType) {
                        DataType.FLOAT32 -> {
                            byteBuffer.putFloat(r)
                            byteBuffer.putFloat(g)
                            byteBuffer.putFloat(b)
                        }
                        DataType.INT8 -> {
                            byteBuffer.put(((r / scale) + zeroPoint).toInt().coerceIn(-128, 127).toByte())
                            byteBuffer.put(((g / scale) + zeroPoint).toInt().coerceIn(-128, 127).toByte())
                            byteBuffer.put(((b / scale) + zeroPoint).toInt().coerceIn(-128, 127).toByte())
                        }
                        DataType.UINT8 -> {
                            byteBuffer.put(((r / scale) + zeroPoint).toInt().coerceIn(0, 255).toByte())
                            byteBuffer.put(((g / scale) + zeroPoint).toInt().coerceIn(0, 255).toByte())
                            byteBuffer.put(((b / scale) + zeroPoint).toInt().coerceIn(0, 255).toByte())
                        }
                        else -> {
                            byteBuffer.putFloat(r)
                            byteBuffer.putFloat(g)
                            byteBuffer.putFloat(b)
                        }
                    }
                }
            }
        } else {
            // NCHW format: planar R, then G, then B
            val numPixels = width * height
            for (p in 0 until numPixels) {
                val value = intValues[p]
                val r = ((value shr 16) and 0xFF) / 255.0f
                putPixelChannel(byteBuffer, r, dataType, scale, zeroPoint)
            }
            for (p in 0 until numPixels) {
                val value = intValues[p]
                val g = ((value shr 8) and 0xFF) / 255.0f
                putPixelChannel(byteBuffer, g, dataType, scale, zeroPoint)
            }
            for (p in 0 until numPixels) {
                val value = intValues[p]
                val b = (value and 0xFF) / 255.0f
                putPixelChannel(byteBuffer, b, dataType, scale, zeroPoint)
            }
        }
        return byteBuffer
    }

    private fun putPixelChannel(
        byteBuffer: ByteBuffer,
        value: Float,
        dataType: DataType,
        scale: Float,
        zeroPoint: Int
    ) {
        when (dataType) {
            DataType.FLOAT32 -> byteBuffer.putFloat(value)
            DataType.INT8 -> byteBuffer.put(((value / scale) + zeroPoint).toInt().coerceIn(-128, 127).toByte())
            DataType.UINT8 -> byteBuffer.put(((value / scale) + zeroPoint).toInt().coerceIn(0, 255).toByte())
            else -> byteBuffer.putFloat(value)
        }
    }

    private fun sigmoid(x: Float): Float = 1.0f / (1.0f + exp(-x))

    /**
     * Parses raw YOLOv8 tensor predictions into detection bounding boxes mapped back to original image space.
     */
    private fun parseYoloOutput(
        output: Array<Array<FloatArray>>,
        confidenceThreshold: Float,
        letterboxInfo: LetterboxInfo
    ): List<RawDetection> {
        val candidates = mutableListOf<RawDetection>()
        val predictions = output[0]

        val rows = predictions.size
        val cols = predictions[0].size

        val isTransposed = rows < cols && rows <= 100
        val numChannels = if (isTransposed) rows else cols
        val numAnchors = if (isTransposed) cols else rows

        // Ultralytics YOLOv8 export: [4 box params + numClasses], NO objectness channel.
        // Class count comes from the model output, never from labels.txt.
        val hasObjectness = false
        val boxParamCount = 4
        val actualNumClasses = numChannels - boxParamCount
        if (labels.size != actualNumClasses) {
            Log.e(TAG, "LABEL_MISMATCH: model has $actualNumClasses classes but labels.txt has ${labels.size}. Fix labels.txt!")
        }

        Log.d(TAG, "YOLO_CHANNELS = $numChannels, HAS_OBJ = $hasObjectness, NUM_CLASSES = $actualNumClasses")

        var globalTopProbScore = 0f
        var globalTopClassIdx = -1
        var minScore = Float.MAX_VALUE
        var maxScore = -Float.MAX_VALUE

        val minRawObjConf = -1.1f // sigmoid(-1.1) approx 0.25 (background threshold)

        for (i in 0 until numAnchors) {
            val rawObjConf = if (hasObjectness) {
                if (isTransposed) predictions[4][i] else predictions[i][4]
            } else {
                0.0f
            }

            // High-speed early skip in logit space: skips 95%+ background anchors without exp() calls
            if (hasObjectness && rawObjConf < minRawObjConf) {
                continue
            }

            var maxRawClassScore = -Float.MAX_VALUE
            var bestClass = -1

            for (c in 0 until actualNumClasses) {
                val channelIdx = boxParamCount + c
                val rawScore = if (isTransposed) predictions[channelIdx][i] else predictions[i][channelIdx]
                if (rawScore > maxRawClassScore) {
                    maxRawClassScore = rawScore
                    bestClass = c
                }
            }

            // Properly resolve score whether model output is raw logit or pre-sigmoid probability
            val objConf = if (hasObjectness) {
                if (rawObjConf in 0.0f..1.0f) rawObjConf else sigmoid(rawObjConf)
            } else {
                1.0f
            }

            val maxClassScore = if (maxRawClassScore in 0.0f..1.0f) {
                maxRawClassScore
            } else {
                sigmoid(maxRawClassScore)
            }

            val finalScore = objConf * maxClassScore
            if (finalScore < minScore) minScore = finalScore
            if (finalScore > maxScore) maxScore = finalScore

            if (finalScore > globalTopProbScore) {
                globalTopProbScore = finalScore
                globalTopClassIdx = bestClass
            }

            if (finalScore >= confidenceThreshold && bestClass >= 0) {
                var cx = if (isTransposed) predictions[0][i] else predictions[i][0]
                var cy = if (isTransposed) predictions[1][i] else predictions[i][1]
                var w = if (isTransposed) predictions[2][i] else predictions[i][2]
                var h = if (isTransposed) predictions[3][i] else predictions[i][3]

                // Standardize to model input resolution if coordinates are normalized [0.0, 1.0]
                if (cx <= 1.0f && cy <= 1.0f && w <= 1.0f && h <= 1.0f) {
                    cx *= inputWidth
                    cy *= inputHeight
                    w *= inputWidth
                    h *= inputHeight
                }

                val leftTarget = cx - w / 2f
                val topTarget = cy - h / 2f
                val rightTarget = cx + w / 2f
                val bottomTarget = cy + h / 2f

                val boxW = rightTarget - leftTarget
                val boxH = bottomTarget - topTarget

                // Discard invalid, collapsed or micro boxes (must be at least 24px wide and high in model space)
                if (boxW >= 24f && boxH >= 24f) {
                    val origRect = ImagePreprocessor.unletterbox(
                        leftTarget, topTarget, rightTarget, bottomTarget,
                        letterboxInfo
                    )

                    val label = labels.getOrElse(bestClass) { "Product #$bestClass" }
                    candidates.add(
                        RawDetection(
                            boundingBox = origRect,
                            classIndex = bestClass,
                            label = label,
                            score = finalScore,
                            origWidth = letterboxInfo.origWidth,
                            origHeight = letterboxInfo.origHeight
                        )
                    )
                }
            }
        }

        lastMinRawScore = if (minScore != Float.MAX_VALUE) minScore else 0f
        lastMaxRawScore = if (maxScore != -Float.MAX_VALUE) maxScore else 0f
        lastCandidateCountAfterConfidence = candidates.size
        lastTopClassId = globalTopClassIdx
        lastTopScore = globalTopProbScore
        lastTopClass = labels.getOrElse(globalTopClassIdx) { "N/A" }

        Log.d(TAG, "RAW_OUTPUT_MIN = ${String.format("%.4f", lastMinRawScore)}")
        Log.d(TAG, "RAW_OUTPUT_MAX = ${String.format("%.4f", lastMaxRawScore)}")
        Log.d(TAG, "RAW_CANDIDATES = $numAnchors")
        Log.d(TAG, "AFTER_CONFIDENCE = $lastCandidateCountAfterConfidence")
        Log.d(TAG, "TOP_CLASS_ID = $lastTopClassId")
        Log.d(TAG, "TOP_CLASS_NAME = $lastTopClass")
        Log.d(TAG, "TOP_CONFIDENCE = ${String.format("%.4f", lastTopScore)}")

        return candidates
    }

    /**
     * Applies robust Non-Maximum Suppression (NMS) with:
     *
     * 1. Stage 1 (Intra-Class Suppression with intraClassIouThreshold=0.45f):
     *    Eliminates redundant overlapping anchor boxes predicting the SAME class.
     *    Keeps only the highest-scoring bounding box for that class.
     *
     * 2. Stage 2 (Cross-Class Spatial Deduplication with crossClassIouThreshold=0.30f):
     *    Eliminates competing/conflicting predictions where visually near-identical SKU classes
     *    (e.g., 250ml vs 500ml vs 1L variants or same brand conditioning) fire on the EXACT same
     *    physical product. The prediction with the highest confidence score wins.
     *    A lower IoU (0.30f) is intentionally used here because slightly offset bounding boxes
     *    between different predicted classes on the same physical object still represent the same item.
     *
     * 3. Stage 3 (Safety Hard Cap with maxDetections=50):
     *    Limits total output to the top N detections by score to prevent runaway box explosions.
     */
    private fun applyRobustNMS(
        detections: List<RawDetection>,
        intraClassIouThreshold: Float = IOU_THRESHOLD,
        crossClassIouThreshold: Float = CROSS_CLASS_IOU_THRESHOLD,
        maxDetections: Int = MAX_DETECTIONS_CAP
    ): List<RawDetection> {
        if (detections.isEmpty()) return emptyList()

        // Stage 1: Intra-class suppression
        // Groups candidates by classIndex so duplicate boxes of the same class are pruned first.
        val groupedByClass = detections.groupBy { it.classIndex }
        val classSuppressed = mutableListOf<RawDetection>()

        for ((_, classDetections) in groupedByClass) {
            val sorted = classDetections.sortedByDescending { it.score }
            val keepForClass = mutableListOf<RawDetection>()

            for (candidate in sorted) {
                var keep = true
                for (saved in keepForClass) {
                    val iou = calculateIoU(candidate.boundingBox, saved.boundingBox)
                    // Overlap threshold for same-class duplicates (0.45)
                    if (iou > intraClassIouThreshold) {
                        keep = false
                        break
                    }
                }
                if (keep) {
                    keepForClass.add(candidate)
                }
            }
            classSuppressed.addAll(keepForClass)
        }

        // Stage 2: Cross-class spatial deduplication
        // Sort globally by confidence descending so the most confident class label always takes precedence.
        val sortedGlobal = classSuppressed.sortedByDescending { it.score }
        val finalSelected = mutableListOf<RawDetection>()

        for (candidate in sortedGlobal) {
            var keep = true
            for (saved in finalSelected) {
                val iou = calculateIoU(candidate.boundingBox, saved.boundingBox)
                // Overlap threshold for cross-class deduplication (0.30).
                // If two different classes point to the same physical object, keep only the higher-confidence one!
                if (iou > crossClassIouThreshold) {
                    keep = false
                    break
                }
            }
            if (keep) {
                finalSelected.add(candidate)
                // Stage 3: Hard cap safety ceiling (top 50 detections by score)
                if (finalSelected.size >= maxDetections) {
                    break
                }
            }
        }

        return finalSelected
    }

    /**
     * Calculates Intersection over Union (IoU) between two RectF bounding boxes.
     */
    private fun calculateIoU(a: RectF, b: RectF): Float {
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        if (areaA <= 0f || areaB <= 0f) return 0f

        val intersectionLeft = maxOf(a.left, b.left)
        val intersectionTop = maxOf(a.top, b.top)
        val intersectionRight = minOf(a.right, b.right)
        val intersectionBottom = minOf(a.bottom, b.bottom)

        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) return 0f

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    /**
     * Runs real product image inference through the YOLO pipeline to verify
     * model loading, input buffer creation, inference execution, and output parsing independently of CameraX.
     */
    fun runStaticTest(inputBitmap: Bitmap? = null): List<RawDetection> {
        Log.d(TAG, "=== STARTING YOLO REAL PRODUCT MODEL TEST ===")
        val testBitmap = inputBitmap ?: Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val testDetections = detect(testBitmap, confidenceThreshold = DEFAULT_CONFIDENCE_THRESHOLD)

        Log.d(TAG, "MODEL LOADED: ${interpreter != null}")
        Log.d(TAG, "INPUT VALID: ${interpreter != null && inputShapeStr != "N/A"}")
        Log.d(TAG, "INFERENCE SUCCESS: ${lastInferenceError == null}")
        Log.d(TAG, "INFERENCE TIME: $lastInferenceTimeMs ms")
        Log.d(TAG, "TOP SCORE: ${String.format("%.4f", lastTopScore)}")
        Log.d(TAG, "TOP CLASS: $lastTopClass")
        Log.d(TAG, "DETECTION COUNT: ${testDetections.size}")
        
        if (testDetections.isEmpty()) {
            Log.d(TAG, "TOP PREDICTION: class = $lastTopClass (id=$lastTopClassId), score = ${String.format("%.4f", lastTopScore)}")
        } else {
            for (d in testDetections) {
                Log.d(TAG, "DETECTION: classId=${d.classIndex}, label=${d.label}, confidence=${String.format("%.4f", d.score)}, box=${d.boundingBox}")
            }
        }
        Log.d(TAG, "=== END YOLO REAL PRODUCT MODEL TEST ===")

        return testDetections
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        cachedLetterboxBitmap?.recycle()
        cachedLetterboxBitmap = null
        cachedInputBuffer = null
        cachedIntValues = null
        cachedFloatOutput = null
    }

    companion object {
        private const val TAG = "YOLODetector"
        const val INPUT_SIZE = 640

        /**
         * Confidence threshold for raw candidate detection filtering.
         * Set to 0.25f (25%) as standard YOLO default to detect real physical items
         * (including faint or partially occluded products).
         */
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.20f

        /**
         * IoU threshold for intra-class Non-Maximum Suppression (NMS).
         * Used to suppress duplicate bounding boxes predicting the EXACT same product class.
         * A standard threshold of 0.45f allows tightly packed distinct items to coexist.
         */
        const val IOU_THRESHOLD = 0.45f

        /**
         * IoU threshold for cross-class spatial deduplication.
         * Used to resolve conflicts where multiple near-identical SKU classes (e.g. 250ml vs 500ml vs 1L)
         * fire predictions over the same physical product.
         * Set lower (0.30f) than intra-class NMS because bounding boxes predicted by different classes
         * on the same bottle may be slightly shifted or have less mutual overlap.
         */
        const val CROSS_CLASS_IOU_THRESHOLD = 0.30f

        /**
         * Maximum cap on the total number of detections returned after NMS.
         * Serves as a critical safety valve against runaway output, UI lag, and duplicate box floods
         * when scanning complex or textured scenes. Real retail/warehouse shelf scans contain 5-25 items.
         */
        const val MAX_DETECTIONS_CAP = 50
    }
}

