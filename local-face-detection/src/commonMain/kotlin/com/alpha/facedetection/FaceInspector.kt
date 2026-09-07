package com.alpha.facedetection

/** INDETERMINATE includes unsupported platforms and images that could not be inspected. */
enum class FaceInspectionResult {
    NO_FACE,
    FACE_DETECTED,
    INDETERMINATE,
}

fun interface FaceInspector {
    /** Inspects locally. Cancellation propagates; it is never converted into a detection result. */
    suspend fun inspect(encodedImage: ByteArray): FaceInspectionResult
}

/** Creates a lazily initialized inspector; no application Context or caller cleanup is required. */
expect fun createFaceInspector(): FaceInspector
