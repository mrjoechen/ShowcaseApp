package com.alpha.showcase.common.components

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification

private const val STATUS_BAR_VISIBILITY_NOTIFICATION = "ShowcaseStatusBarVisibilityDidChange"
private const val STATUS_BAR_HIDDEN_USER_INFO_KEY = "hidden"

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

object AppleScreenFeature : ScreenFeature {
    private var keepScreenOnRequested = false

    // Re-apply on activation so playback keeps the screen on after the app returns to the foreground.
    private val didBecomeActiveObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        if (keepScreenOnRequested) UIApplication.sharedApplication.idleTimerDisabled = true
    }

    override fun keepScreenOn(on: Boolean) {
        keepScreenOnRequested = on
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
