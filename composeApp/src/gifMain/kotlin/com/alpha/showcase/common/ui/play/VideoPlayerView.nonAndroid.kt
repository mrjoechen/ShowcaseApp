package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.media_error_unsupported_type

@Composable
internal actual fun PlatformVideoPlayer(
    data: Any,
    modifier: Modifier,
    contentScale: ContentScale,
    active: Boolean,
    muted: Boolean,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onCompleted: () -> Unit,
) {
    val unsupportedType = stringResource(Res.string.media_error_unsupported_type)
    LaunchedEffect(data) {
        onError(unsupportedType)
    }
}
