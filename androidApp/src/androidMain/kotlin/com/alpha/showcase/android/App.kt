package com.alpha.showcase.android

import AndroidApp
import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.alpha.showcase.common.Startup
import currentActivity

class App: Application() {
  override fun onCreate() {
    super.onCreate()
    AndroidApp = this
    Startup.run()

    registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity as androidx.activity.ComponentActivity
      }

      override fun onActivityStarted(activity: Activity) {
      }

      override fun onActivityResumed(activity: Activity) {
      }

      override fun onActivityPaused(activity: Activity) {
      }

      override fun onActivityStopped(activity: Activity) {
      }

      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
      }

      override fun onActivityDestroyed(activity: android.app.Activity) {
        if (currentActivity == activity) {
          currentActivity = null
        }
      }
    })
  }

  override fun onTerminate() {
    super.onTerminate()
    currentActivity = null
  }
}