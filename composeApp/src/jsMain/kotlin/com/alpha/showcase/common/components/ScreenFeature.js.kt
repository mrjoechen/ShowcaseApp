package com.alpha.showcase.common.components

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

class WebScreenFeature : ScreenFeature {
    private val wakeLock = createBrowserWakeLockController()

    override fun keepScreenOn(on: Boolean) = wakeLock.setEnabled(on)

    override fun fullScreen() {
        requestDocumentFullscreen()
    }

    override fun exitFullScreen() {
        exitDocumentFullscreen()
    }
}
