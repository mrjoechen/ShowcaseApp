package com.alpha.showcase.common.weather

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
private data class IpGeoResponse(
    val status: String,
    val country: String? = null,
    @SerialName("countryCode")
    val countryCode: String? = null,
    val region: String? = null,
    @SerialName("regionName")
    val regionName: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val offset: Long? = null,
    val isp: String? = null,
    val org: String? = null,
    @SerialName("as")
    val asName: String? = null,
    val query: String? = null
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
        "http://ip-api.com/json/?fields=status,country,countryCode,region,regionName,city,zip,lat,lon,timezone,offset,isp,org,as,query"

    suspend fun getCurrentLocation(): LocationResult? {
        val nativeLocation = runCatching { getNativeLocationOrNull() }
            .onFailure { Log.w("LocationProvider", "Native location failed: ${it.message}") }
            .getOrNull()
        if (nativeLocation != null) {
            return nativeLocation
        }

        return getLocationFromIp()
    }

    private suspend fun getLocationFromIp(): LocationResult? {
        return runCatching {
            withTimeout(5_000) {
                val body = locationHttpClient.get(IP_GEO_URL).bodyAsText()
                val data = locationJson.decodeFromString<IpGeoResponse>(body)
                if (data.status != "success") return@withTimeout null
                val latitude = data.lat ?: return@withTimeout null
                val longitude = data.lon ?: return@withTimeout null
                LocationResult(
                    latitude = latitude,
                    longitude = longitude,
                    provider = "ip",
                    city = data.city,
                    regionName = data.regionName,
                    country = data.country
                )
            }
        }.onFailure {
            Log.w("LocationProvider", "IP geolocation failed: ${it.message}")
        }.getOrNull()
    }
}
