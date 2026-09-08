package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * A common gesture ancestor for a pager and its stationary, interactive overlays.
 * The pager still handles gestures over media, including its own overscroll;
 * this parent handles drags that start over sibling summary text or actions.
 */
@Composable
internal fun PagerMediaViewport(
    state: PagerState,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    reverseLayout: Boolean = false,
    onInteraction: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal
    Box(
        modifier
            .scrollable(
                state = state,
                orientation = orientation,
                flingBehavior = PagerDefaults.flingBehavior(state),
                reverseDirection = ScrollableDefaults.reverseDirection(
                    LocalLayoutDirection.current, orientation, reverseLayout,
                ),
            )
            .mediaActivity(onInteraction),
        content = content,
    )
}

/** Observe activity over both media and interactive overlays without consuming it. */
@Composable
internal fun Modifier.mediaActivity(onInteraction: () -> Unit): Modifier {
    val currentInteraction by rememberUpdatedState(onInteraction)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // Desktop Compose re-sends stationary hover events after relayout.
                // Treating those as activity would reopen a button as it fades out.
                val userActivity = event.type == PointerEventType.Press ||
                    event.type == PointerEventType.Release ||
                    event.type == PointerEventType.Enter ||
                    event.changes.any {
                        it.position != it.previousPosition || it.scrollDelta != Offset.Zero
                    }
                if (userActivity) {
                    currentInteraction()
                }
            }
        }
    }
}
