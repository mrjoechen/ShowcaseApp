package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayRecoveryEventTest {

    @Test
    fun observerAwaitsOnceAndRetriesOnlyReconciliation() = runTest {
        var awaitCount = 0
        var reconcileCount = 0
        val failures = mutableListOf<Triple<RecoveryObserverStage, Int, Throwable>>()
        var exhausted: Throwable? = null

        val job = launchBoundedRecoveryObserver(
            awaitEvent = { awaitCount += 1; "terminal" },
            maxAttempts = 3,
            retryDelayMillis = 1,
            reconcile = {
                reconcileCount += 1
                if (reconcileCount < 3) error("database busy")
            },
            onAttemptFailure = { stage, attempt, failure ->
                failures += Triple(stage, attempt, failure)
            },
            onExhausted = { exhausted = it },
        )
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(1, awaitCount)
        assertEquals(3, reconcileCount)
        assertEquals(
            listOf(RecoveryObserverStage.Reconcile to 1, RecoveryObserverStage.Reconcile to 2),
            failures.map { it.first to it.second },
        )
        assertEquals(null, exhausted)
    }

    @Test
    fun observerCancelsWithOwningSessionScope() = runTest {
        val terminal = CompletableDeferred<String>()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val job = owner.launchBoundedRecoveryObserver(
            awaitEvent = { terminal.await() },
            reconcile = {},
            onExhausted = {},
        )
        testScheduler.runCurrent()

        owner.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }

    @Test
    fun observerReportsEveryAttemptAndFinalFailureExactlyOnceAfterBudgetIsExhausted() = runTest {
        var attempts = 0
        val attemptedFailures = mutableListOf<Throwable>()
        val exhaustedFailures = mutableListOf<Throwable>()
        val job = launchBoundedRecoveryObserver(
            awaitEvent = { Unit },
            maxAttempts = 3,
            retryDelayMillis = 1,
            reconcile = {
                attempts += 1
                error("still unavailable")
            },
            onAttemptFailure = { stage, attempt, failure ->
                assertEquals(RecoveryObserverStage.Reconcile, stage)
                assertEquals(attempts, attempt)
                attemptedFailures += failure
            },
            onExhausted = { exhaustedFailures += it },
        )
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(3, attempts)
        assertEquals(3, attemptedFailures.size)
        assertEquals(1, exhaustedFailures.size)
        assertSame(attemptedFailures.last(), exhaustedFailures.single())
    }

    @Test
    fun awaitFailureIsReportedOnceWithoutRetryingAwaitOrReconcile() = runTest {
        val expected = IllegalStateException("terminal source unavailable")
        var awaitCount = 0
        var reconcileCount = 0
        val failures = mutableListOf<Triple<RecoveryObserverStage, Int, Throwable>>()
        val exhausted = mutableListOf<Throwable>()

        val job = launchBoundedRecoveryObserver(
            awaitEvent = {
                awaitCount += 1
                throw expected
            },
            reconcile = { reconcileCount += 1 },
            onAttemptFailure = { stage, attempt, failure ->
                failures += Triple(stage, attempt, failure)
            },
            onExhausted = { exhausted += it },
        )
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(1, awaitCount)
        assertEquals(0, reconcileCount)
        assertEquals(1, failures.size)
        assertEquals(RecoveryObserverStage.AwaitEvent, failures.single().first)
        assertEquals(1, failures.single().second)
        assertSame(expected, failures.single().third)
        assertEquals(1, exhausted.size)
        assertSame(expected, exhausted.single())
    }

    @Test
    fun observerFailureCallbacksCannotBreakRecoveryOrLeakIntoTheOwner() = runTest {
        var reconcileCount = 0
        var exhaustedCount = 0
        val job = launchBoundedRecoveryObserver(
            awaitEvent = { Unit },
            maxAttempts = 2,
            retryDelayMillis = 0,
            reconcile = {
                reconcileCount += 1
                error("database busy")
            },
            onAttemptFailure = { _, _, _ -> error("attempt callback broken") },
            onExhausted = {
                exhaustedCount += 1
                error("exhausted callback broken")
            },
        )
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(2, reconcileCount)
        assertEquals(1, exhaustedCount)
        assertFalse(job.isCancelled)
    }

    @Test
    fun recoveryCallbacksPropagateTheOriginalCancellationInstance() = runTest {
        suspend fun completionCause(job: Job): Throwable? {
            var cause: Throwable? = null
            job.invokeOnCompletion { cause = it }
            job.join()
            return cause
        }

        val attemptCancellation = CancellationException("attempt callback cancelled")
        val attemptJob = launchBoundedRecoveryObserver(
            awaitEvent = { Unit },
            maxAttempts = 2,
            retryDelayMillis = 0,
            reconcile = { error("retry") },
            onAttemptFailure = { _, _, _ -> throw attemptCancellation },
            onExhausted = {},
        )
        testScheduler.runCurrent()
        assertSame(attemptCancellation, completionCause(attemptJob))

        val exhaustedCancellation = CancellationException("exhausted callback cancelled")
        val exhaustedJob = launchBoundedRecoveryObserver(
            awaitEvent = { Unit },
            maxAttempts = 1,
            reconcile = { error("exhaust") },
            onExhausted = { throw exhaustedCancellation },
        )
        testScheduler.runCurrent()
        assertSame(exhaustedCancellation, completionCause(exhaustedJob))
    }

    @Test
    fun cancellationFromAwaitReconcileAndDelayKeepsTheOriginalInstance() = runTest {
        suspend fun completionCause(job: Job): Throwable? {
            var cause: Throwable? = null
            job.invokeOnCompletion { cause = it }
            job.join()
            return cause
        }

        val awaitCancellation = CancellationException("await cancelled")
        val awaitJob = launchBoundedRecoveryObserver<Unit>(
            awaitEvent = { throw awaitCancellation },
            reconcile = {},
            onExhausted = {},
        )
        testScheduler.runCurrent()
        assertSame(awaitCancellation, completionCause(awaitJob))

        val reconcileCancellation = CancellationException("reconcile cancelled")
        val reconcileJob = launchBoundedRecoveryObserver(
            awaitEvent = { Unit },
            reconcile = { throw reconcileCancellation },
            onExhausted = {},
        )
        testScheduler.runCurrent()
        assertSame(reconcileCancellation, completionCause(reconcileJob))

        val delayCancellation = CancellationException("retry delay cancelled")
        val delayJob = launchBoundedRecoveryObserver(
            awaitEvent = { Unit },
            retryDelayMillis = 10_000,
            reconcile = { error("retry me") },
            onExhausted = {},
        )
        testScheduler.runCurrent()
        delayJob.cancel(delayCancellation)
        testScheduler.runCurrent()
        assertSame(delayCancellation, completionCause(delayJob))
    }

    @Test
    fun observerRejectsInvalidRetryConfigurationBeforeLaunching() = runTest {
        assertFailsWith<IllegalArgumentException> {
            launchBoundedRecoveryObserver(
                awaitEvent = { Unit },
                maxAttempts = 0,
                reconcile = {},
                onExhausted = {},
            )
        }
        assertFailsWith<IllegalArgumentException> {
            launchBoundedRecoveryObserver(
                awaitEvent = { Unit },
                retryDelayMillis = -1,
                reconcile = {},
                onExhausted = {},
            )
        }
    }

    @Test
    fun recoveryLogSummaryNeverIncludesFailureMessage() {
        val secret = "Authorization: Bearer top-secret https://signed.example/object"
        val summary = recoveryFailureLogMessage(
            stage = RecoveryObserverStage.Exhausted,
            attempt = null,
            failure = IllegalStateException(secret),
        )

        assertEquals("stage=exhausted exception=IllegalStateException", summary)
        assertFalse(summary.contains(secret))
        assertFalse(summary.contains("top-secret"))
        assertFalse(summary.contains("signed.example"))
    }

    @Test
    fun pagingSessionKeepsOwnedChildrenUntilItsOwnerIsCancelled() = runTest {
        val ownedChildCancelled = CompletableDeferred<Unit>()
        var loaded: String? = null
        var warnings = 0
        val owner = launch {
            runPagingSession(
                warningDelayMillis = 1,
                shouldWarn = true,
                load = { sessionScope ->
                    sessionScope.launch {
                        try {
                            awaitCancellation()
                        } finally {
                            ownedChildCancelled.complete(Unit)
                        }
                    }
                    "ready"
                },
                onLoaded = { loaded = it },
                onWarning = { warnings += 1 },
            )
        }
        testScheduler.runCurrent()

        assertEquals("ready", loaded)
        assertEquals(0, warnings, "a completed load must cancel its warning job")
        assertTrue(owner.isActive, "the session must stay alive to own paging children")

        owner.cancel()
        owner.join()

        assertTrue(ownedChildCancelled.isCompleted)
        assertTrue(owner.isCancelled)
    }

    @Test
    fun pagingSessionWarnsOnlyWhileTheInitialLoadIsActuallyActive() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        var warnings = 0
        var loaded: String? = null
        val owner = launch {
            runPagingSession(
                warningDelayMillis = 10,
                shouldWarn = true,
                load = {
                    loadGate.await()
                    "ready"
                },
                onLoaded = { loaded = it },
                onWarning = { warnings += 1 },
            )
        }
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(10)
        testScheduler.runCurrent()
        assertEquals(1, warnings)
        assertEquals(null, loaded)

        loadGate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals("ready", loaded)
        testScheduler.advanceTimeBy(20)
        testScheduler.runCurrent()
        assertEquals(1, warnings)

        owner.cancel()
        owner.join()
    }

    @Test
    fun pagingSessionInitialLoadFailureCancelsAllChildrenWithoutPublishing() = runTest {
        val expected = IllegalStateException("initial load failed")
        val ownedChildStarted = CompletableDeferred<Unit>()
        val ownedChildCancelled = CompletableDeferred<Unit>()
        var loaded: String? = null
        var warnings = 0

        val actual = assertFailsWith<IllegalStateException> {
            runPagingSession<String>(
                warningDelayMillis = 10_000,
                shouldWarn = true,
                load = { sessionScope ->
                    sessionScope.launch {
                        try {
                            ownedChildStarted.complete(Unit)
                            awaitCancellation()
                        } finally {
                            ownedChildCancelled.complete(Unit)
                        }
                    }
                    ownedChildStarted.await()
                    throw expected
                },
                onLoaded = { loaded = it },
                onWarning = { warnings += 1 },
            )
        }

        assertSame(expected, actual)
        assertTrue(ownedChildCancelled.isCompleted)
        assertEquals(null, loaded)
        testScheduler.advanceTimeBy(20_000)
        testScheduler.runCurrent()
        assertEquals(0, warnings)
    }

    @Test
    fun slowRecoveryCannotCompleteBeforeTerminalSignal() = runTest {
        val terminal = CompletableDeferred<Unit>()

        val result = async {
            awaitRecoveredUiState(
                awaitCompletion = { terminal.await() },
                rebuild = { UiState.Content("ready") },
            )
        }
        testScheduler.runCurrent()

        assertFalse(result.isCompleted)
        terminal.complete(Unit)
        assertEquals(UiState.Content("ready"), result.await())
    }

    @Test
    fun alreadyTerminalRecoveryReturnsErrorThroughTheSameResult() = runTest {
        val terminal = CompletableDeferred(Unit)

        val result = awaitRecoveredUiState<String>(
            awaitCompletion = { terminal.await() },
            rebuild = { UiState.Error("limited") },
        )

        assertEquals(UiState.Error("limited"), result)
    }

    @Test
    fun concurrentRecoveriesDoNotCancelOrConsumeEachOther() = runTest {
        val firstTerminal = CompletableDeferred<Unit>()
        val secondTerminal = CompletableDeferred<Unit>()
        val first = async {
            awaitRecoveredUiState(
                awaitCompletion = { firstTerminal.await() },
                rebuild = { UiState.Content("first") },
            )
        }
        val second = async {
            awaitRecoveredUiState(
                awaitCompletion = { secondTerminal.await() },
                rebuild = { UiState.Content("second") },
            )
        }
        testScheduler.runCurrent()

        secondTerminal.complete(Unit)
        assertEquals(UiState.Content("second"), second.await())
        assertFalse(first.isCompleted)

        firstTerminal.complete(Unit)
        assertEquals(UiState.Content("first"), first.await())
    }
}
