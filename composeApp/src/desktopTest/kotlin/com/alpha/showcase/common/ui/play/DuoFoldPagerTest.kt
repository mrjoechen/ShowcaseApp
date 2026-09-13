package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.network.httpHeaders
import coil3.request.crossfade
import com.alpha.showcase.common.ui.settings.*
import com.alpha.showcase.common.ui.play.fold.LocalDuoFoldEnabled
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.slide_effect
import showcaseapp.composeapp.generated.resources.duo_fold_retreat
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class DuoFoldPagerTest {
    @Test fun disabledFeatureHidesSavedSelectionWithoutRewritingSettings() = runDesktopComposeUiTest(width = 640, height = 760) {
        val mode = Settings.SlideMode(effect = SlideEffect.DuoFold.value)
        var changes = 0
        var effectLabel = ""
        var retreatLabel = ""
        setContent {
            effectLabel = stringResource(Res.string.slide_effect)
            retreatLabel = stringResource(Res.string.duo_fold_retreat)
            CompositionLocalProvider(LocalDuoFoldEnabled provides false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SlideModeView(mode) { _, _ -> changes++ }
                }
            }
        }
        onNodeWithText("Duo Fold").assertDoesNotExist()
        onNodeWithContentDescription(retreatLabel).assertDoesNotExist()
        onNodeWithText(effectLabel).performClick()
        onNodeWithText("Duo Fold").assertDoesNotExist()
        runOnIdle { assertEquals(0, changes); assertEquals(5, mode.effect) }
    }

    @Test fun directPagerEntryFallsBackWithoutCreatingFoldLayers() = runDesktopComposeUiTest(width = 320, height = 240) {
        val loader = loader { _, _ -> }
        mainClock.autoAdvance = false
        try {
            setContent {
                val scope = rememberCoroutineScope()
                val data = remember { PagingPlayItems.fromList(photos, scope) }
                CompositionLocalProvider(LocalImageLoader provides loader,
                    LocalPlaybackActive provides false, LocalDuoFoldEnabled provides false) {
                    DuoFoldPager(data, showProgress = false)
                }
            }
            waitUntil(timeoutMillis = 15_000) {
                mainClock.advanceTimeByFrame()
                onRoot().captureToImage().toPixelMap()[160, 120].red > .8f
            }
            onNodeWithTag("duo-fold-pager").assertDoesNotExist()
            onRoot().performTouchInput { swipeLeft() }
            mainClock.advanceTimeBy(2500)
            waitUntil(timeoutMillis = 15_000) {
                mainClock.advanceTimeByFrame()
                onRoot().captureToImage().toPixelMap()[160, 120].green > .8f
            }
            onNodeWithTag("duo-fold-transition").assertDoesNotExist()
        } finally { loader.shutdown() }
    }

    @Test fun effectSelectionAndRetreatPersistWithoutChangingOldSettings() = runDesktopComposeUiTest(width = 640, height = 760) {
        var mode by mutableStateOf(Settings.SlideMode())
        var effectLabel = ""
        var retreatLabel = ""
        setContent {
            effectLabel = stringResource(Res.string.slide_effect)
            retreatLabel = stringResource(Res.string.duo_fold_retreat)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SlideModeView(mode) { key, value ->
                    mode = when (key) {
                        SlideEffect.key -> mode.copy(effect = value as Int)
                        DUO_FOLD_RETREAT_KEY -> mode.copy(duoFoldRetreat = value as Boolean)
                        else -> mode
                    }
                }
            }
        }
        onNodeWithText(effectLabel).performClick()
        onNodeWithText("Duo Fold").performClick()
        onNode(hasContentDescription(retreatLabel) and isToggleable()).performScrollTo().performClick()
        runOnIdle {
            assertEquals(SlideEffect.DuoFold, SlideEffect.fromValue(mode.effect))
            assertFalse(mode.duoFoldRetreat)
            assertEquals(mode, Json.decodeFromString<Settings.SlideMode>(Json.encodeToString(mode)))
            assertTrue(Json.decodeFromString<Settings.SlideMode>("{\"effect\":4}").duoFoldRetreat)
            assertEquals(SlideEffect.Flip, SlideEffect.fromValue(4))
        }
    }

    @Test fun hugeVirtualPageOffsetsKeepFractionalProgress() {
        val page = 536870911
        assertEquals(page, duoFoldLowerPage(page, .28f))
        assertEquals(page - 1, duoFoldLowerPage(page, -.28f))
        assertEquals(.28f, duoFoldProgress(.28f))
        assertEquals(.72f, duoFoldProgress(-.28f))
    }

    @Test fun authenticatedPagerItemsFoldBothWaysAndKeepFitBackgrounds() = runDesktopComposeUiTest(width = 400, height = 300) {
        val requested = ConcurrentHashMap<String, Boolean>()
        val loader = loader { url, headers ->
            assertEquals(listOf("demo-token"), headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value)
            requested[url] = true
        }
        lateinit var pager: PagerState
        var retreat by mutableStateOf(true)
        val entries = photos.map { MediaItemState(it, fitSize = true) }
        mainClock.autoAdvance = false
        try {
            setContent {
                pager = rememberPagerState(initialPage = 1) { entries.size }
                CompositionLocalProvider(LocalImageLoader provides loader, LocalPlaybackActive provides false) {
                    PagerMediaViewport(pager, Modifier.fillMaxSize().background(Color(0xFF141716)).testTag("viewport")) {
                        DuoFoldPages(pager, retreatEnabled = retreat) { entries[it] }
                    }
                }
            }
            waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); entries.all { it.ready } }
            assertEquals(3, requested.size)
            assertDominant(Color.Green, onRoot().captureToImage().toPixelMap()[1, 1])
            runOnIdle { retreat = false }
            mainClock.advanceTimeByFrame()
            assertDominant(Color.Green, onRoot().captureToImage().toPixelMap()[1, 1])
            runOnIdle { retreat = true }
            mainClock.advanceTimeByFrame()
            assertDominant(Color.Green, onRoot().captureToImage().toPixelMap()[200, 150])
            onNodeWithTag("viewport").performTouchInput {
                down(Offset(320f, 150f)); moveBy(Offset(-120f, 0f), delayMillis = 150)
            }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("duo-fold-transition").assertExists()
            val pixels = onRoot().captureToImage().toPixelMap()
            // ContentScale.Fit has a blurred image background, which must stay in the
            // captured image rather than suddenly turning into black letterboxing.
            assertTrue(pixels[60, 55].green > .2f)
            assertTrue(pixels[200, 2].green < .2f, "Retreat must expose viewport background")
            onNodeWithTag("viewport").performTouchInput { moveBy(Offset(-140f, 0f), delayMillis = 150); up() }
            mainClock.advanceTimeBy(2500)
            runOnIdle { assertEquals(2, pager.currentPage) }
            onNodeWithTag("viewport").performTouchInput { swipeRight() }
            mainClock.advanceTimeBy(2500)
            runOnIdle { assertEquals(1, pager.currentPage) }
            assertDominant(Color.Green, onRoot().captureToImage().toPixelMap()[200, 150])
            assertDominant(Color.Green, onRoot().captureToImage().toPixelMap()[1, 1])
        } finally { loader.shutdown() }
    }

    @Test fun autoplayWaitsForAdjacentImageAndPausesWithShowcase() = runDesktopComposeUiTest(width = 320, height = 240) {
        val releaseNext = CompletableDeferred<Unit>()
        val requests = ConcurrentHashMap<String, Boolean>()
        val loader = loader { url, _ ->
            requests[url] = true
            if (url.endsWith("green.jpg")) releaseNext.await()
        }
        var active by mutableStateOf(false)
        mainClock.autoAdvance = false
        try {
            setContent {
                val scope = rememberCoroutineScope()
                val data = remember { PagingPlayItems.fromList(photos, scope) }
                CompositionLocalProvider(LocalImageLoader provides loader, LocalPlaybackActive provides active) {
                    DuoFoldPager(data, interval = 500, showProgress = false)
                }
            }
            waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); requests.size == 3 }
            // Let the initial image finish its async decode without depending on a network.
            waitUntil(timeoutMillis = 15_000) {
                mainClock.advanceTimeByFrame()
                onRoot().captureToImage().toPixelMap()[160, 120].red > .8f
            }
            mainClock.advanceTimeBy(3000)
            assertDominant(Color.Red, onRoot().captureToImage().toPixelMap()[160, 120])
            runOnIdle { active = true }
            mainClock.advanceTimeBy(3000)
            assertDominant(Color.Red, onRoot().captureToImage().toPixelMap()[160, 120])
            releaseNext.complete(Unit)
            waitUntil(timeoutMillis = 15_000) {
                mainClock.advanceTimeBy(100)
                onRoot().captureToImage().toPixelMap()[160, 120].green > .8f
            }
            runOnIdle { active = false }
            mainClock.advanceTimeBy(100)
            val paused = onRoot().captureToImage().toPixelMap()[160, 120]
            mainClock.advanceTimeBy(3000)
            assertEquals(paused, onRoot().captureToImage().toPixelMap()[160, 120])
        } finally { releaseNext.complete(Unit); loader.shutdown() }
    }

    private val photos = listOf("red", "green", "blue").map {
        UrlWithAuth("https://showcase.example/$it.jpg", "Authorization", "demo-token")
    }

    private fun loader(onRequest: suspend (String, Map<String, List<String>>) -> Unit): ImageLoader {
        val images = listOf("red" to 0xffff0000.toInt(), "green" to 0xff00ff00.toInt(), "blue" to 0xff0000ff.toInt())
            .associate { (name, color) ->
                name to Bitmap().use { bitmap ->
                    bitmap.allocN32Pixels(160, 60); bitmap.erase(color)
                    org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
                    }
                }
            }
        return ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Interceptor { chain ->
                val url = chain.request.data as String
                onRequest(url, chain.request.httpHeaders.asMap())
                val bytes = images.getValue(url.substringAfterLast('/').substringBefore('.'))
                chain.withRequest(chain.request.newBuilder().data(bytes).crossfade(false).build()).proceed()
            })
        }.build()
    }

    private fun assertDominant(expected: Color, actual: Color) {
        assertTrue(kotlin.math.abs(expected.red - actual.red) < .15f &&
            kotlin.math.abs(expected.green - actual.green) < .15f &&
            kotlin.math.abs(expected.blue - actual.blue) < .15f, "Expected $expected, got $actual")
    }
}
