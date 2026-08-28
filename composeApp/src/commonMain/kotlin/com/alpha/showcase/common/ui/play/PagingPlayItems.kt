package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.utils.Log
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Identity key used for re-anchoring after a refresh: media equality must
 * survive metadata drift (a NetworkFile whose size/modTime updated is still the
 * same picture) and auth-header churn — only the underlying path/url identifies
 * the media. Non-media items fall back to their own equality.
 */
internal fun stableMediaKey(item: Any?): Any? = when (item) {
    is NetworkFile -> item.path
    is UrlWithAuth -> item.url
    is ResolvedImageModel -> item.stableKey
    is DataWithType -> stableMediaKey(item.data)
    else -> item
}

/** Read-only completion signal for the page containing a requested index. */
internal data class PageAttemptRevision(
    val pageNumber: Int,
    val revision: Int,
)

internal enum class PagingFailureStage(internal val logName: String) {
    RefreshPrepare("refresh_prepare"),
    RefreshPageZero("refresh_page_zero"),
    PageLoad("page_load"),
    RefreshWork("refresh_work"),
    RefreshAbandonCallback("refresh_abandon_callback"),
    PreparedCommit("prepared_commit"),
    PreparedCommitCallback("prepared_commit_callback"),
    FailureCallback("failure_callback"),
    RefreshExhaustedCallback("refresh_exhausted_callback"),
}

internal fun pagingFailureLogMessage(
    stage: PagingFailureStage,
    pageNumber: Int?,
    failure: Throwable,
): String = buildString {
    append("stage=")
    append(stage.logName)
    if (pageNumber != null) {
        append(" page=")
        append(pageNumber)
    }
    append(" exception=")
    append(failure::class.simpleName ?: "Throwable")
}

internal inline fun <T> invokePagingCallback(
    callback: () -> T,
    onFailure: (Throwable) -> T,
): T = try {
    callback()
} catch (e: CancellationException) {
    throw e
} catch (failure: Exception) {
    onFailure(failure)
}

internal data class PreparedPagingRefresh(
    val totalCount: Int,
    val firstPage: List<Any>,
    /**
     * Non-suspending pin publication attempted inside the reducer commit boundary.
     * It must be non-throwing and non-reentrant; false rejects stale publication.
     * The reducer still guards unexpected exceptions before touching paging state.
     */
    val commitCandidatePin: () -> Boolean,
) {
    init {
        require(totalCount >= 0)
        require(
            (totalCount == 0 && firstPage.isEmpty()) ||
                (totalCount > 0 && firstPage.isNotEmpty()),
        )
    }
}

/**
 * A Compose-friendly paged data source for play/showcase UI components.
 *
 * Items are loaded in pages on demand and cached in memory with a window-based
 * eviction strategy to prevent OOM when dealing with large datasets.
 *
 * UI components access items by index via [get]. If the page containing that index
 * is not loaded yet, a fallback item from an already-loaded page is returned and
 * an async load is triggered. Once the page arrives, the Compose snapshot state
 * map update triggers recomposition so the correct item is displayed.
 *
 * The dataset is refreshed in-place via [refresh] once a background sync
 * completes (fresh page 0 is loaded first, then pages/count/generation are
 * swapped atomically). [get] never throws on a transient empty state. A monotonic
 * [generation] counter invalidates stale in-flight page loads; the infinite pagers
 * additionally use a deferred display size (see InfinitePagerController) so the
 * visible item never teleports when [totalCount] changes mid-interaction.
 */
