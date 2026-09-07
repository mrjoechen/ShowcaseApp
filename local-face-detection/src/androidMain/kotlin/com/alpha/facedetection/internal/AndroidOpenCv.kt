package com.alpha.facedetection.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

internal fun loadOpenCv() = System.loadLibrary("opencv_java4")

internal fun decodeToBgr(encoded: ByteArray): Mat? {
    val bitmap = BitmapFactory.decodeByteArray(
        encoded, 0, encoded.size,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
    ) ?: return null
    try {
        val size = detectionSize(bitmap.width, bitmap.height)
        val scaled = if (size.width == bitmap.width && size.height == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, size.width, size.height, true)
        try {
            val software = if (scaled.config == Bitmap.Config.ARGB_8888) scaled
            else checkNotNull(scaled.copy(Bitmap.Config.ARGB_8888, false))
            try {
                return Mat().useMat { rgba ->
                    Utils.bitmapToMat(software, rgba)
                    val bgr = Mat()
                    try {
                        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                        bgr
                    } catch (failure: Throwable) {
                        bgr.release()
                        throw failure
                    }
                }
            } finally {
                if (software !== scaled) software.recycle()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    } finally {
        bitmap.recycle()
    }
}
