package com.alpha.showcase.common.weather

import com.alpha.showcase.common.utils.Log

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val city: String? = null,
    val regionName: String? = null,
    val country: String? = null
) {
    val displayName: String
        get() = listOfNotNull(city, regionName, country).distinct().joinToString(" · ")
}

expect fun hasLocationPermission(): Boolean

expect fun requestLocationPermission()

expect suspend fun getNativeLocationOrNull(): LocationResult?

object LocationProvider {
    suspend fun getCurrentLocation(): LocationResult? {
        if (!hasLocationPermission()) {
            return null
        }

        return runCatching { getNativeLocationOrNull() }
            .onFailure { Log.w("LocationProvider", "Native location failed: ${it.message}") }
            .getOrNull()
    }
}
