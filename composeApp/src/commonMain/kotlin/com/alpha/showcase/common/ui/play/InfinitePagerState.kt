package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Fixed virtual page count for the infinite slide pagers. It is deliberately
 * INDEPENDENT of the dataset size: the pager addresses items with `page % size`,
 * so a single large, constant page count works for any size and — crucially —
 * never has to change when the dataset is refreshed. That lets us keep ONE
 * [PagerState] for the pager's whole lifetime instead of rebuilding it (which
 * would cancel an in-progress drag/animation and reset progress).
 *
 * Half of Int.MAX_VALUE keeps `page * something` arithmetic inside Int range while
 * still giving effectively-infinite forward/backward travel.
 */
private const val INFINITE_PAGE_COUNT = Int.MAX_VALUE / 2

/**
 * How many pages on each side of the current page to keep pinned during a refresh.
 * The pager renders the current page plus the one(s) it is dragging toward, so
 * pinning a small window (not just the current page) keeps an adjacent page that
 * is partially visible during a half-drag from flashing when a refresh lands.
 */
private const val PIN_RADIUS = 1

internal fun shouldAttemptInitialPagerAlignment(isScrollInProgress: Boolean): Boolean =
    !isScrollInProgress

/**
 * Owns the state an infinite pager (Cube / Flip / Reveal) needs to stay stable
 * across a background-sync [PagingPlayItems.refresh] WITHOUT rebuilding the pager:
 *
 *  - [pagerState] is created ONCE and never rebuilt, so refreshes never cancel a
 *    gesture or animation.
 *  - When a refresh lands while the pager is mid-interaction (scrolling / settling
 *    onto a new page), the items in the visible window are FROZEN to a snapshot
 *    captured before the swap, and [item] keeps returning those frozen values. It
 *    is not enough to freeze only the modulo size: `data[index]` itself returns
 *    refreshed content (page 0 was replaced, other pages dropped), so the visible
 *    item must be pinned to its captured value, not re-read from `data`.
 *  - Only when the pager goes idle and has settled on a page do we drop the freeze
 *    and adopt the new size, so the next render reads live data. The image under
 *    the user's finger therefore never changes mid-gesture; the refreshed data is
 *    adopted seamlessly at the next natural turn.
 */
