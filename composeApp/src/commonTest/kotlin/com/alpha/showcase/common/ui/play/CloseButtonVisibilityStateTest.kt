package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CloseButtonVisibilityStateTest {
    @Test
    fun startsHiddenAndHidesThreeSecondsAfterInteraction() = runTest {
        withCloseButton {
            assertFalse(state.isVisible)
            state.onInteraction()
            frame()
            assertTrue(state.isVisible)
            advanceTimeBy(2_999)
            runCurrent()
            assertTrue(state.isVisible)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(state.isVisible)
        }
    }

    @Test
    fun interactionWhileVisibleRestartsTheFullDelay() = runTest {
        withCloseButton {
            state.onInteraction()
            frame()
            advanceTimeBy(2_000)
            state.onInteraction()
            frame()
            advanceTimeBy(2_999)
            runCurrent()
            assertTrue(state.isVisible)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(state.isVisible)
        }
    }

    @Test
    fun interactionAfterTimeoutBeforeRecompositionStillAutoHides() = runTest {
        withCloseButton {
            state.onInteraction()
            frame()
            advanceTimeBy(3_000)
            runCurrent()
            assertFalse(state.isVisible)

            // No frame between hiding and showing: Compose only sees true again.
            state.onInteraction()
            assertTrue(state.isVisible)
            frame()
            advanceTimeBy(3_000)
            runCurrent()
            frame()
            assertFalse(state.isVisible)
        }
    }

    @Test
    fun interactionAtDeadlineCancelsOldTimeoutWithoutWaitingForAFrame() = runTest {
        withCloseButton {
            state.onInteraction()
            frame()
            // advanceTimeBy leaves work at the deadline queued until runCurrent.
            advanceTimeBy(3_000)
            state.onInteraction()
            runCurrent()
            assertTrue(state.isVisible)
            advanceTimeBy(3_000)
            runCurrent()
            assertFalse(state.isVisible)
        }
    }

    @Test
    fun leavingCompositionCancelsPendingHide() = runTest {
        withCloseButton {
            state.onInteraction()
            frame()
            advanceTimeBy(1_000)
            composition.setContent {}
            frame()
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(state.isVisible, "Disposed state must not be mutated by a pending timer")
        }
    }

    private suspend fun TestScope.withCloseButton(block: Fixture.() -> Unit) {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(EmptyApplier(), recomposer)
        val fixture = Fixture(this, clock, composition)
        try {
            composition.setContent { fixture.state = rememberCloseButtonVisibilityState() }
            fixture.frame()
            fixture.block()
        } finally {
            composition.dispose()
            recomposer.cancel()
            job.cancelAndJoin()
        }
    }

    private class Fixture(
        val scope: TestScope,
        val clock: BroadcastFrameClock,
        val composition: Composition,
    ) {
        lateinit var state: CloseButtonVisibilityState

        fun frame() {
            Snapshot.sendApplyNotifications()
            scope.runCurrent()
            clock.sendFrame(scope.testScheduler.currentTime * 1_000_000L)
            scope.runCurrent()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun remove(index: Int, count: Int) {}
        override fun onClear() {}
    }
}
