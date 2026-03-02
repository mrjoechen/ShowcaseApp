package com.alpha.showcase.common.weather

actual fun hasLocationPermission(): Boolean = true

actual fun requestLocationPermission() {
    // No runtime permission required on desktop.
}

actual suspend fun getNativeLocationOrNull(): LocationResult? = null
