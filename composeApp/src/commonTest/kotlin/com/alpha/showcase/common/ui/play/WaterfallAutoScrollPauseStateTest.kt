package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaterfallAutoScrollPauseStateTest {
    @Test
    fun manualScrollPausesAutoScrollForThreeSeconds() {
        val state = WaterfallAutoScrollPauseState()

        assertTrue(state.canAutoScroll(nowMillis = 1_000L))
        state.onUserScroll(nowMillis = 1_000L)

        assertFalse(state.canAutoScroll(nowMillis = 3_999L))
        assertTrue(state.canAutoScroll(nowMillis = 4_000L))
    }

    @Test
    fun repeatedManualScrollRestartsTheThreeSecondPause() {
        val state = WaterfallAutoScrollPauseState()

        state.onUserScroll(nowMillis = 1_000L)
        state.onUserScroll(nowMillis = 3_000L)

        assertFalse(state.canAutoScroll(nowMillis = 5_999L))
        assertTrue(state.canAutoScroll(nowMillis = 6_000L))
    }
}
