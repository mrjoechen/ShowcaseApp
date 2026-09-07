package com.alpha.facedetection.internal

import com.alpha.facedetection.FaceInspectionResult
import com.alpha.facedetection.FaceInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Size
import org.opencv.objdetect.FaceDetectorYN

/** All factory instances share the queue and lazily loaded library/model. */
internal object OpenCvFaceInspector : FaceInspector {
    private val mutex = Mutex()
    private val runtime by lazy { loadOpenCv() }
    // The common API has no close operation. Like the source adapter, retain the
    // detector for reuse; this singleton deliberately owns it for process lifetime.
    // Initialization and every mutation are protected by mutex.
    private val detector: FaceDetectorYN by lazy {
        MatOfByte().useMat { model ->
            model.fromArray(*YuNetModel.bytes)
            MatOfByte().useMat { config ->
                FaceDetectorYN.create(
                    "onnx", model, config, Size(320.0, 320.0),
                    SCORE_THRESHOLD, NMS_THRESHOLD, TOP_K,
                )
            }
        }
    }

    override suspend fun inspect(encodedImage: ByteArray): FaceInspectionResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (encodedImage.isEmpty()) return@withLock FaceInspectionResult.INDETERMINATE
                try {
                    runtime
                    val result = inspectBlocking(encodedImage)
                    currentCoroutineContext().ensureActive()
                    result
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    FaceInspectionResult.INDETERMINATE
                } catch (_: LinkageError) {
                    FaceInspectionResult.INDETERMINATE
                } catch (_: OutOfMemoryError) {
                    FaceInspectionResult.INDETERMINATE
                }
            }
        }

    private fun inspectBlocking(encodedImage: ByteArray): FaceInspectionResult {
        val input = decodeToBgr(encodedImage) ?: return FaceInspectionResult.INDETERMINATE
        return input.useMat { prepared ->
            check(!prepared.empty()) { "Decoded image is empty" }
            val activeDetector = detector
            activeDetector.setInputSize(prepared.size())
            Mat().useMat facesResult@ { faces ->
                activeDetector.detect(prepared, faces)
                if (faces.empty()) return@facesResult FaceInspectionResult.NO_FACE
                check(faces.cols() >= RESULT_COLUMNS) { "Unexpected YuNet output shape" }
                val row = FloatArray(faces.cols())
                for (index in 0 until faces.rows()) {
                    check(faces.get(index, 0, row) > 0) { "Unable to read YuNet output" }
                    if (isVisibleFace(row, prepared.cols(), prepared.rows())) {
                        return@facesResult FaceInspectionResult.FACE_DETECTED
                    }
                }
                FaceInspectionResult.NO_FACE
            }
        }
    }
}
