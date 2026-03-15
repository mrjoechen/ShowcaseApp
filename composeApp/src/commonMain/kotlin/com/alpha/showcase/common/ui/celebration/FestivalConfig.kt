@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.celebration

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class FestivalConfig(
    val name: String,
    val year: Int? = null,
    val startMonth: Month,
    val startDay: Int,
    val endMonth: Month,
    val endDay: Int,
    val animations: List<FestivalAnimation> = emptyList(),
    val minIntervalMinutes: Int = 1,
    val maxIntervalMinutes: Int = 8,
    val maxPlaysPerSession: Int = 0,
)

enum class AnimationPosition {
    TOP_START,
    TOP_CENTER,
    TOP_END,
    CENTER_START,
    CENTER,
    CENTER_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END,
    FULL_SCREEN,
}

data class FestivalAnimation(
    val lottieAsset: String,
    val weight: Int = 1,
    val iterations: Int = 1,
    val positions: List<AnimationPosition> = listOf(AnimationPosition.FULL_SCREEN),
    val sizeFraction: Float? = null,
)

fun FestivalConfig.isDateInRange(
    date: LocalDate = today(),
): Boolean {
    if (year != null && year != date.year) return false

    val todayKey = monthDayKey(date.month, date.day)
    val startKey = monthDayKey(startMonth, startDay)
    val endKey = monthDayKey(endMonth, endDay)

    return if (startKey <= endKey) {
        todayKey in startKey..endKey
    } else {
        // e.g. 12/24..01/02
        todayKey !in (endKey + 1)..<startKey
    }
}

object Festivals {

    val all: List<FestivalConfig> = listOf(
        springFestival2026(),
        newYear(),
        valentinesDay(),
        halloween(),
        christmas(),
    )

    fun findActive(date: LocalDate = today()): FestivalConfig? {
        return all.firstOrNull { it.animations.isNotEmpty() && it.isDateInRange(date) }
    }

    private fun springFestival2026() = FestivalConfig(
        name = "Spring Festival 2027",
        year = 2027,
        startMonth = Month.FEBRUARY,
        startDay = 4,
        endMonth = Month.FEBRUARY,
        endDay = 13,
        animations = listOf(
            FestivalAnimation(
                lottieAsset = "lottie/lottie_chinese_new_year_1.json",
                weight = 2,
                iterations = 4,
                positions = listOf(AnimationPosition.BOTTOM_START),
                sizeFraction = 0.28f,
            ),
            FestivalAnimation(
                lottieAsset = "lottie/lottie_chinese_new_year_2.json",
                weight = 3,
                iterations = 4,
                positions = listOf(AnimationPosition.TOP_END),
                sizeFraction = 0.28f,
            ),
            FestivalAnimation(
                lottieAsset = "lottie/lottie_firework_1.json",
                weight = 2,
                iterations = 3,
            ),
            FestivalAnimation(
                lottieAsset = "lottie/lottie_firework_2.json",
                weight = 2,
                iterations = 3,
            ),
        ),
        minIntervalMinutes = 1,
        maxIntervalMinutes = 2,
    )

    private fun newYear() = FestivalConfig(
        name = "New Year",
        startMonth = Month.JANUARY,
        startDay = 1,
        endMonth = Month.JANUARY,
        endDay = 1,
        animations = listOf(
            FestivalAnimation(
                lottieAsset = "lottie/lottie_happy_new_year_red.json",
                iterations = 5,
                positions = listOf(AnimationPosition.BOTTOM_START),
                sizeFraction = 0.30f,
            ),
            FestivalAnimation(
                lottieAsset = "lottie/lottie_firework_1.json",
                weight = 2,
                iterations = 3,
            ),
        ),
        minIntervalMinutes = 1,
        maxIntervalMinutes = 2,
    )

    private fun valentinesDay() = FestivalConfig(
        name = "Valentine's Day",
        startMonth = Month.FEBRUARY,
        startDay = 14,
        endMonth = Month.FEBRUARY,
        endDay = 14,
        animations = listOf(
            FestivalAnimation(
                lottieAsset = "lottie/lottie_lights.json",
                iterations = 2,
                positions = listOf(AnimationPosition.FULL_SCREEN),
            ),
        ),
        minIntervalMinutes = 2,
        maxIntervalMinutes = 4,
    )

    private fun halloween() = FestivalConfig(
        name = "Halloween",
        startMonth = Month.OCTOBER,
        startDay = 31,
        endMonth = Month.OCTOBER,
        endDay = 31,
        animations = listOf(
            FestivalAnimation(
                lottieAsset = "lottie/lottie_lights.json",
                iterations = 1,
                sizeFraction = 0.55f,
            ),
        ),
        minIntervalMinutes = 2,
        maxIntervalMinutes = 4,
    )

    private fun christmas() = FestivalConfig(
        name = "Christmas",
        startMonth = Month.DECEMBER,
        startDay = 24,
        endMonth = Month.DECEMBER,
        endDay = 26,
        animations = listOf(
            FestivalAnimation(
                lottieAsset = "lottie/lottie_snow.json",
                iterations = 2,
                positions = listOf(AnimationPosition.FULL_SCREEN),
            ),
            FestivalAnimation(
                lottieAsset = "lottie/lottie_firework_2.json",
                weight = 2,
                iterations = 2,
            ),
        ),
        minIntervalMinutes = 2,
        maxIntervalMinutes = 4,
    )
}

private fun monthDayKey(
    month: Month,
    day: Int,
): Int = month.number * 100 + day

private fun today(): LocalDate {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}
