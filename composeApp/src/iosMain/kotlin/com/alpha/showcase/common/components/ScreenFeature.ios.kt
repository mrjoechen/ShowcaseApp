package com.alpha.showcase.common.components

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication

private const val STATUS_BAR_VISIBILITY_NOTIFICATION = "ShowcaseStatusBarVisibilityDidChange"
private const val STATUS_BAR_HIDDEN_USER_INFO_KEY = "hidden"

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
        postStatusBarVisibility(hidden = true)
    }

    override fun exitFullScreen() {
        postStatusBarVisibility(hidden = false)
    }

    private fun postStatusBarVisibility(hidden: Boolean) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = STATUS_BAR_VISIBILITY_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(STATUS_BAR_HIDDEN_USER_INFO_KEY to hidden)
        )
    }
}
