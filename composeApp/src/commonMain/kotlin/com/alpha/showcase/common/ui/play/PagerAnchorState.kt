package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

internal const val MAX_ANCHOR_LOAD_ATTEMPTS = 4

/**
 * Counts only completed loads for one concrete anchor identity. Reconciles caused
 * by unrelated page changes repeatedly present the same [targetAttemptRevision]
 * and therefore cannot spend this anchor's retry budget.
 */
internal class AnchorWaitState(
    private val maxAttempts: Int = MAX_ANCHOR_LOAD_ATTEMPTS,
) {
    private data class Key(
        val generation: Int,
        val targetPage: Int,
        val targetIndex: Int,
        val stableMediaKey: Any?,
    )

    private var key: Key? = null
    private var observedTargetAttemptRevision = 0

    internal var completedAttempts: Int = 0
        private set

    init {
        require(maxAttempts > 0)
    }

    /** Returns true while this exact anchor still has a target-load attempt left. */
    fun shouldWait(
        generation: Int,
        targetPage: Int,
        targetIndex: Int,
        stableMediaKey: Any?,
        targetAttemptRevision: Int,
    ): Boolean {
        val nextKey = Key(generation, targetPage, targetIndex, stableMediaKey)
        if (nextKey != key) {
            key = nextKey
            observedTargetAttemptRevision = targetAttemptRevision
            completedAttempts = 0
            return true
        }

        if (targetAttemptRevision > observedTargetAttemptRevision) {
            completedAttempts = (
                completedAttempts + targetAttemptRevision - observedTargetAttemptRevision
            ).coerceAtMost(maxAttempts)
        }
        // A same-key revision is monotonic. If a defensive reset is ever observed,
        // use it as the next baseline without inventing a completed attempt.
        observedTargetAttemptRevision = targetAttemptRevision
        return completedAttempts < maxAttempts
    }

    /** A cancelled/stale snap retains its budget; only real adoption clears it. */
    fun onAdoptionResult(adopted: Boolean) {
        if (!adopted) return
        key = null
        observedTargetAttemptRevision = 0
        completedAttempts = 0
    }
}

/**
 * Immutable plan for one internal pager snap. Its pins are installed before the
 * suspending mutation, and adoption is allowed only if the entire plan is still
 * current after that suspension returns.
 */
internal data class AnchorSnapPlan(
    val plannedGeneration: Int,
    val originPage: Int,
    val targetPage: Int,
    val shownItem: Any?,
) {
    fun withTemporaryPins(existing: Map<Int, Any>): Map<Int, Any> {
        val shown = shownItem ?: return existing
        return existing.toMutableMap().apply {
            this[originPage] = shown
            this[targetPage] = shown
        }
    }

    fun canAdopt(
        mutationCompleted: Boolean,
        currentGeneration: Int,
        currentPage: Int,
        isScrollInProgress: Boolean,
    ): Boolean = mutationCompleted &&
        currentGeneration == plannedGeneration &&
        currentPage == targetPage &&
        !isScrollInProgress
}

/**
 * A Pager MutatorMutex can cancel one programmatic mutation when UserInput wins
 * without cancelling the surrounding observer job. Treat that as an interrupted
 * reconcile; a cancelled parent/effect is still rethrown by [ensureActive].
 */
internal suspend fun runPagerMutation(mutation: suspend () -> Unit): Boolean = try {
    mutation()
    true
} catch (_: CancellationException) {
    currentCoroutineContext().ensureActive()
    false
}

/**
 * Observable retry input for a controller's reconcile snapshot. A local pager
 * mutation cancellation changes no dataset or pager field, so this revision is
 * the explicit wake-up that lets the otherwise-identical plan run once more.
 */
internal class PagerReconcileSignal {
    private val retryRevisionState = mutableIntStateOf(0)

    val retryRevision: Int
        get() = retryRevisionState.intValue

    fun requestRetry() {
        retryRevisionState.intValue += 1
    }
}

/**
 * Chooses one captured window item. During a pending generation, preserving the
 * previous pin avoids flashes while a load is pending. Immediately after adoption
 * the old pins must be ignored because rendering now reads live data; otherwise a
 * later generation could reactivate a pre-refresh item that was no longer shown.
 */
internal fun capturedPagerItem(
    loaded: Any?,
    previousPin: Any?,
    preservePreviousPin: Boolean,
    fallback: () -> Any?,
): Any? {
    if (loaded != null) return loaded
    if (preservePreviousPin && previousPin != null) return previousPin
    return fallback()
}
