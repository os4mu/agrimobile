package com.example.cv

import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCvInit {
    private const val TAG = "OpenCvInit"

    val isReady: Boolean by lazy {
        try {
            val ready = OpenCVLoader.initLocal()
            if (ready) {
                Log.i(TAG, "OPENCV_READY = true (version: ${OpenCVLoader.OPENCV_VERSION})")
            } else {
                Log.w(TAG, "OPENCV_READY = false (initLocal returned false)")
            }
            ready
        } catch (t: Throwable) {
            Log.e(TAG, "OPENCV_READY = false (${t.message})")
            false
        }
    }

    fun init() {
        val ready = isReady
    }
}
