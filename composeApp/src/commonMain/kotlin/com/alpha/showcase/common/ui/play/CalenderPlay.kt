@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.theme.smileySansFontFamily
import com.alpha.showcase.common.theme.showcaseOverlayTextShadow
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime


const val HORIZONTAL_IMAGE_WEIGHT = 0.67f
const val VERTICAL_IMAGE_WEIGHT = 0.6f

private const val CALENDAR_PAGE_COUNT = 3
private const val LAYOUT_ROTATION_INTERVAL_MILLIS = 15 * 60 * 1000L

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
    var imageDragging by remember { mutableStateOf(false) }
    val imageSwipe = Modifier.calendarImageSwipe(
        enabled = rememberPlaybackActive() && pagingItems.size > 1,
        onDragging = { imageDragging = it },
        onStep = { direction ->
            val size = pagingItems.size
            if (size > 1) {
                currentShowIndex.value = (currentShowIndex.value + direction + size) % size
            }
        },
    )
    rememberImagePlaybackProgress(duration, current = {
        // This renderer cannot play videos; unsupported entries must also time out and advance.
        ImagePlaybackFrame(currentShowIndex.value to mediaState, mediaState.ready,
            paused = imageDragging,
            enabled = autoPlay && pagingItems.size > 1)
    }) {
        val size = pagingItems.size
        if (size > 1) currentShowIndex.value = (currentShowIndex.value + 1) % size
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .playbackArrowKeys { direction ->
                val size = pagingItems.size
                if (size > 0) {
                    currentShowIndex.value =
                        (currentShowIndex.value + direction).coerceIn(0L, (size - 1).toLong())
                }
            },
    ) {
        // Treat a landscape window below this width as narrow so that the two
        // panels remain readable instead of being squeezed side by side.
        val wideLayout = maxWidth > maxHeight && maxWidth >= 600.dp
        var imageFirst by remember { mutableStateOf(true) }

        // Periodically move both panels. This also applies to the stacked
        // layout, which prevents a fixed bright edge from remaining on screen.
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(LAYOUT_ROTATION_INTERVAL_MILLIS)
                imageFirst = !imageFirst
            }
        }

        if (wideLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (imageFirst) {
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(HORIZONTAL_IMAGE_WEIGHT).then(imageSwipe))
                    CalendarDatePanel(Modifier.weight(1f - HORIZONTAL_IMAGE_WEIGHT))
                } else {
                    CalendarDatePanel(Modifier.weight(1f - HORIZONTAL_IMAGE_WEIGHT))
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(HORIZONTAL_IMAGE_WEIGHT).then(imageSwipe))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (imageFirst) {
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(VERTICAL_IMAGE_WEIGHT).then(imageSwipe))
                    CalendarDatePanel(Modifier.weight(1f - VERTICAL_IMAGE_WEIGHT))
                } else {
                    CalendarDatePanel(Modifier.weight(1f - VERTICAL_IMAGE_WEIGHT))
                    CalendarImagePanel(mediaState, showTimeAndDate, avoidImageSummary,
                        Modifier.weight(VERTICAL_IMAGE_WEIGHT).then(imageSwipe))
                }
            }
        }
    }


}

/** Only claim horizontal drags; taps and zoom gestures remain with the media. */
@Composable
private fun Modifier.calendarImageSwipe(
    enabled: Boolean,
    onDragging: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
): Modifier {
    val currentOnDragging by rememberUpdatedState(onDragging)
    val currentOnStep by rememberUpdatedState(onStep)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var distance = 0f
        val threshold = 48.dp.toPx()
        try {
            detectHorizontalDragGestures(
                onDragStart = {
                    distance = 0f
                    currentOnDragging(true)
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    distance += amount
                },
                onDragEnd = {
                    when {
                        distance <= -threshold -> currentOnStep(1)
                        distance >= threshold -> currentOnStep(-1)
                    }
                    currentOnDragging(false)
                },
                onDragCancel = { currentOnDragging(false) },
            )
        } finally {
            currentOnDragging(false)
        }
    }
}

@Composable
private fun CalendarImagePanel(
    mediaState: MediaItemState,
    showTimeAndDate: Boolean,
    avoidImageSummary: Boolean,
    modifier: Modifier,
) {
    Box(modifier = modifier.testTag("calendar-image-panel")) {
        DisplayView(state = mediaState)
        if (showTimeAndDate) {
            TimeCard(avoidImageSummary = avoidImageSummary)
        }
    }
}

@Composable
private fun CalendarDatePanel(modifier: Modifier) {
    Box(modifier = modifier.testTag("calendar-date-panel")) {
        CalendarView()
    }
}

@Composable
fun DisplayView(state: MediaItemState) {
    val overlays = MediaOverlayConfig.forStyle(SHOWCASE_MODE_CALENDER)
    var summaryVisible by remember { mutableStateOf(true) }
    var previousState by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (previousState !== state) {
            previousState = state
            summaryVisible = false
            delay(2_600)
            summaryVisible = true
        }
    }
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
        MediaOverlayTransition(
            state,
            SHOWCASE_MODE_CALENDER,
            summaryVisible = summaryVisible,
        )
    }
}


