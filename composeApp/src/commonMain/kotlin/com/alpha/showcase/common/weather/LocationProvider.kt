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
private data class IpGeoLocation(
    @SerialName("country_name")
    val countryName: String? = null,
    @SerialName("state_prov")
    val stateProv: String? = null,
    val city: String? = null,
    val latitude: String? = null,
    val longitude: String? = null
)

@Serializable
private data class IpGeoResponse(
    val ip: String? = null,
    val location: IpGeoLocation? = null
)

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

private val locationHttpClient by lazy {
    HttpClient {
        expectSuccess = true
    }
}

object LocationProvider {
    private const val IP_GEO_URL =
        "https://api.ipgeolocation.io/v3/ipgeo?apiKey=${IP_GEO_API_KEY}"
    private val locationCacheStore = objectStoreOf<String>("ip_location_cache")

    suspend fun getCurrentLocation(): LocationResult? {
        val nativeLocation = runCatching { getNativeLocationOrNull() }
            .onFailure { Log.w("LocationProvider", "Native location failed: ${it.message}") }
            .getOrNull()
        if (nativeLocation != null) {
            cacheLocation(nativeLocation)
            return nativeLocation
        }

        val ipLocation = getLocationFromIp()
        if (ipLocation != null) {
            cacheLocation(ipLocation)
            return ipLocation
        }

        return getCachedLocation()
    }

    private suspend fun getLocationFromIp(): LocationResult? {
        return runCatching {
            withTimeout(5_000) {
                val body = locationHttpClient.get(IP_GEO_URL).bodyAsText()
                val data = locationJson.decodeFromString<IpGeoResponse>(body)
                val location = data.location ?: return@withTimeout null
                val latitude = location.latitude?.toDoubleOrNull() ?: return@withTimeout null
                val longitude = location.longitude?.toDoubleOrNull() ?: return@withTimeout null
                LocationResult(
                    latitude = latitude,
                    longitude = longitude,
                    provider = "ipgeolocation",
                    city = location.city,
                    regionName = location.stateProv,
                    country = location.countryName
                )
            }
        }.onFailure {
            Log.w("LocationProvider", "IP geolocation failed: ${it.message}")
        }.getOrNull()
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
                provider = "ip-cache",
                city = data.city,
                regionName = data.regionName,
                country = data.country
            )
        }.onFailure {
            Log.w("LocationProvider", "Load cached location failed: ${it.message}")
        }.getOrNull()
    }
}
