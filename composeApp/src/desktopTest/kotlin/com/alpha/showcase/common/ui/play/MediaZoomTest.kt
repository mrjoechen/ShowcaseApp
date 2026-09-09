package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.asImage
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import org.jetbrains.skia.Bitmap
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class MediaZoomTest {
    @Test fun singleTapOnlyTogglesExifWhileDoubleTapZoomsPixelsAndNewMediaResets() = runDesktopComposeUiTest {
        var state by mutableStateOf(MediaItemState(DataWithType(exifFixture(), "jpg"), fitSize = true))
        mainClock.autoAdvance = false
        setContent {
            val loader = remember { metadataImageLoader() }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            CompositionLocalProvider(LocalImageLoader provides loader) {
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    PagerItem(state, Modifier.fillMaxSize(), onInteraction = { state.interact(MediaOverlayConfig(), it) })
                    MediaOverlays(state, MediaOverlayConfig(), parentType = SHOWCASE_MODE_FADE)
                }
            }
        }
        waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); state.ready }
        mainClock.advanceTimeBy(800)
        val photo = onNode(SemanticsMatcher.keyIsDefined(MediaZoomScaleKey))
        photo.performTouchInput { click(center) }
        mainClock.advanceTimeBy(800)
        assertEquals(ContentScale.Fit, state.contentScale)
        val camera = onNodeWithText("Canon EOS R5")
        camera.assertIsDisplayed()
        val bounds = camera.fetchSemanticsNode().boundsInRoot
        photo.performTouchInput { doubleClick(center) }
        mainClock.advanceTimeBy(1000)
        assertTrue(photo.fetchSemanticsNode().config[MediaZoomScaleKey] > 2f)
        assertEquals(bounds, camera.fetchSemanticsNode().boundsInRoot)
        assertEquals(ContentScale.Fit, state.contentScale)
        photo.performTouchInput { click(center) }
        mainClock.advanceTimeBy(800)
        camera.assertDoesNotExist()
        photo.performTouchInput { doubleClick(center) }
        mainClock.advanceTimeBy(1000)
        assertEquals(1f, photo.fetchSemanticsNode().config[MediaZoomScaleKey], 0.01f)
        photo.performTouchInput { doubleClick(center) }
        mainClock.advanceTimeBy(1000)
        runOnIdle { state = MediaItemState(DataWithType(metadataFixture("New photo"), "png"), fitSize = true) }
        waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); state.ready }
        mainClock.advanceTimeBy(800)
        assertEquals(1f, photo.fetchSemanticsNode().config[MediaZoomScaleKey], 0.01f)
        assertFalse(state.showMetadata)
    }

    @Test fun horizontalPagerRetainsSwipesAndZoomedPhotoPansWithoutChangingPage() = pagerGestures(false)
    @Test fun verticalPagerRetainsSwipesAndZoomedPhotoPansWithoutChangingPage() = pagerGestures(true)

    private fun pagerGestures(vertical: Boolean) = runDesktopComposeUiTest {
        val bitmap = Bitmap().apply { allocN32Pixels(600, 400) }
        lateinit var pager: PagerState
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            pager = rememberPagerState(initialPage = 1, pageCount = { 4 })
            PagerMediaViewport(pager, Modifier.size(600.dp, 400.dp).testTag("viewport"), vertical) {
                val page: @Composable (Int) -> Unit = { index ->
                    val media = remember(index) { MediaItemState("$index.jpg").apply { loaded(bitmap.asImage()) } }
                    Box(Modifier.fillMaxSize().testTag("photo-$index").mediaZoom(media, index == pager.currentPage) {})
                }
                if (vertical) VerticalPager(pager, Modifier.fillMaxSize()) { page(it) }
                else HorizontalPager(pager, Modifier.fillMaxSize()) { page(it) }
            }
        }
        val viewport = onNodeWithTag("viewport")
        viewport.performTouchInput { if (vertical) swipeUp() else swipeLeft() }
        waitForIdle()
        runOnIdle { assertEquals(2, pager.currentPage) }
        onNodeWithTag("photo-2").performTouchInput { doubleClick(center) }
        waitForIdle()
        assertTrue(onNodeWithTag("photo-2").fetchSemanticsNode().config[MediaZoomScaleKey] > 2f)
        viewport.performTouchInput {
            swipe(center, if (vertical) center - Offset(0f, 150f) else center - Offset(150f, 0f), 400)
        }
        waitForIdle()
        runOnIdle { assertEquals(2, pager.currentPage) }
    }
}
