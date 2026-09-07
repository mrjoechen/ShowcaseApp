package com.alpha.facedetection.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FaceGeometryTest {
    @Test
    fun resizePreservesAspectRatioRoundsAndNeverUpscales() {
        assertEquals(ImageSize(640, 320), detectionSize(2000, 1000))
        assertEquals(ImageSize(320, 640), detectionSize(1000, 2000))
        assertEquals(ImageSize(17, 23), detectionSize(17, 23))
        assertEquals(ImageSize(640, 214), detectionSize(1920, 641))
        assertEquals(ImageSize(1, 640), detectionSize(1, 2000))
    }

    @Test
    fun scoreAndVisibleAreaBothRequired() {
        assertTrue(isVisibleFace(row(-5f, -5f, 15f, 15f, 0.6f), 100, 100))
        assertFalse(isVisibleFace(row(10f, 10f, 15f, 15f, 0.599f), 100, 100))
        assertFalse(isVisibleFace(row(101f, 10f, 15f, 15f, 0.9f), 100, 100))
        assertFalse(isVisibleFace(row(10f, 10f, 0f, 15f, 0.9f), 100, 100))
        assertFalse(isVisibleFace(row(10f, 10f, -1f, 15f, 0.9f), 100, 100))
        assertFalse(isVisibleFace(row(10f, 10f, 15f, 15f, Float.NaN), 100, 100))
        assertFalse(isVisibleFace(row(Float.NaN, 10f, 15f, 15f, 0.9f), 100, 100))
    }

    private fun row(x: Float, y: Float, width: Float, height: Float, score: Float) =
        FloatArray(15).apply {
            this[0] = x
            this[1] = y
            this[2] = width
            this[3] = height
            this[14] = score
        }
}
