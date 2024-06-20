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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview


const val HORIZONTAL_IMAGE_WEIGHT = 0.67f
const val VERTICAL_IMAGE_WEIGHT = 0.67f

@Composable
fun CalenderPlay(
    autoPlay: Boolean = true,
    duration: Long,
    sortRule: Int,
    data: List<Any>
) {

//    val currentDate = LocalDate.now()
//    // 获取年
//    val year = currentDate.year
//    println("Year: $year")
//    // 获取月
//    val month = currentDate.month
//    println("Month: $month (${month.getDisplayName(TextStyle.FULL, Locale.getDefault())})")
//    // 获取日
//    val day = currentDate.dayOfMonth
//    println("Day: $day")
//    // 获取星期
//    val dayOfWeek = currentDate.dayOfWeek
//    println(
//        "Day of Week: $dayOfWeek (${
//            dayOfWeek.getDisplayName(
//                TextStyle.FULL,
//                Locale.getDefault()
//            )
//        })"
//    )

    val currentShowIndex = remember {
        mutableLongStateOf(0L)
    }

    val currentShow by remember {
        derivedStateOf {
            data[currentShowIndex.value.toInt()]
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(duration + 2000)
            currentShowIndex.value++
            if (currentShowIndex.value >= data.size) {
                currentShowIndex.value = 0
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(HORIZONTAL_IMAGE_WEIGHT)) {
            DisplayView(data = currentShow)
        }
        Box(modifier = Modifier.weight(1 - HORIZONTAL_IMAGE_WEIGHT)) {
            CalendarView()
        }
    }


}

@Composable
fun DisplayView(data: Any) {
    AnimatedContent(
        data,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(
                animationSpec = tween(2500, delayMillis = 100),
                initialAlpha = 0.2f
            ).togetherWith(
                fadeOut(animationSpec = tween(2500), targetAlpha = 0.2f)
            )
        }, label = "Image display"
    ) {
        PagerItem(
            modifier = Modifier.padding(0.dp),
            data = it,
            false,
            parentType = SHOWCASE_MODE_CALENDER
        )
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
    val month = date.monthNumber
    return month.toString()
}

private fun getDayOfMonthString(): String {
    val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    // 获取日
    val day = date.dayOfMonth
    return day.toString()
}
private fun getDayOfWeekString(): String {
    val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val dayOfWeek = date.dayOfWeek
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
        else -> ""
    }
}

