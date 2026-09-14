package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared dwell indicator for every playback style; timer reads never recompose media content. */
@Composable
internal fun PlaybackProgressBar(
    elapsedMillis: () -> Float,
    durationMillis: Long,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // Milliseconds avoid spring threshold snapping at long display intervals.
    val elapsed = elapsedMillis()
    val animatedElapsed = animateFloatAsState(
        targetValue = elapsed,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "Playback dwell progress",
    )
    AnimatedVisibility(
        visible = visible && elapsed > 0f,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LinearProgressIndicator(
            progress = { (animatedElapsed.value / durationMillis.coerceAtLeast(1L)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
    }
}
