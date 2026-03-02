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
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

private const val IOS_LOCATION_TIMEOUT_MS = 10_000L

private val permissionLocationManager = CLLocationManager()
private var permissionDelegate: PermissionDelegate? = null

@Volatile
private var isPermissionSystemInitialized: Boolean = false

// CLLocationManager.delegate is weak, keep request delegates strongly referenced.
private val activeLocationDelegates = mutableSetOf<NSObject>()

// Optional app startup hook.
fun initializeLocationPermissionSystem() {
    dispatch_async(dispatch_get_main_queue()) {
        initializePermissionSystemOnMain()
    }
}

private fun initializePermissionSystemOnMain() {
    if (isPermissionSystemInitialized) return
    val delegate = permissionDelegate ?: PermissionDelegate().also { permissionDelegate = it }
    permissionLocationManager.delegate = delegate
    isPermissionSystemInitialized = true
}

private suspend fun ensurePermissionSystemInitialized() {
    if (isPermissionSystemInitialized) return
    suspendCancellableCoroutine<Unit> { continuation ->
        dispatch_async(dispatch_get_main_queue()) {
            initializePermissionSystemOnMain()
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }
}

actual fun hasLocationPermission(): Boolean {
    return when (permissionLocationManager.authorizationStatus) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse -> true

        else -> false
    }
}

actual fun requestLocationPermission() {
    if (hasLocationPermission()) return

    dispatch_async(dispatch_get_main_queue()) {
        initializePermissionSystemOnMain()
        permissionLocationManager.requestWhenInUseAuthorization()
    }
}

private class PermissionDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        // Intentionally left empty: authorizationStatus is checked on demand.
        // Keeping this callback implemented follows Apple guidance.
    }
}

actual suspend fun getNativeLocationOrNull(): LocationResult? {
    ensurePermissionSystemInitialized()
    if (!hasLocationPermission()) {
        requestLocationPermission()
        return null
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
                    cleanupLocationRequest(manager, this)
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: NSError
                ) {
                    if (!continuation.isActive) return
                    continuation.resume(null)
                    cleanupLocationRequest(manager, this)
                }
            }

            continuation.invokeOnCancellation {
                cleanupLocationRequest(manager, delegate)
            }

            dispatch_async(dispatch_get_main_queue()) {
                if (!continuation.isActive) return@dispatch_async
                activeLocationDelegates.add(delegate)
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

private fun cleanupLocationRequest(manager: CLLocationManager, delegate: NSObject) {
    dispatch_async(dispatch_get_main_queue()) {
        manager.stopUpdatingLocation()
        manager.delegate = null
        activeLocationDelegates.remove(delegate)
    }
}
