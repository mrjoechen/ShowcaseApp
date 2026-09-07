@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.facedetection

import com.alpha.facedetection.cinterop.showcase_face_inspect
import com.alpha.facedetection.cinterop.SHOWCASE_FACE_NO_FACE
import com.alpha.facedetection.cinterop.SHOWCASE_FACE_DETECTED
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual fun createFaceInspector(): FaceInspector = IosYuNetFaceInspector

private object IosYuNetFaceInspector : FaceInspector {
    override suspend fun inspect(encodedImage: ByteArray): FaceInspectionResult = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        if (encodedImage.isEmpty()) return@withContext FaceInspectionResult.INDETERMINATE
        // The C++ boundary serializes the cached detector and catches native exceptions.
        // The exact frozen upload bytes remain pinned until synchronous inference returns.
        val result = encodedImage.usePinned {
            showcase_face_inspect(it.addressOf(0).reinterpret<UByteVar>(), encodedImage.size.convert())
        }
        currentCoroutineContext().ensureActive()
        when (result) {
            SHOWCASE_FACE_NO_FACE -> FaceInspectionResult.NO_FACE
            SHOWCASE_FACE_DETECTED -> FaceInspectionResult.FACE_DETECTED
            else -> FaceInspectionResult.INDETERMINATE
        }
    }
}
