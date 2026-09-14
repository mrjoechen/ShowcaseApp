package com.alpha.showcase.common.ui.play.flip

import LocalImageLoader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.alpha.showcase.common.ui.play.rememberPagerImagePlaybackProgress
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.alpha.showcase.common.ui.play.rememberInfinitePagerController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.compose.LocalPlatformContext
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import com.alpha.showcase.common.ui.play.ChangePage
import com.alpha.showcase.common.ui.play.PlaybackProgressBar
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.play.PagerItem
import com.alpha.showcase.common.ui.play.rememberMediaItemStateStore
import com.alpha.showcase.common.ui.play.MediaOverlayConfig
import com.alpha.showcase.common.ui.play.MediaOverlayTransition
import com.alpha.showcase.common.ui.play.PagerMediaViewport
import com.alpha.showcase.common.ui.play.PagingPlayItems
import com.alpha.showcase.common.ui.play.ImagePrefetchWindow
import com.alpha.showcase.common.ui.play.prefetchImages
import com.alpha.showcase.common.ui.play.isImage
import com.alpha.showcase.common.ui.play.isVideo
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE

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
        LaunchedEffect(controller, mediaStates, imageLoader, context) {
            prefetchImages(snapshotFlow {
                val page = pagerState.currentPage
                val current = mediaStates.get(page, controller.item(page), fitSize)
                val next = if (controller.displaySize > 1) controller.item(page + 1).takeIf { it.isImage() } else null
                ImagePrefetchWindow(current.data, current.ready, next)
            }) { imageLoader?.execute(buildMediaImageRequest(context, it)) }
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
        val progressDuration = interval.takeIf { it > 0 } ?: DEFAULT_PERIOD
        val progress = rememberPagerImagePlaybackProgress(
            pagerState, interval, controller.displaySize, animationMillis = 2000,
        ) { page -> mediaStates.get(page, controller.item(page), fitSize) }
        PlaybackProgressBar(
            elapsedMillis = { progress.value * progressDuration },
            durationMillis = progressDuration,
            visible = showProgress && !pagerState.isScrollInProgress
                && controller.displaySize > 1 && !controller.item(pagerState.currentPage).isVideo(),
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        ChangePage(pagerState, showOpButton)
    }
}
