package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.focusScaleEffect
import com.alpha.showcase.common.ui.source.Config
import com.alpha.showcase.common.ui.source.Delete
import com.alpha.showcase.common.ui.source.Operation
import com.alpha.showcase.common.ui.tvScreenFocus
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.delete
import showcaseapp.composeapp.generated.resources.edit
import usesRemoteNavigation

/** The source card's edit and delete actions as a list a remote can focus. Back dismisses it. */
@Composable
internal fun SourceActionDialog(
    title: String,
    operations: List<Operation>,
    onSelect: (Operation) -> Unit,
    onDismiss: () -> Unit,
    useTvNavigation: Boolean = usesRemoteNavigation(),
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 5.dp,
            shadowElevation = 9.dp,
        ) {
            Column(
                modifier = Modifier.widthIn(min = 280.dp).padding(Dimen.spaceL),
                verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Dimen.spaceM, vertical = Dimen.spaceS),
                )
                // Resolve input mode and focus inside the dialog's own Compose scene.
                Column(
                    modifier = Modifier.tvScreenFocus(enabled = useTvNavigation),
                    verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
                ) {
                    operations.forEach { operation ->
                        when (operation) {
                            is Config -> SourceActionItem(
                                icon = Icons.Outlined.EditNote,
                                label = stringResource(Res.string.edit),
                                accent = MaterialTheme.colorScheme.primary,
                                onAccent = MaterialTheme.colorScheme.onPrimary,
                                idleContent = MaterialTheme.colorScheme.onSurface,
                                onClick = { onSelect(operation) },
                            )

                            is Delete -> SourceActionItem(
                                icon = Icons.Outlined.DeleteOutline,
                                label = stringResource(Res.string.delete),
                                accent = MaterialTheme.colorScheme.error,
                                onAccent = MaterialTheme.colorScheme.onError,
                                idleContent = MaterialTheme.colorScheme.error,
                                onClick = { onSelect(operation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceActionItem(
    icon: ImageVector,
    label: String,
    accent: Color,
    onAccent: Color,
    idleContent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusScaleEffect(interactionSource = interaction),
        shape = MaterialTheme.shapes.medium,
        color = if (focused) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (focused) onAccent else idleContent,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimen.spaceL, vertical = Dimen.spaceM + Dimen.spaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimen.spaceM),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
