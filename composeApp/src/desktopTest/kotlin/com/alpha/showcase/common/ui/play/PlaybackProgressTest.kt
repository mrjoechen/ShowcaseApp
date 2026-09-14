package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import coil3.ImageLoader
import coil3.PlatformContext
import com.alpha.showcase.common.ui.play.flip.FlipPager
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalComposeTracingApi::class)
class PlaybackProgressTest {
    @Test fun slideMovesEveryFrame() = assertSmooth { SlideImagePager(it, switchDuration = 5000) }
    @Test fun verticalSlideMovesEveryFrame() = assertSmooth { SlideImagePager(it, vertical = true, switchDuration = 5000) }
    @Test fun cubeMovesEveryFrame() = assertSmooth { CubePager(data = it, interval = 5000) }
    @Test fun revealMovesEveryFrame() = assertSmooth { CircleRevealPager(data = it, interval = 5000) }
    @Test fun flipMovesEveryFrame() = assertSmooth { FlipPager(data = it, interval = 5000) }
    @Test fun duoFoldMovesEveryFrame() = assertSmooth {
        CompositionLocalProvider(com.alpha.showcase.common.ui.play.fold.LocalDuoFoldEnabled provides true) {
            DuoFoldPager(it, interval = 5000)
        }
    }
    @Test fun fadeMovesEveryFrame() = assertSmooth(photo = false) { ProgressIndicator(Modifier, timeMill = 5000) }
    @Test fun longFadeMovesEveryFrame() = assertSmooth(photo = false) { ProgressIndicator(Modifier, timeMill = 60000) }

    @Test fun fadeStartsNewImageAtZero() = runDesktopComposeUiTest {
        var page by mutableIntStateOf(0)
        mainClock.autoAdvance = false
        setContent { ProgressIndicator(Modifier, key = page, timeMill = 5000) }
        mainClock.advanceTimeBy(3000)
        runOnIdle { page++ }
        mainClock.advanceTimeByFrame()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
        mainClock.advanceTimeBy(150)
        val progress = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(progress < .01f, "New image inherited the old progress: $progress")
    }

    private fun assertSmooth(photo: Boolean = true, content: @Composable (PagingPlayItems) -> Unit) = runDesktopComposeUiTest(width = 320, height = 240) {
        val bytes = Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(16, 16)
            bitmap.erase(0xffff0000.toInt())
            org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
            }
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        var active by mutableStateOf(false)
        mainClock.autoAdvance = false
        try {
            setContent {
                val scope = rememberCoroutineScope()
                val data = remember { PagingPlayItems.fromList(listOf(DataWithType(bytes, "png"), DataWithType(bytes.copyOf(), "png")), scope) }
                CompositionLocalProvider(LocalImageLoader provides loader, LocalPlaybackActive provides active) {
                    MaterialTheme(colorScheme = lightColorScheme(primary = Color.Green)) {
                        Box(Modifier.fillMaxSize()) { content(data) }
                    }
                }
            }
            if (photo) waitUntil(timeoutMillis = 15000) {
                mainClock.advanceTimeByFrame()
                val color = onRoot().captureToImage().toPixelMap()[160, 120]
                color.red > .8f && color.green < .2f
            }
            runOnIdle { active = true }
            mainClock.advanceTimeBy(700)
            fun progress() = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            var previous = progress()
            var advancingFrames = 0
            val traces = mutableMapOf<String, Int>()
            Composer.setTracer(object : CompositionTracer {
                override fun isTraceInProgress() = true
                override fun traceEventEnd() = Unit
                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    if (info.startsWith("com.alpha.showcase.common.ui.play.")) traces[info] = (traces[info] ?: 0) + 1
                }
            })
            repeat(30) {
                mainClock.advanceTimeByFrame()
                val current = progress()
                val pixels = onRoot().captureToImage().toPixelMap()
                val y = if (photo) pixels.height - 1 else 1
                val end = (3 until pixels.width - 4).firstOrNull { x ->
                    val color = pixels[x, y]
                    color.green < .8f || color.red > .2f
                } ?: (pixels.width - 4)
                if (current * pixels.width > 5) {
                    assertTrue(kotlin.math.abs(end - current * pixels.width) < 3,
                        "Drawn progress endpoint $end disagrees with ${current * pixels.width} at y=$y")
                }
                assertTrue(current >= previous, "Progress moved backwards: $previous -> $current")
                if (current > previous) advancingFrames++
                previous = current
            }
            assertTrue(advancingFrames >= 25, "Progress advanced on only $advancingFrames/30 frames")
            val mediaRecompositions = traces.filterKeys {
                // Pager page slots are nested lambdas; Duo Fold's outer overlay
                // slot may run while its media pages correctly remain skipped.
                it.contains("Pager.<anonymous>.<anonymous>") || it.contains(".PagerItem (")
            }
            assertTrue(mediaRecompositions.isEmpty(),
                "Timer ticks recomposed stationary media: $mediaRecompositions")
        } finally { Composer.setTracer(null); loader.shutdown() }
    }
}
