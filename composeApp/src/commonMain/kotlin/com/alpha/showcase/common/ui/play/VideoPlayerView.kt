package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

internal val LocalVideoMuted = staticCompositionLocalOf { false }

@Composable
internal expect fun PlatformVideoPlayer(
    data: Any,
    modifier: Modifier,
    contentScale: ContentScale,
    active: Boolean,
    muted: Boolean = false,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onCompleted: () -> Unit,
)
