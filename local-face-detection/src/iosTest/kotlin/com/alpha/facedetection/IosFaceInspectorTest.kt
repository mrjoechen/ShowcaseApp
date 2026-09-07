package com.alpha.facedetection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the public factory and the real, bundled YuNet model on an iOS simulator/device.
 * The build generates internal object FaceTestFixtures in this package with ByteArray properties:
 * face (the existing opencv/lena.jpg), blank (opaque blank PNG), blankJpeg (opaque blank JPEG).
 * Give the blanks different dimensions, with at least one edge above 640, to exercise input resizing.
 * Generated Kotlin sources belong in build/generated/iosTestFixtures, registered with iosTest.
 */
class IosFaceInspectorTest {
    @Test
    fun detectsRealFaceThroughPublicFactory() = runTest {
        assertEquals(
            FaceInspectionResult.FACE_DETECTED,
            createFaceInspector().inspect(FaceTestFixtures.face),
        )
    }

    @Test
    fun blankPngAndJpegHaveNoFacesAndChangingSizesPreservesDetection() = runTest {
        val inspector = createFaceInspector()
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(FaceTestFixtures.face))
        assertEquals(FaceInspectionResult.NO_FACE, inspector.inspect(FaceTestFixtures.blank))
        assertEquals(FaceInspectionResult.NO_FACE, inspector.inspect(FaceTestFixtures.blankJpeg))
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(FaceTestFixtures.face))
    }

    @Test
    fun invalidImagesAreIndeterminateAndDoNotPoisonLaterInference() = runTest {
        val inspector = createFaceInspector()
        for (invalid in listOf(byteArrayOf(), byteArrayOf(1, 2, 3), FaceTestFixtures.face.copyOf(16))) {
            assertEquals(FaceInspectionResult.INDETERMINATE, inspector.inspect(invalid))
        }
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(FaceTestFixtures.face))
        assertEquals(FaceInspectionResult.NO_FACE, inspector.inspect(FaceTestFixtures.blank))
    }

    @Test
    fun concurrentFactoryInstancesKeepFaceBlankAndInvalidResultsSeparate() = runTest {
        val cases = listOf(
            FaceTestFixtures.face to FaceInspectionResult.FACE_DETECTED,
            FaceTestFixtures.blank to FaceInspectionResult.NO_FACE,
            FaceTestFixtures.blankJpeg to FaceInspectionResult.NO_FACE,
            byteArrayOf(1, 2, 3) to FaceInspectionResult.INDETERMINATE,
        )
        val start = CompletableDeferred<Unit>()
        val inspections = (0 until 12).map { index ->
            async(Dispatchers.Default) {
                val inspector = createFaceInspector()
                val (encoded, expected) = cases[index % cases.size]
                start.await()
                assertEquals(expected, inspector.inspect(encoded), "Concurrent inspection $index")
            }
        }
        start.complete(Unit)
        inspections.awaitAll()
    }

    @Test
    fun cancellationPropagatesAndTheSharedDetectorRemainsUsable() = runTest {
        val inspector = createFaceInspector()
        // Initialize the real detector so this also covers cancellation after initialization.
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(FaceTestFixtures.face))
        var observedCancellation = false
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            assertFailsWith<CancellationException> { inspector.inspect(FaceTestFixtures.face) }
            observedCancellation = true
        }
        cancelled.join()
        assertTrue(observedCancellation, "The cancelled coroutine must actually call inspect")
        assertEquals(FaceInspectionResult.NO_FACE, inspector.inspect(FaceTestFixtures.blank))
        assertEquals(
            FaceInspectionResult.FACE_DETECTED,
            createFaceInspector().inspect(FaceTestFixtures.face),
        )
    }
}
