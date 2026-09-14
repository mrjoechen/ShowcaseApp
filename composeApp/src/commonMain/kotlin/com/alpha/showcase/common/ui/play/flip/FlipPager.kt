package com.alpha.showcase.common.ui.play.flip

import LocalImageLoader
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.alpha.showcase.common.ui.play.PlaybackEffect
import com.alpha.showcase.common.ui.play.PlaybackProgressBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.alpha.showcase.common.ui.play.rememberInfinitePagerController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import com.alpha.showcase.common.ui.play.ChangePage
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.play.PagerItem
import com.alpha.showcase.common.ui.play.rememberMediaItemStateStore
import com.alpha.showcase.common.ui.play.MediaOverlayConfig
import com.alpha.showcase.common.ui.play.MediaOverlayTransition
import com.alpha.showcase.common.ui.play.PagerMediaViewport
import com.alpha.showcase.common.ui.play.PagingPlayItems
import com.alpha.showcase.common.ui.play.isVideo
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun FlipPager(interval: Long = DEFAULT_PERIOD, data: PagingPlayItems, fitSize: Boolean = true, vertical: Boolean = false, showProgress: Boolean = true) {

    val controller = rememberInfinitePagerController(data)
    val pagerState = controller.pagerState
    val mediaStates = rememberMediaItemStateStore(fitSize)
    val overlayConfig = MediaOverlayConfig.forStyle(SHOWCASE_MODE_SLIDE)
    var showOpButton by remember { mutableStateOf(false) }

    PagerMediaViewport(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        vertical = vertical,
        onInteraction = {
            showOpButton = true
            val page = pagerState.currentPage
            mediaStates.get(page, controller.item(page), fitSize).interact(overlayConfig)
        },
    ) {

        val imageLoader = LocalImageLoader.current
        val context = LocalPlatformContext.current
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { currentPage ->
                    for (i in 1..4) {
                        imageLoader?.enqueue(buildMediaImageRequest(context, controller.item(currentPage + i)))
                    }
                }
        }


        Flip(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            orientation = if (vertical) FlipPagerOrientation.Vertical else FlipPagerOrientation.Horizontal,
        ) { page ->
            val mediaState = mediaStates.get(page, controller.item(page), fitSize)
            PagerItem(
                state = mediaState,
                active = page == pagerState.currentPage,
                onInteraction = { mediaState.interact(overlayConfig, it) },
            )
        }

        MediaOverlayTransition(mediaStates.get(pagerState.currentPage, controller.item(pagerState.currentPage), fitSize), SHOWCASE_MODE_SLIDE)
        var progress by remember { mutableFloatStateOf(-1f) }
        var currentPage by remember { mutableIntStateOf(0) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { _ ->
                progress = 0f
                currentPage = pagerState.currentPage
            }
        }
        PlaybackProgressBar(
            elapsedMillis = { progress },
            durationMillis = interval,
            visible = showProgress && !pagerState.isScrollInProgress
                && controller.displaySize > 1 && !controller.item(currentPage).isVideo(),
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        PlaybackEffect(Unit){
            while (isActive) {
                delay(100)
                if (!pagerState.isScrollInProgress) {
                    if (progress > interval + 100 && !controller.item(currentPage).isVideo()) {
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
                            throw e
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

        ChangePage(pagerState, showOpButton)
    }
}
