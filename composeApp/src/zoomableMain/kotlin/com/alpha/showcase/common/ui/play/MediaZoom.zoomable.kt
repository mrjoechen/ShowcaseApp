package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import net.engawapg.lib.zoomable.*

@Composable
internal actual fun Modifier.mediaZoom(state: MediaItemState, active: Boolean, onTap: () -> Unit): Modifier {
    val zoom = remember(state) { ZoomState(maxScale = 5f) }
    LaunchedEffect(state.displayedImage, state.contentScale) {
        // Zoomable fits contentSize into its viewport. Crop already fills the viewport.
        zoom.setContentSize(if (state.contentScale == ContentScale.Fit) {
            state.displayedImage?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Zero
        } else Size.Zero)
        zoom.reset()
    }
    LaunchedEffect(active) { if (!active) zoom.reset() }
    return semantics {
        mediaZoomScale = zoom.scale
        if (active) onClick { onTap(); true }
    }.zoomable(
        zoomState = zoom,
        zoomEnabled = active && state.ready,
        scrollGesturePropagation = ScrollGesturePropagation.NotZoomed,
        onTap = { if (active) onTap() },
    )
}
