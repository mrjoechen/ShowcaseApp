package com.alpha.showcase.android

import BOOT_LAUNCHER_PREFS
import KEY_LAUNCH_ON_BOOT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICKBOOT_POWERON &&
            action != ACTION_HTC_QUICKBOOT_POWERON
        ) {
            return
        }

        val prefs = context.getSharedPreferences(BOOT_LAUNCHER_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_LAUNCH_ON_BOOT, false)) {
            return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching {
            context.startActivity(launchIntent)
        }.onFailure {
            it.printStackTrace()
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
