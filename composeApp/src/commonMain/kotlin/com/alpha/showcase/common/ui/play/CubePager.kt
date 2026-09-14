package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.play.flip.offsetForPage
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random

@Composable
fun CubePager(interval: Long = DEFAULT_PERIOD, data: PagingPlayItems, fitSize: Boolean = false, showProgress: Boolean = true) {

    // [infinite pager]: https://stackoverflow.com/questions/75468555/how-to-create-an-endless-pager-in-jetpack-compose
    val controller = rememberInfinitePagerController(data)
    val pagerState = controller.pagerState
    val mediaStates = rememberMediaItemStateStore(fitSize)
    val overlayConfig = MediaOverlayConfig.forStyle(SHOWCASE_MODE_SLIDE)

    val scale by remember {
        derivedStateOf {
            1f - (pagerState.currentPageOffsetFraction.absoluteValue) * .3f
        }
    }
    var showOpButton by remember { mutableStateOf(false) }

    LaunchedEffect(showOpButton) {
        if (showOpButton) {
            delay(5000) // Wait for 10 seconds
            showOpButton = false // Hide the button
        }
    }
    val scope = rememberCoroutineScope()
    PagerMediaViewport(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        onInteraction = {
            showOpButton = true
            val page = pagerState.currentPage
            mediaStates.get(page, controller.item(page), fitSize).interact(overlayConfig)
        },
    ) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .scale(1f, scaleY = scale)
        ) { page ->
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val pageOffset = pagerState.offsetForPage(page)
                        val offScreenRight = pageOffset < 0f
                        val deg = 85f
                        val interpolated = FastOutLinearInEasing.transform(pageOffset.absoluteValue)
                        rotationY = min(interpolated * if (offScreenRight) deg else -deg, 85f)

                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (offScreenRight) 0f else 1f,
                            pivotFractionY = .5f
                        )
                    }
                    .drawWithContent {
                        val pageOffset = pagerState.offsetForPage(page)

                        this.drawContent()
                        drawRect(
                            Color.Black.copy(
                                (pageOffset.absoluteValue * .7f)
                            )
                        )
                    }
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val mediaState = mediaStates.get(page, controller.item(page), fitSize)
                PagerItem(
                    modifier = Modifier.fillMaxSize(),
                    state = mediaState,
                    onInteraction = { mediaState.interact(overlayConfig, it) },
                    active = page == pagerState.currentPage
                ){
                    if (it.isVideo()){
                        scope.launch {
                            try {
                                if (pagerState.canScrollForward) {
                                    pagerState.animateScrollToPage(
                                        page = pagerState.currentPage + 1,
                                        animationSpec = tween(800)
                                    )
                                } else {
                                    pagerState.animateScrollToPage(
                                        page = 0
                                    )
                                }
                            }catch (e: CancellationException){
                                throw e
                            }
                        }
                    }
                }
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


        MediaOverlayTransition(mediaStates.get(pagerState.currentPage, controller.item(pagerState.currentPage), fitSize), SHOWCASE_MODE_SLIDE)
        val progress by rememberPagerImagePlaybackProgress(
            pagerState, interval, controller.displaySize, animationMillis = 2000,
        ) { page -> mediaStates.get(page, controller.item(page), fitSize) }
        val progressAnimationValue by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "progress animateFloat"
        )

        AnimatedVisibility(showProgress
                && !pagerState.isScrollInProgress
                && controller.displaySize > 1
                && !controller.item(pagerState.currentPage).isVideo() && progress > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {

            LinearProgressIndicator(
                progress = {
                    progressAnimationValue
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
            )
        }

        ChangePage(pagerState, showOpButton)
    }
}