class InfinitePagerController internal constructor(
    private val data: PagingPlayItems,
    val pagerState: PagerState,
) {
    // Modulo divisor for indexing; only advanced to the live size on a clean settle.
    private var displaySizeState by mutableIntStateOf(data.size.coerceAtLeast(1))
    // The generation we have currently adopted. While the live generation matches,
    // we render live data and keep recording the settled item. When it diverges
    // (a refresh landed), we keep showing the recorded pre-refresh item until the
    // pager settles on a new page.
    private var adoptedGeneration by mutableIntStateOf(data.generation)

    private var lastSettledPage = pagerState.currentPage
    // The items shown in the visible window (current page ± PIN_RADIUS), recorded
    // CONTINUOUSLY by virtual page while the generation is stable (i.e. captured
    // BEFORE any refresh). When a refresh lands we keep returning these so neither
    // the current page nor an adjacent half-visible page changes — they are the
    // genuine pre-refresh images, not whatever data[index] returns after the swap.
    // Initialized empty FIRST: captureWindow merges the previous pin, so it must
    // never observe an uninitialized field.
    private var pinnedWindow: Map<Int, Any> = emptyMap()
    // Set while we issue an internal re-anchor scrollToPage so reconcile() ignores
    // the resulting currentPage change instead of treating it as a user turn.
    private var reanchoring = false
    // A locally-cancelled or generation-invalidated snap must be eligible to retry
    // even when it stopped on lastSettledPage. The destination pin remains the
    // shown identity for that next round.
    private var anchorSnapPending = false

    // Deferred adoption belongs to a concrete generation + located page + stable
    // media key and counts only that page's completed requests.
    private val anchorWait = AnchorWaitState()
    private val reconcileSignal = PagerReconcileSignal()

    init {
        pinnedWindow = captureWindow(pagerState.currentPage, displaySize)
    }

    /** The effective number of distinct items currently shown by the pager. */
    val displaySize: Int get() = displaySizeState

    internal val retryRevision: Int
        get() = reconcileSignal.retryRevision

    /**
     * Item rendered for a virtual [page]. While a refresh is pending, pages in the
     * frozen window keep their pre-refresh value so neither the current page nor an
     * adjacent half-visible page changes under an in-progress gesture/dwell; every
     * other page reads live data (so a page being scrolled toward shows fresh data).
     */
    fun item(page: Int): Any {
        if (data.generation != adoptedGeneration) {
            pinnedWindow[page]?.let { return it }
        }
        val size = displaySizeState.coerceAtLeast(1)
        return data[page % size]
    }

    private fun captureWindow(
        centerPage: Int,
        size: Int,
        preservePreviousPins: Boolean = true,
    ): Map<Int, Any> {
        val s = size.coerceAtLeast(1)
        val window = HashMap<Int, Any>(PIN_RADIUS * 2 + 1)
        for (p in (centerPage - PIN_RADIUS)..(centerPage + PIN_RADIUS)) {
            // Prefer the genuinely LOADED item: data[p % s] substitutes a page-0
            // fallback while a page is still loading, and pinning that stand-in
            // would make the next refresh "jump back" to a wrong image. While the
            // real item hasn't arrived, keep the previous pin for this virtual
            // page; only as a last resort record the fallback (still better than
            // letting the page change mid-gesture). Reading data[] also triggers
            // the async load, whose arrival bumps pagesRevision and re-runs this
            // capture with the real item.
            val value = capturedPagerItem(
                loaded = data.peekLoaded(p % s),
                previousPin = pinnedWindow[p],
                preservePreviousPin = preservePreviousPins,
            ) { runCatching { data[p % s] }.getOrNull() }
            value?.let { window[p] = it }
        }
        return window
    }

    /**
     * On controller construction (fresh entry, or a SOURCE SWAP that reuses the
     * long-lived PagerState): if the current virtual page maps to an index whose
     * page is NOT loaded, snap to the nearest page mapping to index 0 — the only
     * page guaranteed loaded at session start. Otherwise the first frames render
     * the page-0 fallback item and visibly swap once the far page arrives.
     */
    internal suspend fun alignToLoadedIndex(): Boolean {
        if (!shouldAttemptInitialPagerAlignment(pagerState.isScrollInProgress)) return false
        val size = displaySizeState.coerceAtLeast(1)
        val page = pagerState.currentPage
        val index = page % size
        if (index == 0 || data.peekLoaded(index) != null) return true
        val aligned = page - index
        val plan = AnchorSnapPlan(
            plannedGeneration = data.generation,
            originPage = page,
            targetPage = aligned,
            shownItem = pinnedWindow[page],
        )
        pinnedWindow = plan.withTemporaryPins(pinnedWindow)
        reanchoring = true
        val mutationCompleted = try {
            runPagerMutation { pagerState.scrollToPage(aligned) }
        } finally {
            reanchoring = false
        }
        if (!mutationCompleted) reconcileSignal.requestRetry()
        if (!plan.canAdopt(
                mutationCompleted = mutationCompleted,
                currentGeneration = data.generation,
                currentPage = pagerState.currentPage,
                isScrollInProgress = pagerState.isScrollInProgress,
            )
        ) {
            return false
        }
        lastSettledPage = aligned
        pinnedWindow = captureWindow(aligned, size)
        return true
    }

    /**
     * Called on any pager-state or generation change. While the generation is
     * stable it keeps recording the current settled item (the pre-refresh pin).
     * Once a refresh has landed AND the pager has settled on a DIFFERENT page, it
     * adopts the refreshed size/generation — re-anchoring the pager page so the
     * just-settled image is preserved across the size change (otherwise the same
     * virtual page would re-map to a different index under the new modulo and the
     * image would jump at the end of the turn).
     */
    internal suspend fun reconcile() {
        if (reanchoring) return
        val liveGeneration = data.generation
        val scrolling = pagerState.isScrollInProgress
        val page = pagerState.currentPage

        if (liveGeneration == adoptedGeneration) {
            anchorSnapPending = false
            // No refresh pending: keep the pinned window up to date with what's on
            // screen, but only while idle on the settled page (so the recorded
            // values are committed images, captured before any future refresh).
            if (!scrolling) {
                lastSettledPage = page
                pinnedWindow = captureWindow(page, displaySize)
            }
            return
        }

        // A refresh is pending. Don't adopt while scrolling, and don't adopt until
        // the user has turned to a new page — the window keeps the visible images
        // (current + adjacent) fixed throughout the gesture.
        if (scrolling || (page == lastSettledPage && !anchorSnapPending)) return

        // The image the user is ACTUALLY seeing at this just-settled page. While a
        // refresh is pending, item() returns pinnedWindow[page] (the pre-refresh
        // image) — NOT data[page % oldSize], which now yields refreshed content
        // because page 0 was swapped. We must re-anchor onto the pinned image, so
        // prefer it and only fall back to live data when the page wasn't pinned.
        val oldSize = displaySizeState.coerceAtLeast(1)
        val shownItem = pinnedWindow[page] ?: runCatching { data[page % oldSize] }.getOrNull()

        val newSize = data.size.coerceAtLeast(1)

        // Find the shown item's index in the refreshed data and pick a virtual page
        // that maps to it under newSize. Falls back to the raw mapping when the item
        // no longer exists in the refreshed data.
        var resolvedIndex = shownItem?.let { data.indexOfLoaded(it) }
        if (resolvedIndex == null && shownItem != null) {
            // Right after a refresh the fresh cache usually holds only page 0, so
            // an item that lived on another page cannot be found in memory. Ask
            // the BACKING STORE for the item's real index under the refreshed
            // ordering (stable media key): a numeric-modulo guess would wait on an
            // unrelated page and unfreeze onto a different image.
            val located = data.locate(shownItem)
            // Re-validate after the suspension: a newer refresh (G2) may have
            // landed or the user may have started a new gesture while the DB
            // lookup ran; scrolling/unfreezing against that stale plan would jump.
            // Bail — whatever invalidated us re-runs reconcile via snapshotFlow.
            if (data.generation != liveGeneration ||
                pagerState.isScrollInProgress ||
                pagerState.currentPage != page
            ) {
                return
            }
            if (located != null) {
                if (data.peekLoaded(located) == null) {
                    // Stay frozen (item() keeps returning the pinned pre-refresh
                    // images) until the item's ACTUAL page arrives — its load bumps
                    // its page-specific completion revision and re-runs reconcile.
                    // Global completions on other pages cannot spend this budget.
                    val targetAttempt = data.pageAttemptRevision(located)
                    if (anchorWait.shouldWait(
                            generation = liveGeneration,
                            targetPage = targetAttempt.pageNumber,
                            targetIndex = located,
                            stableMediaKey = stableMediaKey(shownItem),
                            targetAttemptRevision = targetAttempt.revision,
                        )
                    ) {
                        data.preload(located)
                        return
                    }
                    // Repeated failure/empty completions for the true target page
                    // exhausted the wait. The stable item remains pinned through
                    // the positional snap below.
                    resolvedIndex = located
                } else {
                    // The page may have loaded between locate and this check; find
                    // the exact post-shuffle slot by stable key before snapping.
                    resolvedIndex = data.indexOfLoaded(shownItem) ?: located
                }
            }
            // located == null: the media no longer exists in the refreshed data —
            // fall through to the positional mapping.
        }
        val targetIndex = (resolvedIndex ?: (page % newSize))
            .coerceIn(0, newSize - 1)
        var target = page - (page % newSize) + targetIndex
        if (target < 0) target += newSize
        if (target >= INFINITE_PAGE_COUNT) target -= newSize
        target = target.coerceIn(0, INFINITE_PAGE_COUNT - 1)

        // Do not publish newSize/adoptedGeneration before the suspending mutation.
        // Both endpoints are pinned to the G1 item so a G2 arriving mid-snap cannot
        // make the destination render G2's unrelated live modulo item.
        val plan = AnchorSnapPlan(
            plannedGeneration = liveGeneration,
            originPage = page,
            targetPage = target,
            shownItem = shownItem,
        )
        pinnedWindow = plan.withTemporaryPins(pinnedWindow)
        anchorSnapPending = true

        val mutationCompleted = if (target == page) {
            true
        } else {
            reanchoring = true
            try {
                runPagerMutation { pagerState.scrollToPage(target) }
            } finally {
                reanchoring = false
            }
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
            return
        }

        // Commit only after the plan is still current and the Pager is idle at its
        // exact destination. Until here displaySize remains the previously adopted
        // value and origin/destination remain frozen.
        displaySizeState = newSize
        adoptedGeneration = liveGeneration
        lastSettledPage = target
        anchorSnapPending = false
        pinnedWindow = captureWindow(
            centerPage = target,
            size = newSize,
            preservePreviousPins = false,
        )
        anchorWait.onAdoptionResult(adopted = true)
    }
}

