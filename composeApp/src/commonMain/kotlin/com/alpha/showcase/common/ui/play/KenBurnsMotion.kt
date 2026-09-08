package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Motion starts after decoding, survives the outgoing fade, and ends with the media entry. */
@Composable
internal fun rememberKenBurnsModifier(state: MediaItemState, parentType: Int): Modifier {
    if (!supportsKenBurns(parentType) || !state.data.isImage() || !state.ready) return Modifier
    return key(state) {
        val motion = rememberInfiniteTransition(label = "media motion")
        val zoom = motion.animateFloat(
            initialValue = 1f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                tween(15_000, easing = LinearOutSlowInEasing), RepeatMode.Reverse,
            ), label = "ken burns zoom",
        )
        Modifier.graphicsLayer {
            scaleX = zoom.value
            scaleY = zoom.value
        }
    }
}
