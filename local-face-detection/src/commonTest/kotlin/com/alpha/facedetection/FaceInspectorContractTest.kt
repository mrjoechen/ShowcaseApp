package com.alpha.facedetection

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FaceInspectorContractTest {
    @Test
    fun emptyInputCannotBeReportedAsNoFace() = runTest {
        assertEquals(FaceInspectionResult.INDETERMINATE, createFaceInspector().inspect(byteArrayOf()))
    }

    @Test
    fun invalidEncodingCannotBeReportedAsNoFace() = runTest {
        assertEquals(
            FaceInspectionResult.INDETERMINATE,
            createFaceInspector().inspect(byteArrayOf(1, 2, 3, 4)),
        )
    }
}
