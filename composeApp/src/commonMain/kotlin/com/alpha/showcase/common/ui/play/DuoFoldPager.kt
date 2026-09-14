package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alpha.showcase.common.ui.play.fold.FoldDirection
import com.alpha.showcase.common.ui.play.fold.duoFoldAnimationSpec
import com.alpha.showcase.common.ui.play.fold.FoldImageTransition
import com.alpha.showcase.common.ui.play.fold.foldRetreatScale
import com.alpha.showcase.common.ui.play.fold.isDuoFoldAvailable
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A normal Showcase media pager; only its pixels are replaced by the Duo Fold transition. */
@Composable
fun DuoFoldPager(
    data: PagingPlayItems,
    interval: Long = DEFAULT_PERIOD,
    fitSize: Boolean = false,
    showProgress: Boolean = true,
    retreatEnabled: Boolean = true,
) {
    if (!isDuoFoldAvailable()) {
        SlideImagePager(data, fitSize = fitSize, switchDuration = interval, showProgress = showProgress)
        return
    }
    val controller = rememberInfinitePagerController(data)
    val pager = controller.pagerState
    val mediaStates = rememberMediaItemStateStore(fitSize)
    val overlayConfig = MediaOverlayConfig.forStyle(SHOWCASE_MODE_SLIDE)
    fun media(page: Int) = mediaStates.get(page, controller.item(page), fitSize)
    val current = media(pager.currentPage)
    var showButtons by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    fun step(delta: Int) {
        val target = pager.currentPage + delta
        if (!pager.isScrollInProgress && target in 0 until pager.pageCount) {
            scope.launch { pager.animateScrollToPage(target, animationSpec = duoFoldAnimationSpec()) }
        }
    }

    LaunchedEffect(showButtons) {
        if (showButtons) { delay(5000); showButtons = false }
    }
    LaunchedEffect(current) { elapsed = 0L }

    PagerMediaViewport(
        state = pager,
        userScrollEnabled = controller.displaySize > 1,
        modifier = Modifier.fillMaxSize().background(Color(0xFF141716)).clipToBounds().testTag("duo-fold-pager"),
        onInteraction = { showButtons = true; current.interact(overlayConfig) },
        pageAnimationSpec = duoFoldAnimationSpec(),
        flingBehavior = duoFoldFlingBehavior(pager),
    ) {
        DuoFoldPages(pager, retreatEnabled, controller.displaySize > 1) { page -> media(page) }
        // Metadata and AI actions belong to the viewport, never to the folding layers.
        MediaOverlayTransition(current, SHOWCASE_MODE_SLIDE)
        PlaybackProgressBar(
            elapsedMillis = { elapsed.toFloat() },
            durationMillis = interval,
            visible = showProgress && data.size > 1 && !pager.isScrollInProgress && current.ready,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        ChangePage(
            show = showButtons && controller.displaySize > 1,
            canScrollForward = pager.canScrollForward,
            canScrollBackward = pager.canScrollBackward,
            onForward = { step(1) },
            onBackward = { step(-1) },
        )
    }

    PlaybackEffect(controller, mediaStates, interval) {
        while (isActive) {
            delay(100)
            if (data.size <= 1 || pager.isScrollInProgress) { elapsed = 0L; continue }
            // A lifecycle pause may interrupt an automatic turn part way through.
            if (abs(pager.currentPageOffsetFraction) > .0001f) {
                pager.animateScrollToPage(pager.currentPage, animationSpec = tween(250))
                continue
            }
            val visible = media(pager.currentPage)
            if (!visible.finishedLoading) { elapsed = 0L; continue }
            elapsed = (elapsed + 100).coerceAtMost(interval.coerceAtLeast(1))
            val nextPage = if (pager.canScrollForward) pager.currentPage + 1 else 0
            // The adjacent PagerItem preloads through the same authenticated request path.
            // Failed/unsupported entries can advance after their error screen's dwell.
            if (elapsed >= interval.coerceAtLeast(1) && media(nextPage).finishedLoading) {
                pager.animateScrollToPage(nextPage, animationSpec = duoFoldAnimationSpec())
                elapsed = 0L
            }
        }
    }
}

private val MediaItemState.finishedLoading: Boolean
    get() = ready || error != null || !data.isImage()

@Composable
private fun duoFoldFlingBehavior(pager: PagerState) =
    PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(900))

/** Never add the fractional offset to the huge virtual page number: Float loses it. */
internal fun duoFoldLowerPage(page: Int, offset: Float): Int = page - if (offset < 0f) 1 else 0
internal fun duoFoldProgress(offset: Float): Float = if (offset < 0f) 1f + offset else offset

private class DuoFoldCapture(val media: MediaItemState, val layer: GraphicsLayer) {
    var recorded by mutableStateOf(false)
    // Retain GPU/display-list references, including Fit backgrounds and animated images.
    // No CPU bitmap capture or extra Coil request is needed for a transition.
    val painter = object : Painter() {
        override val intrinsicSize = Size.Unspecified
        override fun DrawScope.onDraw() { drawLayer(layer) }
    }
}

@Composable
internal fun DuoFoldPages(
    pager: PagerState,
    retreatEnabled: Boolean,
    userScrollEnabled: Boolean = true,
    media: (Int) -> MediaItemState,
) {
    val captures = remember { mutableStateMapOf<Int, DuoFoldCapture>() }
    val lowerPage by remember(pager) {
        derivedStateOf { duoFoldLowerPage(pager.currentPage, pager.currentPageOffsetFraction) }
    }
    val lower = captures[lowerPage]
    val upper = captures[lowerPage + 1]
    val folding by remember(pager, lower, upper) {
        derivedStateOf {
            abs(pager.currentPageOffsetFraction) > .0001f &&
                lower?.recorded == true && upper?.recorded == true &&
                lower.media.ready && upper.media.ready
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            userScrollEnabled = userScrollEnabled,
            flingBehavior = duoFoldFlingBehavior(pager),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = media(page)
            val layer = rememberGraphicsLayer()
            val capture = remember(entry, layer) { DuoFoldCapture(entry, layer) }
            DisposableEffect(page, capture) {
                captures[page] = capture
                onDispose { if (captures[page] === capture) captures.remove(page) }
            }
            Box(Modifier.fillMaxSize()
                .zIndex(if (page == pager.currentPage) 1f else 0f)
                .graphicsLayer {
                    // Cancel the pager's translation, leaving a fixed gesture/zoom surface.
                    translationX = ((pager.currentPage - page) + pager.currentPageOffsetFraction) * size.width
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    capture.recorded = true
                    if (!folding && page == pager.currentPage) drawLayer(layer)
                }) {
                PagerItem(
                    state = entry,
                    modifier = Modifier.fillMaxSize(),
                    active = page == pager.currentPage,
                    onInteraction = { entry.interact(MediaOverlayConfig.forStyle(SHOWCASE_MODE_SLIDE), it) },
                )
            }
        }
        if (folding && lower != null && upper != null) {
            FoldImageTransition(
                current = lower.painter,
                next = upper.painter,
                progress = { duoFoldProgress(pager.currentPageOffsetFraction) },
                direction = FoldDirection.Backward,
                cornerRadius = if (retreatEnabled) 24.dp else 0.dp,
                roundCornersOnlyWhileFolding = true,
                modifier = Modifier.fillMaxSize().testTag("duo-fold-transition").graphicsLayer {
                    val scale = if (retreatEnabled) foldRetreatScale(duoFoldProgress(pager.currentPageOffsetFraction)) else 1f
                    scaleX = scale
                    scaleY = scale
                    clip = false
                },
            )
        }
    }
}
