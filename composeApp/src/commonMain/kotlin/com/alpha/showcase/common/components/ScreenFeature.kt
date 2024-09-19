package com.alpha.showcase.common.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

expect interface ScreenFeature {
    fun keepScreenOn(on: Boolean)
    fun fullScreen()
    fun exitFullScreen()
}

@Composable
fun ScreenControlEffect(
    screenFeature: ScreenFeature,
    keepScreenOn: Boolean = false,
    fullScreen: Boolean = false
) {
    DisposableEffect(keepScreenOn, fullScreen) {
        screenFeature.keepScreenOn(keepScreenOn)
        if (fullScreen) {
            screenFeature.fullScreen()
        } else {
            screenFeature.exitFullScreen()
        }

        onDispose {
            screenFeature.keepScreenOn(false)
            screenFeature.exitFullScreen()
        }
    }
}