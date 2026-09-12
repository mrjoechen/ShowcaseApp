package com.alpha.showcase.common.ui.play.fold

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class FoldDirection { Forward, Backward }

internal fun boundedFoldProgress(progress: Float): Float =
    if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

/** Keeps the perspective-expanded flap inside the viewport, with a little breathing room. */
internal fun foldRetreatScale(progress: Float): Float {
    val p = boundedFoldProgress(progress)
    if (p == 0f || p == 1f) return 1f
    // Fold height expands by 1 / (1 - sin(angle) / 5). Retreating by 26% at
    // edge-on exceeds the 20% needed to fit, and follows the same angle both ways.
    return 1f - .26f * sin(PI.toFloat() * p)
}

internal data class FoldFrame(
    val opening: Boolean,
    val movingLeft: Boolean,
    val angle: Float,
    val motion: Float,
    val outerX: Float,
    val outerTop: Float,
)

/** Camera distance scales with the image, so portrait and landscape folds have the same depth. */
internal fun foldFrame(progress: Float, direction: FoldDirection, width: Float, height: Float): FoldFrame {
    val p = boundedFoldProgress(progress)
    val opening = p >= .5f
    val angle = if (opening) (1f - p) * 180f else p * 180f
    val radians = angle * PI.toFloat() / 180f
    val perspective = 1f / (1f - sin(radians) / 5f)
    val movingLeft = (direction == FoldDirection.Forward) != opening
    val halfWidth = width / 2f
    val extent = halfWidth * cos(radians).coerceAtLeast(0f) * perspective
    val t = angle / 90f
    return FoldFrame(
        opening, movingLeft, angle, t * t * (3f - 2f * t),
        halfWidth + if (movingLeft) -extent else extent,
        height * (1f - perspective) / 2f,
    )
}
