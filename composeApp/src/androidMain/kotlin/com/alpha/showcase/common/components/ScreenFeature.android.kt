package com.alpha.showcase.common.components

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

class AndroidScreenFeature(private val activity: android.app.Activity) : ScreenFeature {
    override fun keepScreenOn(on: Boolean) {
        if (on) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun fullScreen() {
        activity.hideSystemUI()
    }

    override fun exitFullScreen() {
        activity.showSystemUI()
    }
}

fun Activity.hideSystemUI() {
    val container = window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, container).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

fun Activity.showSystemUI() {
    val container = window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(window, container).show(WindowInsetsCompat.Type.systemBars())
}