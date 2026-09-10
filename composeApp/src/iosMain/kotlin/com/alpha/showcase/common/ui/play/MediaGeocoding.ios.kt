@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.alpha.showcase.common.ui.play

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import platform.Foundation.NSLocale
import kotlin.coroutines.resume

internal actual suspend fun nativePhotoAddress(coordinates: PhotoCoordinates, language: String): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val geocoder = CLGeocoder()
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.reverseGeocodeLocation(
                CLLocation(coordinates.latitude, coordinates.longitude),
                preferredLocale = NSLocale(localeIdentifier = language),
            ) { placemarks, error ->
                val place = placemarks?.firstOrNull() as? CLPlacemark
                val address = if (error != null || place == null) null else listOfNotNull(
                    place.country, place.administrativeArea, place.subAdministrativeArea,
                    place.locality, place.subLocality, place.thoroughfare, place.subThoroughfare, place.name,
                ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                if (continuation.isActive) continuation.resume(address)
            }
        }
    }
