@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.time.ExperimentalTime

@Composable
fun FadeLayout(
    imageList: List<Any>,
    fitSize: Boolean = false,
    switchDuration: Long = DEFAULT_PERIOD * 2,
    showProgress: Boolean = true,
    showContentInfo: Boolean = false
) {

    if (imageList.isNotEmpty()) {
        var currentImageIndex by remember { mutableIntStateOf(0) }
        var currentData by remember {
            mutableStateOf<Any?>(null)
        }

        LaunchedEffect(key1 = currentImageIndex) {
            while (true) {
                delay(switchDuration)
                if (!showProgress && !imageList[currentImageIndex].isVideo()) {
                    currentImageIndex = (currentImageIndex + 1) % imageList.size
                }
            }
        }

        val draggableState = rememberDraggableState {}
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = draggableState,
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    onDragStopped = {

                        if (abs(it) > 50f) {
                            currentImageIndex = if (it < 0) {
                                (currentImageIndex + 1 + imageList.size) % imageList.size
                            } else {
                                if (currentImageIndex <= 0) 0 else (currentImageIndex - 1 + imageList.size) % imageList.size
                            }
                        }
                    })
        ) {
            val targetState = imageList[currentImageIndex]
            Crossfade(
                targetState = targetState,
                animationSpec = tween(durationMillis = 3000),
                label = "fade anim"
            ) { image ->
                PagerItem(modifier = Modifier, data = image, fitSize, SHOWCASE_MODE_FADE) {
                    currentData = it
                    if (targetState.isVideo()) {
                        currentImageIndex = (currentImageIndex + 1) % imageList.size
                    }
                }
            }
            if (showProgress && currentData != null && !targetState.isVideo()) {
                ProgressIndicator(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    key = currentImageIndex,
                    switchDuration
                ) {
                    currentData = null
                    currentImageIndex = (currentImageIndex + 1) % imageList.size
                }
            }
        }

//        AnimatedVisibility(
//            visible = true,
//            enter = fadeIn(animationSpec = tween(1500)) + scaleIn(animationSpec = tween(1500)),
//            exit = fadeOut(animationSpec = tween(1500)) + scaleOut(animationSpec = tween(1500))
//        ) {
//            PagerItem(modifier = Modifier, data = imageList[currentImageIndex], fitSize)
//        }
    } else {
        DataNotFoundAnim()
    }
}


@Composable
fun ProgressIndicator(
    modifier: Modifier,
    key: Any? = null,
    timeMill: Long,
    onTick: () -> Unit = {}
) {
    var progress by remember(key ?: Unit) { mutableFloatStateOf(0f) }
    val progressAnimation by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "Progress Indicator"
    )
//    delay(delay)

    LinearProgressIndicator(
        progress = {
            progressAnimation
        },
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(20.dp)), // Rounded edges
    )

    LaunchedEffect(key ?: Unit) {
        var currentTimeMillis = Clock.System.now().toEpochMilliseconds()
        while (true) {
            val time = Clock.System.now().toEpochMilliseconds() - currentTimeMillis
            progress = time.toFloat() / timeMill
            if (time > timeMill) {
                progress = 1f
                onTick()
                delay(200)
                progress = 0f
                delay(100)
                currentTimeMillis = Clock.System.now().toEpochMilliseconds()
            } else {
                delay(100)
            }
        }
    }
}
