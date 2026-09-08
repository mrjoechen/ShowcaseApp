package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

internal val MediaZoomScaleKey = SemanticsPropertyKey<Float>("MediaZoomScale")
internal var SemanticsPropertyReceiver.mediaZoomScale by MediaZoomScaleKey

/** Image-only gestures. A single tap never changes the layout's Fit/Crop setting. */
@Composable
internal expect fun Modifier.mediaZoom(state: MediaItemState, active: Boolean, onTap: () -> Unit): Modifier
