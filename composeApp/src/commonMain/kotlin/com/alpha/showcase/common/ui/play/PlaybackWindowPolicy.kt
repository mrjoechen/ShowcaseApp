package com.alpha.showcase.common.ui.play

fun shouldOpenExternalPlaybackWindow(
    autoFullscreen: Boolean,
    hasExternalPlaybackWindow: Boolean
): Boolean = autoFullscreen && hasExternalPlaybackWindow

fun shouldFullscreenMainPlaybackWindow(
    autoFullscreen: Boolean,
    fullscreenRequested: Boolean,
    hasExternalPlaybackWindow: Boolean,
): Boolean = autoFullscreen && fullscreenRequested && !hasExternalPlaybackWindow
