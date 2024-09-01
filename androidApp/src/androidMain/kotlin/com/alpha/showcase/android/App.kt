package com.alpha.showcase.android

import AndroidApp
import android.app.Application
import com.alpha.showcase.common.Startup

class App: Application() {
  override fun onCreate() {
    super.onCreate()
    AndroidApp = this
    Startup.run()
  }
}