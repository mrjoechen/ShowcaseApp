package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.SlideItem
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.auto_play
import showcaseapp.composeapp.generated.resources.interval_time_unit
import showcaseapp.composeapp.generated.resources.minutes
import showcaseapp.composeapp.generated.resources.second

@Composable
fun CalenderView(calenderMode: Settings.CalenderMode, onSet: (String, Any) -> Unit) {


    val secondRange = 1f..60f
    val minuteRange = 1f..30f

    SlideItem(
        Icons.Outlined.Timer,
        desc = stringResource(Res.string.auto_play),
        value = if (calenderMode.intervalTime.toInt() == 0) {
            if (calenderMode.intervalTimeUnit == 0) DEFAULT_PERIOD.toInt() / 1000 else minuteRange.start.toInt()
        } else if ((calenderMode.intervalTimeUnit == 0 && calenderMode.intervalTime.toFloat() !in secondRange) || (calenderMode.intervalTimeUnit == 1 && calenderMode.intervalTime.toFloat() !in minuteRange))
            if (calenderMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
        else
            calenderMode.intervalTime.toInt(),
        step = (if (calenderMode.intervalTimeUnit == 0) secondRange.endInclusive - secondRange.start else minuteRange.endInclusive - minuteRange.start).toInt(),
        range = if (calenderMode.intervalTimeUnit == 0) secondRange else minuteRange,
        unit = if (calenderMode.intervalTimeUnit == 0) stringResource(Res.string.second) else stringResource(
            Res.string.minutes
        ),
        onValueChanged = {
            onSet(AutoPlayDuration.key, it)
        }
    )

    CheckItem(
        Icons.Outlined.HistoryToggleOff,
        IntervalTimeUnit.fromValue(calenderMode.intervalTimeUnit).toPairWithResString(),
        stringResource(Res.string.interval_time_unit),
        listOf(IntervalTimeUnit.S.toPairWithResString(), IntervalTimeUnit.M.toPairWithResString()),
        onCheck = {
            onSet(IntervalTimeUnit.key, it.first)
        }
    )

}