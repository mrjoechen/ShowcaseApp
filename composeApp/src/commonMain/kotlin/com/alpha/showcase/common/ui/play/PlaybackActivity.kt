package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CoroutineScope

/** A single visibility contract for timers, video, animated images and AI requests. */
internal val LocalPlaybackActive = staticCompositionLocalOf { true }

@Composable
internal fun rememberPlaybackActive(): Boolean {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return LocalPlaybackActive.current && lifecycle == Lifecycle.State.RESUMED
}

@Composable
internal fun PlaybackEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val active = rememberPlaybackActive()
    LaunchedEffect(*keys, active) { if (active) block() }
}
