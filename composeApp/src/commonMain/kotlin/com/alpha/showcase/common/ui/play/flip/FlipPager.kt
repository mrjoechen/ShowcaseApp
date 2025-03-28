package com.alpha.showcase.common.ui.play.flip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.play.PagerItem
import com.alpha.showcase.common.ui.play.isVideo
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

@Composable
fun FlipPager(interval: Long = DEFAULT_PERIOD, data: List<Any>, fitSize: Boolean = true, vertical: Boolean = false, showProgress: Boolean = true) {

    val pageCount = min(data.size * 800, Int.MAX_VALUE / 2)

    val pagerState = rememberPagerState(
        initialPage = pageCount / 2,
        pageCount = {
            pageCount
        }
    )

    Box (
        modifier = Modifier.fillMaxSize(),
    ) {
        Flip(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            orientation = if (vertical) FlipPagerOrientation.Vertical else FlipPagerOrientation.Horizontal,
        ) { page ->
            PagerItem(data = data[page % data.size], fitSize = fitSize, parentType = SHOWCASE_MODE_SLIDE)
        }

        var progress by remember { mutableFloatStateOf(-1f) }
        var currentPage by remember { mutableIntStateOf(0) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { _ ->
                progress = 0f
                currentPage = pagerState.currentPage
            }
        }
        val progressAnimationValue by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "progress animateFloat"
        )

        AnimatedVisibility(showProgress
                && !pagerState.isScrollInProgress
                && data.size > 1
                && !data[currentPage % data.size].isVideo() && progress > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {

            LinearProgressIndicator(
                progress = {
                    progressAnimationValue / interval.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
            )
        }

        LaunchedEffect(Unit){
            while (isActive) {
                delay(100)
                if (!pagerState.isScrollInProgress) {
                    if (progress > interval + 100 && !data[currentPage % data.size].isVideo()) {
                        try {
                            if (pagerState.canScrollForward) {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = tween(2000)
                                )
                            } else {
                                pagerState.animateScrollToPage(
                                    page = 0
                                )
                            }
                        }catch (e: kotlinx.coroutines.CancellationException){
                            e.printStackTrace()
                        }

                        delay(300)
                    } else {
                        if (!pagerState.isScrollInProgress) {
                            progress += 100
                        }
                    }
                }

            }
        }
    }
}