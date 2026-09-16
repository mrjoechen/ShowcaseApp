package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Compose's pager excludes mouse pointers; touch and wheel input keep its native behavior. */
internal fun Modifier.summaryPreviewMouseDrag(
    pager: PagerState,
    scope: CoroutineScope,
    layoutDirection: LayoutDirection,
): Modifier = pointerInput(pager, scope, layoutDirection) {
    var settling: Job? = null
    val scrollDirection = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        settling?.cancel()
        val startPage = pager.currentPage
        var scrollDistance = 0f
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            scrollDistance += overSlop * scrollDirection
            pager.dispatchRawDelta(overSlop * scrollDirection)
        } ?: return@awaitEachGesture
        val released = horizontalDrag(drag.id) { change ->
            val delta = change.positionChange().x * scrollDirection
            scrollDistance += delta
            pager.dispatchRawDelta(delta)
            change.consume()
        }
        val threshold = (size.width * 0.15f).coerceAtLeast(viewConfiguration.touchSlop)
        val target = if (released && abs(scrollDistance) >= threshold) {
            (startPage + scrollDistance.sign.toInt()).coerceIn(0, pager.pageCount - 1)
        } else startPage
        settling = scope.launch { pager.animateScrollToPage(target) }
    }
}
