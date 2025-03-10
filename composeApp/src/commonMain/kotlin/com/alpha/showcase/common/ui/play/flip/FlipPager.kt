package com.alpha.showcase.common.ui.play.flip

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.play.PagerItem
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

@Composable
fun FlipPager(interval: Long = DEFAULT_PERIOD, data: List<Any>, fitSize: Boolean = true, vertical: Boolean = false) {

    val pageCount = min(data.size * 800, Int.MAX_VALUE / 2)

    val pagerState = rememberPagerState(
        initialPage = pageCount / 2,
        pageCount = {
            pageCount
        }
    )

    LaunchedEffect(Unit) {
        while (isActive) {
            val timeMillis = if (interval <= 1) DEFAULT_PERIOD else interval
            delay(timeMillis)
            try {
                if (!pagerState.isScrollInProgress) {
                    if (pagerState.canScrollForward) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(2000))
                    } else {
                        pagerState.animateScrollToPage(0)
                    }
                }
            } catch (e: CancellationException) {
                e.printStackTrace()
            }

        }
    }

    Flip(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        orientation = if (vertical) FlipPagerOrientation.Vertical else FlipPagerOrientation.Horizontal,
    ) { page ->
        PagerItem(data = data[page % data.size], fitSize = fitSize, parentType = SHOWCASE_MODE_SLIDE)
    }
}