package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagerAnchorWaitStateTest {

    @Test
    fun sameTargetInNewGenerationGetsFreshAttemptBudget() {
        val state = AnchorWaitState(maxAttempts = 4)

        assertTrue(state.shouldWait(1, 3, 13, "stable-key", targetAttemptRevision = 10))
        assertTrue(state.shouldWait(1, 3, 13, "stable-key", targetAttemptRevision = 11))
        assertTrue(state.shouldWait(1, 3, 13, "stable-key", targetAttemptRevision = 12))
        assertTrue(state.shouldWait(1, 3, 13, "stable-key", targetAttemptRevision = 13))
        assertFalse(state.shouldWait(1, 3, 13, "stable-key", targetAttemptRevision = 14))
        assertEquals(4, state.completedAttempts)

        // G2 can land while the virtual pager page and located target page are
        // unchanged. Its wait must start from the revision it first observes,
        // rather than inheriting G1's exhausted budget.
        assertTrue(state.shouldWait(2, 3, 13, "stable-key", targetAttemptRevision = 22))
        assertEquals(0, state.completedAttempts)
    }

    @Test
    fun unrelatedPageCompletionsDoNotConsumeTargetBudget() {
        val state = AnchorWaitState(maxAttempts = 4)

        assertTrue(state.shouldWait(7, 5, 25, "shown", targetAttemptRevision = 8))
        // These calls represent reconciles caused by global pagesRevision changes
        // on other pages: the target page's own revision remains 8.
        repeat(20) {
            assertTrue(state.shouldWait(7, 5, 25, "shown", targetAttemptRevision = 8))
        }
        assertEquals(0, state.completedAttempts)

        // Only a completion belonging to target page 5 spends one attempt.
        assertTrue(state.shouldWait(7, 5, 25, "shown", targetAttemptRevision = 9))
        assertEquals(1, state.completedAttempts)
    }

    @Test
    fun targetPageOrStableKeyChangeResetsBudget() {
        val state = AnchorWaitState(maxAttempts = 2)

        state.shouldWait(1, 4, 20, "a", targetAttemptRevision = 0)
        assertTrue(state.shouldWait(1, 4, 20, "a", targetAttemptRevision = 1))
        assertEquals(1, state.completedAttempts)

        assertTrue(state.shouldWait(1, 5, 25, "a", targetAttemptRevision = 9))
        assertEquals(0, state.completedAttempts)

        state.shouldWait(1, 5, 25, "a", targetAttemptRevision = 10)
        assertTrue(state.shouldWait(1, 5, 25, "b", targetAttemptRevision = 10))
        assertEquals(0, state.completedAttempts)
    }

    @Test
    fun locatedIndexChangeWithinSamePageGetsFreshBudget() {
        val state = AnchorWaitState(maxAttempts = 2)

        state.shouldWait(4, 2, 10, "same-media", targetAttemptRevision = 3)
        state.shouldWait(4, 2, 10, "same-media", targetAttemptRevision = 4)
        assertFalse(state.shouldWait(4, 2, 10, "same-media", targetAttemptRevision = 5))

        // The backing-store locator moved the stable item from index 10 to 11.
        // Both indices share page 2, but they are different anchor identities.
        assertTrue(state.shouldWait(4, 2, 11, "same-media", targetAttemptRevision = 5))
        assertEquals(0, state.completedAttempts)
    }

    @Test
    fun interruptedSnapKeepsExhaustedBudgetUntilAdoptionSucceeds() {
        val state = AnchorWaitState(maxAttempts = 2)

        state.shouldWait(8, 6, 30, "shown", targetAttemptRevision = 0)
        state.shouldWait(8, 6, 30, "shown", targetAttemptRevision = 1)
        assertFalse(state.shouldWait(8, 6, 30, "shown", targetAttemptRevision = 2))

        state.onAdoptionResult(adopted = false)
        assertFalse(
            state.shouldWait(8, 6, 30, "shown", targetAttemptRevision = 2),
            "UserInput cancellation must not grant the same G1 anchor a new budget",
        )

        state.onAdoptionResult(adopted = true)
        assertTrue(state.shouldWait(8, 6, 30, "shown", targetAttemptRevision = 2))
    }

    @Test
    fun adoptedWindowCaptureIgnoresPreRefreshPin() {
        val value = capturedPagerItem(
            loaded = null,
            previousPin = "pre-refresh-item",
            preservePreviousPin = false,
        ) { "live-positional-fallback" }

        assertEquals("live-positional-fallback", value)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun finiteControllerFreezesInitialPageBeforeObserverStarts() = runTest {
        val items = PagingPlayItems(
            totalCount = 1,
            initialPage = listOf("old-item"),
            coroutineScope = this,
            loadPage = { _, _ -> listOf("new-item") },
        )
        val controller = FinitePagerCountController(items) { null }

        // A very fast sync can refresh the data after composition constructs the
        // controller but before its LaunchedEffect observer receives the initial
        // snapshotFlow value. The controller must already have captured page 0,
        // otherwise this frame renders the new item before re-anchoring (flash).
        items.refresh(newTotalCount = 1)
        advanceUntilIdle()

        assertEquals(1, items.generation)
        assertEquals("old-item", controller.item(0))
    }

    @Test
    fun snapPlanPinsOriginAndDestinationAndRejectsHalfCommit() {
        val plan = AnchorSnapPlan(
            plannedGeneration = 11,
            originPage = 100,
            targetPage = 104,
            shownItem = "g1-shown",
        )

        val pins = plan.withTemporaryPins(
            mapOf(
                99 to "neighbor",
                104 to "g2-live-wrong-item",
            ),
        )
        assertEquals("g1-shown", pins[100])
        assertEquals("g1-shown", pins[104])
        assertEquals("neighbor", pins[99])

        assertFalse(
            plan.canAdopt(
                mutationCompleted = false,
                currentGeneration = 11,
                currentPage = 104,
                isScrollInProgress = false,
            ),
            "a UserInput cancellation must not commit the planned generation",
        )
        assertFalse(
            plan.canAdopt(
                mutationCompleted = true,
                currentGeneration = 12,
                currentPage = 104,
                isScrollInProgress = false,
            ),
            "G2 landing during the G1 snap must invalidate G1 adoption",
        )
        assertFalse(
            plan.canAdopt(
                mutationCompleted = true,
                currentGeneration = 11,
                currentPage = 103,
                isScrollInProgress = false,
            ),
        )
        assertFalse(
            plan.canAdopt(
                mutationCompleted = true,
                currentGeneration = 11,
                currentPage = 104,
                isScrollInProgress = true,
            ),
        )
        assertTrue(
            plan.canAdopt(
                mutationCompleted = true,
                currentGeneration = 11,
                currentPage = 104,
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun pagerMutationCancellationIsLocalWhileParentIsActive() = runTest {
        val completed = runPagerMutation {
            throw CancellationException("cancelled by UserInput MutatorMutex owner")
        }

        assertFalse(completed)
    }

    @Test
    fun localPagerCancellationChangesOtherwiseIdenticalReconcileTuple() = runTest {
        val signal = PagerReconcileSignal()
        val revisions = mutableListOf<Int>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { signal.retryRevision }.take(2).toList(revisions)
        }

        val completed = runPagerMutation {
            throw CancellationException("UserInput won MutatorMutex")
        }
        if (!completed) signal.requestRetry()
        Snapshot.sendApplyNotifications()
        observer.join()

        assertFalse(completed)
        assertEquals(listOf(0, 1), revisions)
    }

    @Test
    fun initialAlignmentRetryWaitsForIdlePager() {
        val attemptedRevisions = mutableListOf<Int>()
        fun observe(retryRevision: Int, isScrollInProgress: Boolean) {
            if (shouldAttemptInitialPagerAlignment(isScrollInProgress)) {
                attemptedRevisions += retryRevision
            }
        }

        observe(retryRevision = 0, isScrollInProgress = true)
        observe(retryRevision = 1, isScrollInProgress = true)
        assertTrue(attemptedRevisions.isEmpty())

        observe(retryRevision = 1, isScrollInProgress = false)
        assertEquals(listOf(1), attemptedRevisions)
    }

    @Test
    fun parentCancellationStillPropagates() = runTest {
        val enteredMutation = CompletableDeferred<Unit>()
        var cancellationPropagated = false
        val observer = launch {
            try {
                runPagerMutation {
                    enteredMutation.complete(Unit)
                    awaitCancellation()
                }
            } catch (_: CancellationException) {
                cancellationPropagated = true
            }
        }

        enteredMutation.await()
        observer.cancel()
        observer.join()

        assertTrue(cancellationPropagated)
    }
}
