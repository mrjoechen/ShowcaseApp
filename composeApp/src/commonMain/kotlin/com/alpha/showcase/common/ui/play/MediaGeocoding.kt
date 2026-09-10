package com.alpha.showcase.common.ui.play

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal data class PhotoCoordinates(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** Native geocoding uses the photo's coordinates, never requests the device's location. */
internal expect suspend fun nativePhotoAddress(coordinates: PhotoCoordinates, language: String): String?

internal class PhotoAddressResolver(
    private val resolve: suspend (PhotoCoordinates, String) -> String? = ::nativePhotoAddress,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Pair<PhotoCoordinates, String>, String>()

    suspend fun address(coordinates: PhotoCoordinates, language: String): String? {
        if (!coordinates.valid) return null
        return mutex.withLock {
            val key = coordinates to language
            cache[key]?.let { return@withLock it }
            val result = try { withTimeoutOrNull(8_000) { resolve(coordinates, language) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            result?.takeIf { it.isNotBlank() }?.also {
                if (cache.size >= 128) cache.remove(cache.keys.first())
                cache[key] = it
            }
        }
    }
}

internal val photoAddresses = PhotoAddressResolver()
