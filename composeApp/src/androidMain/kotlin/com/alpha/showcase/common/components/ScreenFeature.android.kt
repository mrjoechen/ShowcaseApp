package com.alpha.showcase.common.components

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
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
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun fullScreen() {
        activity.hideStatusBar()
    }

    override fun exitFullScreen() {
        (activity as ComponentActivity).restoreStatusBar()
    }
}

fun Activity.hideStatusBar() {
    val container = window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
    WindowInsetsControllerCompat(window, container).let { controller ->
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun ComponentActivity.restoreStatusBar() {
    val container = window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
    enableEdgeToEdge()
    WindowInsetsControllerCompat(window, container).let { controller ->
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.isAppearanceLightStatusBars = false
    }
}
