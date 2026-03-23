package com.alpha.showcase.common.storage

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

private const val KEY = "showcase_durable_device_id"

actual fun getDurableDeviceId(): String? {
    return localStorage[KEY]
}

actual fun saveDurableDeviceId(deviceId: String) {
    localStorage[KEY] = deviceId
}
