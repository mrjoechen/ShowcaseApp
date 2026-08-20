package com.alpha.showcase.common.ui.config

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteOptionsLoaderTest {

    @Test
    fun pagedRemoteOptionsAreCombinedUntilTheLastPage() = runTest {
        val requestedPages = mutableListOf<Int>()

        val options = loadAllRemoteOptions { page ->
            requestedPages += page
            RemoteOptionsPage(
                items = listOf("item-$page"),
                hasMore = page < 3,
            )
        }

        assertEquals(listOf(1, 2, 3), requestedPages)
        assertEquals(listOf("item-1", "item-2", "item-3"), options)
    }

    @Test
    fun pagedRemoteOptionsRespectTheSafetyPageLimit() = runTest {
        val requestedPages = mutableListOf<Int>()

        val options = loadAllRemoteOptions(maxPages = 2) { page ->
            requestedPages += page
            RemoteOptionsPage(items = listOf(page), hasMore = true)
        }

        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(listOf(1, 2), options)
    }

    @Test
    fun remoteOptionLoadingReturnsFailuresToTheConfigurationPage() = runTest {
        val failure = IllegalStateException("HTTP 403")

        val result = loadRemoteOptions<String> { throw failure }

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun remoteOptionLoadingPreservesCoroutineCancellation() = runTest {
        assertFailsWith<CancellationException> {
            loadRemoteOptions<String> { throw CancellationException("cancelled") }
        }
    }
}
