package com.alpha.showcase.common.ui.play

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The visible model can change without changing the page (for example, paging placeholders). */
internal data class ImagePrefetchWindow(val current: Any, val ready: Boolean, val next: Any?)

/** One speculative request; keep it alive when the user moves onto that same image. */
internal suspend fun prefetchImages(windows: Flow<ImagePrefetchWindow>, load: suspend (Any) -> Unit) = coroutineScope {
    var target: Any? = null
    var request: Job? = null
    val available = MutableStateFlow(true)
    // Completion also revisits the latest window: the visible image may have become
    // ready just before its prefetch finished decoding.
    windows.combine(available) { window, _ -> window }.collect { window ->
        if (target != window.current && target != window.next) {
            request?.cancelAndJoin()
            request = null
            target = null
        }
        if (window.ready && window.next != null && window.next != target && request?.isActive != true) {
            target = window.next
            available.value = false
            request = launch {
                try { load(window.next) }
                finally { available.value = true }
            }
        }
    }
}
