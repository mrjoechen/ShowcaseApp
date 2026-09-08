@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.time.ExperimentalTime

@Composable
fun FadeLayout(
    pagingItems: PagingPlayItems,
    fitSize: Boolean = false,
    switchDuration: Long = DEFAULT_PERIOD * 2,
    showProgress: Boolean = true,
    showContentInfo: Boolean = false
) {

    if (pagingItems.size > 0) {
        var currentImageIndex by remember { mutableIntStateOf(0) }
        var currentData by remember {
            mutableStateOf<Any?>(null)
        }

        // A dataset shrink can strand currentImageIndex beyond the new size. get()
        // wraps for display, but the STORED index must be folded too or the next
        // advance jumps ((850+1) % 300) instead of stepping from the shown image.
        // Prefer the on-screen item's new index when it is still locatable in
        // loaded pages (identity re-anchor); otherwise fold by modulo.
        LaunchedEffect(pagingItems) {
            snapshotFlow { pagingItems.size }.collect { size ->
                if (size > 0 && currentImageIndex >= size) {
                    currentImageIndex = (currentData?.let { pagingItems.indexOfLoaded(it) }
                        ?: (currentImageIndex % size)).coerceIn(0, size - 1)
                }
            }
        }

        LaunchedEffect(key1 = currentImageIndex) {
            while (true) {
                delay(switchDuration)
                // Guard against an in-place markEmpty() (size -> 0) landing before
                // this effect is cancelled: avoid a % 0 crash.
                val size = pagingItems.size
                if (size <= 0) continue
                if (!showProgress && !pagingItems[currentImageIndex].isVideo()) {
                    currentImageIndex = (currentImageIndex + 1) % size
                }
            }
        }

        val draggableState = rememberDraggableState {}
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .draggable(
                    state = draggableState,
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    onDragStopped = {
                        val size = pagingItems.size
                        if (size > 0 && abs(it) > 50f) {
                            currentImageIndex = if (it < 0) {
                                (currentImageIndex + 1 + size) % size
                            } else {
                                if (currentImageIndex <= 0) 0 else (currentImageIndex - 1 + size) % size
                            }
                        }
                    })
        ) {
            val targetState = pagingItems[currentImageIndex]
            Crossfade(
                targetState = targetState,
                animationSpec = tween(durationMillis = 3000),
                label = "fade anim"
            ) { image ->
                val imageModifier = if (image.isImage()) {
                    // Each Crossfade entry owns its motion, including while fading out.
                    // Scale the whole image layer so privacy masks stay aligned.
                    val motion = rememberInfiniteTransition(label = "fade image motion")
                    val zoom = motion.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(15_000, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "ken burns zoom",
                    )
                    Modifier.graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                    }
                } else {
                    Modifier
                }
                PagerItem(modifier = imageModifier, data = image, fitSize, SHOWCASE_MODE_FADE, active = image == targetState) {
                    currentData = it
                    val size = pagingItems.size
                    if (size > 0 && targetState.isVideo()) {
                        currentImageIndex = (currentImageIndex + 1) % size
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
                    val size = pagingItems.size
                    if (size > 0) currentImageIndex = (currentImageIndex + 1) % size
                }
            }
        }
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
