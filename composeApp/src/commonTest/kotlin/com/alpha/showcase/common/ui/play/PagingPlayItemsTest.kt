package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.cache.CacheSyncResult
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.repo.CachedSourceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PagingPlayItemsTest {

    /**
     * Runs one dispatched continuation at a time. This exposes the production
     * queue ordering that TestCoroutineScheduler.runCurrent() intentionally drains
     * in one go: an old load completion can be queued before the reducer runs,
     * followed by a newer refresh API call.
     */
    private class StepDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runNext() {
            check(tasks.isNotEmpty()) { "No dispatched task available" }
            tasks.removeFirst().run()
        }
    }

    private fun item(i: Int): Any = "item-$i"

    private fun cachedInfo(version: Long) = CachedSourceInfo(
        sourceType = "rss",
        sourceKey = "feed",
        remoteApi = RssSource("News", "https://example.com/feed.xml"),
        syncCompletion = CompletableDeferred<CacheSyncResult>(),
        committedSyncVersion = version,
    )

    @Test
    fun idlePagingDoesNotKeepPermanentReducerChildInSharedScope() =
        runTest(UnconfinedTestDispatcher()) {
            val scopeJob = backgroundScope.coroutineContext[Job]!!
            val childrenBefore = scopeJob.children.count()

            PagingPlayItems.fromList(listOf(item(0)), backgroundScope)
            testScheduler.runCurrent()

            assertEquals(
                childrenBefore,
                scopeJob.children.count(),
                "an idle paging instance must not be retained by a permanent channel receiver",
            )
        }

    /**
     * Builds a paging source whose loadPage returns deterministic items for the
     * requested offset/limit, capped at [total].
     */
    private fun pagingOf(
        total: Int,
        initial: List<Any>,
        pageSize: Int = 4,
        scope: kotlinx.coroutines.CoroutineScope,
        onLoad: (Int, Int) -> Unit = { _, _ -> },
    ): PagingPlayItems = PagingPlayItems(
        totalCount = total,
        initialPage = initial,
        pageSize = pageSize,
        coroutineScope = scope,
        loadPage = { offset, limit ->
            onLoad(offset, limit)
            (offset until (offset + limit).coerceAtMost(total)).map { item(it) }
        },
    )

    @Test
    fun get_triggersAsyncLoadOfUnloadedPage() = runTest(UnconfinedTestDispatcher()) {
        val loadOffsets = mutableListOf<Int>()
        val paging = pagingOf(
            total = 12,
            initial = listOf(item(0), item(1), item(2), item(3)),
            pageSize = 4,
            scope = backgroundScope,
            onLoad = { offset, _ -> loadOffsets += offset },
        )
        // Access an index in page 2 (offset 8) which is not loaded.
        paging[8]
        testScheduler.advanceUntilIdle()
        assertTrue(loadOffsets.contains(8), "get() should async-load offset 8; loads=$loadOffsets")
        // After the load commits, the exact item is returned.
        assertEquals(item(8), paging[8])
    }

    @Test
    fun get_returnsItemsFromInitialPage() = runTest(UnconfinedTestDispatcher()) {
        val paging = pagingOf(
            total = 10,
            initial = listOf(item(0), item(1), item(2), item(3)),
            scope = backgroundScope,
        )
        assertEquals(item(0), paging[0])
        assertEquals(item(3), paging[3])
    }

    @Test
    fun get_doesNotThrowAfterExpand_evenBeforeNewPagesLoad() = runTest(UnconfinedTestDispatcher()) {
        // Regression: expand() used to clear all pages, so the next get() saw an
        // empty cache and threw "PagingPlayItems is empty".
        val paging = pagingOf(
            total = 4,
            initial = listOf(item(0), item(1), item(2), item(3)),
            scope = backgroundScope,
        )
        paging.expand(1000)
        // Must not throw. Page 0 is retained, so index 500 (page not loaded yet)
        // falls back to a real item rather than crashing or returning a blank.
        val value = paging[500]
        assertEquals(item(0), value)
        assertEquals(1000, paging.totalCount)
    }

    @Test
    fun get_negativeIndexIsHandled() = runTest(UnconfinedTestDispatcher()) {
        val paging = pagingOf(
            total = 4,
            initial = listOf(item(0), item(1), item(2), item(3)),
            scope = backgroundScope,
        )
        // ((-1 % 4) + 4) % 4 == 3
        assertEquals(item(3), paging[-1])
    }

    @Test
    fun expand_growsCountAndBumpsGeneration() = runTest(UnconfinedTestDispatcher()) {
        val paging = pagingOf(
            total = 4,
            initial = listOf(item(0), item(1), item(2), item(3)),
            scope = backgroundScope,
        )
        val gen0 = paging.generation
        paging.expand(50)
        assertEquals(50, paging.totalCount)
        assertEquals(gen0 + 1, paging.generation)
    }

    @Test
    fun refresh_isNoOpOnlyForInvalidCount() = runTest(UnconfinedTestDispatcher()) {
        val paging = pagingOf(
            total = 4,
            initial = listOf(item(0), item(1), item(2), item(3)),
            scope = backgroundScope,
        )
        val gen0 = paging.generation
        // Invalid counts are ignored: no generation bump.
        paging.refresh(0)
        paging.refresh(-3)
        assertEquals(4, paging.totalCount)
        assertEquals(gen0, paging.generation)

        // A same-count refresh is NOT a no-op: it must still bump generation so
        // stale loaded pages are dropped (file replacement / re-sort keeps total).
        paging.refresh(4)
        assertEquals(4, paging.totalCount)
        assertEquals(gen0 + 1, paging.generation)
    }

    @Test
    fun markEmpty_setsEmptyStateAndStopsThrowing() = runTest(UnconfinedTestDispatcher()) {
        val paging = pagingOf(
            total = 4,
            initial = listOf(item(0), item(1), item(2), item(3)),
            scope = backgroundScope,
        )
        assertFalse(paging.isEmpty)
        paging.markEmpty()
        assertTrue(paging.isEmpty)
        assertEquals(0, paging.totalCount)
        // get() on an empty set must not throw; it returns the safe placeholder.
        assertEquals(EMPTY_PLACEHOLDER, paging[0])
    }

    @Test
    fun staleLoad_fromBeforeExpand_isDiscarded() = runTest(UnconfinedTestDispatcher()) {
        // A page load that is in-flight ACROSS an expand() must not write its
        // (now stale) result back after the generation changed.
        val gate = CompletableDeferred<Unit>()
        var staleLoadStarted = false
        var sawStaleWrite = false
        val paging = PagingPlayItems(
            totalCount = 12,
            initialPage = listOf("p0-0", "p0-1", "p0-2", "p0-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, limit ->
                // Only the FIRST page-1 load (before expand) is the gated, stale one.
                if (offset == 4 && !staleLoadStarted) {
                    staleLoadStarted = true
                    gate.await()
                    sawStaleWrite = true
                    List(limit) { "STALE-${offset + it}" }
                } else {
                    (offset until (offset + limit).coerceAtMost(40)).map { "p$offset-$it" }
                }
            },
        )
        // Start the page-1 load; it suspends on the gate.
        paging[4]
        // Expand while the load is parked -> generation bumps.
        paging.expand(40)
        // Now let the stale load finish.
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        // The stale load body ran, but its write must have been discarded by the
        // generation guard: index 4 must NOT surface a "STALE-*" value.
        assertTrue(sawStaleWrite, "stale load body should have executed")
        val value4 = paging[4]
        assertEquals(false, value4.toString().startsWith("STALE"), "stale page must be discarded, got $value4")
        assertEquals(40, paging.totalCount)
    }

    @Test
    fun expand_refreshesPage0WithCurrentGenerationData() = runTest(UnconfinedTestDispatcher()) {
        // Regression for review finding: the retained page 0 was never refreshed,
        // so deterministic sorts kept showing pre-expand ordering forever. After
        // expand(), page 0 must be reloaded.
        var version = "v1"
        val loadOffsets = mutableListOf<Int>()
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("v1-0", "v1-1", "v1-2", "v1-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, limit ->
                loadOffsets += offset
                (offset until (offset + limit).coerceAtMost(100)).map { "$version-$it" }
            },
        )
        assertEquals("v1-0", paging[0])

        // Simulate the DB now returning fresh rows for the same offsets.
        version = "v2"
        paging.expand(40)
        testScheduler.advanceUntilIdle()

        // Page 0 was force-reloaded under the new generation -> fresh data.
        assertTrue(loadOffsets.contains(0), "expand() should re-fetch offset 0; loads=$loadOffsets")
        assertEquals("v2-0", paging[0])
        assertEquals(40, paging.totalCount)
    }

    @Test
    fun refresh_swapsPage0AtomicallyBeforeGenerationVisible() = runTest(UnconfinedTestDispatcher()) {
        // The fresh page 0 must be in place AT the moment generation changes, so a
        // generation-keyed consumer never observes stale page-0 content. We assert
        // that once totalCount/generation reflect the refresh, page 0 already holds
        // v2 data (atomic swap), and that re-anchoring by item identity resolves the
        // NEW index rather than a stale one.
        var version = "v1"
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("v1-0", "v1-1", "v1-2", "v1-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, limit ->
                // v2 inserts a new item at the front, shifting "v1-1" to index 2.
                if (version == "v2") {
                    listOf("v2-new", "v2-a", "v1-1", "v2-b").take(limit)
                } else {
                    (offset until (offset + limit).coerceAtMost(100)).map { "$version-$it" }
                }
            },
        )
        val gen0 = paging.generation
        // Old identity of the item we're "watching".
        assertEquals(1, paging.indexOfLoaded("v1-1"))

        version = "v2"
        paging.refresh(8)
        testScheduler.advanceUntilIdle()

        // Generation advanced AND page 0 is already the v2 page (atomic).
        assertEquals(gen0 + 1, paging.generation)
        assertEquals("v2-new", paging[0])
        // Re-anchoring by stable identity finds the watched item's NEW index (2),
        // not the stale 1 — so the pager would not jump to a different image.
        assertEquals(2, paging.indexOfLoaded("v1-1"))
    }

    @Test
    fun refreshPreparedKeepsCandidatePinPrivateUntilAtomicSwap() =
        runTest(UnconfinedTestDispatcher()) {
            val prepareGate = CompletableDeferred<Unit>()
            var visiblePin = "v1"
            val paging = PagingPlayItems(
                totalCount = 8,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, limit ->
                    (offset until (offset + limit).coerceAtMost(8)).map { "$visiblePin-$it" }
                },
            )
            val generationBeforeRefresh = paging.generation

            paging.refreshPrepared {
                prepareGate.await()
                PreparedPagingRefresh(
                    totalCount = 8,
                    firstPage = List(4) { "v2-$it" },
                    commitCandidatePin = {
                        visiblePin = "v2"
                        true
                    },
                )
            }
            testScheduler.runCurrent()

            paging[4]
            testScheduler.runCurrent()
            assertEquals("v1", visiblePin)
            assertEquals(generationBeforeRefresh, paging.generation)
            assertEquals("v1-0", paging.peekLoaded(0))
            assertEquals("v1-4", paging.peekLoaded(4))

            prepareGate.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals("v2", visiblePin)
            assertEquals(generationBeforeRefresh + 1, paging.generation)
            assertEquals("v2-0", paging.peekLoaded(0))
        }

    @Test
    fun preparedEmptyRefreshCommitsPinAndEmptyStateAtomically() =
        runTest(UnconfinedTestDispatcher()) {
            assertFailsWith<IllegalArgumentException> {
                PreparedPagingRefresh(-1, emptyList(), { true })
            }
            assertFailsWith<IllegalArgumentException> {
                PreparedPagingRefresh(1, emptyList(), { true })
            }
            assertFailsWith<IllegalArgumentException> {
                PreparedPagingRefresh(0, listOf("unexpected"), { true })
            }

            var visiblePin = "v1"
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ -> emptyList() },
            )
            val generationBeforeRefresh = paging.generation
            val pagesRevisionBeforeRefresh = paging.pagesRevision

            paging.refreshPrepared {
                PreparedPagingRefresh(
                    totalCount = 0,
                    firstPage = emptyList(),
                    commitCandidatePin = {
                        visiblePin = "v2"
                        true
                    },
                )
            }
            testScheduler.advanceUntilIdle()

            assertEquals("v2", visiblePin)
            assertEquals(0, paging.totalCount)
            assertTrue(paging.isEmpty)
            assertEquals(null, paging.peekLoaded(0))
            assertEquals(EMPTY_PLACEHOLDER, paging[0])
            assertEquals(generationBeforeRefresh + 1, paging.generation)
            assertEquals(pagesRevisionBeforeRefresh + 1, paging.pagesRevision)
        }

    @Test
    fun obsoleteEmptyPreparationCannotSupersedeNewerPreparedContent() =
        runTest(UnconfinedTestDispatcher()) {
            val emptyStarted = CompletableDeferred<Unit>()
            val releaseEmpty = CompletableDeferred<Unit>()
            val contentStarted = CompletableDeferred<Unit>()
            val releaseContent = CompletableDeferred<Unit>()
            var visiblePin = "v1"
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ -> emptyList() },
            )
            val generationBeforeRefresh = paging.generation
            val pagesRevisionBeforeRefresh = paging.pagesRevision

            paging.refreshPrepared {
                emptyStarted.complete(Unit)
                releaseEmpty.await()
                PreparedPagingRefresh(
                    totalCount = 0,
                    firstPage = emptyList(),
                    commitCandidatePin = {
                        visiblePin = "a"
                        true
                    },
                )
            }
            emptyStarted.await()

            paging.refreshPrepared {
                contentStarted.complete(Unit)
                releaseContent.await()
                PreparedPagingRefresh(
                    totalCount = 4,
                    firstPage = List(4) { "b-$it" },
                    commitCandidatePin = {
                        visiblePin = "b"
                        true
                    },
                )
            }
            contentStarted.await()

            releaseEmpty.complete(Unit)
            testScheduler.runCurrent()

            assertEquals("v1", visiblePin)
            assertEquals(4, paging.totalCount)
            assertEquals("v1-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRefresh, paging.generation)
            assertEquals(pagesRevisionBeforeRefresh, paging.pagesRevision)

            releaseContent.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals("b", visiblePin)
            assertEquals(4, paging.totalCount)
            assertEquals("b-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRefresh + 1, paging.generation)
            assertEquals(pagesRevisionBeforeRefresh + 1, paging.pagesRevision)
        }

    @Test
    fun deadPinRecoveryNeverReturnsCandidateDataIntoOldGeneration() =
        runTest(UnconfinedTestDispatcher()) {
            val candidateGate = CompletableDeferred<Unit>()
            val session = PagedSourceSession(cachedInfo(version = 1L))
            val requestedPages = mutableListOf<Pair<Long?, Int>>()
            val stagedCandidates = mutableListOf<CachedSourceInfo>()
            var expiredFailures = 0
            lateinit var paging: PagingPlayItems

            session.onPinLost = { recovery ->
                val candidate = recovery.candidate
                stagedCandidates += candidate
                paging.refreshPrepared(
                    claimIf = { session.isCurrent(recovery) },
                    onAbandoned = { session.abandonRecovery(recovery) },
                ) {
                    candidateGate.await()
                    PreparedPagingRefresh(
                        totalCount = 8,
                        firstPage = List(4) { "v${candidate.committedSyncVersion}-$it" },
                        commitCandidatePin = { session.commitRecovery(recovery) },
                    )
                }
            }
            paging = PagingPlayItems(
                totalCount = 8,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, limit ->
                    try {
                        loadPinnedPageOrStageRecovery(
                            session = session,
                            loadPinned = { pinned ->
                                requestedPages += pinned.committedSyncVersion to offset
                                if (pinned.committedSyncVersion == 1L) {
                                    emptyList()
                                } else {
                                    List(limit) { "v${pinned.committedSyncVersion}-${offset + it}" }
                                }
                            },
                            resolveLiveVersion = { 2L },
                        )
                    } catch (e: PinnedSourceVersionExpiredException) {
                        expiredFailures += 1
                        throw e
                    }
                },
            )
            val generationBeforeRecovery = paging.generation

            paging[4]
            testScheduler.runCurrent()

            assertEquals(1, expiredFailures)
            assertEquals(listOf<Pair<Long?, Int>>(1L to 4), requestedPages)
            assertEquals(2L, stagedCandidates.single().committedSyncVersion)
            assertEquals(1L, session.info.committedSyncVersion)
            assertEquals(generationBeforeRecovery, paging.generation)
            assertEquals(null, paging.peekLoaded(4))
            assertEquals("v1-0", paging.peekLoaded(0))

            candidateGate.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(2L, session.info.committedSyncVersion)
            assertEquals(generationBeforeRecovery + 1, paging.generation)
            assertEquals("v2-0", paging.peekLoaded(0))
        }

    @Test
    fun repeatedDeadPinPagesCoalesceOnePendingRecovery() =
        runTest(UnconfinedTestDispatcher()) {
            val recoveryGate = CompletableDeferred<Unit>()
            val session = PagedSourceSession(cachedInfo(version = 1L))
            var recoverySchedules = 0
            lateinit var paging: PagingPlayItems

            session.onPinLost = { recovery ->
                val candidate = recovery.candidate
                recoverySchedules += 1
                paging.refreshPrepared(
                    claimIf = { session.isCurrent(recovery) },
                    onAbandoned = { session.abandonRecovery(recovery) },
                ) {
                    recoveryGate.await()
                    PreparedPagingRefresh(
                        totalCount = 12,
                        firstPage = List(4) { "v${candidate.committedSyncVersion}-$it" },
                        commitCandidatePin = { session.commitRecovery(recovery) },
                    )
                }
            }
            paging = PagingPlayItems(
                totalCount = 12,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ ->
                    loadPinnedPageOrStageRecovery(
                        session = session,
                        loadPinned = { emptyList<String>() },
                        resolveLiveVersion = { 2L },
                    )
                },
            )
            val generationBeforeRecovery = paging.generation

            paging[4]
            paging[8]
            testScheduler.runCurrent()

            assertEquals(1, recoverySchedules)
            assertEquals(1L, session.info.committedSyncVersion)
            assertEquals(generationBeforeRecovery, paging.generation)

            recoveryGate.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(2L, session.info.committedSyncVersion)
            assertEquals("v2-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRecovery + 1, paging.generation)
        }

    @Test
    fun exhaustedDeadPinRecoveryReleasesLeaseForLaterRetry() =
        runTest(UnconfinedTestDispatcher()) {
            val session = PagedSourceSession(cachedInfo(version = 1L))
            var recoverySchedules = 0
            var prepareAttempts = 0
            lateinit var paging: PagingPlayItems

            session.onPinLost = { recovery ->
                recoverySchedules += 1
                paging.refreshPrepared(
                    claimIf = { session.isCurrent(recovery) },
                    onAbandoned = { session.abandonRecovery(recovery) },
                ) {
                    prepareAttempts += 1
                    null
                }
            }
            paging = PagingPlayItems(
                totalCount = 12,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ ->
                    loadPinnedPageOrStageRecovery(
                        session = session,
                        loadPinned = { emptyList<String>() },
                        resolveLiveVersion = { 2L },
                    )
                },
            )
            val generationBeforeRecovery = paging.generation

            paging[4]
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()

            assertEquals(1, recoverySchedules)
            assertTrue(prepareAttempts > 1, "the first recovery should exhaust its retries")
            assertEquals(1L, session.info.committedSyncVersion)
            assertEquals(generationBeforeRecovery, paging.generation)

            paging[8]
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()

            assertEquals(2, recoverySchedules)
            assertEquals(1L, session.info.committedSyncVersion)
            assertEquals(generationBeforeRecovery, paging.generation)
        }

    @Test
    fun newerDeadPinCandidateReplacesPendingWhileOlderResolutionIsIgnored() =
        runTest(UnconfinedTestDispatcher()) {
            val version2Gate = CompletableDeferred<Unit>()
            val version3Gate = CompletableDeferred<Unit>()
            val session = PagedSourceSession(cachedInfo(version = 1L))
            val scheduledVersions = mutableListOf<Long?>()
            lateinit var paging: PagingPlayItems

            session.onPinLost = { recovery ->
                val candidate = recovery.candidate
                scheduledVersions += candidate.committedSyncVersion
                paging.refreshPrepared(
                    claimIf = { session.isCurrent(recovery) },
                    onAbandoned = { session.abandonRecovery(recovery) },
                ) {
                    when (candidate.committedSyncVersion) {
                        2L -> version2Gate.await()
                        3L -> version3Gate.await()
                    }
                    PreparedPagingRefresh(
                        totalCount = 16,
                        firstPage = List(4) { "v${candidate.committedSyncVersion}-$it" },
                        commitCandidatePin = { session.commitRecovery(recovery) },
                    )
                }
            }
            paging = PagingPlayItems(
                totalCount = 16,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, _ ->
                    loadPinnedPageOrStageRecovery(
                        session = session,
                        loadPinned = { emptyList<String>() },
                        resolveLiveVersion = {
                            when (offset) {
                                4 -> 2L
                                8 -> 3L
                                else -> 2L
                            }
                        },
                    )
                },
            )
            val generationBeforeRecovery = paging.generation

            paging[4]
            testScheduler.runCurrent()
            paging[8]
            testScheduler.runCurrent()
            paging[12]
            testScheduler.runCurrent()

            assertEquals(listOf<Long?>(2L, 3L), scheduledVersions)

            version2Gate.complete(Unit)
            version3Gate.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(3L, session.info.committedSyncVersion)
            assertEquals("v3-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRecovery + 1, paging.generation)
        }

    @Test
    fun staleNormalCandidateCannotRegressCommittedRecovery() =
        runTest(UnconfinedTestDispatcher()) {
            val normalRelease = CompletableDeferred<Unit>()
            val normalStarted = CompletableDeferred<Unit>()
            val session = PagedSourceSession(cachedInfo(version = 1L))
            val expectedPinned = session.info
            val normalCandidate = expectedPinned.copy(
                committedSyncVersion = 2L,
                initialSnapshot = false,
            )
            var normalRefreshAccepted = false
            var normalPrepareAttempts = 0
            var normalCleanupCalls = 0
            lateinit var paging: PagingPlayItems

            session.onPinLost = { recovery ->
                paging.refreshPrepared(
                    claimIf = { session.isCurrent(recovery) },
                    onAbandoned = { session.abandonRecovery(recovery) },
                ) {
                    PreparedPagingRefresh(
                        totalCount = 12,
                        firstPage = List(4) { "v3-$it" },
                        commitCandidatePin = { session.commitRecovery(recovery) },
                    )
                }
            }
            paging = PagingPlayItems(
                totalCount = 12,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ ->
                    loadPinnedPageOrStageRecovery(
                        session = session,
                        loadPinned = { emptyList<String>() },
                        resolveLiveVersion = { 3L },
                    )
                },
            )
            val generationBeforeRecovery = paging.generation

            backgroundScope.launch {
                normalStarted.complete(Unit)
                normalRelease.await()
                normalRefreshAccepted = paging.refreshPrepared(
                    claimIf = { session.info === expectedPinned },
                    onAbandoned = { normalCleanupCalls += 1 },
                ) {
                    normalPrepareAttempts += 1
                    PreparedPagingRefresh(
                        totalCount = 8,
                        firstPage = List(4) { "v2-$it" },
                        commitCandidatePin = {
                            session.commitCandidate(expectedPinned, normalCandidate)
                        },
                    )
                }
            }
            normalStarted.await()

            paging[4]
            testScheduler.runCurrent()

            assertEquals(3L, session.info.committedSyncVersion)
            assertEquals("v3-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRecovery + 1, paging.generation)

            normalRelease.complete(Unit)
            testScheduler.runCurrent()

            assertEquals(3L, session.info.committedSyncVersion)
            assertEquals("v3-0", paging.peekLoaded(0))
            assertEquals(12, paging.totalCount)
            assertEquals(generationBeforeRecovery + 1, paging.generation)
            assertFalse(normalRefreshAccepted)
            assertEquals(0, normalPrepareAttempts)
            assertEquals(1, normalCleanupCalls)
        }

    @Test
    fun supersededPreparedWorkIsCancelledWithoutKillingReducer() =
        runTest(UnconfinedTestDispatcher()) {
            val obsoleteStarted = CompletableDeferred<Unit>()
            val obsoleteGate = CompletableDeferred<Unit>()
            val obsoleteCancelled = CompletableDeferred<Unit>()
            var visiblePin = "v1"
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ -> emptyList() },
            )
            val generationBeforeRefresh = paging.generation

            paging.refreshPrepared {
                obsoleteStarted.complete(Unit)
                try {
                    obsoleteGate.await()
                    PreparedPagingRefresh(4, List(4) { "a-$it" }, { true })
                } catch (e: CancellationException) {
                    obsoleteCancelled.complete(Unit)
                    throw e
                }
            }
            obsoleteStarted.await()

            paging.refreshPrepared {
                PreparedPagingRefresh(
                    totalCount = 4,
                    firstPage = List(4) { "b-$it" },
                    commitCandidatePin = {
                        visiblePin = "b"
                        true
                    },
                )
            }
            testScheduler.runCurrent()

            assertTrue(obsoleteCancelled.isCompleted)
            assertEquals("b", visiblePin)
            assertEquals("b-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRefresh + 1, paging.generation)

            paging.refreshPrepared {
                PreparedPagingRefresh(
                    totalCount = 4,
                    firstPage = List(4) { "c-$it" },
                    commitCandidatePin = {
                        visiblePin = "c"
                        true
                    },
                )
            }
            testScheduler.advanceUntilIdle()

            assertEquals("c", visiblePin)
            assertEquals("c-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRefresh + 2, paging.generation)
        }

    @Test
    fun throwingPreparedCommitDoesNotPublishOrKillReducer() =
        runTest(UnconfinedTestDispatcher()) {
            var visiblePin = "v1"
            var reportedFailure: Throwable? = null
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ -> emptyList() },
            )
            paging.onPreparedCommitFailure = { reportedFailure = it }
            val generationBeforeRefresh = paging.generation
            val pagesRevisionBeforeRefresh = paging.pagesRevision

            paging.refreshPrepared {
                PreparedPagingRefresh(
                    totalCount = 8,
                    firstPage = List(4) { "broken-$it" },
                    commitCandidatePin = { error("pin commit failed") },
                )
            }
            testScheduler.runCurrent()

            assertEquals("v1", visiblePin)
            assertEquals(4, paging.totalCount)
            assertEquals("v1-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRefresh, paging.generation)
            assertEquals(pagesRevisionBeforeRefresh, paging.pagesRevision)
            assertEquals("pin commit failed", reportedFailure?.message)

            paging.refreshPrepared {
                PreparedPagingRefresh(
                    totalCount = 4,
                    firstPage = List(4) { "v2-$it" },
                    commitCandidatePin = {
                        visiblePin = "v2"
                        true
                    },
                )
            }
            testScheduler.advanceUntilIdle()

            assertEquals("v2", visiblePin)
            assertEquals("v2-0", paging.peekLoaded(0))
            assertEquals(generationBeforeRefresh + 1, paging.generation)
        }

    @Test
    fun failedPreparedPage0DoesNotPublishBeforeRetrySucceeds() =
        runTest(UnconfinedTestDispatcher()) {
            // If preparation fails, the old count/page/generation stay published until
            // a bounded retry can prepare a complete replacement.
            var phase = "v1"
            var failNextPage0 = false
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = listOf("v1-0", "v1-1", "v1-2", "v1-3"),
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, _ ->
                    if (offset == 0 && failNextPage0) {
                        failNextPage0 = false
                        throw RuntimeException("transient page-0 failure")
                    }
                    if (phase == "v2") listOf("v2-0", "v2-1", "v2-2", "v2-3")
                    else listOf("v1-0", "v1-1", "v1-2", "v1-3")
                },
            )
            assertEquals("v1-0", paging[0])
            val genBeforeRefresh = paging.generation

            // The first page-0 load during refresh fails; retry should succeed with v2.
            phase = "v2"
            failNextPage0 = true
            paging.refresh(8)
            testScheduler.runCurrent()

            assertEquals(4, paging.totalCount)
            assertEquals("v1-0", paging.peekLoaded(0))
            assertEquals(genBeforeRefresh, paging.generation)

            // Advance past the retry backoff so the background retry fires.
            testScheduler.advanceTimeBy(5_000)
            testScheduler.advanceUntilIdle()

            assertEquals(8, paging.totalCount)
            assertEquals("v2-0", paging[0])
            assertEquals(genBeforeRefresh + 1, paging.generation)
        }

    @Test
    fun olderRefreshRetryCannotApplyAfterNewerRefreshWasCalled() = runTest(UnconfinedTestDispatcher()) {
        // R1 fails preparation and parks in retry backoff. R2 is CALLED but its
        // page-0 preparation is still pending. R1's retry must already have lost
        // ownership even though neither request has committed a generation yet.
        val r2Page0Gate = CompletableDeferred<Unit>()
        var page0Attempt = 0
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("initial-0", "initial-1", "initial-2", "initial-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, _ ->
                check(offset == 0)
                when (++page0Attempt) {
                    1 -> throw RuntimeException("R1 initial fetch fails")
                    2 -> {
                        r2Page0Gate.await()
                        listOf("R2-0", "R2-1", "R2-2", "R2-3")
                    }
                    else -> listOf("R1-RETRY-0", "R1-RETRY-1", "R1-RETRY-2", "R1-RETRY-3")
                }
            },
        )

        paging.refresh(8) // R1: leaves current state untouched and schedules retry.
        val generationAfterR1 = paging.generation
        paging.refresh(12) // R2: claims latest ownership, then parks in loadPage.

        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        // R1 retry must not write page 0 or bump generation while R2 is pending.
        assertEquals("initial-0", paging.peekLoaded(0))
        assertEquals(generationAfterR1, paging.generation)

        r2Page0Gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals("R2-0", paging.peekLoaded(0))
        assertEquals(12, paging.totalCount)
    }

    @Test
    fun page0RetryPropagatesCancellationInsteadOfContinuingAttempts() =
        runTest(UnconfinedTestDispatcher()) {
            var attempts = 0
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = listOf("initial-0", "initial-1", "initial-2", "initial-3"),
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ ->
                    attempts += 1
                    if (attempts == 1) throw RuntimeException("initial transient failure")
                    throw CancellationException("retry owner cancelled")
                },
            )

            paging.refresh(4)
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()

            assertEquals(2, attempts, "CancellationException must terminate the retry coroutine")
        }

    @Test
    fun successfulPage0RetryInvalidatesOlderGenerationPageMarkers() =
        runTest(UnconfinedTestDispatcher()) {
            val oldGenerationPageGate = CompletableDeferred<Unit>()
            val newGenerationPageGate = CompletableDeferred<Unit>()
            var page0Attempts = 0
            var page1Requests = 0
            val paging = PagingPlayItems(
                totalCount = 12,
                initialPage = listOf("initial-0", "initial-1", "initial-2", "initial-3"),
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, limit ->
                    when (offset) {
                        0 -> {
                            page0Attempts += 1
                            if (page0Attempts == 1) throw RuntimeException("initial page-0 failure")
                            List(limit) { "recovered-$it" }
                        }
                        4 -> {
                            page1Requests += 1
                            if (page1Requests == 1) {
                                oldGenerationPageGate.await()
                            } else {
                                newGenerationPageGate.await()
                            }
                            List(limit) { "page1-${offset + it}" }
                        }
                        else -> error("unexpected offset $offset")
                    }
                },
            )

            paging.refresh(12) // Initial page-0 preparation fails; state is unchanged.
            paging[4] // Page 1 is now pending under the old generation.
            assertEquals(1, page1Requests)

            testScheduler.advanceTimeBy(1_000)
            testScheduler.runCurrent() // Retry atomically commits page 0 and one generation.

            paging[4]
            testScheduler.runCurrent()
            assertEquals(
                2,
                page1Requests,
                "retry generation bump must not leave the old page marker blocking a new request",
            )

            oldGenerationPageGate.complete(Unit)
            newGenerationPageGate.complete(Unit)
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun reAnchorScenario_sizeGrowsAndShownItemKeepsLocatableIndex() = runTest(UnconfinedTestDispatcher()) {
        // Mirrors the infinite-pager re-anchor case (reviewer's 4 -> 5 example): the
        // item shown before the refresh must be locatable at its NEW index after the
        // size changes, so the controller can scroll to preserve the image instead
        // of letting `page % newSize` remap to a different one.
        var phase = "v1"
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("a", "b", "c", "d"),
            pageSize = 8,
            coroutineScope = backgroundScope,
            loadPage = { _, _ ->
                // v2 grows to 5 items, inserting "x" at the front so "c" moves 2 -> 3.
                if (phase == "v2") listOf("x", "a", "b", "c", "d")
                else listOf("a", "b", "c", "d")
            },
        )
        // Item shown at logical index 2 before refresh.
        assertEquals("c", paging[2])
        assertEquals(2, paging.indexOfLoaded("c"))

        phase = "v2"
        paging.refresh(5)
        testScheduler.advanceUntilIdle()

        // After the size grows to 5, the same item "c" is now at index 3 and is
        // locatable, so the pager can re-anchor onto it (no jump).
        assertEquals(5, paging.totalCount)
        assertEquals(3, paging.indexOfLoaded("c"))
        assertEquals("c", paging[3])
    }

    @Test
    fun refresh_keepsShowingOldFarPageUntilFreshPageArrives() = runTest(UnconfinedTestDispatcher()) {
        // Regression (review P1): refresh() used to drop every page but 0, so a
        // mode displaying a far page (Slide/Fade) flashed the page-0 fallback and
        // then flipped to the reloaded page. The old far-page content must stay
        // visible as STALE data until its fresh replacement actually lands.
        val gate = CompletableDeferred<Unit>()
        var version = "v1"
        val paging = PagingPlayItems(
            totalCount = 12,
            initialPage = listOf("v1-0", "v1-1", "v1-2", "v1-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, limit ->
                // Hold back the refreshed NON-zero pages; page 0 loads instantly.
                if (version == "v2" && offset > 0) gate.await()
                (offset until (offset + limit).coerceAtMost(12)).map { "$version-$it" }
            },
        )
        // Load page 2 (offset 8) under v1 — this is the "currently shown" far page.
        paging[8]
        testScheduler.advanceUntilIdle()
        assertEquals("v1-8", paging[8])

        version = "v2"
        paging.refresh(12)
        testScheduler.advanceUntilIdle()

        // The refresh landed (fresh page 0, new generation) but page 2's reload is
        // still parked on the gate: index 8 must keep the OLD item, not fall back
        // to page 0 content.
        assertEquals("v2-0", paging[0])
        assertEquals("v1-8", paging[8])

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals("v2-8", paging[8])
    }

    @Test
    fun peekLoaded_returnsOnlyGenuinelyLoadedItemsWithoutSideEffects() = runTest(UnconfinedTestDispatcher()) {
        val loadOffsets = mutableListOf<Int>()
        val paging = pagingOf(
            total = 12,
            initial = listOf(item(0), item(1), item(2), item(3)),
            pageSize = 4,
            scope = backgroundScope,
            onLoad = { offset, _ -> loadOffsets += offset },
        )
        assertEquals(item(1), paging.peekLoaded(1))
        // Page 2 not loaded: peek is null (get() would substitute a fallback) and
        // does NOT trigger a load — pin/anchor capture must be side-effect free.
        assertEquals(null, paging.peekLoaded(8))
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList(), loadOffsets.filter { it == 8 })
    }

    @Test
    fun olderRefreshCannotOverwriteNewerOne() = runTest(UnconfinedTestDispatcher()) {
        // Latest-wins: both page-0 loads park. After B has been CALLED, A finishes
        // first while B is still pending. A must not make any count/page/generation
        // state observable; only B may eventually commit.
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        var phase = "v1"
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("v1-0", "v1-1", "v1-2", "v1-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, limit ->
                val p = phase
                if (p == "vA") gateA.await()
                if (p == "vB") gateB.await()
                (offset until (offset + limit).coerceAtMost(offset + 4)).map { "$p-$it" }
            },
        )
        val generationBeforeRefresh = paging.generation
        phase = "vA"
        paging.refresh(8)   // A: claims the earlier ticket, parks on the gate.
        phase = "vB"
        paging.refresh(12)  // B: newer ticket, also parks.
        testScheduler.runCurrent()

        gateA.complete(Unit)
        testScheduler.runCurrent()
        // A applies after the newer call but before B finishes: nothing is allowed
        // to become observable from A's obsolete count/content/generation.
        assertEquals(4, paging.totalCount)
        assertEquals("v1-0", paging.peekLoaded(0))
        assertEquals(generationBeforeRefresh, paging.generation)

        gateB.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(12, paging.totalCount)
        assertEquals("vB-0", paging.peekLoaded(0))
        assertEquals(generationBeforeRefresh + 1, paging.generation)
    }

    @Test
    fun newerRefreshClaimedAfterOldCompletionQueuedPreventsOldCommit() {
        val dispatcher = StepDispatcher()
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + dispatcher)
        var loadVersion = "R1"
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("initial-0", "initial-1", "initial-2", "initial-3"),
            pageSize = 4,
            coroutineScope = scope,
            loadPage = { _, _ -> List(4) { "$loadVersion-$it" } },
        )
        val generationBeforeRefresh = paging.generation

        paging.refresh(8)
        dispatcher.runNext() // Reduce R1 request and dispatch its page-0 load.
        dispatcher.runNext() // Finish R1 load; R1 completion is now queued.

        // R2 is called before the reducer is allowed to process R1 completion.
        loadVersion = "R2"
        paging.refresh(12)
        dispatcher.runNext() // Reduce the queued R1 completion followed by R2 request.

        assertEquals(
            4,
            paging.totalCount,
            "R1 must lose ownership as soon as the newer refresh API call is made",
        )
        assertEquals("initial-0", paging.peekLoaded(0))
        assertEquals(generationBeforeRefresh, paging.generation)

        scope.cancel()
    }

    @Test
    fun staleRefreshCannotResurrectContentAfterMarkEmpty() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        var hold = false
        val paging = PagingPlayItems(
            totalCount = 4,
            initialPage = listOf("v1-0", "v1-1", "v1-2", "v1-3"),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, _ ->
                if (hold) gate.await()
                List(4) { "r-${offset + it}" }
            },
        )
        hold = true
        paging.refresh(8)   // parks
        paging.markEmpty()  // newer: applies immediately
        testScheduler.advanceUntilIdle()
        assertTrue(paging.isEmpty)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        // The stale refresh must not bring the dataset back to life.
        assertTrue(paging.isEmpty)
        assertEquals(0, paging.totalCount)
    }

    @Test
    fun pageAttemptRevisionAdvancesOnlyForTheRequestedPage() =
        runTest(UnconfinedTestDispatcher()) {
            val paging = pagingOf(
                total = 16,
                initial = listOf(item(0), item(1), item(2), item(3)),
                pageSize = 4,
                scope = backgroundScope,
            )
            val targetBefore = paging.pageAttemptRevision(8)
            assertEquals(2, targetBefore.pageNumber)
            assertEquals(0, targetBefore.revision)

            val globalBefore = paging.pagesRevision
            paging[4]
            testScheduler.advanceUntilIdle()
            paging[12]
            testScheduler.advanceUntilIdle()

            assertTrue(paging.pagesRevision >= globalBefore + 2)
            assertEquals(
                targetBefore,
                paging.pageAttemptRevision(8),
                "completions for pages 1 and 3 must not change page 2's attempt revision",
            )

            paging[8]
            testScheduler.advanceUntilIdle()
            assertEquals(
                targetBefore.copy(revision = 1),
                paging.pageAttemptRevision(8),
                "success/empty/failure completion for the target page advances its own revision",
            )
        }

    @Test
    fun pagesRevision_advancesOnFailedAndEmptyLoadAttempts() = runTest(UnconfinedTestDispatcher()) {
        // Anchor-wait reconciliation is driven by pagesRevision: EVERY finished
        // load attempt must advance it — a failing or empty target page would
        // otherwise never produce the retry event and the pending refresh would
        // stay frozen forever.
        var mode = "ok"
        val paging = PagingPlayItems(
            totalCount = 12,
            initialPage = listOf(item(0), item(1), item(2), item(3)),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { offset, limit ->
                when (mode) {
                    "fail" -> throw RuntimeException("transient load failure")
                    "empty" -> emptyList()
                    else -> (offset until (offset + limit).coerceAtMost(12)).map { item(it) }
                }
            },
        )
        val beforeFail = paging.pagesRevision
        mode = "fail"
        paging[4]
        testScheduler.advanceUntilIdle()
        assertTrue(paging.pagesRevision > beforeFail, "failed attempt must advance the revision")

        val beforeEmpty = paging.pagesRevision
        mode = "empty"
        paging[8]
        testScheduler.advanceUntilIdle()
        assertTrue(paging.pagesRevision > beforeEmpty, "empty attempt must advance the revision")
    }

    @Test
    fun oldGenerationFinallyCannotRemoveNewGenerationLoadingMarker() =
        runTest(UnconfinedTestDispatcher()) {
            val generation0Gate = CompletableDeferred<Unit>()
            val generation1Gate = CompletableDeferred<Unit>()
            var page1Requests = 0
            var dataVersion = 0
            val paging = PagingPlayItems(
                totalCount = 12,
                initialPage = listOf("g0-0", "g0-1", "g0-2", "g0-3"),
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, limit ->
                    if (offset == 0) {
                        List(limit) { "g1-$it" }
                    } else {
                        check(offset == 4)
                        page1Requests += 1
                        val requestVersion = dataVersion
                        if (requestVersion == 0) generation0Gate.await() else generation1Gate.await()
                        List(limit) { "g$requestVersion-${offset + it}" }
                    }
                },
            )

            paging[4] // G0 page 1 load is now pending.
            assertEquals(1, page1Requests)

            dataVersion = 1
            paging.refresh(12)
            testScheduler.runCurrent()
            paging[4] // G1 page 1 load is now pending under a new marker.
            assertEquals(2, page1Requests)

            generation0Gate.complete(Unit)
            testScheduler.runCurrent() // G0 finally executes before G1 completes.
            val revisionBeforeRepeatedAccess = paging.pagesRevision

            paging[4]
            testScheduler.runCurrent()
            assertEquals(2, page1Requests, "pending G1 marker must suppress a duplicate G1 request")
            assertEquals(
                revisionBeforeRepeatedAccess,
                paging.pagesRevision,
                "repeated access while G1 is pending must not add a load completion revision",
            )

            generation1Gate.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals("g1-4", paging.peekLoaded(4))
        }

    @Test
    fun indexOfLoaded_matchesByStableMediaKeyNotFullEquality() = runTest(UnconfinedTestDispatcher()) {
        // A refreshed row whose metadata drifted (same url, different auth value)
        // is still the same picture: identity must be keyed on the media url/path.
        val paging = PagingPlayItems(
            totalCount = 2,
            initialPage = listOf(
                UrlWithAuth("https://img/a.jpg", "Authorization", "token-1"),
                UrlWithAuth("https://img/b.jpg", "Authorization", "token-1"),
            ),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { _, _ -> emptyList() },
        )
        val drifted = UrlWithAuth("https://img/b.jpg", "Authorization", "token-2")
        assertEquals(1, paging.indexOfLoaded(drifted))
    }

    @Test
    fun locate_usesBackingStoreLookupAndValidatesRange() = runTest(UnconfinedTestDispatcher()) {
        val paging = PagingPlayItems(
            totalCount = 10,
            initialPage = listOf(item(0), item(1), item(2), item(3)),
            pageSize = 4,
            coroutineScope = backgroundScope,
            loadPage = { _, _ -> emptyList() },
            locateIndex = { item -> if (item == "find-me") 7 else if (item == "gone") null else 99 },
        )
        assertEquals(7, paging.locate("find-me"))
        assertEquals(null, paging.locate("gone"))
        // Out-of-range results from a stale store answer are rejected.
        assertEquals(null, paging.locate("anything-else"))
        // No locator wired -> null.
        val plain = pagingOf(total = 4, initial = listOf(item(0)), scope = backgroundScope)
        assertEquals(null, plain.locate(item(0)))
    }

    @Test
    fun eviction_keepsNearestPagesNotFarthest() = runTest(UnconfinedTestDispatcher()) {
        // Regression: distant-page eviction sorted farthest-first then drop(MAX),
        // which kept the FARTHEST pages and evicted the nearest -> thrashing.
        // pageSize=4, 40 items => 10 pages. Walk forward to load many pages, then
        // assert the page nearest the current position survives in cache.
        val paging = pagingOf(
            total = 40,
            initial = listOf(item(0), item(1), item(2), item(3)),
            pageSize = 4,
            scope = backgroundScope,
        )
        // Touch indices across the first ~9 pages so eviction runs.
        for (p in 0..8) {
            paging[p * 4]
            testScheduler.advanceUntilIdle()
        }
        // Verify cache residency without get(), which would reload an evicted page
        // and make the assertion pass after the very thrash this test guards against.
        assertEquals(item(32), paging.peekLoaded(32))
        // And page 0 (pinned fallback) is still present.
        assertEquals(item(0), paging.peekLoaded(0))
    }

    @Test
    fun indexOfLoaded_findsItemInLoadedPagesOrNull() = runTest(UnconfinedTestDispatcher()) {
        val paging = pagingOf(
            total = 40,
            initial = listOf(item(0), item(1), item(2), item(3)),
            pageSize = 4,
            scope = backgroundScope,
        )
        // Item in the loaded initial page 0.
        assertEquals(2, paging.indexOfLoaded(item(2)))
        // Load page 1 (offset 4) and confirm its items resolve to global indices.
        paging[4]
        testScheduler.advanceUntilIdle()
        assertEquals(5, paging.indexOfLoaded(item(5)))
        // Not in any loaded page -> null (caller must fall back).
        assertEquals(null, paging.indexOfLoaded(item(99)))
        assertEquals(null, paging.indexOfLoaded(null))
    }

    @Test
    fun fromList_wrapsListWithMatchingCount() = runTest(UnconfinedTestDispatcher()) {
        val list = listOf(item(0), item(1), item(2))
        val paging = PagingPlayItems.fromList(list, backgroundScope)
        assertEquals(3, paging.totalCount)
        assertEquals(item(0), paging[0])
        assertEquals(item(2), paging[2])
        // Cycles via modulo.
        assertEquals(item(0), paging[3])
    }

    @Test
    fun throwingPreparedRefreshReportsEveryAttemptAndOneExhaustionWithoutPublishing() =
        runTest(UnconfinedTestDispatcher()) {
            val expected = IllegalStateException("credential=https://secret.example/token")
            val reports = mutableListOf<Triple<PagingFailureStage, Int?, Throwable>>()
            var exhaustedCount = 0
            var attempts = 0
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ -> emptyList() },
            )
            paging.onFailure = { stage, pageNumber, failure ->
                reports += Triple(stage, pageNumber, failure)
            }
            paging.onRefreshExhausted = { exhaustedCount += 1 }
            val generationBefore = paging.generation
            val revisionBefore = paging.pagesRevision

            paging.refreshPrepared {
                attempts += 1
                throw expected
            }
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()

            assertEquals(4, attempts)
            assertEquals(4, reports.size)
            assertTrue(reports.all { it.first == PagingFailureStage.RefreshPrepare })
            assertTrue(reports.all { it.second == null })
            assertTrue(reports.all { it.third === expected })
            assertEquals(1, exhaustedCount)
            assertEquals(4, paging.totalCount)
            assertEquals("v1-0", paging.peekLoaded(0))
            assertEquals(generationBefore, paging.generation)
            assertEquals(revisionBefore, paging.pagesRevision)
        }

    @Test
    fun throwingPageZeroRefreshIsReportedWithoutClearingVisibleData() =
        runTest(UnconfinedTestDispatcher()) {
            val expected = IllegalStateException("Authorization: Bearer private")
            val reports = mutableListOf<Triple<PagingFailureStage, Int?, Throwable>>()
            var exhaustedCount = 0
            var attempts = 0
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ ->
                    attempts += 1
                    throw expected
                },
            )
            paging.onFailure = { stage, pageNumber, failure ->
                reports += Triple(stage, pageNumber, failure)
            }
            paging.onRefreshExhausted = { exhaustedCount += 1 }
            val generationBefore = paging.generation

            paging.refresh(8)
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()

            assertEquals(4, attempts)
            assertEquals(4, reports.size)
            assertTrue(reports.all { it.first == PagingFailureStage.RefreshPageZero })
            assertTrue(reports.all { it.second == 0 })
            assertTrue(reports.all { it.third === expected })
            assertEquals(1, exhaustedCount)
            assertEquals(4, paging.totalCount)
            assertEquals("v1-0", paging.peekLoaded(0))
            assertEquals(generationBefore, paging.generation)
        }

    @Test
    fun explicitNullPreparationExhaustsWithoutFalseFailureReport() =
        runTest(UnconfinedTestDispatcher()) {
            var attempts = 0
            var failureReports = 0
            var exhaustedCount = 0
            val paging = PagingPlayItems(
                totalCount = 4,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { _, _ -> emptyList() },
            )
            paging.onFailure = { _, _, _ -> failureReports += 1 }
            paging.onRefreshExhausted = { exhaustedCount += 1 }

            paging.refreshPrepared {
                attempts += 1
                null
            }
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()

            assertEquals(4, attempts)
            assertEquals(0, failureReports)
            assertEquals(1, exhaustedCount)
            assertEquals(4, paging.totalCount)
            assertEquals("v1-0", paging.peekLoaded(0))
        }

    @Test
    fun failedLazyPageLoadReportsOnceKeepsStalePageAndAdvancesRevision() =
        runTest(UnconfinedTestDispatcher()) {
            val expected = IllegalStateException("signedUrl=https://secret.example/object")
            var phase = "v1"
            var failPageOne = false
            val reports = mutableListOf<Triple<PagingFailureStage, Int?, Throwable>>()
            val paging = PagingPlayItems(
                totalCount = 8,
                initialPage = List(4) { "v1-$it" },
                pageSize = 4,
                coroutineScope = backgroundScope,
                loadPage = { offset, limit ->
                    if (offset == 4 && failPageOne) throw expected
                    (offset until (offset + limit).coerceAtMost(8)).map { "$phase-$it" }
                },
            )
            paging.onFailure = { stage, pageNumber, failure ->
                reports += Triple(stage, pageNumber, failure)
            }

            paging[4]
            testScheduler.runCurrent()
            assertEquals("v1-4", paging.peekLoaded(4))

            phase = "v2"
            paging.refreshPrepared {
                PreparedPagingRefresh(
                    totalCount = 8,
                    firstPage = List(4) { "v2-$it" },
                    commitCandidatePin = { true },
                )
            }
            testScheduler.runCurrent()
            val revisionBeforeFailure = paging.pagesRevision
            failPageOne = true

            val visibleDuringFailure = paging[4]
            testScheduler.runCurrent()

            assertEquals("v1-4", visibleDuringFailure)
            assertEquals(null, paging.peekLoaded(4))
            assertEquals(revisionBeforeFailure + 1, paging.pagesRevision)
            assertEquals(1, reports.size)
            assertEquals(PagingFailureStage.PageLoad, reports.single().first)
            assertEquals(1, reports.single().second)
            assertSame(expected, reports.single().third)
        }

    @Test
    fun failureLogSummaryContainsOnlyFixedContextAndExceptionType() {
        val secret = "Authorization: Bearer top-secret https://signed.example/object"
        val failure = IllegalStateException(secret)

        val summary = pagingFailureLogMessage(
            stage = PagingFailureStage.PageLoad,
            pageNumber = 7,
            failure = failure,
        )

        assertEquals("stage=page_load page=7 exception=IllegalStateException", summary)
        assertFalse(summary.contains(secret))
        assertFalse(summary.contains("top-secret"))
        assertFalse(summary.contains("signed.example"))
    }

    @Test
    fun pagingCallbackWrapperPropagatesTheOriginalCancellationInstance() {
        val expected = CancellationException("session cancelled")

        val actual = assertFailsWith<CancellationException> {
            invokePagingCallback(
                callback = { throw expected },
                onFailure = { error("cancellation must not be isolated") },
            )
        }

        assertSame(expected, actual)
    }
}
