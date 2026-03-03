@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.weather

import com.alpha.showcase.common.ui.vm.BaseViewModel
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.weather_error_fetch_failed
import showcaseapp.composeapp.generated.resources.weather_error_location_unavailable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class WeatherState(
    val weatherData: WeatherSnapshot? = null,
    val location: LocationResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdateTime: Long = 0L
)

object WeatherViewModel : BaseViewModel() {
    private const val WEATHER_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    private const val WEATHER_CHECK_INTERVAL_MS = 15 * 1000L
    private const val PERMISSION_REQUEST_INTERVAL_MS = 60 * 1000L

    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private var refreshJob: Job? = null
    private var lastPermissionRequestTime: Long = 0L

    fun startWeatherUpdates() {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            while (isActive) {
                maybeRequestPermission()
                refreshIfNeeded()
                delay(WEATHER_CHECK_INTERVAL_MS)
            }
        }
    }

    fun stopWeatherUpdates() {
        refreshJob?.cancel()
        refreshJob = null
    }

    suspend fun refreshIfNeeded(maxAgeMs: Long = WEATHER_REFRESH_INTERVAL_MS) {
        val now = Clock.System.now().toEpochMilliseconds()
        val state = _weatherState.value
        val isStale = now - state.lastUpdateTime > maxAgeMs
        if (state.weatherData == null || isStale) {
            fetchWeather()
        }
    }

    suspend fun fetchWeather() {
        _weatherState.value = _weatherState.value.copy(isLoading = true, error = null)

        runCatching {
            val location = LocationProvider.getCurrentLocation()
            if (location == null) {
                _weatherState.value = _weatherState.value.copy(
                    isLoading = false,
                    error = getString(Res.string.weather_error_location_unavailable)
                )
                return
            }

            val weatherData = WeatherApi.getWeather(location.latitude, location.longitude)
            if (weatherData == null) {
                _weatherState.value = _weatherState.value.copy(
                    location = location,
                    isLoading = false,
                    error = getString(Res.string.weather_error_fetch_failed)
                )
                return
            }

            _weatherState.value = WeatherState(
                weatherData = weatherData,
                location = location,
                isLoading = false,
                lastUpdateTime = Clock.System.now().toEpochMilliseconds()
            )
        }.onFailure {
            Log.w("WeatherViewModel", "fetchWeather failed: ${it.message}")
            _weatherState.value = _weatherState.value.copy(
                isLoading = false,
                error = getString(Res.string.weather_error_fetch_failed)
            )
        }
    }

    private fun maybeRequestPermission() {
        if (hasLocationPermission()) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastPermissionRequestTime < PERMISSION_REQUEST_INTERVAL_MS) return
        lastPermissionRequestTime = now
        requestLocationPermission()
    }
}
