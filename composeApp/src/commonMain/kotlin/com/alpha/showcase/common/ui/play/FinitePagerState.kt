package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps a FINITE pager (Slide) visually stable across a background-sync
 * [PagingPlayItems.refresh] by deferring BOTH the count and the content:
 *
 *  - Count: a finite [PagerState] re-reads its pageCount lambda continuously; if
 *    the dataset SHRINKS while the current page is beyond the new count, the
 *    pager clamps the position instantly (1000 -> 300 with page 850 teleports to
 *    299). The pager must therefore expose [displayCount], which only adopts a
 *    smaller count after the idle re-anchor below.
 *  - Content: freezing the count alone is NOT enough — [PagingPlayItems.get]
 *    wraps indices by the LIVE total, so page 850 would immediately render the
 *    item at 850 % 300 = 250. The pager must render via [item], which serves the
 *    pre-refresh content of the visible window until the refresh is adopted.
 *
 * Adoption happens while the pager is idle: re-anchor first (preferring the
 * shown item's identity — loaded pages, then the stable-key store lookup — over
 * raw position), then switch the count, so the clamp never fires and the visible
 * image survives whenever it still exists in the refreshed data.
 */
class FinitePagerCountController internal constructor(
    private val data: PagingPlayItems,
    // The item currently on screen (e.g. Slide's last-loaded media callback);
    // consulted as the re-anchor identity when the frozen window has no record.
    private val shownItem: () -> Any?,
) {
    // Keep the adopted count separate from any temporary growth needed solely to
    // make a snap destination addressable. A cancelled/stale snap never commits
    // its generation's count; if it already reached a newly exposed page, that
    // temporary range stays frozen until the next valid plan re-anchors it.
    private var adoptedDisplayCountState by mutableIntStateOf(data.size.coerceAtLeast(1))
    private var temporaryDisplayCountState by mutableIntStateOf(0)

    // The generation this controller has adopted. While it matches the live one,
    // item() reads live data and the window keeps recording; when a refresh lands
    // the window is served frozen until the idle re-anchor adopts the new data.
    private var adoptedGeneration by mutableIntStateOf(data.generation)

    // Pre-refresh content of the visible window (current page ± 1), recorded
    // continuously while the generation is stable.
    private var frozenWindow: Map<Int, Any> = emptyMap()

    private val anchorWait = AnchorWaitState()
    private val reconcileSignal = PagerReconcileSignal()

    init {
        // PagerState starts at page 0. Capture it synchronously so a sync that
        // finishes between controller construction and the observer's first
        // snapshotFlow emission cannot make the first rendered frame switch to
        // refreshed content before the identity re-anchor runs.
        frozenWindow = captureWindow(centerPage = 0)
    }

    /** The page count the pager should expose — NOT the live data.size. */
    val displayCount: Int
        get() = maxOf(adoptedDisplayCountState, temporaryDisplayCountState)

    /**
     * Content for [page]. While a refresh is pending (live generation differs
     * from the adopted one), pages in the frozen window keep their pre-refresh
     * content so the on-screen image can't change before the idle re-anchor.
     */
    fun item(page: Int): Any {
        if (data.generation != adoptedGeneration) {
            frozenWindow[page]?.let { return it }
        }
        return data[page]
    }

    /**
     * Call from a LaunchedEffect; observes the dataset and the pager's motion and
     * adopts refreshes at safe moments. Suspends until cancelled.
     */
    suspend fun observeAndReconcile(pagerState: PagerState) {
        snapshotFlow {
            listOf(
                data.generation,
                data.pagesRevision,
                data.size,
                pagerState.currentPage,
                pagerState.isScrollInProgress,
                reconcileSignal.retryRevision,
            )
        }.collect { reconcile(pagerState) }
    }

    private suspend fun reconcile(pagerState: PagerState) {
        val liveGeneration = data.generation
        val scrolling = pagerState.isScrollInProgress
        val page = pagerState.currentPage

        if (liveGeneration == adoptedGeneration) {
            // Stable: keep recording what is actually on screen — these are the
            // genuine pre-refresh images a future freeze must serve.
            if (!scrolling) frozenWindow = captureWindow(page)
            return
        }

        // A refresh landed. markEmpty (size 0): adopt silently and keep the last
        // count — PlayPage swaps to the not-found view; clamping mid-frame helps
        // nobody.
        val live = data.size
        if (live <= 0) {
            adoptedDisplayCountState = displayCount
            temporaryDisplayCountState = 0
            adoptedGeneration = liveGeneration
            frozenWindow = emptyMap()
            anchorWait.onAdoptionResult(adopted = true)
            return
        }
        // Only adopt while idle; mid-gesture/animation the frozen window keeps
        // the visuals stable.
        if (scrolling) return

        // Identity re-anchor: prefer the shown item's index in the refreshed data
        // (loaded pages first, then the stable-key store lookup), falling back to
        // the position-preserving modulo.
        val shown = frozenWindow[page] ?: shownItem()
        var resolvedIndex = shown?.let { data.indexOfLoaded(it) }
        if (resolvedIndex == null && shown != null) {
            val located = data.locate(shown)
            // Re-validate after the suspension: a newer refresh may have landed,
            // or the user may have moved the pager while the DB lookup ran.
            // Acting on the stale plan would scroll/unfreeze against the wrong
            // state — bail; whatever invalidated us re-runs reconcile.
            if (data.generation != liveGeneration ||
                pagerState.isScrollInProgress ||
                pagerState.currentPage != page
            ) {
                return
            }
            if (located != null) {
                if (data.peekLoaded(located) == null) {
                    // The item's page isn't loaded: STAY FROZEN until it arrives
                    // (every finished load attempt — success, empty or failure —
                    // bumps that page's own attempt revision). Random sessions
                    // especially must wait: the DB ordinal is the PRE-shuffle
                    // position, and only the in-memory stable-key search finds the
                    // item's real slot within the shuffled page.
                    val targetAttempt = data.pageAttemptRevision(located)
                    if (anchorWait.shouldWait(
                            generation = liveGeneration,
                            targetPage = targetAttempt.pageNumber,
                            targetIndex = located,
                            stableMediaKey = stableMediaKey(shown),
                            targetAttemptRevision = targetAttempt.revision,
                        )
                    ) {
                        data.preload(located)
                        return
                    }
                    // Only repeated completions of this exact target page can
                    // exhaust the wait. Keep the stable item pinned during the
                    // positional snap; the image fills in if the page later loads.
                    resolvedIndex = located
                } else {
                    // Page arrived (possibly between the top search and here):
                    // re-find by stable key for the exact post-shuffle slot.
                    resolvedIndex = data.indexOfLoaded(shown) ?: located
                }
            }
            // located == null: media gone from the refreshed data -> positional.
        }
        val target = (resolvedIndex ?: (page % live)).coerceIn(0, live - 1)
        val plan = AnchorSnapPlan(
            plannedGeneration = liveGeneration,
            originPage = page,
            targetPage = target,
            shownItem = shown,
        )
        // Both endpoints render the captured G1 item even if G2 changes live data
        // during scrollToPage. For growth expose only enough temporary count to
        // address the destination; this is not an adopted count.
        frozenWindow = plan.withTemporaryPins(frozenWindow)
        if (target >= displayCount) {
            temporaryDisplayCountState = target + 1
        }

        val mutationCompleted = if (target == page) {
            true
        } else {
            runPagerMutation { pagerState.scrollToPage(target) }
        }
        if (!mutationCompleted) reconcileSignal.requestRetry()
        if (!plan.canAdopt(
                mutationCompleted = mutationCompleted,
                currentGeneration = data.generation,
                currentPage = pagerState.currentPage,
                isScrollInProgress = pagerState.isScrollInProgress,
            )
        ) {
            anchorWait.onAdoptionResult(adopted = false)
            // If cancellation left us inside the committed range, the temporary
            // growth is no longer needed. Otherwise retain it together with the
            // destination pin so PagerState cannot clamp a half-completed snap.
            if (pagerState.currentPage < adoptedDisplayCountState) {
                temporaryDisplayCountState = 0
            }
            return
        }

        adoptedDisplayCountState = live
        temporaryDisplayCountState = 0
        adoptedGeneration = liveGeneration
        frozenWindow = captureWindow(target, preservePreviousPins = false)
        anchorWait.onAdoptionResult(adopted = true)
    }

    private fun captureWindow(
        centerPage: Int,
        preservePreviousPins: Boolean = true,
    ): Map<Int, Any> {
        val window = HashMap<Int, Any>(3)
        for (p in (centerPage - 1)..(centerPage + 1)) {
            if (p < 0) continue
            // Record only genuinely-loaded items; while a page is loading keep the
            // previous record, and only as a last resort store the fallback that
            // is on screen anyway (reading data[] also triggers the async load).
            val value = capturedPagerItem(
                loaded = data.peekLoaded(p),
                previousPin = frozenWindow[p],
                preservePreviousPin = preservePreviousPins,
            ) { runCatching { data[p] }.getOrNull() }
            value?.let { window[p] = it }
        }
        return window
    }
}

/**
 * Builds a stable [FinitePagerCountController] for [data]. Create it BEFORE the
 * [PagerState], pass `{ controller.displayCount }` as the pager's pageCount
 * lambda, render pages via [FinitePagerCountController.item], and wire it with
 * `LaunchedEffect(controller, pagerState) { controller.observeAndReconcile(pagerState) }`.
 */
@Composable
fun rememberFinitePagerCountController(
    data: PagingPlayItems,
    shownItem: () -> Any?,
): FinitePagerCountController {
    // Keyed on data: a SOURCE SWAP replaces the PagingPlayItems object and must
    // rebuild the controller (in-place refreshes keep the object, so they don't).
    return remember(data) { FinitePagerCountController(data, shownItem) }
}
