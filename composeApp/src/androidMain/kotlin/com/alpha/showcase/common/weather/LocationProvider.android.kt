package com.alpha.showcase.common.weather

import AndroidApp
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.alpha.showcase.common.utils.Log
import currentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

actual fun hasLocationPermission(): Boolean {
    val context = runCatching { AndroidApp.applicationContext }.getOrNull() ?: return false
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

actual fun requestLocationPermission() {
    val activity = currentActivity ?: return
    if (hasLocationPermission()) return

    activity.runOnUiThread {
        runCatching {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1203
            )
        }.onFailure {
            Log.w("LocationProvider", "Failed to request permission: ${it.message}")
        }
    }
}

actual suspend fun getNativeLocationOrNull(): LocationResult? {
    val context = runCatching { AndroidApp.applicationContext }.getOrNull() ?: return null
    if (!hasLocationPermission()) return null

    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    getLastKnownLocation(locationManager)?.let { location ->
        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            provider = "last_known"
        )
    }

    getLocationFromProvider(locationManager, LocationManager.NETWORK_PROVIDER)?.let { location ->
        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            provider = LocationManager.NETWORK_PROVIDER
        )
    }

    getLocationFromProvider(locationManager, LocationManager.GPS_PROVIDER)?.let { location ->
        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            provider = LocationManager.GPS_PROVIDER
        )
    }

    return null
}

private suspend fun getLocationFromProvider(
    locationManager: LocationManager,
    provider: String
): Location? {
    if (!runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)) {
        return null
    }

    return try {
        withTimeout(10_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        @Suppress("DEPRECATION")
                        locationManager.requestSingleUpdate(
                            provider,
                            { location ->
                                if (continuation.isActive) {
                                    continuation.resume(location)
                                }
                            },
                            null
                        )
                    } catch (e: SecurityException) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun getLastKnownLocation(locationManager: LocationManager): Location? {
    return runCatching {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        providers.asSequence()
            .filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }
            .mapNotNull { provider ->
                @Suppress("DEPRECATION")
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .firstOrNull()
    }.getOrNull()
}
