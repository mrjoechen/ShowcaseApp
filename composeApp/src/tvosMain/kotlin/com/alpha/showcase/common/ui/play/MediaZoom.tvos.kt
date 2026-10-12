package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/** Remote select exposes the shared media controls; pinch gestures are not available on TV. */
@Composable
internal actual fun Modifier.mediaZoom(state: MediaItemState, active: Boolean, onTap: () -> Unit): Modifier =
    semantics { mediaZoomScale = 1f }.clickable(enabled = active, onClick = onTap)
