package com.alpha.showcase.common.ui.play

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeCardVisibilityStateTest {
    @Test
    fun touchHidesCardUntilFiveSecondsAfterRelease() = runTest {
        val state = TimeCardVisibilityState(backgroundScope)
        assertTrue(state.isVisible)

        state.onTouch(isPressed = true)
        assertFalse(state.isVisible)
        state.onTouch(isPressed = false)

        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(state.isVisible)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(state.isVisible)
    }

    @Test
    fun anotherTouchRestartsTheFullIdleDelay() = runTest {
        val state = TimeCardVisibilityState(backgroundScope)
        state.onTouch(isPressed = true)
        state.onTouch(isPressed = false)
        advanceTimeBy(4_000)

        state.onTouch(isPressed = true)
        state.onTouch(isPressed = false)
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(state.isVisible)
        advanceTimeBy(3_999)
        runCurrent()
        assertFalse(state.isVisible)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(state.isVisible)
    }

    @Test
    fun holdingTouchCancelsAnyPendingRestoreUntilAllPointersRelease() = runTest {
        val state = TimeCardVisibilityState(backgroundScope)
        state.onTouch(isPressed = true)
        state.onTouch(isPressed = false)
        advanceTimeBy(4_000)

        state.onTouch(isPressed = true)
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(state.isVisible)

        // A move or one finger lifting while another remains down is still pressed.
        state.onTouch(isPressed = true)
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(state.isVisible)

        state.onTouch(isPressed = false)
        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(state.isVisible)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(state.isVisible)
    }
}
