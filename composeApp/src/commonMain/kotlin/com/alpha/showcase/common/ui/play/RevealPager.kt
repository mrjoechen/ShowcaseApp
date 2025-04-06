package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.play.flip.endOffsetForPage
import com.alpha.showcase.common.ui.play.flip.offsetForPage
import com.alpha.showcase.common.ui.play.flip.startOffsetForPage
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun CircleRevealPager(interval: Long = DEFAULT_PERIOD, data: List<Any>, fitSize: Boolean = false, showProgress: Boolean = true) {
    val pageCount = min(data.size * 800, Int.MAX_VALUE / 2)

    val pagerState = rememberPagerState(
        initialPage = pageCount / 2,
        pageCount = {
            pageCount
        }
    )
//    LaunchedEffect(Unit) {
//        while (isActive) {
//            delay(if (interval <= 1) DEFAULT_PERIOD else interval)
//            if (pagerState.canScrollForward) {
//                pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(2000))
//            } else {
//                pagerState.animateScrollToPage(0)
//            }
//        }
//    }
    var showOpButton by remember { mutableStateOf(false) }
    var offsetY by remember { mutableStateOf(0f) }
    var scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()
        .pointerInput(Unit) {
            // Listen for pointer (mouse) movements
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.isNotEmpty()) {
                        showOpButton = true
                    }
                }
            }
        }
    ){
        HorizontalPager(
            modifier = Modifier
//                .pointerInteropFilter {
//                    offsetY = it.y
//                    false
//                }
//            .clip(
//                RoundedCornerShape(25.dp)
//            )
            ,
            state = pagerState,
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = pagerState.offsetForPage(page)
                        if (pageOffset != 0f){
                            translationX = size.width * pageOffset

                            val endOffset = pagerState.endOffsetForPage(page)

                            shadowElevation = 20f
                            shape = CirclePath(
                                progress = 1f - endOffset.absoluteValue,
                                origin = Offset(
                                    size.width,
                                    offsetY,
                                )
                            )
                            clip = true

                            val absoluteOffset = pagerState.offsetForPage(page).absoluteValue
                            val scale = 1f + (absoluteOffset.absoluteValue * .4f)

                            scaleX = scale
                            scaleY = scale

                            val startOffset = pagerState.startOffsetForPage(page)
                            alpha = (2f - startOffset) / 2f
                        }


                    },
                contentAlignment = Alignment.Center,
            ) {
                PagerItem(
                    modifier = Modifier.fillMaxSize(),
                    data = data[page % data.size],
                    fitSize,
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
//            Box(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .fillMaxWidth()
//                    .fillMaxHeight(.8f)
//                    .background(
//                        brush = Brush.verticalGradient(
//                            listOf(
//                                Color.Black.copy(alpha = 0f),
//                                Color.Black.copy(alpha = .7f),
//                            )
//                        )
//                    )
//            )
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
        ChangePage(pagerState, showOpButton)
    }

}

class CirclePath(private val progress: Float, private val origin: Offset = Offset(0f, 0f)) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {

        val center = Offset(
            x = size.center.x - ((size.center.x - origin.x) * (1f - progress)),
            y = size.center.y - ((size.center.y - origin.y) * (1f - progress)),
        )
        val radius = (sqrt(
            size.height * size.height + size.width * size.width
        ) * .5f) * progress

        return Outline.Generic(Path().apply {
            addOval(
                Rect(
                    center = center,
                    radius = radius,
                )
            )
        })
    }
}