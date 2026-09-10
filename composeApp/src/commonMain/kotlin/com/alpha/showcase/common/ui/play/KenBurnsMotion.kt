package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/** Retain the presentation value while a page covers playback, then continue its motion. */
@Composable
internal fun rememberKenBurnsModifier(state: MediaItemState, parentType: Int): Modifier {
    if (!supportsKenBurns(parentType) || !state.data.isImage() || !state.ready) return Modifier
    return key(state) {
        val zoom = remember { Animatable(1f) }
        var target by remember { mutableFloatStateOf(1.08f) }
        PlaybackEffect(state) {
            while (true) {
                val remaining = (15_000 * abs(target - zoom.value) / 0.08f).toInt().coerceAtLeast(1)
                zoom.animateTo(target, tween(remaining, easing = LinearEasing))
                target = if (target > 1f) 1f else 1.08f
            }
        }
        Modifier.graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }
    }
}
