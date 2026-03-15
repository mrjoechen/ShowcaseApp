package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import com.alpha.showcase.common.ui.view.LottieAssetLoader
import com.alpha.showcase.common.weather.WeatherViewModel

@Composable
fun WeatherBackgroundLayer(
    lottieAsset: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 0.22f,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape? = null
) {
    if (lottieAsset.isNullOrBlank()) return

    val clippedModifier = if (shape != null) {
        modifier.clip(shape)
    } else {
        modifier
    }

    LottieAssetLoader(
        lottieAsset = lottieAsset,
        modifier = clippedModifier.alpha(alpha),
        contentScale = contentScale
    )
}

@Composable
fun WeatherBackgroundLayer(
    modifier: Modifier = Modifier,
    alpha: Float = 0.22f,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape? = null,
    autoStartUpdates: Boolean = true
) {
    val weatherState by WeatherViewModel.weatherState.collectAsState()

    if (autoStartUpdates) {
        LaunchedEffect(Unit) {
            WeatherViewModel.startWeatherUpdates()
        }
    }

    WeatherBackgroundLayer(
        lottieAsset = weatherState.weatherData?.backgroundLottieAsset,
        modifier = modifier,
        alpha = alpha,
        contentScale = contentScale,
        shape = shape
    )
}
