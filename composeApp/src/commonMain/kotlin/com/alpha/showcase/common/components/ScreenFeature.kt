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
    DisposableEffect(screenFeature, keepScreenOn, fullScreen) {
        screenFeature.keepScreenOn(keepScreenOn)
        if (fullScreen) {
            screenFeature.fullScreen()
        }

        onDispose {
            screenFeature.keepScreenOn(false)
            if (fullScreen) {
                screenFeature.exitFullScreen()
            }
        }
    }
}
