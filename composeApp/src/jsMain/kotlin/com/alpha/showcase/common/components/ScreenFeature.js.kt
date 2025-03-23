package com.alpha.showcase.common.components

val playWithFullscreen = true
val keepScreenOn = true
actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

class WebScreenFeature : ScreenFeature {
    private var wakeLock: dynamic = null

    override fun keepScreenOn(on: Boolean) {
        if (!keepScreenOn) return
        if (on) {
            requestWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun requestWakeLock() {
//        val lock = js("navigator.wakeLock.request('screen')").await()
//        wakeLock = lock
    }

    private fun releaseWakeLock() {
//        wakeLock?.release()
//        wakeLock = null
    }

    override fun fullScreen() {
        if (!playWithFullscreen) return
        js("document.documentElement.requestFullscreen()")
    }

    override fun exitFullScreen() {
        js("document.exitFullscreen()")
    }
}