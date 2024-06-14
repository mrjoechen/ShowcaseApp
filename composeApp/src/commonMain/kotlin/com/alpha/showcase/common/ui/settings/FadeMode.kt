package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.data.Settings
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.SlideItem
import com.alpha.showcase.common.ui.view.SwitchItem

@Composable
fun FadeModeView(fadeMode: Settings.FadeMode, onSet: (String, Any) -> Unit) {
  CheckItem(
    if (fadeMode.displayMode == DisplayMode.FitScreen.value) Icons.Outlined.FitScreen else Icons.Outlined.FullscreenExit,
    DisplayMode.fromValue(fadeMode.displayMode).toPairWithResString(),
    "Display Mode",
    listOf(DisplayMode.FitScreen.toPairWithResString(), DisplayMode.CenterCrop.toPairWithResString()),
    onCheck = {
      onSet(DisplayMode.key, it.first)
    }
  )

  SwitchItem(
    Icons.Outlined.ModelTraining,
    check = fadeMode.showTimeProgressIndicator,
    desc = "Time indicator",
    onCheck = {
      onSet(ShowTimeProgressIndicator.key, it)
    }
  )

  val secondRange = 5f .. 60f
  val minuteRange = 1f .. 30f

  SlideItem(
    Icons.Outlined.Timer,
    desc = "Auto",
    value = if (fadeMode.intervalTime == 0) {
      if (fadeMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
    } else if ((fadeMode.intervalTimeUnit == 0 && fadeMode.intervalTime.toFloat() !in secondRange) || (fadeMode.intervalTimeUnit == 1 && fadeMode.intervalTime.toFloat() !in minuteRange))
      if (fadeMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
    else
      fadeMode.intervalTime,
    range = if (fadeMode.intervalTimeUnit == 0) secondRange else minuteRange,
    step = if (fadeMode.intervalTimeUnit == 0) 10 else 28,
    unit = if (fadeMode.intervalTimeUnit == 0) " s" else " m",
    onValueChanged = {
      onSet(AutoPlayDuration.key, it)
    }
  )

  CheckItem(
    Icons.Outlined.HistoryToggleOff,
    IntervalTimeUnit.fromValue(fadeMode.intervalTimeUnit).toPairWithResString(),
    "time unit",
    listOf(IntervalTimeUnit.S.toPairWithResString(), IntervalTimeUnit.M.toPairWithResString()),
    onCheck = {
      onSet(IntervalTimeUnit.key, it.first)
    }
  )

}