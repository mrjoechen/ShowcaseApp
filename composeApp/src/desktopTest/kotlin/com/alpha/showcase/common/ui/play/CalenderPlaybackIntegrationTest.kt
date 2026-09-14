package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.runtime.*
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the calendar renderer and its shared loading/display timer through Coil. */
@OptIn(ExperimentalTestApi::class)
class CalenderPlaybackIntegrationTest {
    @Test fun loadedCalendarImageUsesConfiguredIntervalInCropMode() = assertSuccessfulCalendarAdvances(false)
    @Test fun loadedCalendarImageUsesConfiguredIntervalInFitMode() = assertSuccessfulCalendarAdvances(true)

    @Test fun unsupportedVideoMustTimeoutInsteadOfStoppingCalendarPlayback() = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val next = "https://calendar.test/after-video.jpg"
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Fetcher.Factory<Uri> { data, _, _ ->
                object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        requests += data.toString()
                        awaitCancellation()
                    }
                }
            })
        }.build()
        try {
            mainClock.autoAdvance = false
            setContent {
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf("https://calendar.test/clip.mp4", next), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader,
                    LocalImagePlaybackConfig provides ImagePlaybackConfig(1200)) {
                    CalenderPlay(duration = 100, sortRule = 0, pagingItems = items)
                }
            }
            mainClock.advanceTimeBy(3000)
            waitForIdle()
            assertTrue(next in requests, "An unsupported video must not permanently stop automatic playback")
        } finally { loader.shutdown() }
    }

    private fun assertSuccessfulCalendarAdvances(fitSize: Boolean) = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val bitmap = Bitmap().apply { allocN32Pixels(16, 16) }
        val first = "https://calendar.test/first.jpg"
        val second = "https://calendar.test/second.jpg"
        // Synchronous memory-cache hits remove network/decoder scheduling from the timer check.
        val cache = coil3.memory.MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()
        cache[coil3.memory.MemoryCache.Key(first)] = coil3.memory.MemoryCache.Value(bitmap.asImage())
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).memoryCache(cache).components {
            add(Fetcher.Factory<Uri> { data, _, _ ->
                object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        requests += data.toString()
                        return ImageFetchResult(bitmap.asImage(), false, DataSource.NETWORK)
                    }
                }
            })
        }.build()
        try {
            mainClock.autoAdvance = false
            setContent {
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf(first, second), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader) {
                    CalenderPlay(duration = 1000, sortRule = 0, pagingItems = items, fitSize = fitSize)
                }
            }
            mainClock.advanceTimeBy(500)
            waitForIdle()
            mainClock.advanceTimeBy(5000)
            waitForIdle()
            assertTrue(second in requests,
                "A successful calendar image must advance after its 1s interval, before the 30s load timeout")
        } finally { loader.shutdown(); bitmap.close() }
    }

    @Test fun loadingCalendarImageStillWaitsForConfiguredTimeout() = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Fetcher.Factory<Uri> { data, _, _ ->
                object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        requests += data.toString()
                        awaitCancellation()
                    }
                }
            })
        }.build()
        try {
            mainClock.autoAdvance = false
            setContent {
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf("https://calendar.test/a.jpg", "https://calendar.test/b.jpg"), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader,
                    LocalImagePlaybackConfig provides ImagePlaybackConfig(1200)) {
                    CalenderPlay(duration = 100, sortRule = 0, pagingItems = items)
                }
            }
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.isNotEmpty() }
            mainClock.advanceTimeBy(500)
            assertEquals(1, requests.distinct().size)
            mainClock.advanceTimeBy(1500)
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.distinct().size == 2 }
        } finally { loader.shutdown() }
    }
}
