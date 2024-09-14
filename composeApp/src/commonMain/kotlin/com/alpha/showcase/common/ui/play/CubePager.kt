package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

@Composable
fun CubePager(interval: Long = DEFAULT_PERIOD, data: List<Any>) {
    val state = rememberPagerState{
        data.size
    }

    val scale by remember {
        derivedStateOf {
            1f - (state.currentPageOffsetFraction.absoluteValue) * .3f
        }
    }

    Box(
        modifier = Modifier
            .background(Color.White)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val offsetFromStart = state.offsetForPage(0).absoluteValue
        Box(
            modifier = Modifier
//                .aspectRatio(1f)
                .offset { IntOffset(0, 150.dp.roundToPx()) }
                .scale(scaleX = .6f, scaleY = .24f)
                .scale(scale)
                .rotate(offsetFromStart * 90f)
                .blur(
                    radius = 110.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(Color.Black.copy(alpha = .5f))
        )

        LaunchedEffect(state) {
            while (true) {
                delay(if (interval <= 1) DEFAULT_PERIOD else interval)
                if (state.canScrollForward) {
                    state.animateScrollToPage(state.currentPage + 1, animationSpec = tween(1000))
                } else {
                    state.animateScrollToPage(0)
                }
            }
        }

        HorizontalPager(
            state = state,
            modifier = Modifier
                .scale(1f, scaleY = scale)
//                .aspectRatio(1f),
        ) { page ->
            Box(
                modifier = Modifier
//                    .aspectRatio(1f)
                    .graphicsLayer {
                        val pageOffset = state.offsetForPage(page)
                        val offScreenRight = pageOffset < 0f
                        val deg = 105f
                        val interpolated = FastOutLinearInEasing.transform(pageOffset.absoluteValue)
                        rotationY = min(interpolated * if (offScreenRight) deg else -deg, 90f)

                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (offScreenRight) 0f else 1f,
                            pivotFractionY = .5f
                        )
                    }
                    .drawWithContent {
                        val pageOffset = state.offsetForPage(page)

                        this.drawContent()
                        drawRect(
                            Color.Black.copy(
                                (pageOffset.absoluteValue * .7f)
                            )
                        )
                    }
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                PagerItem(
                    modifier = Modifier.fillMaxSize(),
                    data = data[page]
                )
//                Text(
//                    text = "Hello", style = MaterialTheme.typography.headlineMedium.copy(
//                        color = Color.White,
//                        fontWeight = FontWeight.Bold,
//                        shadow = Shadow(
//                            color = Color.Black.copy(alpha = .6f),
//                            blurRadius = 30f,
//                        )
//                    )
//                )
            }
        }
    }
}

// ACTUAL OFFSET
fun PagerState.offsetForPage(page: Int) = (currentPage - page) + currentPageOffsetFraction

// OFFSET ONLY FROM THE LEFT
fun PagerState.startOffsetForPage(page: Int): Float {
    return offsetForPage(page).coerceAtLeast(0f)
}

// OFFSET ONLY FROM THE RIGHT
fun PagerState.endOffsetForPage(page: Int): Float {
    return offsetForPage(page).coerceAtMost(0f)
}