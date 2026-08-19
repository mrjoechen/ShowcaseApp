package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.play.SquareLayoutPolicy
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.LevelSliderItem
import com.alpha.showcase.common.ui.view.SlideItem
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.display_mode
import showcaseapp.composeapp.generated.resources.focus_scale
import showcaseapp.composeapp.generated.resources.photo_display_duration
import showcaseapp.composeapp.generated.resources.photo_size
import showcaseapp.composeapp.generated.resources.photo_spacing
import showcaseapp.composeapp.generated.resources.second

@Composable
fun SquareView(
    squareMode: Settings.SquareMode,
    onChanged: (Settings.SquareMode) -> Unit
) {
    SlideItem(
        icon = Icons.Outlined.Timer,
        desc = stringResource(Res.string.photo_display_duration),
        value = squareMode.interval.coerceIn(1, 60),
        range = 1f..60f,
        unit = stringResource(Res.string.second),
        onValueChanged = { onChanged(squareMode.copy(interval = it)) }
    )

    LevelSliderItem(
        icon = Icons.Outlined.PhotoSizeSelectLarge,
        desc = stringResource(Res.string.photo_size),
        value = SquareLayoutPolicy.itemSizeDp(squareMode.squareSize),
        levels = photoSizeLevelOptions(SquareLayoutPolicy.ITEM_SIZE_LEVELS_DP),
        onValueChanged = { onChanged(squareMode.copy(squareSize = it)) }
    )

    val focusScaleOptions = focusLevelOptions(
        SquareLayoutPolicy.FOCUS_SCALE_LEVELS_PERCENT
    )
    val focusScale = SquareLayoutPolicy.focusScalePercent(squareMode.focusScale)
    CheckItem(
        icon = Icons.Outlined.CenterFocusStrong,
        value = focusScaleOptions.firstOrNull { option ->
            option.value == focusScale
        }?.let { option ->
            option.value to option.label
        } ?: (focusScale to focusScale.toString()),
        desc = stringResource(Res.string.focus_scale),
        choices = focusScaleOptions.map { option -> option.value to option.label },
        onCheck = { onChanged(squareMode.copy(focusScale = it.first)) }
    )

    LevelSliderItem(
        icon = Icons.Outlined.GridView,
        desc = stringResource(Res.string.photo_spacing),
        value = SquareLayoutPolicy.spacingDp(squareMode.spacing),
        levels = spacingLevelOptions(SquareLayoutPolicy.SPACING_LEVELS_DP),
        onValueChanged = { onChanged(squareMode.copy(spacing = it)) }
    )

    val displayMode = if (squareMode.displayMode == DisplayMode.CenterCrop.value) {
        DisplayMode.CenterCrop
    } else {
        DisplayMode.Full
    }
    CheckItem(
        icon = Icons.Outlined.AspectRatio,
        value = displayMode.toPairWithResString(),
        desc = stringResource(Res.string.display_mode),
        choices = listOf(
            DisplayMode.Full.toPairWithResString(),
            DisplayMode.CenterCrop.toPairWithResString()
        ),
        onCheck = { onChanged(squareMode.copy(displayMode = it.first)) }
    )
}
