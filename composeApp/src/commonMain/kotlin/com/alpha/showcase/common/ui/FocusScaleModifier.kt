package com.alpha.showcase.common.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import com.valentinilk.shimmer.shimmer

/**
 * A Modifier extension that adds scale and shimmer effects on focus and press.
 *
 * @param focusScale The scale factor when focused (default: 1.05f)
 * @param pressedScale The scale factor when pressed (default: 1.1f)
 * @param enableShimmer Whether to enable shimmer effect on focus/press (default: true)
 * @param interactionSource Optional interaction source to use. If not provided, a new one will be created.
 * @return A Modifier with scale and shimmer effects applied
 */
fun Modifier.focusScaleEffect(
    focusScale: Float = 1.05f,
    pressedScale: Float = 1.1f,
    enableShimmer: Boolean = false,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by source.collectIsFocusedAsState()
    val isPressed by source.collectIsPressedAsState()
    val isHovered by source.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> pressedScale
            isFocused -> focusScale
            isHovered -> focusScale
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "focus scale effect"
    )

    this
        .scale(scale)
        .then(
            if (enableShimmer && (isFocused || isPressed || isHovered)) {
                Modifier.shimmer()
            } else {
                Modifier
            }
        )
}
