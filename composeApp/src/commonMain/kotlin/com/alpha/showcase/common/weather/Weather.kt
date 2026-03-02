package com.alpha.showcase.common.weather

import com.alpha.showcase.common.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.*
import kotlin.math.roundToInt

@Serializable
private data class OpenMeteoWeatherResponse(
    val current: OpenMeteoCurrentWeather? = null
)

@Serializable
private data class OpenMeteoCurrentWeather(
    @SerialName("temperature_2m")
    val temperatureCelsius: Double,
    @SerialName("weather_code")
    val weatherCode: Int,
    @SerialName("is_day")
    val isDay: Int
)

data class WeatherSnapshot(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val iconRes: DrawableResource,
    val backgroundLottieAsset: String?
)

fun WeatherSnapshot.tempC(): String = "${temperatureCelsius.roundToInt()}°C"
fun WeatherSnapshot.descriptionRes(): StringResource = weatherCodeToDescriptionRes(weatherCode, isDay)

private val weatherJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private val weatherHttpClient by lazy {
    HttpClient {
        expectSuccess = true
    }
}

object WeatherApi {
    private const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"

    suspend fun getWeather(latitude: Double, longitude: Double): WeatherSnapshot? {
        return runCatching {
            withTimeout(8_000) {
                val body = weatherHttpClient.get(OPEN_METEO_URL) {
                    parameter("latitude", latitude)
                    parameter("longitude", longitude)
                    parameter("current", "temperature_2m,weather_code,is_day")
                    parameter("temperature_unit", "celsius")
                    parameter("timezone", "auto")
                }.bodyAsText()

                weatherJson.decodeFromString<OpenMeteoWeatherResponse>(body).current?.toSnapshot()
            }
        }.onFailure {
            Log.w("WeatherApi", "Failed to fetch weather: ${it.message}")
        }.getOrNull()
    }
}

private fun OpenMeteoCurrentWeather.toSnapshot(): WeatherSnapshot {
    val day = isDay == 1
    return WeatherSnapshot(
        temperatureCelsius = temperatureCelsius,
        weatherCode = weatherCode,
        isDay = day,
        iconRes = weatherCodeToGoogleIcon(weatherCode, day),
        backgroundLottieAsset = weatherCodeToLottie(weatherCode)
    )
}

private fun weatherCodeToDescriptionRes(code: Int, isDay: Boolean): StringResource {
    return when {
        code == 0 && isDay -> Res.string.weather_desc_clear_day
        code == 0 && !isDay -> Res.string.weather_desc_clear_night
        code in setOf(1, 2) -> Res.string.weather_desc_cloudy
        code == 3 -> Res.string.weather_desc_overcast
        code in setOf(45, 48) -> Res.string.weather_desc_mist
        code in setOf(51, 53, 55, 56, 57) -> Res.string.weather_desc_drizzle
        code in setOf(61, 63, 65, 66, 67, 80, 81, 82) -> Res.string.weather_desc_rain
        code in setOf(71, 73, 75, 77, 85, 86) -> Res.string.weather_desc_snow
        code in setOf(95, 96, 99) -> Res.string.weather_desc_thunderstorm
        else -> Res.string.weather_desc_unknown
    }
}

private fun weatherCodeToLottie(code: Int): String? {
    return when {
        code in setOf(45, 48) -> "lottie/lottie_mist.json"
        code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) -> "lottie/lottie_rain.json"
        code in setOf(71, 73, 75, 77, 85, 86) -> "lottie/lottie_snow.json"
        else -> null
    }
}

private fun weatherCodeToGoogleIcon(code: Int, isDay: Boolean): DrawableResource {
    return when {
        code in setOf(95) -> {
            if (isDay) Res.drawable.ic_weather_thunderstorms_day
            else Res.drawable.ic_weather_thunderstorms_night
        }

        code in setOf(96, 99) -> {
            Res.drawable.ic_weather_strong_thunderstorms
        }

        code in setOf(75, 86) -> {
            Res.drawable.ic_weather_blizzard
        }

        code in setOf(71, 73, 77, 85) -> {
            if (isDay) Res.drawable.ic_weather_scattered_snow_showers_day
            else Res.drawable.ic_weather_scattered_snow_showers_night
        }

        code in setOf(51, 53, 55) -> {
            Res.drawable.ic_weather_drizzle
        }

        code in setOf(56, 57, 66, 67) -> {
            Res.drawable.ic_weather_flurries
        }

        code in setOf(61, 63) -> {
            Res.drawable.ic_weather_rain_showers
        }

        code in setOf(65, 82) -> {
            Res.drawable.ic_weather_heavy_rain
        }

        code in setOf(80, 81) -> {
            if (isDay) Res.drawable.ic_weather_scattered_rain_showers_day
            else Res.drawable.ic_weather_scattered_rain_showers_night
        }

        code in setOf(45, 48) -> {
            Res.drawable.ic_weather_haze_fog
        }

        code == 0 && isDay -> {
            Res.drawable.ic_weather_clear_day
        }

        code == 0 && !isDay -> {
            Res.drawable.ic_weather_clear_night
        }

        code == 1 && isDay -> {
            Res.drawable.ic_weather_partly_cloudy_day
        }

        code == 1 && !isDay -> {
            Res.drawable.ic_weather_partly_cloudy_night
        }

        code == 2 && isDay -> {
            Res.drawable.ic_weather_mostly_cloudy_day
        }

        code == 2 && !isDay -> {
            Res.drawable.ic_weather_mostly_cloudy_night
        }

        code == 3 -> {
            Res.drawable.ic_weather_cloudy
        }

        else -> {
            Res.drawable.ic_weather_not_available
        }
    }
}