@Stable
class PagingPlayItems(
    totalCount: Int,
    initialPage: List<Any>,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val coroutineScope: CoroutineScope,
    private val loadPage: suspend (offset: Int, limit: Int) -> List<Any>,
    // Optional stable-key locator against the session's backing store (DB):
    // resolves an item's CURRENT global index regardless of which pages are
    // loaded in memory. Null for static-list sessions.
    private val locateIndex: (suspend (item: Any) -> Int?)? = null,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 200
        private const val MAX_PAGES_IN_MEMORY = 5
        private const val PAGE0_RETRY_ATTEMPTS = 3
        private const val PAGE0_RETRY_DELAY_MS = 1_000L

        fun fromList(list: List<Any>, scope: CoroutineScope): PagingPlayItems {
            return PagingPlayItems(
                totalCount = list.size,
                initialPage = list,
                pageSize = list.size.coerceAtLeast(1),
                coroutineScope = scope,
                loadPage = { _, _ -> emptyList() }
            )
        }
    }

    private val _totalCount = mutableIntStateOf(totalCount)
    val totalCount: Int get() = _totalCount.intValue

    /**
     * Bumped on every [expand]. Snapshot-state-backed so that observing it in a
     * composable triggers recomposition. Two uses:
     *  - In-flight [requestPage] coroutines capture the generation they started
     *    in and discard their result if it changed, preventing stale pages from
     *    being written back after the dataset reshuffled.
     *  - Infinite pagers observe it via LaunchedEffect(generation) and re-anchor
     *    their current page so the visible item index stays consistent across a
     *    totalCount change instead of jumping due to a different `page % size`.
     */
    private val _generation = mutableIntStateOf(0)
    val generation: Int get() = _generation.intValue

    private val pages = mutableStateMapOf<Int, List<Any>>()

    private data class PageLoadIdentity(
        val generation: Int,
        val requestToken: Int,
    )

    private val pageLoadRequestCounter = atomic(0)
    private val loadingPages = mutableMapOf<Int, PageLoadIdentity>()

    /**
     * Pre-refresh copies of loaded pages, kept ONLY between a [refresh] and the
     * arrival of each page's fresh replacement. A mode currently displaying a far
     * page (Slide/Fade have no infinite-pager pinning) keeps rendering its old
     * content instead of flashing the page-0 fallback and then flipping back once
     * the page reloads.
     */
    private val stalePages = mutableStateMapOf<Int, List<Any>>()

    /**
     * Bumped whenever the content of loaded pages changes WITHOUT a generation
     * change — i.e. an async page load landing. Snapshot-state-backed so pin/anchor
     * logic (InfinitePagerController) can observe page arrivals and re-record what
     * is actually on screen; generation alone doesn't change when a lazily-loaded
     * page replaces its fallback.
     */
    private val _pagesRevision = mutableIntStateOf(0)
    val pagesRevision: Int get() = _pagesRevision.intValue

    // Current-generation load completions, isolated by page. Unlike the global
    // pagesRevision this lets anchor waits ignore activity on unrelated pages.
    private val pageAttemptRevisions = mutableStateMapOf<Int, Int>()

    internal fun pageAttemptRevision(index: Int): PageAttemptRevision {
        val count = totalCount
        val safeIndex = if (count <= 0) 0 else ((index % count) + count) % count
        val pageNumber = safeIndex / pageSize
        return PageAttemptRevision(
            pageNumber = pageNumber,
            revision = pageAttemptRevisions[pageNumber] ?: 0,
        )
    }

    // Latest-wins ordering for refresh()/markEmpty(). Claiming the ticket at API
    // call time is the ownership linearization point. Observable commits share the
    // same short lock with that claim, while all work is reduced by one serial
    // queue. Channel submission remains non-blocking for UI callers and is
    // available on every KMP target.
    private val refreshCommands = Channel<RefreshCommand>(Channel.UNLIMITED)
    private val refreshReducerWork = atomic(0)
    private val latestRefreshTicket = atomic<RefreshTicket?>(null)
    private val refreshOwnershipLock = SynchronizedObject()
    private var activeRefreshWork: ActiveRefreshWork? = null

    internal var onPreparedCommitFailure: ((Throwable) -> Unit)? = null
    internal var onFailure: ((PagingFailureStage, Int?, Throwable) -> Unit)? = null
    internal var onRefreshExhausted: (() -> Unit)? = null

    private class RefreshTicket(
        val generationAtClaim: Int,
        val onAbandoned: () -> Unit,
    ) {
        private val settled = atomic(false)

        fun markCommitted() {
            settled.compareAndSet(expect = false, update = true)
        }

        fun markAbandoned(): Boolean = settled.compareAndSet(expect = false, update = true)
    }

    private class ActiveRefreshWork(val ticket: RefreshTicket) {
        lateinit var job: Job
    }

    private sealed interface PreparedApplyResult {
        data object Committed : PreparedApplyResult
        data object Rejected : PreparedApplyResult
        data class Failed(val failure: Throwable) : PreparedApplyResult
    }

    private sealed interface RefreshCommand

    private data class RefreshRequested(
        val ticket: RefreshTicket,
        val prepare: suspend () -> PreparedPagingRefresh?,
    ) : RefreshCommand

    private data class RefreshPrepared(
        val ticket: RefreshTicket,
        val prepare: suspend () -> PreparedPagingRefresh?,
        val prepared: PreparedPagingRefresh?,
    ) : RefreshCommand

    private data class RetryDelayRequested(
        val ticket: RefreshTicket,
        val prepare: suspend () -> PreparedPagingRefresh?,
        val attempt: Int,
    ) : RefreshCommand

    private data class RetryDelayElapsed(
        val ticket: RefreshTicket,
        val prepare: suspend () -> PreparedPagingRefresh?,
        val attempt: Int,
    ) : RefreshCommand

    private data class RetryPrepared(
        val ticket: RefreshTicket,
        val prepare: suspend () -> PreparedPagingRefresh?,
        val attempt: Int,
        val prepared: PreparedPagingRefresh?,
    ) : RefreshCommand

    private data class EmptyRequested(val ticket: RefreshTicket) : RefreshCommand

    val size: Int get() = totalCount

    init {
        if (initialPage.isNotEmpty()) {
            pages[0] = initialPage
        }
    }

    /**
     * Refresh the dataset in-place after a background sync completes — used for
     * BOTH growth and same-count refreshes (file replacement, re-sort, deletions
     * that net to the same total). A successfully prepared refresh advances
     * [generation] and drops stale pages, so loaded-but-stale pages can never be
     * mixed with freshly-loaded ones. Failed preparation leaves the old generation
     * visible while the bounded retry policy runs.
     *
     * Ordering is critical: the FRESH page 0 is fetched FIRST, then `pages`,
     * `totalCount` and `generation` are swapped atomically (on one dispatcher, in
     * one continuation with no suspension between the writes). Only then do the
     * generation-keyed consumers (infinite pagers, FrameWall, Bento) recompose —
     * and they observe the already-fresh page 0, so they re-anchor / re-sample
     * onto current media rather than a stale copy. The old page 0 is kept until the
     * swap so [get] never hits the [EMPTY_PLACEHOLDER] path under a live pager.
     *
     * Runs on [coroutineScope] (the page dispatcher) so it is serialized with
     * [get]/[requestPage], which touch the plain [loadingPages] identity map.
     */
    fun refresh(newTotalCount: Int) {
        if (newTotalCount <= 0) return
        refreshPrepared {
            val freshPage0 = loadPage0OrNull() ?: return@refreshPrepared null
            PreparedPagingRefresh(
                totalCount = newTotalCount,
                firstPage = freshPage0,
                commitCandidatePin = { true },
            )
        }
    }

    /**
     * Prepare a candidate count/page/pin off the reducer, then publish them in one
     * non-suspending reducer commit. A failed preparation leaves the currently
     * visible dataset untouched and is retried by the same bounded policy as
     * [refresh].
     */
    internal fun refreshPrepared(
        claimIf: () -> Boolean = { true },
        onAbandoned: () -> Unit = {},
        prepare: suspend () -> PreparedPagingRefresh?,
    ): Boolean {
        val ticket = claimRefreshTicket(claimIf, onAbandoned) ?: return false
        if (!enqueueRefreshCommand(RefreshRequested(ticket, prepare))) {
            abandonTicket(ticket)
            return false
        }
        return true
    }

    /** Backwards-compatible alias; [refresh] now handles same-count refreshes too. */
    fun expand(newTotalCount: Int) = refresh(newTotalCount)

    /**
     * The source became empty after a refresh. Drops everything and sets the count
     * to 0 so readers of [size]/[isEmpty] recompose into an empty state instead of
     * continuing to show stale items. Dispatched onto [coroutineScope] like
     * [refresh] so all state mutations share one dispatcher.
     */
    fun markEmpty() {
        val ticket = claimRefreshTicket(claimIf = { true }, onAbandoned = {}) ?: return
        if (!enqueueRefreshCommand(EmptyRequested(ticket))) abandonTicket(ticket)
    }

    /**
     * Claim ownership and detach the previous ticket/work in one short critical
     * section. Eligibility may consult a session recovery lease; lock order is
     * always Paging -> Session. Cancellation and callbacks happen after unlock.
     */
    private fun claimRefreshTicket(
        claimIf: () -> Boolean,
        onAbandoned: () -> Unit,
    ): RefreshTicket? {
        var previousTicket: RefreshTicket? = null
        var previousJob: Job? = null
        val claimed = synchronized(refreshOwnershipLock) {
            if (!claimIf()) return@synchronized null

            previousTicket = latestRefreshTicket.value
            previousJob = activeRefreshWork?.job
            activeRefreshWork = null

            RefreshTicket(
                generationAtClaim = _generation.intValue,
                onAbandoned = onAbandoned,
            ).also { latestRefreshTicket.value = it }
        }

        if (claimed == null) {
            invokeAbandonCallback(onAbandoned)
            return null
        }

        previousJob?.cancel()
        previousTicket?.let(::abandonTicket)
        return claimed
    }

    /**
     * Enqueue without blocking the caller, starting a finite reducer only when
     * work exists. The work counter closes the empty-queue race without keeping a
     * permanent receiver (and therefore an obsolete PagingPlayItems) in a shared
     * scope. A cancelled reducer closes the channel; later submissions observe
     * failure instead of accumulating in an orphaned unlimited queue.
     */
    private fun enqueueRefreshCommand(command: RefreshCommand): Boolean {
        if (!coroutineScope.isActive) return false
        val sent = refreshCommands.trySend(command)
        if (sent.isFailure) return false
        if (refreshReducerWork.getAndIncrement() != 0) return true

        val reducerJob = coroutineScope.launch {
            drainRefreshCommands()
        }
        reducerJob.invokeOnCompletion { failure ->
            if (failure != null) {
                refreshCommands.close(failure)
                refreshReducerWork.value = 0
            }
        }
        return true
    }

    private fun drainRefreshCommands() {
        var submitted = 1
        while (true) {
            while (true) {
                val command = refreshCommands.tryReceive().getOrNull() ?: break
                reduceRefreshCommand(command)
            }
            submitted = refreshReducerWork.addAndGet(-submitted)
            if (submitted == 0) return
        }
    }

    /**
     * Single non-suspending reducer for refresh ownership and observable state.
     * Requests and load completions share this queue, so eligibility cannot be
     * checked on one dispatcher and committed later on another.
     */
    private fun reduceRefreshCommand(command: RefreshCommand) {
        when (command) {
            is RefreshRequested -> {
                // Ownership is claimed at the public call site. A request can be
                // obsolete before the reducer reaches it when an earlier load
                // completion and a newer request are already queued together.
                if (!ownsCurrentGeneration(command.ticket)) return
                launchOwnedWork(command.ticket) {
                    val prepared = prepareRefreshOrNull(command.prepare)
                    RefreshPrepared(
                        ticket = command.ticket,
                        prepare = command.prepare,
                        prepared = prepared,
                    )
                }
            }

            is RefreshPrepared -> {
                handlePreparedRefresh(
                    ticket = command.ticket,
                    prepared = command.prepared,
                    retryCommand = RetryDelayRequested(
                        ticket = command.ticket,
                        prepare = command.prepare,
                        attempt = 0,
                    ),
                )
            }

            is RetryDelayRequested -> {
                // Eligibility is checked immediately before scheduling the delay.
                if (!ownsCurrentGeneration(command.ticket)) return
                if (command.attempt >= PAGE0_RETRY_ATTEMPTS) {
                    reportRefreshExhausted()
                    abandonTicket(command.ticket)
                    return
                }
                launchOwnedWork(command.ticket) {
                    delay(PAGE0_RETRY_DELAY_MS)
                    RetryDelayElapsed(
                        ticket = command.ticket,
                        prepare = command.prepare,
                        attempt = command.attempt,
                    )
                }
            }

            is RetryDelayElapsed -> {
                // This command is reduced after the delay and after every request
                // that was already queued, so a newer call prevents the retry load.
                if (!ownsCurrentGeneration(command.ticket)) return
                launchOwnedWork(command.ticket) {
                    val prepared = prepareRefreshOrNull(command.prepare)
                    RetryPrepared(
                        ticket = command.ticket,
                        prepare = command.prepare,
                        attempt = command.attempt,
                        prepared = prepared,
                    )
                }
            }

            is RetryPrepared -> {
                handlePreparedRefresh(
                    ticket = command.ticket,
                    prepared = command.prepared,
                    retryCommand = RetryDelayRequested(
                        ticket = command.ticket,
                        prepare = command.prepare,
                        attempt = command.attempt + 1,
                    ),
                )
            }

            is EmptyRequested -> {
                var ownsCommit = false
                synchronized(refreshOwnershipLock) {
                    if (!ownsCurrentGeneration(command.ticket)) return@synchronized
                    ownsCommit = true
                    if (totalCount == 0 && pages.isEmpty() && stalePages.isEmpty()) {
                        return@synchronized
                    }
                    pages.clear()
                    stalePages.clear()
                    loadingPages.clear()
                    pageAttemptRevisions.clear()
                    _pagesRevision.intValue += 1
                    _generation.intValue = _generation.intValue + 1
                    _totalCount.intValue = 0
                }
                if (ownsCommit) command.ticket.markCommitted()
            }
        }
    }

    private fun handlePreparedRefresh(
        ticket: RefreshTicket,
        prepared: PreparedPagingRefresh?,
        retryCommand: RetryDelayRequested,
    ) {
        if (!ownsCurrentGeneration(ticket)) return
        if (prepared == null) {
            if (!enqueueRefreshCommand(retryCommand)) abandonTicket(ticket)
            return
        }

        val result = synchronized(refreshOwnershipLock) {
            if (!ownsCurrentGeneration(ticket)) return@synchronized null
            applyPreparedRefresh(prepared)
        } ?: return

        when (result) {
            PreparedApplyResult.Committed -> ticket.markCommitted()
            PreparedApplyResult.Rejected -> abandonTicket(ticket)
            is PreparedApplyResult.Failed -> {
                reportPreparedCommitFailure(result.failure)
                abandonTicket(ticket)
            }
        }
    }

    /**
     * Run exactly one prepare or retry-delay child for the current ticket. The job
     * is installed lazily under ownership, cleared before its completion command is
     * enqueued, and cancelled by a newer public claim.
     */
    private fun launchOwnedWork(
        ticket: RefreshTicket,
        produceNext: suspend () -> RefreshCommand,
    ) {
        val work = ActiveRefreshWork(ticket)
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                val next = produceNext()
                clearActiveWork(work)
                if (!enqueueRefreshCommand(next)) abandonTicket(ticket)
            } catch (e: CancellationException) {
                abandonTicket(ticket)
                throw e
            } catch (failure: Throwable) {
                reportFailure(PagingFailureStage.RefreshWork, null, failure)
                abandonTicket(ticket)
            } finally {
                clearActiveWork(work)
            }
        }
        work.job = job

        val installed = synchronized(refreshOwnershipLock) {
            if (!ownsCurrentGeneration(ticket) || activeRefreshWork != null) {
                false
            } else {
                activeRefreshWork = work
                true
            }
        }

        if (!installed) {
            job.cancel()
            if (!ownsCurrentGeneration(ticket)) abandonTicket(ticket)
            return
        }
        if (!job.start()) {
            clearActiveWork(work)
            abandonTicket(ticket)
        }
    }

    private fun clearActiveWork(work: ActiveRefreshWork) {
        synchronized(refreshOwnershipLock) {
            if (activeRefreshWork === work) activeRefreshWork = null
        }
    }

    private fun abandonTicket(ticket: RefreshTicket) {
        if (ticket.markAbandoned()) invokeAbandonCallback(ticket.onAbandoned)
    }

    private fun invokeAbandonCallback(callback: () -> Unit) {
        invokePagingCallback(callback) { failure ->
            reportFailure(PagingFailureStage.RefreshAbandonCallback, null, failure)
        }
    }

    private fun ownsCurrentGeneration(ticket: RefreshTicket): Boolean =
        ticket === latestRefreshTicket.value &&
            ticket.generationAtClaim == _generation.intValue

    private fun applyPreparedRefresh(prepared: PreparedPagingRefresh): PreparedApplyResult {
        // The reducer calls this directly and there is no suspension in the
        // ownership check + pin/pages/count/generation commit path.
        val pinCommitted = invokePagingCallback(prepared.commitCandidatePin) { failure ->
            return PreparedApplyResult.Failed(failure)
        }
        if (!pinCommitted) return PreparedApplyResult.Rejected

        val previousPages = pages.toMap()
        pages.clear()
        stalePages.clear()
        if (prepared.totalCount > 0) {
            previousPages.forEach { (pageNum, items) ->
                if (pageNum != 0) stalePages[pageNum] = items
            }
        }
        loadingPages.clear()
        pageAttemptRevisions.clear()
        if (prepared.firstPage.isNotEmpty()) {
            pages[0] = prepared.firstPage
        }
        _totalCount.intValue = prepared.totalCount
        _pagesRevision.intValue += 1
        _generation.intValue += 1
        return PreparedApplyResult.Committed
    }

    private fun reportPreparedCommitFailure(failure: Throwable) {
        reportFailure(PagingFailureStage.PreparedCommit, null, failure)
        val callback = onPreparedCommitFailure ?: return
        invokePagingCallback(callback = { callback(failure) }) { callbackFailure ->
            reportFailure(
                PagingFailureStage.PreparedCommitCallback,
                null,
                callbackFailure,
            )
        }
    }

    private fun reportFailure(
        stage: PagingFailureStage,
        pageNumber: Int?,
        failure: Throwable,
        notifyCallback: Boolean = true,
    ) {
        Log.e("PagingPlayItems", pagingFailureLogMessage(stage, pageNumber, failure))
        if (!notifyCallback) return
        val callback = onFailure ?: return
        invokePagingCallback(callback = { callback(stage, pageNumber, failure) }) { callbackFailure ->
            reportFailure(
                PagingFailureStage.FailureCallback,
                pageNumber,
                callbackFailure,
                notifyCallback = false,
            )
        }
    }

    private fun reportRefreshExhausted() {
        Log.e("PagingPlayItems", "stage=refresh_exhausted")
        val callback = onRefreshExhausted ?: return
        invokePagingCallback(callback) { callbackFailure ->
            reportFailure(
                PagingFailureStage.RefreshExhaustedCallback,
                null,
                callbackFailure,
                notifyCallback = false,
            )
        }
    }

    private suspend fun prepareRefreshOrNull(
        prepare: suspend () -> PreparedPagingRefresh?,
    ): PreparedPagingRefresh? = try {
        prepare()
    } catch (e: CancellationException) {
        throw e
    } catch (failure: Exception) {
        reportFailure(PagingFailureStage.RefreshPrepare, null, failure)
        null
    }

    private suspend fun loadPage0OrNull(): List<Any>? = try {
        loadPage(0, pageSize).takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (failure: Exception) {
        reportFailure(PagingFailureStage.RefreshPageZero, 0, failure)
        null
    }

    val isEmpty: Boolean get() = totalCount == 0

    /**
     * Get the item at the given index (modulo totalCount for cycling).
     * Always returns a non-null value and never throws: if no page is loaded yet
     * it returns a best-effort fallback while an async load proceeds.
     */
    operator fun get(index: Int): Any {
        val count = totalCount
        if (count <= 0) return EMPTY_PLACEHOLDER
        if (pages.isEmpty()) {
            // Transient: expansion or eviction left nothing cached. Trigger a
            // load and return a placeholder rather than crashing.
            requestPage(0)
            return EMPTY_PLACEHOLDER
        }
        val safeIndex = ((index % count) + count) % count
        val pageNum = safeIndex / pageSize
        val pageOffset = safeIndex % pageSize

        val page = pages[pageNum]
        if (page != null && pageOffset < page.size) {
            preloadAdjacent(pageNum)
            return page[pageOffset]
        }

        // Target page not loaded - request it and return fallback. Prefer the SAME
        // page's pre-refresh content (kept until its fresh replacement arrives) so
        // a refresh never downgrades a visible far page to the page-0 fallback.
        requestPage(pageNum)
        stalePages[pageNum]?.let { stale ->
            if (stale.isNotEmpty()) {
                return stale[pageOffset.coerceIn(0, stale.size - 1)]
            }
        }
        return getFallback(pageNum, pageOffset)
    }

    /**
     * The item at [index] ONLY if its page is currently loaded — no fallback
     * substitution, no stale copies and no load side effects. Used by pin/anchor
     * logic that must record genuinely-displayed fresh items, never a stand-in.
     */
    fun peekLoaded(index: Int): Any? {
        val count = totalCount
        if (count <= 0) return null
        val safeIndex = ((index % count) + count) % count
        val page = pages[safeIndex / pageSize] ?: return null
        return page.getOrNull(safeIndex % pageSize)
    }

    /**
     * Get a random item from currently loaded pages.
     */
    fun randomLoaded(): Any {
        val allPages = pages.values.toList()
        if (allPages.isEmpty()) return EMPTY_PLACEHOLDER
        val page = allPages[kotlin.random.Random.nextInt(allPages.size)]
        if (page.isEmpty()) return EMPTY_PLACEHOLDER
        return page[kotlin.random.Random.nextInt(page.size)]
    }

    /**
     * Get a batch of items starting from a given index.
     * Returns immediately available items from loaded pages.
     */
    fun getRange(startIndex: Int, count: Int): List<Any> {
        val result = mutableListOf<Any>()
        for (i in startIndex until (startIndex + count).coerceAtMost(totalCount)) {
            result.add(get(i))
        }
        return result
    }

    /**
     * Find the current global index of [item] among the pages currently held in
     * memory, comparing by STABLE MEDIA KEY (path/url) rather than full structural
     * equality — a refreshed row whose size/modTime/auth changed is still the same
     * picture and must keep its identity. Returns null if the item is not in a
     * loaded page — callers must have a fallback (or use [locate] to query the
     * backing store), since unloaded pages cannot be searched synchronously.
     */
    fun indexOfLoaded(item: Any?): Int? {
        if (item == null) return null
        val key = stableMediaKey(item) ?: return null
        for ((pageNum, page) in pages) {
            val offset = page.indexOfFirst { stableMediaKey(it) == key }
            if (offset >= 0) return pageNum * pageSize + offset
        }
        return null
    }

    /**
     * Resolve [item]'s CURRENT global index via the session's backing store
     * (stable-key DB lookup under the session's pinned version + sort), regardless
     * of which pages are loaded. Null when no locator is wired (static lists),
     * the item no longer exists in the refreshed data, or the lookup failed.
     */
    suspend fun locate(item: Any?): Int? {
        if (item == null) return null
        val locator = locateIndex ?: return null
        // Cancellation MUST propagate: swallowing it would let a cancelled
        // reconcile (source swap tearing down the effect) continue and act on a
        // "not found" that never happened. Only real lookup failures become null.
        val located = try {
            locator(item)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return located?.takeIf { it in 0 until totalCount }
    }

    /**
     * Trigger preloading of the page containing the given index.
     */
    fun preload(index: Int) {
        if (totalCount == 0) return
        val pageNum = (index % totalCount) / pageSize
        requestPage(pageNum)
    }

    private fun requestPage(pageNum: Int) {
        if (pages.containsKey(pageNum)) return
        loadPageInto(pageNum)
    }

    private fun loadPageInto(pageNum: Int) {
        if (loadingPages.containsKey(pageNum)) return
        val offset = pageNum * pageSize
        if (offset >= totalCount) return

        val requestGeneration = _generation.intValue
        val requestIdentity = PageLoadIdentity(
            generation = requestGeneration,
            requestToken = pageLoadRequestCounter.incrementAndGet(),
        )
        loadingPages[pageNum] = requestIdentity
        coroutineScope.launch {
            try {
                val result = runCatching { loadPage(offset, pageSize) }
                result.exceptionOrNull()?.let { failure ->
                    if (failure is CancellationException) throw failure
                    reportFailure(PagingFailureStage.PageLoad, pageNum, failure)
                }
                // Discard if the dataset was expanded/reshuffled while we loaded:
                // the offset may now point at different rows.
                if (requestGeneration == _generation.intValue) {
                    val items = result.getOrNull()
                    if (items != null) {
                        if (items.isNotEmpty()) {
                            pages[pageNum] = items
                        }
                        // An authoritative current-generation answer (even an
                        // empty one) obsoletes the pre-refresh stale copy. A
                        // FAILED load is not authoritative — keep showing stale.
                        stalePages.remove(pageNum)
                    }
                    // The revision advances for EVERY finished attempt — success,
                    // empty or failure. Anchor-wait reconciliation is driven by
                    // this signal; without it a failing/empty target page would
                    // never produce the retry event that lets the bounded wait
                    // count up and exit, leaving a pending refresh frozen forever.
                    pageAttemptRevisions[pageNum] =
                        (pageAttemptRevisions[pageNum] ?: 0) + 1
                    _pagesRevision.intValue += 1
                }
            } finally {
                if (loadingPages[pageNum] == requestIdentity) {
                    loadingPages.remove(pageNum)
                }
            }
            if (requestGeneration == _generation.intValue) {
                evictDistantPages(pageNum)
            }
        }
    }

    private fun preloadAdjacent(currentPage: Int) {
        if ((currentPage + 1) * pageSize < totalCount) {
            requestPage(currentPage + 1)
        }
        if (currentPage > 0) {
            requestPage(currentPage - 1)
        }
    }

    private fun evictDistantPages(currentPage: Int) {
        // Page 0 is the guaranteed render fallback for get()/getFallback, so it is
        // pinned and excluded from eviction candidates. The remaining budget is
        // MAX_PAGES_IN_MEMORY other pages closest to currentPage.
        val evictable = pages.keys.filter { it != 0 }
        val excess = evictable.size - MAX_PAGES_IN_MEMORY
        if (excess <= 0) return
        // Farthest-first, then take the excess so we drop the FARTHEST pages and
        // keep the ones nearest the current position.
        val toEvict = evictable
            .sortedByDescending { abs(it - currentPage) }
            .take(excess)
        toEvict.forEach { pages.remove(it) }
    }

    private fun getFallback(targetPage: Int, offset: Int): Any {
        val nearest = pages.keys.minByOrNull { abs(it - targetPage) }
            ?: return firstLoadedOrPlaceholder()
        val page = pages[nearest] ?: return firstLoadedOrPlaceholder()
        if (page.isEmpty()) return firstLoadedOrPlaceholder()
        return page[offset.coerceIn(0, page.size - 1)]
    }

    private fun firstLoadedOrPlaceholder(): Any {
        val page = pages.values.firstOrNull { it.isNotEmpty() } ?: return EMPTY_PLACEHOLDER
        return page.first()
    }
}

/**
 * Returned by [PagingPlayItems.get] only when no data is loaded at all (a
 * transient state during expansion). [PagerItem] renders it as a blank/loading
 * cell rather than crashing.
 */
val EMPTY_PLACEHOLDER: Any = ""