/**
 * Builds a stable [InfinitePagerController]. The pager body should index items via
 * [InfinitePagerController.item] and read [InfinitePagerController.displaySize]
 * (NOT `data.size`) so the freeze/deferred-size logic takes effect. Callers must
 * NOT wrap this in `key(data.generation)` — the whole point is to reuse the pager.
 */
@Composable
fun rememberInfinitePagerController(data: PagingPlayItems): InfinitePagerController {
    // Align the initial virtual page so it maps to index 0 — the only page
    // guaranteed loaded at session start. The raw midpoint maps to an arbitrary
    // far index whose page is unloaded, so the pager would first render the
    // page-0 fallback item and then visibly swap once that page arrived.
    val half = INFINITE_PAGE_COUNT / 2
    val pagerState = rememberPagerState(
        initialPage = half - (half % data.size.coerceAtLeast(1)),
        pageCount = { INFINITE_PAGE_COUNT },
    )
    // Key on BOTH pagerState and data: if the PagingPlayItems object is swapped
    // (e.g. switching source), rebuild the controller so it never keeps showing the
    // old source's content. (Same-object in-place refreshes are handled via
    // generation/pin and do NOT swap the object, so this does not rebuild on them.)
    val controller = remember(pagerState, data) { InfinitePagerController(data, pagerState) }

    // Reconcile on any change to the dataset generation, page-content revision or
    // the pager's motion state: keep the pre-refresh pin current while stable
    // (including when an async page load replaces the fallback that was on
    // screen), and adopt the refreshed data once the pager settles on a new page.
    LaunchedEffect(controller) {
        // A reused PagerState after a SOURCE SWAP can sit on a virtual page whose
        // mapping under the new dataset isn't loaded — realign before reconciling.
        var initialAlignmentComplete = controller.alignToLoadedIndex()
        snapshotFlow {
            listOf(
                data.generation,
                data.pagesRevision,
                pagerState.currentPage,
                pagerState.isScrollInProgress,
                controller.retryRevision,
            )
        }.collect {
            if (!initialAlignmentComplete) {
                initialAlignmentComplete = controller.alignToLoadedIndex()
            }
            controller.reconcile()
        }
    }

    return controller
}
