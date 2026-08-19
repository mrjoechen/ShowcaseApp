package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.play.WaterfallLayoutPolicy
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.LevelSliderItem
import com.alpha.showcase.common.ui.view.SlideItem
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.orientation
import showcaseapp.composeapp.generated.resources.waterfall_column_count
import showcaseapp.composeapp.generated.resources.waterfall_row_count
import showcaseapp.composeapp.generated.resources.waterfall_scroll_speed
import showcaseapp.composeapp.generated.resources.waterfall_spacing

@Composable
fun WaterfallView(
    waterfallMode: Settings.WaterfallMode,
    onChanged: (Settings.WaterfallMode) -> Unit
) {
    val isVertical = waterfallMode.orientation == WaterfallOrientation.Vertical.value
    val orientationIcon = if (isVertical) {
        Icons.Outlined.ViewColumn
    } else {
        Icons.Outlined.ViewStream
    }

    CheckItem(
        icon = orientationIcon,
        value = WaterfallOrientation.fromValue(waterfallMode.orientation).toPairWithResString(),
        desc = stringResource(Res.string.orientation),
        choices = listOf(
            WaterfallOrientation.Vertical.toPairWithResString(),
            WaterfallOrientation.Horizontal.toPairWithResString()
        ),
        onCheck = { onChanged(waterfallMode.copy(orientation = it.first)) }
    )

    SlideItem(
        icon = orientationIcon,
        desc = stringResource(
            if (isVertical) {
                Res.string.waterfall_column_count
            } else {
                Res.string.waterfall_row_count
            }
        ),
        value = WaterfallLayoutPolicy.laneCount(waterfallMode.laneCount),
        range = WaterfallLayoutPolicy.LANE_COUNT_RANGE.first.toFloat()..
            WaterfallLayoutPolicy.LANE_COUNT_RANGE.last.toFloat(),
        step = WaterfallLayoutPolicy.LANE_COUNT_RANGE.last -
            WaterfallLayoutPolicy.LANE_COUNT_RANGE.first - 1,
        onValueChanged = { onChanged(waterfallMode.copy(laneCount = it)) }
    )

    LevelSliderItem(
        icon = Icons.Outlined.Speed,
        desc = stringResource(Res.string.waterfall_scroll_speed),
        value = WaterfallLayoutPolicy.scrollSpeed(waterfallMode.scrollSpeed),
        levels = speedLevelOptions(WaterfallLayoutPolicy.SCROLL_SPEED_LEVELS),
        onValueChanged = { onChanged(waterfallMode.copy(scrollSpeed = it)) }
    )

    LevelSliderItem(
        icon = Icons.Outlined.GridView,
        desc = stringResource(Res.string.waterfall_spacing),
        value = WaterfallLayoutPolicy.spacingDp(waterfallMode.spacing),
        levels = spacingLevelOptions(WaterfallLayoutPolicy.SPACING_LEVELS_DP),
        onValueChanged = { onChanged(waterfallMode.copy(spacing = it)) }
    )
}
