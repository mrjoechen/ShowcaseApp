package com.alpha.showcase.common.weather

import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val city: String? = null,
    val regionName: String? = null,
    val country: String? = null,
    val capturedAtEpochMillis: Long? = null,
) {
    val displayName: String
        get() = listOfNotNull(city, regionName, country).distinct().joinToString(" · ")
}

expect fun hasLocationPermission(): Boolean

expect fun requestLocationPermission()

expect suspend fun getNativeLocationOrNull(): LocationResult?

internal const val LOCATION_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1_000L

@Serializable
private data class PersistedLocationCache(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val regionName: String? = null,
    val country: String? = null,
    val capturedAtEpochMillis: Long? = null,
)

internal data class LocationCacheEntry(
    val location: LocationResult,
    val capturedAtEpochMillis: Long,
    val requiresMigration: Boolean = false,
)

private val locationJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal fun isLocationCacheFresh(
    capturedAtEpochMillis: Long,
    nowEpochMillis: Long,
    ttlMillis: Long = LOCATION_CACHE_TTL_MILLIS,
): Boolean {
    val ageMillis = nowEpochMillis - capturedAtEpochMillis
    return ageMillis >= 0L && ageMillis < ttlMillis
}

internal fun decodeLocationCache(
    value: String,
): LocationCacheEntry {
    val persisted = locationJson.decodeFromString(PersistedLocationCache.serializer(), value)
    val capturedAtEpochMillis = persisted.capturedAtEpochMillis ?: Long.MIN_VALUE
    return LocationCacheEntry(
        location = LocationResult(
            latitude = persisted.latitude,
            longitude = persisted.longitude,
            provider = "cache",
            city = persisted.city,
            regionName = persisted.regionName,
            country = persisted.country,
            capturedAtEpochMillis = persisted.capturedAtEpochMillis,
        ),
        capturedAtEpochMillis = capturedAtEpochMillis,
        requiresMigration = persisted.capturedAtEpochMillis == null,
    )
}

internal fun encodeLocationCache(entry: LocationCacheEntry): String {
    val location = entry.location
    return locationJson.encodeToString(
        PersistedLocationCache.serializer(),
        PersistedLocationCache(
            latitude = location.latitude,
            longitude = location.longitude,
            city = location.city,
            regionName = location.regionName,
            country = location.country,
            capturedAtEpochMillis = entry.capturedAtEpochMillis,
        ),
    )
}

internal class CachedLocationResolver(
    private val hasLocationPermission: () -> Boolean = { true },
    private val getNativeLocation: suspend () -> LocationResult?,
    private val saveToCache: suspend (LocationCacheEntry) -> Unit,
    private val loadFromCache: suspend () -> LocationCacheEntry?,
    private val deleteCache: suspend () -> Unit = {},
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun getLocation(): LocationResult? {
        if (!hasLocationPermission()) return null

        val nativeLocation = try {
            getNativeLocation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("LocationProvider", "Native location failed: ${error.message}")
            null
        }

        // Permission can change while the native provider is suspended.
        if (!hasLocationPermission()) return null

        val nativeReadTime = nowEpochMillis()
        val nativeCaptureTime = nativeLocation?.capturedAtEpochMillis ?: nativeReadTime
        if (
            nativeLocation != null &&
            isLocationCacheFresh(nativeCaptureTime, nativeReadTime)
        ) {
            val cacheEntry = LocationCacheEntry(
                location = nativeLocation,
                capturedAtEpochMillis = nativeCaptureTime,
            )
            try {
                saveToCache(cacheEntry)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("LocationProvider", "Cache location failed: ${error.message}")
            }

            // A cache write can also suspend. Never expose or retain its result after revocation.
            if (!hasLocationPermission()) {
                deleteCacheAfterPermissionRevocation()
                return null
            }
            return nativeLocation
        }

        // Do not begin reading a previous location after permission was revoked.
        if (!hasLocationPermission()) return null
        val cachedLocation = try {
            loadFromCache()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("LocationProvider", "Load cached location failed: ${error.message}")
            null
        } ?: return null

        // A cache read can suspend. Re-check before using or migrating its contents.
        if (!hasLocationPermission()) return null
        if (cachedLocation.requiresMigration) {
            // The age of a legacy cache is unknowable. Treating the read/migration time as its
            // capture time would make an arbitrarily old coordinate appear fresh for 24 hours.
            deleteCacheSafely()
            return null
        }
        if (!isLocationCacheFresh(cachedLocation.capturedAtEpochMillis, nowEpochMillis())) return null

        return if (hasLocationPermission()) cachedLocation.location else null
    }

    private suspend fun deleteCacheAfterPermissionRevocation() {
        deleteCacheSafely()
    }

    private suspend fun deleteCacheSafely() {
        try {
            deleteCache()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("LocationProvider", "Delete cached location failed: ${error.message}")
        }
    }
}

object LocationProvider {
    // Keep the historical key so earlier cache data can receive a one-time timestamp migration.
    private val locationCacheStore = objectStoreOf<String>("ip_location_cache")
    private val resolver = CachedLocationResolver(
        hasLocationPermission = ::hasLocationPermission,
        getNativeLocation = ::getNativeLocationOrNull,
        saveToCache = ::cacheLocation,
        loadFromCache = ::getCachedLocation,
        deleteCache = { locationCacheStore.delete() },
        nowEpochMillis = ::systemClockMillis,
    )

    suspend fun getCurrentLocation(): LocationResult? = resolver.getLocation()

    private suspend fun cacheLocation(entry: LocationCacheEntry) {
        locationCacheStore.set(encodeLocationCache(entry))
    }

    private suspend fun getCachedLocation(): LocationCacheEntry? {
        val cachedValue = locationCacheStore.get() ?: return null
        return decodeLocationCache(cachedValue)
    }
}

private fun systemClockMillis(): Long = Clock.System.now().toEpochMilliseconds()
