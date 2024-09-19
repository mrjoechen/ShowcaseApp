package com.alpha.showcase.common.components

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

class WasmScreenFeature : ScreenFeature {

    override fun keepScreenOn(on: Boolean) {
        if (on) {
            requestWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun requestWakeLock() {

    }

    private fun releaseWakeLock() {
    }

    override fun fullScreen() {
        js("document.documentElement.requestFullscreen()")
    }

    override fun exitFullScreen() {
        js("document.exitFullscreen()")
    }
}