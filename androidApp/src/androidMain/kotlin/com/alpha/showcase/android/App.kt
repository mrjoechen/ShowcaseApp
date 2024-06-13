package com.alpha.showcase.android

import AndroidApp
import android.app.Application

class App: Application() {
  override fun onCreate() {
    super.onCreate()
    AndroidApp = this
  }
}