package com.alpha.showcase.common.storage

import java.io.File

private const val DIR_NAME = ".showcase"
private const val FILE_NAME = "device_id"

private fun getDeviceIdFile(): File {
    val homeDir = System.getProperty("user.home")
    val dir = File(homeDir, DIR_NAME)
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return File(dir, FILE_NAME)
}

actual fun getDurableDeviceId(): String? {
    return try {
        val file = getDeviceIdFile()
        if (file.exists()) {
            file.readText().trim().takeIf { it.isNotEmpty() }
        } else null
    } catch (_: Exception) {
        null
    }
}

actual fun saveDurableDeviceId(deviceId: String) {
    try {
        val file = getDeviceIdFile()
        file.writeText(deviceId)
    } catch (_: Exception) {
        // Silently fail
    }
}
