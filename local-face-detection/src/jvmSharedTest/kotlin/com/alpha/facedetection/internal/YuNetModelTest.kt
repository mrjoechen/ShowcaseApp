package com.alpha.facedetection.internal

import kotlin.test.Test
import kotlin.test.assertFailsWith

class YuNetModelTest {
    @Test
    fun bundledResourcePassesIntegrityChecks() {
        YuNetModel.verify(YuNetModel.bytes)
    }

    @Test
    fun truncatedModelIsRejected() {
        assertFailsWith<IllegalStateException> {
            YuNetModel.verify(YuNetModel.bytes.copyOf(100))
        }
    }

    @Test
    fun sameSizeCorruptionIsRejected() {
        val corrupted = YuNetModel.bytes.copyOf()
        corrupted[0] = (corrupted[0].toInt() xor 1).toByte()
        assertFailsWith<IllegalStateException> { YuNetModel.verify(corrupted) }
    }
}
