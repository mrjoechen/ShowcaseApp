package com.alpha.showcase.common.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import usesRemoteNavigation

/** A filled, scaling remote-control action on TV; a standard icon button elsewhere. */
@Composable
internal fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useTvStyle: Boolean = usesRemoteNavigation(),
    content: @Composable () -> Unit,
) {
    if (!useTvStyle) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = enabled && focused
    val colors = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.tvIconButtonFocus(interaction, enabled),
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (highlighted) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (highlighted) colors.onPrimary else colors.onSurfaceVariant,
        ),
        content = content,
    )
}

/** TV focus is independent of the toggle's checked state and never changes its value. */
@Composable
internal fun TvIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useTvStyle: Boolean = usesRemoteNavigation(),
    content: @Composable () -> Unit,
) {
    if (!useTvStyle) {
        FilledTonalIconToggleButton(checked = checked, onCheckedChange = onCheckedChange,
            modifier = modifier, enabled = enabled, content = content)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = enabled && focused
    val colors = MaterialTheme.colorScheme
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.tvIconButtonFocus(interaction, enabled),
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = if (highlighted) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (highlighted) colors.onPrimary else colors.onSurfaceVariant,
            checkedContainerColor = if (highlighted) colors.primary else colors.secondaryContainer,
            checkedContentColor = if (highlighted) colors.onPrimary else colors.onSecondaryContainer,
        ),
        content = content,
    )
}

private fun Modifier.tvIconButtonFocus(interaction: MutableInteractionSource, enabled: Boolean): Modifier =
    // Keep the focused 48dp surface within a fixed 56dp slot, including in clipped toolbars.
    padding(4.dp).size(48.dp).then(
        if (enabled) Modifier.focusScaleEffect(
            focusScale = 1.15f,
            pressedScale = 1.08f,
            interactionSource = interaction,
        ) else Modifier
    )
