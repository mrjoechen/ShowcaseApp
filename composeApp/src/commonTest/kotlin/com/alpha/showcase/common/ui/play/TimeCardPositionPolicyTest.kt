package com.alpha.showcase.common.ui.play

import androidx.compose.ui.Alignment
import kotlin.test.*

class TimeCardPositionPolicyTest {
    @Test fun summaryKeepsAllRandomPositionsOutOfItsCornerInBothOrientations() {
        for (portrait in listOf(true, false)) {
            val policy = TimeCardPositionPolicy(portrait, avoidImageSummary = true)
            assertFalse(Alignment.BottomStart in policy.positions)
            assertFalse(Alignment.BottomCenter in policy.positions)
            assertEquals(Alignment.BottomEnd, policy.resolve(Alignment.BottomStart))
            assertTrue(policy.initialPosition in policy.positions)
        }
    }
    @Test fun rotationReconcilesTheExistingPosition() {
        val portrait = TimeCardPositionPolicy(true)
        assertEquals(Alignment.BottomCenter, portrait.resolve(Alignment.BottomEnd))
        val landscape = TimeCardPositionPolicy(false)
        assertEquals(Alignment.BottomEnd, landscape.resolve(Alignment.BottomCenter))
    }
}
