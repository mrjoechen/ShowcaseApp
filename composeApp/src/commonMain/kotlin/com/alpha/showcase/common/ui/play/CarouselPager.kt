package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

@Composable
fun CarouselPager(interval: Long = DEFAULT_PERIOD, data: List<Any>, fitSize: Boolean = false) {

    val pageCount = min(data.size * 800, Int.MAX_VALUE / 2)

    val horizontalState = rememberPagerState(
        initialPage = pageCount / 2,
        pageCount = {
            pageCount
        }
    )

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(if (interval <= 1) DEFAULT_PERIOD else interval)
            try {
                if (horizontalState.canScrollForward) {
                    horizontalState.animateScrollToPage(horizontalState.currentPage + 1, animationSpec = tween(1000))
                } else {
                    horizontalState.animateScrollToPage(0)
                }
            }catch (ex: CancellationException){
                ex.printStackTrace()
            }
        }
    }


    Column {
        HorizontalPager(
            modifier = Modifier
                .weight(.7f)
                .padding(
                    vertical = 16.dp
                ),
            state = horizontalState,
            pageSpacing = 1.dp,
            beyondViewportPageCount = 9
        ) { page ->
            Box(
                modifier = Modifier
                    .zIndex(page * 10f)
                    .padding(
                        start = 128.dp,
                        end = 16.dp,
                    )
                    .graphicsLayer {
                        val startOffset = horizontalState.startOffsetForPage(page)
                        translationX = size.width * (startOffset * .99f)
                        alpha = (2f - startOffset) / 2f
                        val scale = 1f - (startOffset * .1f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PagerItem(
                    modifier = Modifier.fillMaxSize(),
                    data = data[page % data.size],
                    fitSize
                )
            }
        }

//        Row(
//            modifier = Modifier
//                .padding(horizontal = 16.dp)
//                .fillMaxWidth()
//                .weight(.3f),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically,
//        ) {
//
//            val verticalState = rememberPagerState{
//                data.size
//            }
//
//            VerticalPager(
//                state = verticalState,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(72.dp),
//                userScrollEnabled = false,
//                horizontalAlignment = Alignment.Start,
//            ) { page ->
//                Column(
//                    verticalArrangement = Arrangement.Center,
//                ) {
//                    Text(
//                        text = movies[page].title,
//                        style = MaterialTheme.typography.headlineMedium.copy(
//                            fontWeight = FontWeight.Thin,
//                            fontSize = 28.sp,
//                        )
//                    )
//                    Text(
//                        text = movies[page].subtitle,
//                        style = MaterialTheme.typography.bodyMedium.copy(
//                            fontWeight = FontWeight.Bold,
//                            fontSize = 14.sp,
//                            color = Color.Black.copy(alpha = .56f),
//                        )
//                    )
//                }
//            }
//
//            LaunchedEffect(Unit) {
//                snapshotFlow {
//                    Pair(
//                        horizontalState.currentPage,
//                        horizontalState.currentPageOffsetFraction
//                    )
//                }.collect { (page, offset) ->
//                    verticalState.scrollToPage(page, offset)
//                }
//            }
//            val interpolatedRating by remember {
//                derivedStateOf {
//                    val position = horizontalState.offsetForPage(0)
//                    val from = floor(position).roundToInt()
//                    val to = ceil(position).roundToInt()
//
//                    val fromRating = movies[from].rating.toFloat()
//                    val toRating = movies[to].rating.toFloat()
//
//                    val fraction = position - position.toInt()
//                    fromRating + ((toRating - fromRating) * fraction)
//                }
//            }
//
//            RatingStars(rating = interpolatedRating)
//        }
    }
}

@Composable
fun RatingStars(
    modifier: Modifier = Modifier,
    rating: Float,
) {
    Row(
        modifier = modifier
    ) {

        for (i in 1..5) {
            val animatedScale by animateFloatAsState(
                targetValue = if (floor(rating) >= i) {
                    1f
                } else if (ceil(rating) < i) {
                    0f
                } else {
                    rating - rating.toInt()
                },
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium
                ),
                label = ""
            )

            Box(
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Rounded.Star),
                    contentDescription = null,
                    modifier = Modifier.alpha(.1f),
                )
                Icon(
                    painter = rememberVectorPainter(image = Icons.Rounded.Star),
                    contentDescription = null,
                    modifier = Modifier.scale(animatedScale),
                    tint = Color(0xFFD59411)
                )
            }

        }

    }
}