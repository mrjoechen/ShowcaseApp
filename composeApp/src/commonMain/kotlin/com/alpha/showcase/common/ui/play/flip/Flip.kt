package com.alpha.showcase.common.ui.play.flip

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import com.alpha.showcase.common.ui.play.flip.offsetForPage
import kotlinx.coroutines.delay

@Composable
fun Flip(
    state: PagerState,
    modifier: Modifier = Modifier,
    orientation: FlipPagerOrientation,
    pageContent: @Composable (Int) -> Unit,
) {
    val overscrollAmount = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { state.isScrollInProgress }.collect {
            if (!it) overscrollAmount.floatValue = 0f
        }
    }
    val animatedOverscrollAmount by animateFloatAsState(
        targetValue = overscrollAmount.floatValue / 500,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = ""
    )
    val nestedScrollConnection = rememberFlipPagerOverscroll(
        orientation = orientation,
        overscrollAmount = overscrollAmount
    )

    when (orientation) {
        FlipPagerOrientation.Vertical -> {
            VerticalPager(
                state = state,
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                pageContent = {
                    Content(
                        it,
                        state,
                        orientation,
                        pageContent,
                        animatedOverscrollAmount
                    )
                }
            )
        }

        FlipPagerOrientation.Horizontal -> {
            HorizontalPager(
                state = state,
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                pageContent = {
                    Content(
                        it,
                        state,
                        orientation,
                        pageContent,
                        animatedOverscrollAmount
                    )
                }
            )
        }
    }
}


@Composable
private fun Content(
    page: Int,
    state: PagerState,
    orientation: FlipPagerOrientation,
    pageContent: @Composable (Int) -> Unit,
    animatedOverscrollAmount: Float
) {
    var zIndex by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { state.offsetForPage(page) }.collect {
            zIndex = when (state.offsetForPage(page)) {
                in -.5f..(.5f) -> 3f
                in -1f..1f -> 2f
                else -> 1f
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .graphicsLayer {
                val pageOffset = state.offsetForPage(page)
                when (orientation) {
                    FlipPagerOrientation.Vertical -> translationY = size.height * pageOffset
                    FlipPagerOrientation.Horizontal -> translationX = size.width * pageOffset
                }
            },
        contentAlignment = Alignment.Center,
    ) {

        var imageBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        val graphicsLayer = rememberGraphicsLayer()
        val isImageBitmapNull by remember {
            derivedStateOf {
                imageBitmap == null
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .alpha(if (state.isScrollInProgress && !isImageBitmapNull) 0f else 1f)
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                },
            contentAlignment = Alignment.Center
        ) {
            pageContent(page)
        }

        LaunchedEffect(state.isScrollInProgress) {
            while (true) {
                if (graphicsLayer.size.width != 0)
                    imageBitmap = graphicsLayer.toImageBitmap()
                delay(if (state.isScrollInProgress) 16 else 300)
            }
        }

        LaunchedEffect(MaterialTheme.colorScheme.surface) {
            if (graphicsLayer.size.width != 0)
                imageBitmap = graphicsLayer.toImageBitmap()
        }

        PageFlap(
            modifier = Modifier.fillMaxSize(),
            pageFlap = when (orientation) {
                FlipPagerOrientation.Vertical -> PageFlapType.Top
                FlipPagerOrientation.Horizontal -> PageFlapType.Left
            },
            imageBitmap = { imageBitmap },
            state = state,
            page = page,
            animatedOverscrollAmount = { animatedOverscrollAmount }
        )

        PageFlap(
            modifier = Modifier.fillMaxSize(),
            pageFlap = when (orientation) {
                FlipPagerOrientation.Vertical -> PageFlapType.Bottom
                FlipPagerOrientation.Horizontal -> PageFlapType.Right
            },
            imageBitmap = { imageBitmap },
            state = state,
            page = page,
            animatedOverscrollAmount = { animatedOverscrollAmount }
        )
    }
}


@Composable
private fun rememberFlipPagerOverscroll(
    orientation: FlipPagerOrientation,
    overscrollAmount: MutableFloatState
): NestedScrollConnection {
    val nestedScrollConnection = remember(orientation) {
        object : NestedScrollConnection {

            private fun calculateOverscroll(available: Float) {
                val previous = overscrollAmount.floatValue
                overscrollAmount.floatValue += available * (.3f)
                overscrollAmount.floatValue = when {
                    previous > 0 -> overscrollAmount.floatValue.coerceAtLeast(0f)
                    previous < 0 -> overscrollAmount.floatValue.coerceAtMost(0f)
                    else -> overscrollAmount.floatValue
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (overscrollAmount.floatValue != 0f) {
                    when (orientation) {
                        FlipPagerOrientation.Vertical -> calculateOverscroll(available.y)
                        FlipPagerOrientation.Horizontal -> calculateOverscroll(available.x)
                    }
                    return available
                }

                return super.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                when (orientation) {
                    FlipPagerOrientation.Vertical -> calculateOverscroll(available.y)
                    FlipPagerOrientation.Horizontal -> calculateOverscroll(available.x)
                }
                return available
            }
        }
    }
    return nestedScrollConnection
}

