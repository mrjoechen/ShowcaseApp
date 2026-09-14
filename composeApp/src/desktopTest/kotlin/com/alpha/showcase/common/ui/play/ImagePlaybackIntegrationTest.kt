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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.skia.Bitmap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ImagePlaybackIntegrationTest {
    @Test fun successfulSlowLoadGetsItsOwnDisplayIntervalBeforeNextRequest() = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val completed = AtomicInteger()
        val finishDownload = CompletableDeferred<Unit>()
        val bitmap = Bitmap().apply { allocN32Pixels(16, 16) }
        val first = "https://slow.test/first.jpg"
        val second = "https://slow.test/second.jpg"
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Fetcher.Factory<Uri> { data, _, _ ->
                object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        requests += data.toString()
                        if (data.toString() != first) awaitCancellation()
                        finishDownload.await()
                        completed.incrementAndGet()
                        return ImageFetchResult(bitmap.asImage(), isSampled = false, dataSource = DataSource.NETWORK)
                    }
                }
            })
        }.build()
        try {
            mainClock.autoAdvance = false
            setContent {
                DisposableEffect(Unit) { onDispose { bitmap.close() } }
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf(first, second), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader) {
                    FadeLayout(items, switchDuration = 3000, showProgress = false)
                }
            }
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.isNotEmpty() }
            mainClock.advanceTimeBy(5000)
            assertEquals(listOf(first), requests.distinct())
            finishDownload.complete(Unit)
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); completed.get() > 0 }
            mainClock.advanceTimeBy(2000)
            assertEquals(listOf(first), requests.distinct(), "Download time must not carry over into display time")
            mainClock.advanceTimeBy(2000)
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); second in requests }
        } finally { loader.shutdown() }
    }

    @Test fun configuredTimeoutSkipsFailedImageWithProgressVisible() = assertFadeTimeout(showProgress = true)
    @Test fun configuredTimeoutSkipsFailedImageWithProgressHidden() = assertFadeTimeout(showProgress = false)

    private fun assertFadeTimeout(showProgress: Boolean) = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val loader = stallingLoader(requests)
        try {
            mainClock.autoAdvance = false
            setContent {
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf("https://slow.test/a.jpg", "https://slow.test/b.jpg"), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader,
                    LocalImagePlaybackConfig provides ImagePlaybackConfig(loadTimeoutMillis = 1200)) {
                    FadeLayout(items, switchDuration = 10_000, showProgress = showProgress)
                }
            }
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.isNotEmpty() }
            mainClock.advanceTimeBy(500)
            assertEquals(1, requests.distinct().size)
            mainClock.advanceTimeBy(1000)
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.distinct().size == 2 }
        } finally { loader.shutdown() }
    }

    @Test fun frameWallWaitsForLoadAndReplacesTimedOutSlot() = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val loader = stallingLoader(requests)
        try {
            mainClock.autoAdvance = false
            setContent {
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf("https://slow.test/a.jpg", "https://slow.test/b.jpg"), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader,
                    LocalImagePlaybackConfig provides ImagePlaybackConfig(loadTimeoutMillis = 1200)) {
                    FrameWallLayout(row = 1, column = 1, pagingItems = items, duration = 10_000)
                }
            }
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.isNotEmpty() }
            mainClock.advanceTimeBy(500)
            assertEquals(1, requests.distinct().size)
            mainClock.advanceTimeBy(2500)
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.distinct().size == 2 }
        } finally { loader.shutdown() }
    }

    @Test fun interruptedTransitionDoesNotPermanentlyStopAutoPlay() = runDesktopComposeUiTest {
        var attempts = 0
        mainClock.autoAdvance = false
        setContent {
            rememberImagePlaybackProgress(500, current = { ImagePlaybackFrame("A", true) }) {
                attempts++
                if (attempts == 1) throw CancellationException("User interrupted animation")
            }
        }
        mainClock.advanceTimeBy(1500)
        runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun inactivePlaybackDoesNotTimeoutUntilItBecomesActiveAgain() = runDesktopComposeUiTest {
        var active by mutableStateOf(false)
        var attempts = 0
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalPlaybackActive provides active,
                LocalImagePlaybackConfig provides ImagePlaybackConfig(loadTimeoutMillis = 1000)) {
                rememberImagePlaybackProgress(500, current = { ImagePlaybackFrame("A", false) }) { attempts++ }
            }
        }
        mainClock.advanceTimeBy(5000)
        runOnIdle { assertEquals(0, attempts); active = true }
        mainClock.advanceTimeBy(500)
        runOnIdle { assertEquals(0, attempts) }
        mainClock.advanceTimeBy(1000)
        runOnIdle { assertEquals(1, attempts) }
    }

    @Test fun fadeWithoutProgressMustNotSwitchWhileCurrentImageIsStillLoading() = runDesktopComposeUiTest {
        val requests = CopyOnWriteArrayList<String>()
        val loader = stallingLoader(requests)
        val first = "https://slow.test/first.jpg"
        val second = "https://slow.test/second.jpg"
        try {
            mainClock.autoAdvance = false
            setContent {
                val scope = rememberCoroutineScope()
                val items = remember { PagingPlayItems.fromList(listOf(first, second), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader) {
                    FadeLayout(items, switchDuration = 500, showProgress = false)
                }
            }
            waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); requests.isNotEmpty() }
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            assertEquals(listOf(first), requests.distinct(), "Loading must not consume the display interval")
        } finally { loader.shutdown() }
    }

    private fun stallingLoader(requests: MutableList<String>) =
        ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Fetcher.Factory<Uri> { data, _, _ ->
                object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        requests += data.toString()
                        awaitCancellation()
                    }
                }
            })
        }.build()
}
