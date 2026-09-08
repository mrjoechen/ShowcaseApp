package com.alpha.showcase.common.ui.play

import androidx.compose.ui.graphics.painter.Painter
import coil3.Image
import kotlinx.coroutines.CoroutineScope

/** Cached pixels are immutable; each viewport owns its animation and native resources. */
internal interface AnimatedMediaImage : Image {
    fun painter(scope: CoroutineScope, isPlaying: () -> Boolean): Painter
}
