package com.alpha.showcase.common

import LocalImageLoader
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.Options
import coil3.request.SuccessResult
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.MediaItemState
import com.alpha.showcase.common.ui.play.PagerItem
import com.alpha.showcase.common.ui.play.mediaMetadataCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.jetbrains.skia.Bitmap
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SkiaGifTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun pagerAnimatesAfterActivationWithoutRepeatingLoadCallbacks() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        val active = mutableStateOf(false)
        val state = MediaItemState(DataWithType(gif(), "gif"), fitSize = true)
        var completed = 0
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .mediaMetadataCache(MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()) {
                addPlatformComponents()
            }.build()
        try {
            setContent {
                CompositionLocalProvider(LocalImageLoader provides loader) {
                    PagerItem(state, Modifier.size(300.dp, 100.dp).testTag("gif"),
                        active = active.value, onComplete = { completed++ })
                }
            }
            waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); state.ready }
            mainClock.advanceTimeBy(1000)
            waitForIdle()
            fun rightPixel(): androidx.compose.ui.graphics.Color {
                val pixels = onNodeWithTag("gif").captureToImage().toPixelMap()
                // Coil crossfade uses wall time; this test advances Compose's animation clock.
                return pixels[pixels.width * 5 / 6, pixels.height / 2].copy(alpha = 1f)
            }
            assertEquals(androidx.compose.ui.graphics.Color.Red, rightPixel())
            runOnIdle { active.value = true }
            mainClock.advanceTimeBy(1600)
            waitForIdle()
            assertEquals(androidx.compose.ui.graphics.Color.Blue, rightPixel())
            assertEquals(1, completed)
            assertEquals(3, state.displayedImage?.width)
        } finally { loader.shutdown() }
    }

    @Test fun headersAreSniffedWithoutConsumingTheSource() {
        val options = Options(PlatformContext.INSTANCE)
        val loader = ImageLoader.Builder(options.context).build()
        try {
            for (header in listOf("GIF87a", "GIF89a", "notgif", "GIF")) {
                val buffer = Buffer().writeUtf8(header)
                val source = ImageSource(buffer, options.fileSystem)
                source.use {
                    val result = SourceFetchResult(source, "image/jpeg", DataSource.NETWORK)
                    val decoder = SkiaGifDecoder.Factory().create(result, options, loader)
                    assertEquals(header.length == 6 && header.startsWith("GIF"), decoder != null)
                    assertEquals(header, buffer.readUtf8())
                }
            }
        } finally { loader.shutdown() }
    }

    @Test fun metadataRequestsCacheImmutableGifDataAndRejectDamagedHeaders() = runTest {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .mediaMetadataCache(MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()) {
                addPlatformComponents()
            }.build()
        try {
            val request = buildMediaImageRequest(PlatformContext.INSTANCE, DataWithType(gif(), "gif"))
                .newBuilder().memoryCacheKey("animated-gif").build()
            val first = assertIs<SuccessResult>(loader.execute(request))
            val image = assertIs<SkiaGifImage>(first.image)
            assertTrue(image.shareable)
            assertEquals(3, image.width)
            assertEquals(1, image.height)
            assertContentEquals(intArrayOf(80, 120, 160), image.durations)
            assertEquals(1, image.repetitions)
            val cached = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
            assertSame(image, cached.image)
            val invalid = request.newBuilder().data("GIF89a".encodeToByteArray())
                .memoryCacheKey("invalid-gif").build()
            assertIs<ErrorResult>(loader.execute(invalid))
        } finally { loader.shutdown() }
    }

    @Test fun playbackHonorsFrameDelaysDisposalLoopsPauseAndIndependentViewports() = runTest {
        val image = decodeGif()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(backgroundScope.coroutineContext + clock)
        val active = mutableStateOf(true)
        val painter = image.painter(scope) { active.value } as SkiaGifPainter
        val inactive = image.painter(scope) { false } as SkiaGifPainter
        var millis = 0L
        fun tick(amount: Long) {
            millis += amount
            clock.sendFrame(millis * 1_000_000)
            runCurrent()
        }
        try {
            painter.onRemembered()
            inactive.onRemembered()
            runCurrent()
            assertTrue(clock.hasAwaiters, "Animation must subscribe to the frame clock")
            tick(1)
            assertContentEquals(intArrayOf(RED, RED, RED), pixels(painter))
            tick(79)
            assertContentEquals(intArrayOf(RED, RED, RED), pixels(painter))
            tick(1)
            assertContentEquals(intArrayOf(RED, GREEN, RED), pixels(painter))
            assertContentEquals(intArrayOf(RED, RED, RED), pixels(inactive))
            active.value = false
            Snapshot.sendApplyNotifications()
            runCurrent()
            tick(1000)
            assertContentEquals(intArrayOf(RED, GREEN, RED), pixels(painter))
            active.value = true
            Snapshot.sendApplyNotifications()
            runCurrent()
            tick(1)
            tick(120)
            // Frame 1 restores the previous canvas, so the green pixel must disappear.
            assertContentEquals(intArrayOf(RED, RED, BLUE), pixels(painter))
            tick(1)
            tick(160)
            assertContentEquals(intArrayOf(RED, RED, RED), pixels(painter))
            tick(1)
            tick(80)
            tick(1)
            tick(120)
            tick(1)
            tick(160)
            tick(1000)
            assertContentEquals(intArrayOf(RED, RED, BLUE), pixels(painter))
            painter.onForgotten()
            runCurrent()
            assertFalse(clock.hasAwaiters)
            assertContentEquals(intArrayOf(RED, RED, RED), pixels(painter))
        } finally {
            painter.onForgotten()
            inactive.onForgotten()
        }
    }

    private suspend fun decodeGif(): SkiaGifImage = ImageSource(Buffer().write(gif()),
        Options(PlatformContext.INSTANCE).fileSystem).use {
        assertIs<SkiaGifImage>(SkiaGifDecoder(it).decode().image)
    }

    private fun pixels(painter: Painter): IntArray = Bitmap().use { bitmap ->
        bitmap.allocN32Pixels(3, 1)
        bitmap.erase(0)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bitmap.asComposeImageBitmap()), Size(3f, 1f)) {
            with(painter) { draw(Size(3f, 1f)) }
        }
        IntArray(3) { bitmap.getColor(it, 0) }
    }

    private fun gif() =
        // Hand-encoded 3x1 GIF: red canvas, green center (restore previous), blue right.
        // Frame delays 80/120/160 ms; Netscape extension requests one additional loop.
        "R0lGODlhAwABAIEAAAAAAP8AAAD/AAAA/yH/C05FVFNDQVBFMi4wAwEBAAAh+QQECAAAACwAAAAAAwABAAACAwzDFAAh+QQMDAAAACwBAAAAAQABAAACAlQBACH5BAQQAAAALAIAAAABAAEAAAICXAEAOw=="
            .decodeBase64()!!.toByteArray()

    private companion object {
        const val RED = 0xffff0000.toInt()
        const val GREEN = 0xff00ff00.toInt()
        const val BLUE = 0xff0000ff.toInt()
    }
}
