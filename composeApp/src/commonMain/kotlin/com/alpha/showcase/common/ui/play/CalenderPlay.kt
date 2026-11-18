package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


const val HORIZONTAL_IMAGE_WEIGHT = 0.67f
const val VERTICAL_IMAGE_WEIGHT = 0.6f

private const val LAYOUT_ROTATION_INTERVAL_MILLIS = 15 * 60 * 1000L

@Composable
fun CalenderPlay(
    autoPlay: Boolean = true,
    duration: Long,
    sortRule: Int,
    pagingItems: PagingPlayItems,
    fitSize: Boolean = false,
    showTimeAndDate: Boolean = false,
    avoidImageSummary: Boolean = false,
) {

    val currentShowIndex = remember {
        mutableLongStateOf(0L)
    }

    val currentShow by remember(pagingItems) {
        derivedStateOf {
            pagingItems[currentShowIndex.value.toInt()]
        }
    }

    // Keep the stored index aligned with the wrapped display index when the dataset shrinks.
    LaunchedEffect(pagingItems) {
        snapshotFlow { pagingItems.size }.collect { size ->
            if (size > 0 && currentShowIndex.value >= size) {
                currentShowIndex.value = currentShowIndex.value % size
            }
        }
    }

    val mediaState = rememberMediaItemState(currentShow, fitSize)
    var imageDragging by remember { mutableStateOf(false) }
    val imageSwipe = Modifier.calendarImageSwipe(
        enabled = rememberPlaybackActive() && pagingItems.size > 1,
        onDragging = { imageDragging = it },
        onStep = { direction ->
            val size = pagingItems.size
            if (size > 1) {
                currentShowIndex.value = (currentShowIndex.value + direction + size) % size
            }
        },
    )
    rememberImagePlaybackProgress(duration, current = {
        // This renderer cannot play videos; unsupported entries must also time out and advance.
        ImagePlaybackFrame(currentShowIndex.value to mediaState, mediaState.ready,
            paused = imageDragging,
            enabled = autoPlay && pagingItems.size > 1)
    }) {
        val size = pagingItems.size
        if (size > 1) currentShowIndex.value = (currentShowIndex.value + 1) % size
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .playbackArrowKeys { direction ->
                val size = pagingItems.size
                if (size > 0) {
                    currentShowIndex.value =
                        (currentShowIndex.value + direction).coerceIn(0L, (size - 1).toLong())
                }
            },
    ) {
        // Treat a landscape window below this width as narrow so that the two
        // panels remain readable instead of being squeezed side by side.
        val wideLayout = maxWidth > maxHeight && maxWidth >= 600.dp
        var imageFirst by remember { mutableStateOf(true) }

        // Periodically move both panels. This also applies to the stacked
        // layout, which prevents a fixed bright edge from remaining on screen.
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(LAYOUT_ROTATION_INTERVAL_MILLIS)
                imageFirst = !imageFirst
            }
        }

        if (wideLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (imageFirst) {
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(HORIZONTAL_IMAGE_WEIGHT).then(imageSwipe))
                    CalendarDatePanel(Modifier.weight(1f - HORIZONTAL_IMAGE_WEIGHT))
                } else {
                    CalendarDatePanel(Modifier.weight(1f - HORIZONTAL_IMAGE_WEIGHT))
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(HORIZONTAL_IMAGE_WEIGHT).then(imageSwipe))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (imageFirst) {
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(VERTICAL_IMAGE_WEIGHT).then(imageSwipe))
                    CalendarDatePanel(Modifier.weight(1f - VERTICAL_IMAGE_WEIGHT))
                } else {
                    CalendarDatePanel(Modifier.weight(1f - VERTICAL_IMAGE_WEIGHT))
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(VERTICAL_IMAGE_WEIGHT).then(imageSwipe))
                }
            }
        }
    }


}

/** Only claim horizontal drags; taps and zoom gestures remain with the media. */
@Composable
private fun Modifier.calendarImageSwipe(
    enabled: Boolean,
    onDragging: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
): Modifier {
    val currentOnDragging by rememberUpdatedState(onDragging)
    val currentOnStep by rememberUpdatedState(onStep)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var distance = 0f
        val threshold = 48.dp.toPx()
        try {
            detectHorizontalDragGestures(
                onDragStart = {
                    distance = 0f
                    currentOnDragging(true)
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    distance += amount
                },
                onDragEnd = {
                    when {
                        distance <= -threshold -> currentOnStep(1)
                        distance >= threshold -> currentOnStep(-1)
                    }
                    currentOnDragging(false)
                },
                onDragCancel = { currentOnDragging(false) },
            )
        } finally {
            currentOnDragging(false)
        }
    }
}

@Composable
private fun CalendarImagePanel(
    mediaState: MediaItemState,
    showTimeAndDate: Boolean,
    avoidImageSummary: Boolean,
    modifier: Modifier,
) {
    Box(modifier = modifier.testTag("calendar-image-panel")) {
        DisplayView(state = mediaState)
        if (showTimeAndDate) {
            TimeCard(avoidImageSummary = avoidImageSummary)
        }
    }
}

@Composable
private fun CalendarDatePanel(modifier: Modifier) {
    Box(modifier = modifier.testTag("calendar-date-panel")) {
        CalendarView()
    }
}

@Composable
fun DisplayView(state: MediaItemState) {
    val overlays = MediaOverlayConfig.forStyle(SHOWCASE_MODE_CALENDER)
    var summaryVisible by remember { mutableStateOf(true) }
    var previousState by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (previousState !== state) {
            previousState = state
            summaryVisible = false
            delay(2_600)
            summaryVisible = true
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds().mediaActivity { state.interact(overlays) }) {
        AnimatedContent(
            state,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(2500, delayMillis = 100), initialAlpha = 0.2f)
                    .togetherWith(fadeOut(animationSpec = tween(2500), targetAlpha = 0.2f))
            }, label = "Image display",
        ) { entry ->
            PagerItem(
                state = entry,
                modifier = rememberKenBurnsModifier(entry, SHOWCASE_MODE_CALENDER),
                active = entry === state,
                onInteraction = { entry.interact(overlays, it) },
            )
        }
        MediaOverlayTransition(
            state,
            SHOWCASE_MODE_CALENDER,
            summaryVisible = summaryVisible,
        )
    }
}
