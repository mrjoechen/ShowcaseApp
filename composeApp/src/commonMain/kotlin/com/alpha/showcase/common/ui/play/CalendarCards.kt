package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Preview
@Composable
fun CalendarView() {
    fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var dateTime by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            dateTime = now()
            delay(60_000L - dateTime.second * 1000L - dateTime.nanosecond / 1_000_000)
        }
    }
    CalendarPager(dateTime)
}

@Composable
internal fun CalendarPager(dateTime: LocalDateTime) {
    val pager = rememberPagerState(pageCount = { 3 })
    val colors = MaterialTheme.colorScheme
    val date = dateTime.date
    val month = stringArrayResource(Res.array.calendar_months).getOrNull(date.month.number - 1).orEmpty()
    val showLunar = stringResource(Res.string.calendar_lunar_date).isNotEmpty()
    val lunarLabel = if (showLunar) {
        remember(date) { date.toChineseLunarDate() }?.localizedLabel().orEmpty()
    } else ""

    Column(
        Modifier.fillMaxSize().background(colors.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f)) { page ->
            when (page) {
                0 -> DateOverview(date, month, lunarLabel)
                1 -> MonthCalendar(date, month, lunarLabel, showLunar)
                2 -> TimeOverview(dateTime, month)
            }
        }
        Row(
            Modifier.padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(pager.pageCount) { index ->
                val color = if (index == pager.currentPage) colors.primary else colors.outlineVariant
                Spacer(Modifier.size(5.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
private fun ChineseLunarDate.localizedLabel(): String {
    val monthName = stringArrayResource(Res.array.calendar_lunar_months).getOrNull(month - 1) ?: return ""
    val dayName = stringArrayResource(Res.array.calendar_lunar_days).getOrNull(day - 1) ?: return ""
    val pattern = if (isLeapMonth) Res.string.calendar_leap_lunar_date else Res.string.calendar_lunar_date
    return stringResource(pattern, monthName, dayName)
}

@Composable
private fun DateOverview(date: LocalDate, month: String, lunar: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CalendarHeading(date.dayOfWeek.toLocalizedLabel(), month, side * 0.12f)
            Spacer(Modifier.height(side * 0.055f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(side * 0.025f),
            ) {
                CalendarText(date.day.toString(), side * 0.55f, weight = FontWeight.Bold)
                if (lunar.isNotEmpty()) {
                    val size = minOf(side * 0.086f, side * 0.43f / lunar.length)
                    CalendarText(
                        lunar.toCharArray().joinToString("\n"), size,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = lunar },
                        weight = FontWeight.SemiBold, lines = lunar.length,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeOverview(dateTime: LocalDateTime, month: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val clockSize = minOf(maxWidth * 0.31f, maxHeight * 0.46f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(side * 0.055f),
        ) {
            CalendarHeading(
                dateTime.dayOfWeek.toLocalizedLabel(),
                stringResource(Res.string.calendar_clock_date, month, dateTime.day, dateTime.year),
                side * 0.075f,
            )
            CalendarText(dateTime.time.toFormatString(), clockSize, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonthCalendar(date: LocalDate, month: String, lunar: String, showLunar: Boolean) {
    val yearMonth = date.yearMonth
    val offset = yearMonth.firstDay.dayOfWeek.isoDayNumber % 7
    val weeks = (offset + yearMonth.numberOfDays + 6) / 7
    val lunarMonthStarts = if (showLunar) remember(yearMonth) {
        yearMonth.days.filter { it.toChineseLunarDate()?.day == 1 }.map { it.day }.toSet()
    } else emptySet()
    val weekdays = stringArrayResource(Res.array.calendar_weekdays)
    val colors = MaterialTheme.colorScheme
    val weekendInk = colors.onSurfaceVariant.copy(alpha = 0.78f)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = minOf(maxWidth * 0.91f / 7, maxHeight * 0.94f / (weeks + 2))
        val gridWidth = minOf(maxWidth * 0.91f, cell * 7 * 1.35f)
        Column(Modifier.width(gridWidth)) {
            Box(Modifier.height(cell).padding(start = cell * 0.19f), contentAlignment = Alignment.CenterStart) {
                CalendarHeading(month, lunar, cell * 0.53f, divider = true)
            }
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    Box(Modifier.weight(1f).height(cell), contentAlignment = Alignment.Center) {
                        CalendarText(
                            weekdays.getOrNull(column).orEmpty(), cell * 0.41f,
                            if (column == 0 || column == 6) weekendInk else colors.onSurface,
                        )
                    }
                }
            }
            repeat(weeks) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = week * 7 + column - offset + 1
                        Box(Modifier.weight(1f).height(cell), contentAlignment = Alignment.Center) {
                            if (day in 1..yearMonth.numberOfDays) {
                                MonthDay(
                                    day = day,
                                    today = day == date.day,
                                    lunarMonthStart = day in lunarMonthStarts,
                                    ink = if (column == 0 || column == 6) weekendInk else colors.onSurface,
                                    cell = cell,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDay(day: Int, today: Boolean, lunarMonthStart: Boolean, ink: Color, cell: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
        if (today) {
            Spacer(Modifier.size(cell * 0.89f).background(colors.primary, CircleShape))
        } else if (lunarMonthStart) {
            Spacer(
                Modifier.align(Alignment.BottomCenter).padding(bottom = cell * 0.11f)
                    .size(cell * 0.54f, cell * 0.04f).background(colors.primary)
            )
        }
        CalendarText(
            day.toString(), cell * 0.51f, if (today) colors.onPrimary else ink,
            modifier = Modifier.semantics { selected = today },
        )
    }
}

@Composable
private fun CalendarHeading(accent: String, detail: String, size: Dp, divider: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size * 0.27f),
    ) {
        CalendarText(accent, size, colors.primary, FontWeight.Bold)
        if (detail.isNotEmpty()) {
            if (divider) {
                Spacer(Modifier.size((size * 0.035f).coerceAtLeast(1.dp), size * 0.86f)
                    .background(colors.outlineVariant))
            }
            CalendarText(detail, size, colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarText(
    text: String,
    size: Dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    weight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    // These display faces scale with the panel (including all seven grid columns).
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text, modifier = modifier, color = color, fontWeight = weight,
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        fontSize = fontSize, lineHeight = fontSize, letterSpacing = 0.sp,
        softWrap = false, maxLines = lines, textAlign = TextAlign.Center,
    )
}
