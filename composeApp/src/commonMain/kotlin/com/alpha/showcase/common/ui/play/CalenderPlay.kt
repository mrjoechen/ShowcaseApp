@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime


const val HORIZONTAL_IMAGE_WEIGHT = 0.67f
const val VERTICAL_IMAGE_WEIGHT = 0.67f

@Composable
fun CalenderPlay(
    autoPlay: Boolean = true,
    duration: Long,
    sortRule: Int,
    pagingItems: PagingPlayItems,
    fitSize: Boolean = false,
    showTimeAndDate: Boolean = false,
    avoidImageSummary: Boolean = false,
) {

    val currentShowIndex = remember {
        mutableLongStateOf(0L)
    }

    val currentShow by remember(pagingItems) {
        derivedStateOf {
            pagingItems[currentShowIndex.value.toInt()]
        }
    }

    // Keep the stored index aligned with the wrapped display index when the dataset shrinks.
    LaunchedEffect(pagingItems) {
        snapshotFlow { pagingItems.size }.collect { size ->
            if (size > 0 && currentShowIndex.value >= size) {
                currentShowIndex.value = currentShowIndex.value % size
            }
        }
    }

    val mediaState = rememberMediaItemState(currentShow, fitSize)
    rememberImagePlaybackProgress(duration, current = {
        // This renderer cannot play videos; unsupported entries must also time out and advance.
        ImagePlaybackFrame(currentShowIndex.value to mediaState, mediaState.ready,
            enabled = autoPlay && pagingItems.size > 1)
    }) {
        val size = pagingItems.size
        if (size > 1) currentShowIndex.value = (currentShowIndex.value + 1) % size
    }

    Row(modifier = Modifier.fillMaxSize().playbackArrowKeys { direction ->
        val size = pagingItems.size
        if (size > 0) currentShowIndex.value = (currentShowIndex.value + direction).coerceIn(0L, (size - 1).toLong())
    }) {
        Box(modifier = Modifier.weight(HORIZONTAL_IMAGE_WEIGHT)) {
            DisplayView(state = mediaState)
            if (showTimeAndDate) {
                TimeCard(avoidImageSummary = avoidImageSummary)
            }
        }
        Box(modifier = Modifier.weight(1 - HORIZONTAL_IMAGE_WEIGHT)) {
            CalendarView()
        }
    }


}

@Composable
fun DisplayView(state: MediaItemState) {
    val overlays = MediaOverlayConfig.forStyle(SHOWCASE_MODE_CALENDER)
    Box(Modifier.fillMaxSize().clipToBounds().mediaActivity { state.interact(overlays) }) {
        AnimatedContent(
            state,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(2500, delayMillis = 100), initialAlpha = 0.2f)
                    .togetherWith(fadeOut(animationSpec = tween(2500), targetAlpha = 0.2f))
            }, label = "Image display",
        ) { entry ->
            PagerItem(
                state = entry,
                modifier = rememberKenBurnsModifier(entry, SHOWCASE_MODE_CALENDER),
                active = entry === state,
                onInteraction = { entry.interact(overlays, it) },
            )
        }
        MediaOverlayTransition(state, SHOWCASE_MODE_CALENDER)
    }
}


@Preview
@Composable
fun CalendarView() {
    val year = getYearString()
    val month = getMonthString()
    val dayOfMonth = getDayOfMonthString()
    val dayOfWeek = getDayOfWeekString()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(0.dp, 8.dp),
            text = dayOfWeek,
            style = TextStyle(fontSize = 24.sp)
        )
        Text(
            text = dayOfMonth,
            style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            modifier = Modifier.padding(0.dp, 8.dp),
            text = "$month / $year",
            style = TextStyle(fontSize = 36.sp)
        )
    }

}


private fun getYearString(): String {
    val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    // 获取年
    val year = date.year
    return year.toString()
}

private fun getMonthString(): String {
    val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    // 获取月
    val month = date.month.number
    return month.toString()
}

private fun getDayOfMonthString(): String {
    val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    // 获取日
    val day = date.day
    return day.toString()
}
private fun getDayOfWeekString(): String {
    val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val dayOfWeek = date.dayOfWeek
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }
}
