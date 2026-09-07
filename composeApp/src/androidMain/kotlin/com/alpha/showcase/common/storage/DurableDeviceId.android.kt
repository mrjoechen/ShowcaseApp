package com.alpha.showcase.common.storage

import AndroidApp
import android.content.Context
import androidx.core.content.edit

private const val PREF_NAME = "showcase_durable_device"
private const val KEY_DEVICE_ID = "durable_device_id"

actual fun getDurableDeviceId(): String? {
    val prefs = AndroidApp.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    // Generation and persistence belong to DurableDeviceIdProvider on every platform.
    return prefs.getString(KEY_DEVICE_ID, null)
}

actual fun saveDurableDeviceId(deviceId: String) {
    val prefs = AndroidApp.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(KEY_DEVICE_ID, deviceId) }
}
