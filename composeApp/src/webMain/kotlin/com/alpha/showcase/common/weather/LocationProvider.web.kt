package com.alpha.showcase.common.weather

actual fun hasLocationPermission(): Boolean = true

actual fun requestLocationPermission() = Unit

actual suspend fun getNativeLocationOrNull(): LocationResult? = null
