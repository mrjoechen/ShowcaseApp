package com.alpha.facedetection.internal

import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

internal fun loadOpenCv() = DesktopNativeLoader.load()

internal fun decodeToBgr(encoded: ByteArray): Mat? = MatOfByte().useMat { buffer ->
    buffer.fromArray(*encoded)
    // Match Android BitmapFactory's encoded pixel orientation; no EXIF rotation.
    val decoded = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
    if (decoded.empty()) {
        decoded.release()
        return@useMat null
    }
    try {
        val size = detectionSize(decoded.cols(), decoded.rows())
        val resized = Mat()
        try {
            Imgproc.resize(decoded, resized, Size(size.width.toDouble(), size.height.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            resized
        } catch (failure: Throwable) {
            resized.release()
            throw failure
        }
    } finally {
        decoded.release()
    }
}
