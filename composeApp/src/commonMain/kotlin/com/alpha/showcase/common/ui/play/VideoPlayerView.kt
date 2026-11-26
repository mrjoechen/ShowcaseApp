package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
internal expect fun PlatformVideoPlayer(
    data: Any,
    modifier: Modifier,
    contentScale: ContentScale,
    active: Boolean,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onCompleted: () -> Unit,
)
