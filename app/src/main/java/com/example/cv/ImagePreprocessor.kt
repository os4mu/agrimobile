package com.example.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class LetterboxInfo(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val origWidth: Int,
    val origHeight: Int
)

/**
 * ImagePreprocessor handles letterbox scaling, RGB normalization,
 * and coordinate un-letterboxing for YOLO model inference.
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /**
     * Optional CLAHE enhancement flag. Off by default as model was trained on unenhanced photos.
     */
    const val ENABLE_ENHANCEMENT = false

    /**
     * When true, processLetterbox() skips OpenCV and forces the Android Canvas path.
     */
    const val FORCE_ANDROID_FALLBACK = false

    @Volatile
    private var loggedPath = false

    /**
     * Resizes a Bitmap to targetWidth x targetHeight using letterboxing (preserving aspect ratio and padding).
     * Delegates to OpenCV when available (unless FORCE_ANDROID_FALLBACK is true), otherwise falls back to Android Canvas.
     */
    fun processLetterbox(
        src: Bitmap,
        targetWidth: Int = 640,
        targetHeight: Int = targetWidth,
        reuseBitmap: Bitmap? = null
    ): LetterboxInfo {
        if (!FORCE_ANDROID_FALLBACK && OpenCvInit.isReady) {
            try {
                if (!loggedPath) {
                    Log.i(TAG, "PREPROCESS_PATH = OPENCV")
                    loggedPath = true
                }
                return processLetterboxOpenCv(src, targetWidth, targetHeight, reuseBitmap)
            } catch (t: Throwable) {
                Log.w(TAG, "OpenCV letterbox preprocessing failed, falling back to Android Canvas: ${t.message}", t)
            }
        }
        if (!loggedPath) {
            Log.i(TAG, "PREPROCESS_PATH = ANDROID_FALLBACK")
            loggedPath = true
        }
        return processLetterboxAndroid(src, targetWidth, targetHeight, reuseBitmap)
    }

    /**
     * OpenCV implementation of letterbox preprocessing.
     */
    fun processLetterboxOpenCv(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        reuseBitmap: Bitmap? = null
    ): LetterboxInfo {
        val origW = src.width
        val origH = src.height

        val scale = minOf(targetWidth.toFloat() / origW, targetHeight.toFloat() / origH)
        val scaledW = (origW * scale).toInt()
        val scaledH = (origH * scale).toInt()

        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        val topPad = padY.toInt()
        val bottomPad = targetHeight - scaledH - topPad
        val leftPad = padX.toInt()
        val rightPad = targetWidth - scaledW - leftPad

        val matRgba = Mat()
        val matResized = Mat()
        val matPadded = Mat()
        val matRgb = Mat()
        val matLab = Mat()
        val matLabClahe = Mat()
        val labChannels = ArrayList<Mat>()

        try {
            Utils.bitmapToMat(src, matRgba)

            val processingMat = if (ENABLE_ENHANCEMENT) {
                Imgproc.cvtColor(matRgba, matRgb, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(matRgb, matLab, Imgproc.COLOR_RGB2Lab)
                Core.split(matLab, labChannels)

                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                clahe.apply(labChannels[0], labChannels[0])

                Core.merge(labChannels, matLabClahe)
                Imgproc.cvtColor(matLabClahe, matRgb, Imgproc.COLOR_Lab2RGB)
                Imgproc.cvtColor(matRgb, matRgba, Imgproc.COLOR_RGB2RGBA)
                matRgba
            } else {
                matRgba
            }

            val interp = if (scale < 1.0f) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
            Imgproc.resize(processingMat, matResized, Size(scaledW.toDouble(), scaledH.toDouble()), 0.0, 0.0, interp)

            Core.copyMakeBorder(
                matResized,
                matPadded,
                topPad,
                bottomPad,
                leftPad,
                rightPad,
                Core.BORDER_CONSTANT,
                Scalar(114.0, 114.0, 114.0, 255.0)
            )

            val outputBitmap = if (reuseBitmap != null && reuseBitmap.width == targetWidth && reuseBitmap.height == targetHeight) {
                reuseBitmap
            } else {
                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            }

            Utils.matToBitmap(matPadded, outputBitmap)

            return LetterboxInfo(
                bitmap = outputBitmap,
                scale = scale,
                padX = padX,
                padY = padY,
                origWidth = origW,
                origHeight = origH
            )
        } finally {
            matRgba.release()
            matResized.release()
            matPadded.release()
            matRgb.release()
            matLab.release()
            matLabClahe.release()
            for (ch in labChannels) {
                ch.release()
            }
        }
    }

    /**
     * Android Canvas fallback implementation of letterbox preprocessing.
     */
    fun processLetterboxAndroid(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        reuseBitmap: Bitmap? = null
    ): LetterboxInfo {
        val origW = src.width
        val origH = src.height

        val scale = minOf(targetWidth.toFloat() / origW, targetHeight.toFloat() / origH)
        val scaledW = (origW * scale).toInt()
        val scaledH = (origH * scale).toInt()

        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        val outputBitmap = if (reuseBitmap != null && reuseBitmap.width == targetWidth && reuseBitmap.height == targetHeight) {
            reuseBitmap
        } else {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114)) // Neutral grey standard YOLO padding

        val srcRect = Rect(0, 0, origW, origH)
        val dstRect = RectF(padX, padY, padX + scaledW, padY + scaledH)
        canvas.drawBitmap(src, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        return LetterboxInfo(
            bitmap = outputBitmap,
            scale = scale,
            padX = padX,
            padY = padY,
            origWidth = origW,
            origHeight = origH
        )
    }

    /**
     * Converts a bounding box in letterboxed target space back to original image space.
     */
    fun unletterbox(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        info: LetterboxInfo
    ): RectF {
        val unpaddedLeft = (left - info.padX) / info.scale
        val unpaddedTop = (top - info.padY) / info.scale
        val unpaddedRight = (right - info.padX) / info.scale
        val unpaddedBottom = (bottom - info.padY) / info.scale

        val origLeft = unpaddedLeft.coerceIn(0f, info.origWidth.toFloat())
        val origTop = unpaddedTop.coerceIn(0f, info.origHeight.toFloat())
        val origRight = unpaddedRight.coerceIn(0f, info.origWidth.toFloat())
        val origBottom = unpaddedBottom.coerceIn(0f, info.origHeight.toFloat())

        return RectF(origLeft, origTop, origRight, origBottom)
    }

    /**
     * Legacy helper retained for backwards compatibility.
     */
    fun processForYolo(bitmap: Bitmap, targetWidth: Int = 640, targetHeight: Int = 640): Bitmap {
        return processLetterbox(bitmap, targetWidth, targetHeight).bitmap
    }
}
