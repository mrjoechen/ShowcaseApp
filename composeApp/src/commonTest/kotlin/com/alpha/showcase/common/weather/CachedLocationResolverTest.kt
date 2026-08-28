package com.alpha.showcase.common.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CachedLocationResolverTest {

    @Test
    fun successfulNativeLocationIsReturnedAndCachedWithCaptureTime() = runTest {
        val now = 1_000L
        val nativeLocation = location(provider = "native", city = "Shanghai")
        var cachedLocation: LocationCacheEntry? = null
        val resolver = CachedLocationResolver(
            getNativeLocation = { nativeLocation },
            saveToCache = { cachedLocation = it },
            loadFromCache = { error("cache fallback must not be read") },
            nowEpochMillis = { now },
        )

        assertEquals(nativeLocation, resolver.getLocation())
        assertEquals(LocationCacheEntry(nativeLocation, now), cachedLocation)
    }

    @Test
    fun unavailableNativeLocationFallsBackToFreshCache() = runTest {
        val now = 2_000L
        val cachedLocation = location(provider = "cache", city = "Hong Kong")
        val resolver = CachedLocationResolver(
            getNativeLocation = { null },
            saveToCache = { error("current cache must not be rewritten") },
            loadFromCache = { LocationCacheEntry(cachedLocation, now - 1) },
            nowEpochMillis = { now },
        )

        assertEquals(cachedLocation, resolver.getLocation())
    }

    @Test
    fun failedNativeLocationFallsBackToFreshCache() = runTest {
        val now = 3_000L
        val cachedLocation = location(provider = "cache", city = "Tokyo")
        val resolver = CachedLocationResolver(
            getNativeLocation = { error("provider temporarily unavailable") },
            saveToCache = { error("current cache must not be rewritten") },
            loadFromCache = { LocationCacheEntry(cachedLocation, now - 1) },
            nowEpochMillis = { now },
        )

        assertEquals(cachedLocation, resolver.getLocation())
    }

    @Test
    fun failedCacheWriteDoesNotDiscardFreshNativeLocation() = runTest {
        val nativeLocation = location(provider = "native", city = "London")
        val resolver = CachedLocationResolver(
            getNativeLocation = { nativeLocation },
            saveToCache = { error("disk full") },
            loadFromCache = { error("cache fallback must not be read") },
            nowEpochMillis = { 4_000L },
        )

        assertEquals(nativeLocation, resolver.getLocation())
    }

    @Test
    fun cancellationIsNotConvertedIntoCachedLocation() = runTest {
        val cancellation = CancellationException("cancel location request")
        val resolver = CachedLocationResolver(
            getNativeLocation = { throw cancellation },
            saveToCache = { error("cancelled native lookup must not be cached") },
            loadFromCache = { error("cancellation must not trigger cache fallback") },
        )

        val thrown = runCatching { resolver.getLocation() }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun deniedPermissionDoesNotUseNativeOrCachedLocation() = runTest {
        var nativeRead = false
        var cacheRead = false
        val resolver = CachedLocationResolver(
            hasLocationPermission = { false },
            getNativeLocation = {
                nativeRead = true
                error("native location must not be read without permission")
            },
            saveToCache = { error("location must not be cached without permission") },
            loadFromCache = {
                cacheRead = true
                LocationCacheEntry(location(provider = "cache"), 1L)
            },
        )

        assertNull(resolver.getLocation())
        assertFalse(nativeRead)
        assertFalse(cacheRead)
    }

    @Test
    fun permissionRevokedDuringNativeRequestDoesNotCacheOrReturnLocation() = runTest {
        var permissionGranted = true
        var cacheWrites = 0
        val resolver = CachedLocationResolver(
            hasLocationPermission = { permissionGranted },
            getNativeLocation = {
                permissionGranted = false
                location(provider = "native")
            },
            saveToCache = { cacheWrites += 1 },
            loadFromCache = { error("cache must not be read after permission revocation") },
        )

        assertNull(resolver.getLocation())
        assertEquals(0, cacheWrites)
    }

    @Test
    fun permissionRevokedWhileWritingNativeCacheDeletesItAndDoesNotReturnLocation() = runTest {
        var permissionGranted = true
        var cacheDeletes = 0
        val resolver = CachedLocationResolver(
            hasLocationPermission = { permissionGranted },
            getNativeLocation = { location(provider = "native") },
            saveToCache = { permissionGranted = false },
            loadFromCache = { error("cache fallback must not be read") },
            deleteCache = { cacheDeletes += 1 },
        )

        assertNull(resolver.getLocation())
        assertEquals(1, cacheDeletes)
    }

    @Test
    fun permissionRevokedAfterNativeFailureDoesNotReadOldCache() = runTest {
        var permissionGranted = true
        var cacheReads = 0
        val resolver = CachedLocationResolver(
            hasLocationPermission = { permissionGranted },
            getNativeLocation = {
                permissionGranted = false
                error("native provider failed while permission was revoked")
            },
            saveToCache = { error("failed native lookup must not be cached") },
            loadFromCache = {
                cacheReads += 1
                LocationCacheEntry(location(provider = "cache"), 1L)
            },
        )

        assertNull(resolver.getLocation())
        assertEquals(0, cacheReads)
    }

    @Test
    fun permissionRevokedWhileReadingCacheDoesNotReturnLocation() = runTest {
        var permissionGranted = true
        val resolver = CachedLocationResolver(
            hasLocationPermission = { permissionGranted },
            getNativeLocation = { null },
            saveToCache = { error("current cache must not be rewritten") },
            loadFromCache = {
                permissionGranted = false
                LocationCacheEntry(location(provider = "cache"), 1_000L)
            },
            nowEpochMillis = { 1_001L },
        )

        assertNull(resolver.getLocation())
    }

    @Test
    fun cacheFreshnessUsesExclusiveTwentyFourHourBoundary() {
        val now = 200_000_000L

        assertTrue(isLocationCacheFresh(now - LOCATION_CACHE_TTL_MILLIS + 1, now))
        assertFalse(isLocationCacheFresh(now - LOCATION_CACHE_TTL_MILLIS, now))
        assertFalse(isLocationCacheFresh(now + 1, now))
    }

    @Test
    fun expiredCacheIsNotReturned() = runTest {
        val now = 300_000_000L
        val resolver = CachedLocationResolver(
            getNativeLocation = { null },
            saveToCache = { error("expired cache must not be rewritten") },
            loadFromCache = {
                LocationCacheEntry(
                    location = location(provider = "cache"),
                    capturedAtEpochMillis = now - LOCATION_CACHE_TTL_MILLIS,
                )
            },
            nowEpochMillis = { now },
        )

        assertNull(resolver.getLocation())
    }

    @Test
    fun legacyCacheWithoutCaptureTimeIsDeletedAndNotReturned() = runTest {
        val now = 400_000_000L
        val storedValue = """
            {
              "latitude": 31.2304,
              "longitude": 121.4737,
              "city": "Shanghai",
              "country": "China"
            }
        """.trimIndent()
        var cacheDeletes = 0
        val resolver = CachedLocationResolver(
            getNativeLocation = { null },
            saveToCache = { error("legacy cache must not be refreshed") },
            loadFromCache = { decodeLocationCache(storedValue) },
            deleteCache = { cacheDeletes += 1 },
            nowEpochMillis = { now },
        )

        assertNull(resolver.getLocation())
        assertEquals(1, cacheDeletes)
    }

    @Test
    fun staleNativeLocationFallsBackToFreshCacheWithoutRefreshingItsAge() = runTest {
        val now = 500_000_000L
        val freshCache = location(provider = "cache", city = "Current")
        val resolver = CachedLocationResolver(
            getNativeLocation = {
                location(
                    provider = "last_known",
                    city = "Old",
                    capturedAtEpochMillis = now - LOCATION_CACHE_TTL_MILLIS,
                )
            },
            saveToCache = { error("stale native location must not be cached") },
            loadFromCache = { LocationCacheEntry(freshCache, now - 1) },
            nowEpochMillis = { now },
        )

        assertEquals(freshCache, resolver.getLocation())
    }

    private fun location(
        provider: String,
        city: String? = null,
        capturedAtEpochMillis: Long? = null,
    ) = LocationResult(
        latitude = 31.2304,
        longitude = 121.4737,
        provider = provider,
        city = city,
        country = "China",
        capturedAtEpochMillis = capturedAtEpochMillis,
    )
}