@Preview
@Composable
fun CalendarView() {
    var currentDateTime by remember { mutableStateOf(currentDateTime()) }
    val pagerState = rememberPagerState(pageCount = { CALENDAR_PAGE_COUNT })

    // Keep all three pages on the same clock. Updating once a second also
    // makes the time page useful without affecting image playback timing.
    LaunchedEffect(Unit) {
        while (isActive) {
            currentDateTime = currentDateTime()
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .padding(8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 18.dp),
        ) { page ->
            when (page) {
                0 -> DateOverview(currentDateTime.toLocalDateTimeDate())
                1 -> MonthCalendar(currentDateTime.toLocalDateTimeDate())
                else -> TimeOverview(currentDateTime)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pagerState.pageCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(6.dp)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                            CircleShape,
                        )
                )
            }
        }
    }
}

private fun currentDateTime() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun kotlinx.datetime.LocalDateTime.toLocalDateTimeDate() = kotlinx.datetime.LocalDate(year, month, day)

@Composable
private fun DateOverview(date: kotlinx.datetime.LocalDate) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val minDimension = minOf(maxWidth, maxHeight)
        val weekdayFontSize = (minDimension.value * 0.1f).coerceIn(14f, 30f).sp
        val dateFontSize = (minDimension.value * 0.55f).coerceIn(42f, 148f).sp
        val monthFontSize = (minDimension.value * 0.13f).coerceIn(16f, 34f).sp
        val calendarFontFamily = smileySansFontFamily()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = dayOfWeekLabel(date.dayOfWeek),
                color = MaterialTheme.colorScheme.primary,
                style = TextStyle(shadow = showcaseOverlayTextShadow),
                fontSize = weekdayFontSize,
                fontWeight = FontWeight.Medium,
                fontFamily = calendarFontFamily,
            )
            Text(
                text = date.day.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = TextStyle(shadow = showcaseOverlayTextShadow),
                fontSize = dateFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = calendarFontFamily,
            )
            Text(
                text = "${date.month.number} / ${date.year}",
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(shadow = showcaseOverlayTextShadow),
                fontSize = monthFontSize,
                fontFamily = calendarFontFamily,
            )
        }
    }
}

@Composable
private fun TimeOverview(dateTime: kotlinx.datetime.LocalDateTime) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val minDimension = minOf(maxWidth, maxHeight)
        val timeFontSize = (minDimension.value * 0.55f).coerceIn(42f, 148f).sp
        val dateFontSize = (minDimension.value * 0.13f).coerceIn(16f, 34f).sp
        val calendarFontFamily = smileySansFontFamily()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = dateTime.time.toClockText(),
                color = MaterialTheme.colorScheme.primary,
                style = TextStyle(shadow = showcaseOverlayTextShadow),
                fontSize = timeFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = calendarFontFamily,
            )
            Text(
                text = "${dateTime.date.month.number} / ${dateTime.date.day} / ${dateTime.date.year}",
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(shadow = showcaseOverlayTextShadow),
                fontSize = dateFontSize,
                fontFamily = calendarFontFamily,
            )
        }
    }
}

@Composable
private fun MonthCalendar(date: kotlinx.datetime.LocalDate) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val calendarFontFamily = smileySansFontFamily()
        val monthFontSize = (minOf(maxWidth, maxHeight) / 16.dp).coerceIn(16f, 28f).sp
        val cellFontSize = (minOf(maxWidth, maxHeight) / 26.dp).coerceIn(10f, 18f).sp
        val daysInMonth = daysInMonth(date.year, date.month.number)
        val firstDayOffset = when (kotlinx.datetime.LocalDate(date.year, date.month, 1).dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }
        val cells = List(42) { index ->
            val day = index - firstDayOffset + 1
            if (day in 1..daysInMonth) day else null
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${date.month.number} / ${date.year}",
                color = MaterialTheme.colorScheme.primary,
                style = TextStyle(shadow = showcaseOverlayTextShadow),
                fontSize = monthFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = calendarFontFamily,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = showcaseOverlayTextShadow),
                        color = if (index == 0 || index == 6) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = cellFontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = calendarFontFamily,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    week.forEachIndexed { index, day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .background(
                                    if (day == date.day) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        Color.Transparent
                                    },
                                    CircleShape,
                                )
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                Text(
                                    text = day.toString(),
                                    style = TextStyle(shadow = showcaseOverlayTextShadow),
                                    color = if (index == 0 || index == 6) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontSize = cellFontSize,
                                    fontFamily = calendarFontFamily,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayOfWeekLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}

private fun kotlinx.datetime.LocalTime.toClockText(): String {
    fun Int.twoDigits() = toString().padStart(2, '0')
    return "${hour.twoDigits()}:${minute.twoDigits()}"
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}
