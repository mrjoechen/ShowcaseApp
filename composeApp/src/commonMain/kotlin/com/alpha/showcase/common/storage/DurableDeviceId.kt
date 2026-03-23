package com.alpha.showcase.common.storage

/**
 * Platform-specific durable device ID storage that survives app reinstalls when possible.
 *
 * - Android: Uses Settings.Secure.ANDROID_ID as seed, persists to SharedPreferences
 *   and a backup file in external storage.
 * - iOS: Uses Keychain which persists across reinstalls.
 * - Desktop: Uses a file in the user's home directory.
 * - Web: Uses localStorage.
 */
expect fun getDurableDeviceId(): String?

expect fun saveDurableDeviceId(deviceId: String)
