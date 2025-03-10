package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.alpha.showcase.common.ui.play.flip.offsetForPage
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

@Composable
fun CubePager(interval: Long = DEFAULT_PERIOD, data: List<Any>, fitSize: Boolean = false, showProgress: Boolean = true) {


    // [infinite pager]: https://stackoverflow.com/questions/75468555/how-to-create-an-endless-pager-in-jetpack-compose
    val pageCount = min(data.size * 800, Int.MAX_VALUE / 2)

    val pagerState = rememberPagerState(
        initialPage = pageCount / 2,
        pageCount = {
            pageCount
        }
    )

    val scale by remember {
        derivedStateOf {
            1f - (pagerState.currentPageOffsetFraction.absoluteValue) * .3f
        }
    }

    val scope = rememberCoroutineScope()
    Box (
        modifier = Modifier.fillMaxSize(),
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
                PagerItem(
                    modifier = Modifier.fillMaxSize(),
                    data = data[page % data.size],
                    fitSize = fitSize,
                    parentType = SHOWCASE_MODE_SLIDE
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
                                e.printStackTrace()
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
                        }catch (e: CancellationException){
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
