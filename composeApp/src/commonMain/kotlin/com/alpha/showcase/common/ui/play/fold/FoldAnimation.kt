package com.alpha.showcase.common.ui.play.fold

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

internal const val DUO_FOLD_DURATION_MILLIS = 2400

// The projected fold already changes its apparent speed with the rotation angle.
// An additional fast-out/slow-in curve makes the second face linger on a slow turn.
internal fun duoFoldAnimationSpec(durationMillis: Int = DUO_FOLD_DURATION_MILLIS) =
    tween<Float>(durationMillis = durationMillis, easing = LinearEasing)
