package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.toSize
import kotlin.math.exp
import kotlin.math.min

/** Zoomable publishes Wasm but no JS artifact; keep the JS renderer gesture-compatible. */
@Composable
internal actual fun Modifier.mediaZoom(state: MediaItemState, active: Boolean, onTap: () -> Unit): Modifier {
    var scale by remember(state) { mutableFloatStateOf(1f) }
    var offset by remember(state) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    val currentTap by rememberUpdatedState(onTap)
    val enabled = active && state.ready
    fun transform(factor: Float, pan: Offset, center: Offset = Offset(viewport.width / 2, viewport.height / 2)) {
        val next = (scale * factor).coerceIn(1f, 5f)
        val image = state.displayedImage
        val size = if (state.contentScale == ContentScale.Fit && image != null) {
            val fit = min(viewport.width / image.width, viewport.height / image.height)
            Size(image.width * fit, image.height * fit)
        } else viewport
        val xBound = ((size.width * next - viewport.width) / 2).coerceAtLeast(0f)
        val yBound = ((size.height * next - viewport.height) / 2).coerceAtLeast(0f)
        val anchor = center - Offset(viewport.width / 2, viewport.height / 2)
        val moved = (offset - anchor) * (next / scale) + anchor + pan
        offset = Offset(moved.x.coerceIn(-xBound, xBound), moved.y.coerceIn(-yBound, yBound))
        scale = next
    }
    LaunchedEffect(state, state.displayedImage, state.contentScale, active) {
        scale = 1f
        offset = Offset.Zero
    }
    val transformable = rememberTransformableState { zoom, pan, _ -> transform(zoom, pan) }
    val currentTransform by rememberUpdatedState(::transform)
    return onSizeChanged { viewport = it.toSize() }
        .semantics {
            mediaZoomScale = scale
            if (active) onClick { currentTap(); true }
        }
        .pointerInput(state, active, enabled) {
            detectTapGestures(
                onTap = { if (active) currentTap() },
                onDoubleTap = { if (enabled) currentTransform(if (scale > 1f) 1f / scale else 2.5f, Offset.Zero, it) },
            )
        }
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (enabled && event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                        event.changes.forEach {
                            currentTransform(exp(-it.scrollDelta.y * 0.1f), Offset.Zero, it.position)
                            it.consume()
                        }
                    }
                }
            }
        }
        .transformable(transformable, canPan = { scale > 1f }, enabled = enabled)
        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
}
