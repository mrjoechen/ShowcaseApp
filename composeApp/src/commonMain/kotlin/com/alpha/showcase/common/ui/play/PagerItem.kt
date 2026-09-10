package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import com.alpha.showcase.common.ui.ext.buildImageRequest
import com.alpha.showcase.common.ui.ext.getSimpleMessage
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.view.LoadingIndicator

/** Media pixels and load/playback events only. Information and actions belong to MediaOverlays. */
@Composable
fun PagerItem(
    state: MediaItemState,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    readMetadata: Boolean = true,
    onInteraction: (tap: Boolean) -> Unit = {},
    onImageDimensionsAvailable: (width: Int, height: Int) -> Unit = { _, _ -> },
    onComplete: (Any) -> Unit = {},
) {
    RetainMediaItemState(state)
    val data = state.data
    if (!data.isImage()) {
        // This KMP renderer has no video/Live Photo backend yet.
        Box(modifier) { DataNotFoundAnim("Unsupported data") }
        return
    }
    val imageLoader = LocalImageLoader.current ?: coil3.SingletonImageLoader.get(LocalPlatformContext.current)
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = rememberUpdatedState(active && LocalPlaybackActive.current && lifecycleState == Lifecycle.State.RESUMED)
    val transform = remember(scope) {
        { result: AsyncImagePainter.State ->
            val image = (result as? AsyncImagePainter.State.Success)?.result?.image
            if (result is AsyncImagePainter.State.Success && image is AnimatedMediaImage) {
                result.copy(painter = image.painter(scope) { playing.value })
            } else result
        }
    }
    val fit = state.contentScale == ContentScale.Fit
    val request = remember(context, state, readMetadata, fit) {
        val base = if (readMetadata) buildMediaImageRequest(context, data) else buildImageRequest(context, data)
        if (fit) base.newBuilder().apply { prepareBlurSource() }.build() else base
    }
    Box(modifier.clipToBounds()) {
        if (fit) {
            state.displayedImage?.let { MediaBlurBackground(it, Modifier.matchParentSize()) }
        }
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            transform = transform,
            onState = {
                when (it) {
                    is AsyncImagePainter.State.Success -> {
                        state.loaded(it.result.image, it.result.mediaMetadata)
                        onImageDimensionsAvailable(it.result.image.width, it.result.image.height)
                        onComplete(data)
                    }
                    is AsyncImagePainter.State.Error -> {
                        state.failed(it.result.throwable.getSimpleMessage())
                        onComplete(data)
                    }
                    is AsyncImagePainter.State.Loading -> state.loading()
                    is AsyncImagePainter.State.Empty -> Unit
                }
            },
            contentScale = state.contentScale,
            modifier = Modifier.fillMaxSize().mediaZoom(state, active) { onInteraction(true) },
        )
        AnimatedVisibility(state.loading, enter = fadeIn(), exit = fadeOut()) { LoadingIndicator() }
        AnimatedVisibility(state.error != null, enter = fadeIn(), exit = fadeOut()) {
            DataNotFoundAnim(state.error.orEmpty())
        }
    }
}
