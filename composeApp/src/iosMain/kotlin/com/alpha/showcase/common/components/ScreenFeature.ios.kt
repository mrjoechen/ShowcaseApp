package com.alpha.showcase.common.components

import platform.UIKit.UIApplication
import platform.UIKit.setStatusBarHidden

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

object IOSScreenFeature : ScreenFeature {
    override fun keepScreenOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }

    override fun fullScreen() {
        UIApplication.sharedApplication.setStatusBarHidden(true, true)
    }

    override fun exitFullScreen() {
        UIApplication.sharedApplication.setStatusBarHidden(false)
    }
}