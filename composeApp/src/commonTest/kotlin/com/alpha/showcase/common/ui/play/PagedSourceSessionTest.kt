package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.cache.CacheSyncResult
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.repo.CachedSourceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class PagedSourceSessionTest {

    @Test
    fun candidatePinIsPrivateUntilCommit() {
        val initial = cachedInfo(version = 1L, initialSnapshot = true)
        val session = PagedSourceSession(initial)

        val candidate = session.candidateFor(2L)

        assertEquals(initial, session.info)
        assertEquals(2L, candidate.committedSyncVersion)
        assertFalse(candidate.initialSnapshot)

        session.commitCandidate(initial, candidate)

        assertEquals(candidate, session.info)
    }

    @Test
    fun pinLostCancellationAbandonsItsLeaseAndCanBeStagedAgain() = runTest {
        val session = PagedSourceSession(cachedInfo(version = 1L, initialSnapshot = false))
        val expected = CancellationException("owner cancelled")
        var firstRecovery: PagedSourceRecovery? = null
        var callbackCount = 0
        session.onPinLost = { recovery ->
            callbackCount += 1
            if (callbackCount == 1) {
                firstRecovery = recovery
                throw expected
            }
            session.abandonRecovery(recovery)
        }

        val actual = assertFailsWith<CancellationException> {
            loadPinnedPageOrStageRecovery(
                session = session,
                loadPinned = { emptyList<String>() },
                resolveLiveVersion = { 2L },
            )
        }

        assertSame(expected, actual)
        assertFalse(session.isCurrent(checkNotNull(firstRecovery)))
        assertFailsWith<PinnedSourceVersionExpiredException> {
            loadPinnedPageOrStageRecovery(
                session = session,
                loadPinned = { emptyList<String>() },
                resolveLiveVersion = { 2L },
            )
        }
        assertEquals(2, callbackCount)
    }

    @Test
    fun lowerLiveGenerationStillStagesDeadPinRecovery() = runTest {
        val initial = cachedInfo(version = 9L, initialSnapshot = false)
        val session = PagedSourceSession(initial)
        var staged: PagedSourceRecovery? = null
        session.onPinLost = { recovery ->
            staged = recovery
            session.abandonRecovery(recovery)
        }

        assertFailsWith<PinnedSourceVersionExpiredException> {
            loadPinnedPageOrStageRecovery(
                session = session,
                loadPinned = { emptyList<String>() },
                resolveLiveVersion = { 4L },
            )
        }

        val recovery = assertNotNull(staged)
        assertSame(initial, recovery.pinned)
        assertEquals(4L, recovery.liveVersion)
        assertEquals(4L, recovery.candidate.committedSyncVersion)
    }

    private fun cachedInfo(
        version: Long,
        initialSnapshot: Boolean,
    ) = CachedSourceInfo(
        sourceType = "rss",
        sourceKey = "feed",
        remoteApi = RssSource("News", "https://example.com/feed.xml"),
        syncCompletion = CompletableDeferred<CacheSyncResult>(),
        committedSyncVersion = version,
        initialSnapshot = initialSnapshot,
    )
}
