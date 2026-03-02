@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.alpha.showcase.common.ui.view.LottieAssetLoader
import com.alpha.showcase.common.weather.descriptionRes
import com.alpha.showcase.common.weather.WeatherState
import com.alpha.showcase.common.weather.WeatherViewModel
import com.alpha.showcase.common.weather.tempC
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val timeCardPositions = listOf(
    Alignment.TopStart,
    Alignment.TopEnd,
    Alignment.BottomStart,
    Alignment.BottomEnd,
    Alignment.TopCenter,
    Alignment.BottomCenter
)

@Preview
@Composable
fun TimeCard() {
    val weatherState by WeatherViewModel.weatherState.collectAsState()

    var position by remember { mutableStateOf(Alignment.BottomEnd) }
    var date by remember { mutableStateOf(currentDate()) }
    var dayOfWeek by remember { mutableStateOf(currentDayOfWeek()) }
    var time by remember { mutableStateOf(currentTime()) }
    var moveCounter by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        WeatherViewModel.startWeatherUpdates()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            date = currentDate()
            dayOfWeek = currentDayOfWeek()
            time = currentTime()
            moveCounter++

            if (moveCounter % 30 == 0L) {
                position = timeCardPositions.random()
                moveCounter = 0L
            }
        }
    }

    AnimatedContent(
        targetState = position,
        transitionSpec = {
            fadeIn(animationSpec = tween(1000, delayMillis = 500))
                .togetherWith(fadeOut(animationSpec = tween(800)))
        },
        label = "time-card-position"
    ) { cardAlignment ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = cardAlignment
        ) {
            val shortSide = min(maxWidth, maxHeight)
            val scale = (shortSide / 480.dp).coerceIn(0.55f, 1.45f)

            val outerPadding = 30.dp * scale
            val innerPadding = 12.dp * scale
            val rowSpacing = 8.dp * scale
            val cornerRadius = 12.dp * scale

            val dateTextSize = (20f * scale).sp
            val timeTextSize = (36f * scale).sp
            val bodyTextSize = (14f * scale).sp
            val captionTextSize = (12f * scale).sp
            val iconSize = 34.dp * scale

            val textShadow = Shadow(
                color = Color.Black.copy(alpha = 0.6f),
                offset = Offset(4f * scale, 4f * scale),
                blurRadius = 14f * scale
            )

            Surface(
                shape = RoundedCornerShape(cornerRadius),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                modifier = Modifier.padding(outerPadding)
            ) {
                var cardSize by remember { mutableStateOf(IntSize.Zero) }
                val density = LocalDensity.current

                Box(
                    modifier = Modifier.onSizeChanged { cardSize = it }
                ) {
                    weatherState.weatherData?.backgroundLottieAsset?.let { lottieAsset ->
                        if (cardSize.width > 0 && cardSize.height > 0) {
                            val bgWidth = with(density) { cardSize.width.toDp() }
                            val bgHeight = with(density) { cardSize.height.toDp() }
                            LottieAssetLoader(
                                lottieAsset = lottieAsset,
                                modifier = Modifier
                                    .width(bgWidth)
                                    .height(bgHeight)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .alpha(0.45f),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = formatDate(date),
                                style = TextStyle(
                                    fontSize = dateTextSize,
                                    fontWeight = FontWeight.Bold,
                                    shadow = textShadow
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp * scale))
                            Text(
                                text = dayOfWeek.toLocalizedLabel(),
                                style = TextStyle(
                                    fontSize = dateTextSize,
                                    fontWeight = FontWeight.Bold,
                                    shadow = textShadow
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(rowSpacing))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = time,
                                style = TextStyle(
                                    fontSize = timeTextSize,
                                    fontWeight = FontWeight.Bold,
                                    shadow = textShadow
                                )
                            )
                            Spacer(modifier = Modifier.width(rowSpacing))

                            TimeCardWeather(
                                weatherState = weatherState,
                                iconSize = iconSize,
                                bodyTextSize = bodyTextSize,
                                captionTextSize = captionTextSize,
                                textShadow = textShadow
                            )
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun TimeCardWeather(
    weatherState: WeatherState,
    iconSize: androidx.compose.ui.unit.Dp,
    bodyTextSize: androidx.compose.ui.unit.TextUnit,
    captionTextSize: androidx.compose.ui.unit.TextUnit,
    textShadow: Shadow
) {
    val weather = weatherState.weatherData
    if (weather == null) {
        val msg = when {
            weatherState.isLoading -> stringResource(Res.string.timecard_weather_loading)
            else -> stringResource(Res.string.timecard_weather_unavailable)
        }
        Text(
            text = msg,
            style = TextStyle(
                fontSize = captionTextSize,
                shadow = textShadow
            )
        )
        return
    }

    val weatherDesc = stringResource(weather.descriptionRes())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(weather.iconRes),
            contentDescription = weatherDesc,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = weather.tempC(),
                style = TextStyle(
                    fontSize = bodyTextSize,
                    fontWeight = FontWeight.Bold,
                    shadow = textShadow
                )
            )
            Text(
                text = weatherDesc,
                style = TextStyle(
                    fontSize = captionTextSize,
                    shadow = textShadow
                )
            )
        }
    }
}

private fun currentDate(): LocalDate {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun currentDayOfWeek(): DayOfWeek {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .dayOfWeek
}

private fun currentTime(): String {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time
        .toFormatString()
}

@Composable
private fun formatDate(date: LocalDate): String {
    return stringResource(
        Res.string.timecard_date_pattern,
        date.year,
        date.month.number,
        date.day
    )
}

@Composable
private fun DayOfWeek.toLocalizedLabel(): String {
    val res = when (this) {
        DayOfWeek.MONDAY -> Res.string.timecard_weekday_monday
        DayOfWeek.TUESDAY -> Res.string.timecard_weekday_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.timecard_weekday_wednesday
        DayOfWeek.THURSDAY -> Res.string.timecard_weekday_thursday
        DayOfWeek.FRIDAY -> Res.string.timecard_weekday_friday
        DayOfWeek.SATURDAY -> Res.string.timecard_weekday_saturday
        DayOfWeek.SUNDAY -> Res.string.timecard_weekday_sunday
    }
    return stringResource(res)
}

private fun LocalTime.toFormatString(): String {
    val h = if (hour < 10) "0$hour" else "$hour"
    val m = if (minute < 10) "0$minute" else "$minute"
    return "$h:$m"
}
