package com.alpha.showcase.common.ui.play

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun FadeLayout(
    pagingItems: PagingPlayItems,
    fitSize: Boolean = false,
    switchDuration: Long = DEFAULT_PERIOD * 2,
    showProgress: Boolean = true,
    showContentInfo: Boolean? = null
) {

    if (pagingItems.size > 0) {
        var currentImageIndex by remember { mutableIntStateOf(0) }
        var showOpButton by remember { mutableStateOf(false) }
        LaunchedEffect(showOpButton) {
            if (showOpButton) {
                delay(5000)
                showOpButton = false
            }
        }
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

        val draggableState = rememberDraggableState {}
        val targetState = pagingItems[currentImageIndex]
        val mediaState = rememberMediaItemState(targetState, fitSize)
        val overlays = MediaOverlayConfig.forStyle(SHOWCASE_MODE_FADE)
        val progress = rememberImagePlaybackProgress(switchDuration, current = {
            ImagePlaybackFrame(currentImageIndex to mediaState, mediaState.ready,
                enabled = pagingItems.size > 1 && !mediaState.data.isVideo())
        }) {
            val size = pagingItems.size
            if (size > 1) currentImageIndex = (currentImageIndex + 1) % size
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .playbackArrowKeys { direction ->
                    val size = pagingItems.size
                    if (size > 0) currentImageIndex = (currentImageIndex + direction).coerceIn(0, size - 1)
                }
                .mediaActivity {
                    showOpButton = true
                    mediaState.interact(overlays)
                }
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
            Crossfade(
                targetState = mediaState,
                animationSpec = tween(durationMillis = 3000),
                label = "fade anim"
            ) { entry ->
                PagerItem(
                    state = entry,
                    modifier = rememberKenBurnsModifier(entry, SHOWCASE_MODE_FADE),
                    active = entry === mediaState,
                    onInteraction = { entry.interact(overlays, it) },
                ) {
                    if (entry === mediaState) {
                        currentData = it
                        val size = pagingItems.size
                        if (size > 0 && targetState.isVideo()) {
                            currentImageIndex = (currentImageIndex + 1) % size
                        }
                    }
                }
            }
            MediaOverlayTransition(mediaState, SHOWCASE_MODE_FADE, showContentInfo)
            ChangePage(
                show = showOpButton,
                canScrollForward = currentImageIndex < pagingItems.size - 1,
                canScrollBackward = currentImageIndex > 0,
                onForward = {
                    if (currentImageIndex < pagingItems.size - 1) currentImageIndex += 1
                },
                onBackward = {
                    if (pagingItems.size > 0 && currentImageIndex > 0) currentImageIndex -= 1
                },
            )
            val progressDuration = switchDuration.takeIf { it > 0 } ?: DEFAULT_PERIOD
            androidx.compose.runtime.key(mediaState, switchDuration) {
                PlaybackProgressBar(
                    elapsedMillis = { progress.value * progressDuration },
                    durationMillis = progressDuration,
                    visible = showProgress && !targetState.isVideo(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    } else {
        DataNotFoundAnim()
    }
}
