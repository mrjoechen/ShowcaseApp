package com.alpha.showcase.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.data.Settings
import com.alpha.showcase.common.ui.settings.AutoPlayDuration
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.settings.DisplayMode
import com.alpha.showcase.common.ui.settings.IntervalTimeUnit
import com.alpha.showcase.common.ui.settings.Orientation
import com.alpha.showcase.common.ui.settings.ShowTimeProgressIndicator
import com.alpha.showcase.common.ui.view.SlideItem
import com.alpha.showcase.common.ui.view.SwitchItem

/**
 *   - Slide
 *
 *      - Display (Full screen, Fit screen)
 *      - Orientation (Horizontal, Vertical)
 *      - Auto play (Interval)
 *      - Interval time
 *      - Interval time unit
 *      - Show time progress
 *      - Show content meta info
 *      - Sort rule
 */
@Composable
fun SlideModeView(slideMode: Settings.SlideMode, onSet: (String, Any) -> Unit){

    CheckItem(
        if (slideMode.displayMode == DisplayMode.FitScreen.value) Icons.Outlined.FitScreen else Icons.Outlined.FullscreenExit,
        DisplayMode.fromValue(slideMode.displayMode).toPairWithResString(),
        "Display mode",
        listOf(DisplayMode.FitScreen.toPairWithResString(), DisplayMode.CenterCrop.toPairWithResString()),
        onCheck = {
            onSet(DisplayMode.key, it.first)
        }
    )

    CheckItem(
        if (slideMode.orientation == Orientation.Horizontal.value) Icons.Outlined.WebStories else Icons.Outlined.ViewDay,
        Orientation.fromValue(slideMode.orientation).toPairWithResString(),
        "orientation",
        listOf(Orientation.Horizontal.toPairWithResString(), Orientation.Vertical.toPairWithResString()),
        onCheck = {
            onSet(Orientation.key, it.first)
        }
    )

    SwitchItem(
        Icons.Outlined.ModelTraining,
        check = slideMode.showTimeProgressIndicator,
        desc = "Show time progress",
        onCheck = {
            onSet(ShowTimeProgressIndicator.key, it)
        }
    )

//    SwitchItem(
//        Icons.Outlined.Ballot,
//        check = slideMode.showContentMetaInfo,
//        desc = stringResource(R.string.show_content_meta_info),
//        onCheck = {
//            onSet(ShowContentMetaInfo.key, it)
//        }
//    )
    val secondRange = 5f .. 60f
    val minuteRange = 1f .. 15f

    SlideItem(
        Icons.Outlined.Timer,
        desc = "Auto play",
        value = if (slideMode.intervalTime.toInt() == 0) {
            if (slideMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
        } else if ((slideMode.intervalTimeUnit == 0 && slideMode.intervalTime.toFloat() !in secondRange) || (slideMode.intervalTimeUnit == 1 && slideMode.intervalTime.toFloat() !in minuteRange))
            if (slideMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
        else
            slideMode.intervalTime.toInt(),
        range = if (slideMode.intervalTimeUnit == 0) secondRange else minuteRange,
        step = if (slideMode.intervalTimeUnit == 0) ((secondRange.endInclusive - secondRange.start) / 5 - 1).toInt() else (minuteRange.endInclusive - minuteRange.start - 1).toInt(),
        unit = if (slideMode.intervalTimeUnit == 0) " s" else " m",
        onValueChanged = {
            onSet(AutoPlayDuration.key, it)
        }
    )

    CheckItem(
        Icons.Outlined.HistoryToggleOff,
        IntervalTimeUnit.fromValue(slideMode.intervalTimeUnit).toPairWithResString(),
        "Time unit",
        listOf(IntervalTimeUnit.S.toPairWithResString(), IntervalTimeUnit.M.toPairWithResString()),
        onCheck = {
            onSet(IntervalTimeUnit.key, it.first)
        }
    )

//    CheckItem(
//        Icons.Outlined.Sort,
//        SortRule.fromValue(slideMode.sortRule).toPair(),
//        stringResource(id = R.string.sort_rule),
//        listOf(SortRule.Random.toPair(), SortRule.NameAsc.toPair(), SortRule.NameDesc.toPair(), SortRule.DateAsc.toPair(), SortRule.DateDesc.toPair()),
//        onCheck = {
//            onSet(SortRule.key, it.first)
//        }
//    )


}