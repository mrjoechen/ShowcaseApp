package com.alpha.showcase.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SupabaseConfigCacheTest {
    private class AccessDenied : Exception()

    @Test
    fun expiresAfterFiveMinutesAndSupportsForcedRefresh() = runTest {
        var now = 0L
        var reads = 0
        val cache = SupabaseConfigCache(nowMillis = { now })
        suspend fun read(force: Boolean = false) = cache.getValue("u1", "key", force) { "v${++reads}" }
        assertEquals("v1", read())
        now = 299_999
        assertEquals("v1", read())
        now = 300_001
        assertEquals("v2", read())
        assertEquals("v3", read(force = true))
        assertEquals(3, reads)
    }

    @Test
    fun networkFailureKeepsLastValueAndRetriesAfterThirtySeconds() = runTest {
        var now = 0L
        val cache = SupabaseConfigCache(nowMillis = { now })
        assertEquals("old", cache.getValue("u1", "key") { "old" })
        now = 300_001
        assertEquals("old", cache.getValue("u1", "key") { error("offline") })
        now += 29_999
        assertEquals("old", cache.getValue("u1", "key") { error("Must be cached") })
        now += 2
        assertEquals("new", cache.getValue("u1", "key") { "new" })
    }

    @Test
    fun successfulMissingValueReplacesOldValueAndIsCached() = runTest {
        val cache = SupabaseConfigCache()
        cache.getValue("u1", "key") { "old" }
        assertNull(cache.getValue("u1", "key", forceRefresh = true) { null })
        assertNull(cache.getValue("u1", "key") { error("Must cache missing value") })
    }

    @Test
    fun rejectedAccessDoesNotReturnAnOldCredential() = runTest {
        val cache = SupabaseConfigCache(allowStaleOnFailure = { it !is AccessDenied })
        cache.getValue("u1", "key") { "credential" }
        assertNull(cache.getValue("u1", "key", forceRefresh = true) { throw AccessDenied() })
        assertNull(cache.getValue("u1", "key") { error("Still in failure TTL") })
    }

    @Test
    fun anotherUserCannotGetThePreviousUsersCacheOnFailure() = runTest {
        val cache = SupabaseConfigCache()
        cache.getValue("u1", "key") { "u1-value" }
        assertNull(cache.getValue("u2", "key") { error("offline") })
    }

    @Test
    fun clearingAClientDropsCachedValues() = runTest {
        val cache = SupabaseConfigCache()
        cache.getValue("u1", "key") { "old" }
        cache.clear()
        assertEquals("new", cache.getValue("u1", "key") { "new" })
    }

    @Test
    fun cancellationIsNotConvertedToNullOrCachedAsFailure() = runTest {
        val cache = SupabaseConfigCache()
        assertFailsWith<CancellationException> {
            cache.getValue("u1", "key") { throw CancellationException() }
        }
        assertEquals("recovered", cache.getValue("u1", "key") { "recovered" })
    }

    @Test
    fun concurrentReadersShareOneFetch() = runTest {
        var reads = 0
        val cache = SupabaseConfigCache()
        val values = List(8) {
            async { cache.getValue("u1", "key") { reads++; delay(10); "value" } }
        }.awaitAll()
        assertEquals(List(8) { "value" }, values)
        assertEquals(1, reads)
    }

    @Test
    fun failuresAreLoggedAtMostOncePerMinute() = runTest {
        var now = 0L
        var logs = 0
        val cache = SupabaseConfigCache(nowMillis = { now }, onFailure = { logs++ })
        repeat(2) { cache.getValue("u1", "key", true) { error("offline") } }
        assertEquals(1, logs)
        now = 60_000
        cache.getValue("u1", "key", true) { error("offline") }
        assertEquals(2, logs)
    }
}
