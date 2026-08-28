package com.alpha.showcase.common.weather

import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

@Serializable
private data class CachedLocation(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val city: String? = null,
    val regionName: String? = null,
    val country: String? = null
)

private val locationJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

object LocationProvider {
    private val locationCacheStore = objectStoreOf<String>("ip_location_cache")

    suspend fun getCurrentLocation(): LocationResult? {
        val nativeLocation = runCatching { getNativeLocationOrNull() }
            .onFailure { Log.w("LocationProvider", "Native location failed: ${it.message}") }
            .getOrNull()
        if (nativeLocation != null) {
            cacheLocation(nativeLocation)
            return nativeLocation
        }

        return getCachedLocation()
    }

    private suspend fun cacheLocation(location: LocationResult) {
        runCatching {
            val data = CachedLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                provider = location.provider,
                city = location.city,
                regionName = location.regionName,
                country = location.country
            )
            locationCacheStore.set(locationJson.encodeToString(CachedLocation.serializer(), data))
            Log.i("LocationProvider", "Cache location: $location")
        }.onFailure {
            Log.w("LocationProvider", "Cache location failed: ${it.message}")
        }
    }

    private suspend fun getCachedLocation(): LocationResult? {
        return runCatching {
            val cached = locationCacheStore.get() ?: return@runCatching null
            val data = locationJson.decodeFromString(CachedLocation.serializer(), cached)
            LocationResult(
                latitude = data.latitude,
                longitude = data.longitude,
                provider = "cache",
                city = data.city,
                regionName = data.regionName,
                country = data.country
            )
        }.onFailure {
            Log.w("LocationProvider", "Load cached location failed: ${it.message}")
        }.getOrNull()
    }
}
