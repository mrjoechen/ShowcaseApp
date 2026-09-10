package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import coil3.Image
import coil3.asImage
import com.alpha.showcase.common.ai.AiSummaryContent
import com.alpha.showcase.common.ai.AiSummaryState
import com.alpha.showcase.common.ui.ai.AiSummaryOverlay
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduces the pointer-ancestry change made by moving the interactive summary
 * from PagerItem to a sibling of the pager. Both arrangements must let a swipe
 * starting on the narration turn the page; narration taps remain interactive.
 */
@OptIn(ExperimentalTestApi::class)
class MediaOverlayPagerGestureTest {
    @Test fun arrowKeysAdvanceAndReverseTheCurrentImage() = runDesktopComposeUiTest {
        lateinit var pager: PagerState
        setContent {
            pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            PagerMediaViewport(pager, Modifier.fillMaxSize()) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
            }
        }
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        runOnIdle { assertEquals(2, pager.currentPage) }
        onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        runOnIdle { assertEquals(1, pager.currentPage) }
    }

    @Test fun horizontalPagerAcceptsSwipeStartingOnNestedSummary() =
        assertSummaryDragTurnsPage(vertical = false, separateOverlay = false)

    @Test fun verticalPagerAcceptsSwipeStartingOnNestedSummary() =
        assertSummaryDragTurnsPage(vertical = true, separateOverlay = false)

    @Test fun horizontalPagerAcceptsSwipeStartingOnViewportSummary() =
        assertSummaryDragTurnsPage(vertical = false, separateOverlay = true)

    @Test fun verticalPagerAcceptsSwipeStartingOnViewportSummary() =
        assertSummaryDragTurnsPage(vertical = true, separateOverlay = true)

    @Test fun horizontalRtlPagerAcceptsSwipeStartingOnViewportSummary() =
        assertSummaryDragTurnsPage(vertical = false, separateOverlay = true, direction = LayoutDirection.Rtl)

    @Test fun verticalRtlPagerKeepsVerticalSwipeDirection() =
        assertSummaryDragTurnsPage(vertical = true, separateOverlay = true, direction = LayoutDirection.Rtl)

    @Test fun sharedViewportPreservesHorizontalMediaDrags() =
        assertSummaryDragTurnsPage(vertical = false, separateOverlay = true, startOnSummary = false)

    @Test fun sharedViewportPreservesVerticalMediaDrags() =
        assertSummaryDragTurnsPage(vertical = true, separateOverlay = true, startOnSummary = false)

    @Test fun viewportObservesSummaryActivityWithoutStealingClicks() = runDesktopComposeUiTest {
        val bitmap = Bitmap().apply { allocN32Pixels(600, 400) }
        val image = bitmap.asImage()
        lateinit var pager: PagerState
        var interactions = 0
        var regenerations = 0
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            PagerMediaViewport(
                state = pager,
                modifier = Modifier.size(600.dp, 400.dp),
                onInteraction = { interactions++ },
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                Summary(image, pager.currentPage, regenerate = { regenerations++ })
            }
        }
        waitForIdle()
        onNodeWithText("Photo narration 1").performTouchInput { click() }
        waitForIdle()
        runOnIdle {
            assertEquals(1, pager.currentPage, "A narration click must not turn the page")
            assertEquals(0, regenerations)
            assertTrue(interactions > 0, "The viewport must observe activity over interactive summary text")
            interactions = 0
        }
        onNodeWithText("Photo narration 1").performTouchInput { doubleClick() }
        waitForIdle()
        runOnIdle {
            assertEquals(1, pager.currentPage)
            assertEquals(1, regenerations, "The scrolling ancestor must preserve summary double-click regeneration")
            assertTrue(interactions > 0)
        }
    }

    private fun assertSummaryDragTurnsPage(
        vertical: Boolean,
        separateOverlay: Boolean,
        direction: LayoutDirection = LayoutDirection.Ltr,
        startOnSummary: Boolean = true,
    ) = runDesktopComposeUiTest {
        val bitmap = Bitmap().apply { allocN32Pixels(600, 400) }
        val image = bitmap.asImage()
        lateinit var pager: PagerState
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                val pagerContent: @Composable () -> Unit = {
                    val page: @Composable (Int) -> Unit = { index ->
                        Box(Modifier.fillMaxSize()) {
                            if (!separateOverlay) Summary(image, index)
                        }
                    }
                    if (vertical) {
                        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page(it) }
                    } else {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page(it) }
                    }
                }
                val modifier = Modifier.size(600.dp, 400.dp).testTag("viewport")
                if (separateOverlay) {
                    PagerMediaViewport(state = pager, modifier = modifier, vertical = vertical) {
                        pagerContent()
                        Summary(image, pager.currentPage)
                    }
                } else {
                    Box(modifier) { pagerContent() }
                }
            }
        }
        waitForIdle()
        val viewport = onNodeWithTag("viewport")
        val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
        val narrationBounds = onNodeWithText("Photo narration 1").fetchSemanticsNode().boundsInRoot
        val start = when {
            startOnSummary -> narrationBounds.center - viewportBounds.topLeft
            vertical -> Offset(viewportBounds.width - 20f, viewportBounds.height - 20f)
            else -> Offset(20f, 20f)
        }
        viewport.performTouchInput {
            val end = when {
                vertical -> Offset(start.x, 20f)
                direction == LayoutDirection.Rtl -> Offset(20f, start.y)
                else -> Offset(width - 20f, start.y)
            }
            swipe(start, end, durationMillis = 400)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(if (vertical) 2 else 0, pager.currentPage,
                "Swiping narration must turn the page (vertical=$vertical, separateOverlay=$separateOverlay)")
        }
    }
}

@Composable
private fun Summary(image: Image, page: Int, regenerate: () -> Unit = {}) {
    AiSummaryOverlay(
        state = AiSummaryState(content = AiSummaryContent("Photo", "Photo narration $page", listOf("Landscape"))),
        image = image,
        fit = false,
        hasProfile = true,
        regenerate = regenerate,
    )
}
