package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import coil3.Image

/** One content version shared by the renderer and its untransformed overlays. */
@Stable
class MediaItemState(val data: Any, fitSize: Boolean = false) {
    var displayedImage: Image? by mutableStateOf(null)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var error: String? by mutableStateOf(null)
        internal set
    var contentScale by mutableStateOf(if (fitSize) ContentScale.Fit else ContentScale.Crop)
        internal set
    var showMetadata by mutableStateOf(false)
    var showActions by mutableStateOf(false)
    var interactionVersion by mutableIntStateOf(0)
        private set
    internal var metadata: MediaMetadata? by mutableStateOf(null)
    val ready: Boolean get() = displayedImage != null && !loading && error == null
    private var owners = 0
    internal val retained: Boolean get() = owners > 0
    internal var onLastRelease: (() -> Unit)? = null

    internal fun retain() { owners++ }
    internal fun release() {
        check(owners > 0)
        if (--owners == 0) {
            displayedImage = null
            metadata = null
            loading = false
            error = null
            showMetadata = false
            showActions = false
            onLastRelease?.invoke()
        }
    }

    fun interact(config: MediaOverlayConfig, toggleMetadata: Boolean = false) {
        interactionVersion++
        if (config.metadata && toggleMetadata) showMetadata = !showMetadata
        if (config.aiGenerate) showActions = true
    }

    internal fun loading() {
        displayedImage = null
        metadata = null
        loading = true
        error = null
    }

    internal fun loaded(image: Image, metadata: MediaMetadata? = null) {
        displayedImage = image
        this.metadata = metadata
        loading = false
        error = null
    }

    internal fun failed(message: String) {
        displayedImage = null
        metadata = null
        loading = false
        error = message
    }
}

@Composable
internal fun RetainMediaItemState(state: MediaItemState) {
    DisposableEffect(state) {
        state.retain()
        onDispose { state.release() }
    }
}

@Composable
fun rememberMediaItemState(data: Any, fitSize: Boolean = false): MediaItemState =
    remember(data) { MediaItemState(data, fitSize) }.also { state ->
        LaunchedEffect(state, fitSize) {
            state.contentScale = if (fitSize) ContentScale.Fit else ContentScale.Crop
        }
    }

/** A page occurrence owns presentation state; equal media in another page loads independently. */
private data class MediaPageKey(val page: Int, val data: Any)

/** Keeps each pager entry attached to its viewport overlay, including outgoing overlay fades. */
internal class MediaItemStateStore(private val capacity: Int = 32) {
    init { require(capacity > 0) }
    private val entries = linkedMapOf<MediaPageKey, MediaItemState>()
    fun get(page: Int, data: Any, fitSize: Boolean = false): MediaItemState {
        val key = MediaPageKey(page, data)
        val state = entries.remove(key) ?: MediaItemState(data, fitSize).also {
            it.onLastRelease = { trim() }
        }
        entries[key] = state
        // The returned state is about to be retained when composition commits.
        trim(keep = key)
        return state
    }

    private fun trim(keep: MediaPageKey? = null) {
        while (entries.size > capacity) {
            // Active renderers/fades may temporarily exceed capacity. Evicting them
            // would disconnect the next viewport lookup from the existing renderer.
            val unused = entries.entries.firstOrNull { it.key != keep && !it.value.retained } ?: break
            entries.remove(unused.key)
            unused.value.onLastRelease = null
        }
    }
}

@Composable
internal fun rememberMediaItemStateStore(fitSize: Boolean): MediaItemStateStore =
    remember(fitSize) { MediaItemStateStore() }
