package com.example.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.data.ProductCatalog
import com.example.detection.RawDetection
import com.example.detection.YOLODetector as CoreYOLODetector

data class BoundingBox(
    val rect: RectF,
    val label: String,
    val confidence: Float,
    val origWidth: Int = 640,
    val origHeight: Int = 640
)

data class DetectedProductItem(
    val productId: String,
    val sku: String,
    val productName: String,
    val categoryId: String = "cat-001",
    val category: String = "General",
    val uomLabel: String = "bottle",
    val unit: String = "bottle",
    val reorderPoint: Int = 10,
    val confidence: Float,
    val quantity: Int,
    val classIndex: Int = 0,
    val label: String = ""
)

data class DetectionResult(
    val productId: String = "prod-001",
    val productName: String,
    val sku: String,
    val categoryId: String = "cat-001",
    val category: String = "General",
    val uomLabel: String = "bottle",
    val unit: String = "bottle",
    val reorderPoint: Int = 10,
    val confidence: Float,
    val quantity: Int,
    val detectedProducts: List<DetectedProductItem> = emptyList(),
    val boundingBoxes: List<BoundingBox> = emptyList(),
    val rawDetections: List<RawDetection> = emptyList()
)

class YOLODetector(private val context: Context) {

    private val coreDetector = CoreYOLODetector(context)

    val activeModelName: String?
        get() = coreDetector.loadedModelName

    val loadError: String?
        get() = coreDetector.loadError

    val lastInferenceError: String?
        get() = coreDetector.lastInferenceError

    val modelSizeBytes: Long
        get() = coreDetector.modelSizeBytes

    val inputShapeStr: String
        get() = coreDetector.inputShapeStr

    val inputTypeStr: String
        get() = coreDetector.inputTypeStr

    val outputShapeStr: String
        get() = coreDetector.outputShapeStr

    val outputTypeStr: String
        get() = coreDetector.outputTypeStr

    val lastInferenceTimeMs: Long
        get() = coreDetector.lastInferenceTimeMs

    val lastTopClass: String
        get() = coreDetector.lastTopClass

    val lastTopScore: Float
        get() = coreDetector.lastTopScore

    val lastRawCandidateCount: Int
        get() = coreDetector.lastRawCandidateCount

    val lastNmsCount: Int
        get() = coreDetector.lastNmsCount

    val inputScale: Float
        get() = coreDetector.inputScale

    val inputZeroPoint: Int
        get() = coreDetector.inputZeroPoint

    val outputScale: Float
        get() = coreDetector.outputScale

    val outputZeroPoint: Int
        get() = coreDetector.outputZeroPoint

    val lastMinRawScore: Float
        get() = coreDetector.lastMinRawScore

    val lastMaxRawScore: Float
        get() = coreDetector.lastMaxRawScore

    val lastCandidateCountAfterConfidence: Int
        get() = coreDetector.lastCandidateCountAfterConfidence

    val lastTopClassId: Int
        get() = coreDetector.lastTopClassId

    val activeLabelsCount: Int
        get() = coreDetector.labels.size

    fun runStaticTest(): List<RawDetection> {
        return coreDetector.runStaticTest()
    }

    /**
     * Executes real YOLOv8 inference pipeline on input frame bitmap using core TFLite detector.
     * Maps every raw detection independently to the product catalog without category filtering.
     */
    fun detect(bitmap: Bitmap, confidenceThreshold: Float = CoreYOLODetector.DEFAULT_CONFIDENCE_THRESHOLD): DetectionResult {
        val rawDetections = coreDetector.detect(bitmap, confidenceThreshold = confidenceThreshold)

        val topLabel = coreDetector.lastTopClass
        val topScore = coreDetector.lastTopScore
        val detectedCount = rawDetections.size
        val preprocessPath = if (ImagePreprocessor.FORCE_ANDROID_FALLBACK || !OpenCvInit.isReady) "ANDROID_FALLBACK" else "OPENCV"
        val timeMs = coreDetector.lastInferenceTimeMs

        Log.i("DETECT_SUMMARY", "Top: $topLabel (${"%.2f".format(topScore)}), Detected: $detectedCount, Path: $preprocessPath, Time: ${timeMs}ms")

        if (rawDetections.isEmpty()) {
            return DetectionResult(
                productId = "",
                productName = "No Object Detected",
                sku = "N/A",
                category = "General",
                uomLabel = "bottle",
                unit = "bottle",
                confidence = 0f,
                quantity = 0,
                detectedProducts = emptyList(),
                boundingBoxes = emptyList(),
                rawDetections = emptyList()
            )
        }

        // Map every raw detection independently to its catalog product
        val mappedDetections = rawDetections.map { det ->
            val catalogItem = ProductCatalog.resolve(label = det.label, classIndex = det.classIndex)
            Pair(det, catalogItem)
        }

        val filteredRawDetections = mappedDetections.map { it.first }

        // Group by unique SKU
        val groupedBySku = mappedDetections.groupBy { it.second.sku }

        val detectedProductItems = groupedBySku.map { (_, group) ->
            val firstPair = group.first()
            val det = firstPair.first
            val catalogItem = firstPair.second

            val groupMaxScore = group.maxOf { it.first.score }
            val groupQty = group.size

            DetectedProductItem(
                productId = catalogItem.productId,
                sku = catalogItem.sku,
                productName = catalogItem.productName,
                categoryId = catalogItem.categoryId,
                category = catalogItem.category,
                uomLabel = catalogItem.uomLabel,
                unit = catalogItem.unit,
                reorderPoint = catalogItem.reorderPoint,
                confidence = groupMaxScore,
                quantity = groupQty,
                classIndex = det.classIndex,
                label = det.label
            )
        }.sortedByDescending { it.quantity }

        val boxes = filteredRawDetections.map {
            BoundingBox(
                rect = it.boundingBox,
                label = it.label,
                confidence = it.score,
                origWidth = it.origWidth,
                origHeight = it.origHeight
            )
        }

        val topItem = detectedProductItems.firstOrNull()
        val totalDetectedObjects = filteredRawDetections.size
        val primaryProductName = if (detectedProductItems.size == 1) {
            topItem?.productName ?: "Detected Product"
        } else {
            "${detectedProductItems.size} Products (${totalDetectedObjects} Total Detected)"
        }

        val primaryUnit = if (detectedProductItems.map { it.uomLabel }.distinct().size == 1) {
            topItem?.uomLabel ?: "bottle"
        } else {
            "items"
        }

        return DetectionResult(
            productId = topItem?.productId ?: "prod-001",
            productName = primaryProductName,
            sku = topItem?.sku ?: "SKU-01-BRODINS100",
            categoryId = topItem?.categoryId ?: "cat-001",
            category = topItem?.category ?: "General",
            uomLabel = primaryUnit,
            unit = primaryUnit,
            reorderPoint = topItem?.reorderPoint ?: 10,
            confidence = topItem?.confidence ?: 0f,
            quantity = totalDetectedObjects,
            detectedProducts = detectedProductItems,
            boundingBoxes = boxes,
            rawDetections = rawDetections
        )
    }
}
