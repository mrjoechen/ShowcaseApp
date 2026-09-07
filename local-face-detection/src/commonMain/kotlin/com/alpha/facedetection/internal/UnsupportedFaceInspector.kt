package com.alpha.facedetection.internal

import com.alpha.facedetection.FaceInspectionResult
import com.alpha.facedetection.FaceInspector
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object UnsupportedFaceInspector : FaceInspector {
    override suspend fun inspect(encodedImage: ByteArray): FaceInspectionResult {
        currentCoroutineContext().ensureActive()
        return FaceInspectionResult.INDETERMINATE
    }
}
