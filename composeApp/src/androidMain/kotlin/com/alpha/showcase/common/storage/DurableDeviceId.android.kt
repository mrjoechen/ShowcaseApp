package com.alpha.showcase.common.storage

import AndroidApp
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val PREF_NAME = "showcase_durable_device"
private const val KEY_DEVICE_ID = "durable_device_id"

@OptIn(ExperimentalUuidApi::class)
actual fun getDurableDeviceId(): String? {
    val prefs = AndroidApp.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
    return Uuid.random().toString()
}

actual fun saveDurableDeviceId(deviceId: String) {
    val prefs = AndroidApp.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(KEY_DEVICE_ID, deviceId) }
}
