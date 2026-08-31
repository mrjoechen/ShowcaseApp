package com.alpha.showcase.common.ui.play

internal fun shouldKeepScreenOnDuringPlayback(
    isDesktop: Boolean,
    autoFullscreen: Boolean,
    isWeb: Boolean = false,
): Boolean = (!isDesktop && !isWeb) || autoFullscreen
