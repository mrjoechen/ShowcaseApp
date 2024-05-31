package com.alpha.showcase.android

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Activity.hideSystemUI() {
  val container = window.decorView
    .findViewById<ViewGroup>(android.R.id.content)
  WindowCompat.setDecorFitsSystemWindows(window, false)
  WindowInsetsControllerCompat(window, container).let {controller ->
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