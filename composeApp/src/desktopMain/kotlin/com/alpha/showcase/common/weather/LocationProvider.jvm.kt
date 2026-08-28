package com.alpha.showcase.common.weather

import com.alpha.showcase.common.IP_GEO_API_KEY
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

actual fun hasLocationPermission(): Boolean = true

actual fun requestLocationPermission() {
    // No runtime permission required on desktop.
}

internal class DesktopIpLocationResolver(
    private val fetchFromIp: suspend () -> LocationResult?,
    private val saveToCache: suspend (LocationResult) -> Unit,
    private val loadFromCache: suspend () -> LocationResult?,
) {
    suspend fun getLocation(): LocationResult? {
        val ipLocation = runCatching { fetchFromIp() }
            .onFailure { Log.w("LocationProvider", "IP geolocation failed: ${it.message}") }
            .getOrNull()

        if (ipLocation != null) {
            runCatching { saveToCache(ipLocation) }
                .onFailure { Log.w("LocationProvider", "Cache location failed: ${it.message}") }
            return ipLocation
        }

        return runCatching { loadFromCache() }
            .onFailure { Log.w("LocationProvider", "Load cached location failed: ${it.message}") }
            .getOrNull()
    }
}

@Serializable
private data class IpGeoLocation(
    @SerialName("country_name")
    val countryName: String? = null,
    @SerialName("state_prov")
    val stateProv: String? = null,
    val city: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
)

@Serializable
private data class IpGeoResponse(
    val location: IpGeoLocation? = null,
)

@Serializable
private data class CachedLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val regionName: String? = null,
    val country: String? = null,
)

private val locationJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private val locationHttpClient by lazy {
    HttpClient {
        expectSuccess = true
    }
}

private val locationCacheStore = objectStoreOf<String>("ip_location_cache")

private val desktopLocationResolver = DesktopIpLocationResolver(
    fetchFromIp = ::getLocationFromIp,
    saveToCache = ::cacheLocation,
    loadFromCache = ::getCachedLocation,
)

actual suspend fun getNativeLocationOrNull(): LocationResult? =
    desktopLocationResolver.getLocation()

private suspend fun getLocationFromIp(): LocationResult? = withTimeout(5_000) {
    val responseBody = locationHttpClient.get(
        "https://api.ipgeolocation.io/v3/ipgeo?apiKey=$IP_GEO_API_KEY"
    ).bodyAsText()
    val location = locationJson.decodeFromString<IpGeoResponse>(responseBody).location
        ?: return@withTimeout null
    val latitude = location.latitude?.toDoubleOrNull() ?: return@withTimeout null
    val longitude = location.longitude?.toDoubleOrNull() ?: return@withTimeout null

    LocationResult(
        latitude = latitude,
        longitude = longitude,
        provider = "ipgeolocation",
        city = location.city,
        regionName = location.stateProv,
        country = location.countryName,
    )
}

private suspend fun cacheLocation(location: LocationResult) {
    val cachedLocation = CachedLocation(
        latitude = location.latitude,
        longitude = location.longitude,
        city = location.city,
        regionName = location.regionName,
        country = location.country,
    )
    locationCacheStore.set(locationJson.encodeToString(CachedLocation.serializer(), cachedLocation))
}

private suspend fun getCachedLocation(): LocationResult? {
    val cachedValue = locationCacheStore.get() ?: return null
    val location = locationJson.decodeFromString<CachedLocation>(cachedValue)
    return LocationResult(
        latitude = location.latitude,
        longitude = location.longitude,
        provider = "ip-cache",
        city = location.city,
        regionName = location.regionName,
        country = location.country,
    )
}
