package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal enum class RecoveryObserverStage(internal val logName: String) {
    AwaitEvent("await_event"),
    Reconcile("reconcile"),
    Exhausted("exhausted"),
    AttemptCallback("attempt_callback"),
    ExhaustedCallback("exhausted_callback"),
}

internal fun recoveryFailureLogMessage(
    stage: RecoveryObserverStage,
    attempt: Int?,
    failure: Throwable,
): String = buildString {
    append("stage=")
    append(stage.logName)
    if (attempt != null) {
        append(" attempt=")
        append(attempt)
    }
    append(" exception=")
    append(failure::class.simpleName ?: "Throwable")
}

/**
 * Awaits one terminal event, then retries only the reconciliation work. The job
 * belongs to the receiver scope, so leaving the owning UI session cancels the
 * await, retry delay, and reconciliation together.
 */
internal fun <E> CoroutineScope.launchBoundedRecoveryObserver(
    awaitEvent: suspend () -> E,
    maxAttempts: Int = 3,
    retryDelayMillis: Long = 250L,
    reconcile: suspend (E) -> Unit,
    onAttemptFailure: (RecoveryObserverStage, Int, Throwable) -> Unit = { _, _, _ -> },
    onExhausted: (Throwable) -> Unit,
): Job {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    require(retryDelayMillis >= 0L) { "retryDelayMillis must not be negative" }

    return launch {
        fun reportAttempt(stage: RecoveryObserverStage, attempt: Int, failure: Throwable) {
            try {
                onAttemptFailure(stage, attempt, failure)
            } catch (e: CancellationException) {
                throw e
            } catch (callbackFailure: Exception) {
                Log.e(
                    "PlayRecovery",
                    recoveryFailureLogMessage(
                        RecoveryObserverStage.AttemptCallback,
                        attempt,
                        callbackFailure,
                    ),
                )
            }
        }

        fun reportExhausted(failure: Throwable) {
            try {
                onExhausted(failure)
            } catch (e: CancellationException) {
                throw e
            } catch (callbackFailure: Exception) {
                Log.e(
                    "PlayRecovery",
                    recoveryFailureLogMessage(
                        RecoveryObserverStage.ExhaustedCallback,
                        null,
                        callbackFailure,
                    ),
                )
            }
        }

        val event = try {
            awaitEvent()
        } catch (e: CancellationException) {
            throw e
        } catch (failure: Exception) {
            reportAttempt(RecoveryObserverStage.AwaitEvent, 1, failure)
            reportExhausted(failure)
            return@launch
        }

        var lastFailure: Throwable? = null
        repeat(maxAttempts) { attemptIndex ->
            try {
                reconcile(event)
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                lastFailure = failure
                val attempt = attemptIndex + 1
                reportAttempt(RecoveryObserverStage.Reconcile, attempt, failure)
                if (attempt < maxAttempts) delay(retryDelayMillis)
            }
        }
        reportExhausted(checkNotNull(lastFailure))
    }
}

/**
 * Keeps initial loading and slow-sync recovery on one suspend-result path.
 * The caller owns this coroutine, so concurrent PlayPages neither cancel each
 * other nor compete for a process-global recovery event.
 */
internal suspend fun <T> awaitRecoveredUiState(
    awaitCompletion: suspend () -> Unit,
    rebuild: suspend () -> UiState<T>,
): UiState<T> {
    awaitCompletion()
    return rebuild()
}
