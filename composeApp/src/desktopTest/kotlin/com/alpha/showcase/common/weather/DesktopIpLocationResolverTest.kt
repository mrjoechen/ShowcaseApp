package com.alpha.showcase.common.weather

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopIpLocationResolverTest {

    @Test
    fun successfulIpLookupIsReturnedAndCached() = runTest {
        val ipLocation = LocationResult(
            latitude = 31.2304,
            longitude = 121.4737,
            provider = "ipgeolocation",
            city = "Shanghai",
            country = "China",
        )
        var cachedLocation: LocationResult? = null
        val resolver = DesktopIpLocationResolver(
            fetchFromIp = { ipLocation },
            saveToCache = { cachedLocation = it },
            loadFromCache = { null },
        )

        val result = resolver.getLocation()

        assertEquals(ipLocation, result)
        assertEquals(ipLocation, cachedLocation)
    }

    @Test
    fun failedIpLookupFallsBackToCachedLocation() = runTest {
        val cachedLocation = LocationResult(
            latitude = 22.3193,
            longitude = 114.1694,
            provider = "ip-cache",
            city = "Hong Kong",
            country = "China",
        )
        val resolver = DesktopIpLocationResolver(
            fetchFromIp = { error("network unavailable") },
            saveToCache = { error("failed lookup must not be cached") },
            loadFromCache = { cachedLocation },
        )

        assertEquals(cachedLocation, resolver.getLocation())
    }
}
