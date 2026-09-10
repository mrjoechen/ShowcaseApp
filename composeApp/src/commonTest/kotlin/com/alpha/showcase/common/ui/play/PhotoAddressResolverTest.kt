package com.alpha.showcase.common.ui.play

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PhotoAddressResolverTest {
    @Test fun cachesByCoordinatesAndLanguageAndRejectsInvalidCoordinates() = runTest {
        var calls = 0
        val resolver = PhotoAddressResolver { _, language -> calls++; language }
        val point = PhotoCoordinates(31.2, 121.5)
        assertEquals("zh-CN", resolver.address(point, "zh-CN"))
        assertEquals("zh-CN", resolver.address(point, "zh-CN"))
        assertEquals(1, calls)
        assertEquals("en-US", resolver.address(point, "en-US"))
        assertEquals(2, calls)
        assertNull(resolver.address(PhotoCoordinates(Double.NaN, 0.0), "en-US"))
        assertNull(resolver.address(PhotoCoordinates(91.0, 0.0), "en-US"))
        assertEquals(2, calls)
    }
    @Test fun failuresPreserveFallbackAndCanBeRetried() = runTest {
        var calls = 0
        val resolver = PhotoAddressResolver { _, _ -> if (++calls == 1) error("offline") else "Shanghai" }
        val point = PhotoCoordinates(31.2, 121.5)
        assertNull(resolver.address(point, "en"))
        assertEquals("Shanghai", resolver.address(point, "en"))
    }
    @Test fun cancellationPropagates() = runTest {
        val resolver = PhotoAddressResolver { _, _ -> throw CancellationException() }
        assertFailsWith<CancellationException> { resolver.address(PhotoCoordinates(0.0, 0.0), "en") }
    }
}
