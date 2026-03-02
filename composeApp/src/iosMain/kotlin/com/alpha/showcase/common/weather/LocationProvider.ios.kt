@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.weather

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val IOS_LOCATION_TIMEOUT_MS = 10_000L

private val permissionLocationManager = CLLocationManager()

actual fun hasLocationPermission(): Boolean {
    return when (CLLocationManager.authorizationStatus()) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse -> true

        else -> false
    }
}

actual fun requestLocationPermission() {
    if (!CLLocationManager.locationServicesEnabled()) return
    if (hasLocationPermission()) return
    dispatch_async(dispatch_get_main_queue()) {
        permissionLocationManager.requestWhenInUseAuthorization()
    }
}

actual suspend fun getNativeLocationOrNull(): LocationResult? {
    if (!CLLocationManager.locationServicesEnabled()) return null
    if (!hasLocationPermission()) {
        requestLocationPermission()
        return null
    }

    permissionLocationManager.location?.let { location ->
        return location.toLocationResult(provider = "ios_last_known")
    }

    return withTimeoutOrNull(IOS_LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val manager = CLLocationManager()

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>
                ) {
                    if (!continuation.isActive) return

                    val location = didUpdateLocations.firstOrNull() as? CLLocation
                    continuation.resume(location?.toLocationResult(provider = "ios"))
                    manager.stopUpdatingLocation()
                    manager.delegate = null
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: NSError
                ) {
                    if (!continuation.isActive) return
                    continuation.resume(null)
                    manager.stopUpdatingLocation()
                    manager.delegate = null
                }
            }

            continuation.invokeOnCancellation {
                manager.stopUpdatingLocation()
                manager.delegate = null
            }

            dispatch_async(dispatch_get_main_queue()) {
                manager.delegate = delegate
                manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
                manager.requestLocation()
            }
        }
    }
}

private fun CLLocation.toLocationResult(provider: String): LocationResult {
    val coord = coordinate
    return LocationResult(
        latitude = coord.useContents { latitude },
        longitude = coord.useContents { longitude },
        provider = provider
    )
}
