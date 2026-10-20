package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.cv.YOLODetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YOLODetectorStaticTest {

    @Test
    fun testYoloModelLoadingAndTensorMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = YOLODetector(context)

        println("=== MODEL TENSOR INSPECTION TEST ===")
        println("Active Model: ${detector.activeModelName}")
        println("Model Size: ${detector.modelSizeBytes} bytes")
        println("Load Error: ${detector.loadError}")
        println("Input Shape: ${detector.inputShapeStr}")
        println("Input Type: ${detector.inputTypeStr}")
        println("Input Scale: ${detector.inputScale}")
        println("Input ZeroPoint: ${detector.inputZeroPoint}")
        println("Output Shape: ${detector.outputShapeStr}")
        println("Output Type: ${detector.outputTypeStr}")
        println("Output Scale: ${detector.outputScale}")
        println("Output ZeroPoint: ${detector.outputZeroPoint}")
        println("Active Labels Count: ${detector.activeLabelsCount}")

        assertTrue("Active labels should contain product classes", detector.activeLabelsCount > 0)
        assertNotNull("Detector instance should initialize without crashing", detector)
    }
}
