package com.alpha.showcase.common.ui.play

import AndroidApp
import android.location.Geocoder
import android.location.Address
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

internal actual suspend fun nativePhotoAddress(coordinates: PhotoCoordinates, language: String): String? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(AndroidApp.applicationContext, Locale.forLanguageTag(language))
        val addresses = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine<List<Address>> { continuation ->
                geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }
                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1).orEmpty()
        }
        addresses.firstOrNull()?.let { address ->
            (0..address.maxAddressLineIndex).mapNotNull(address::getAddressLine).distinct().joinToString(" ")
        }
    }
