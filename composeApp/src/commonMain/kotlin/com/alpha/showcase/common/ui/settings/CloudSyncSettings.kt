package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.sync.ConfigSync
import com.alpha.showcase.common.sync.ConfigSyncStatus
import com.alpha.showcase.common.ui.tvScreenFocus
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.ui.view.rememberMobileHaptic
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.cloud_sync_account_changed_hint
import showcaseapp.composeapp.generated.resources.cloud_sync_credentials_notice
import showcaseapp.composeapp.generated.resources.cloud_sync_credentials_title
import showcaseapp.composeapp.generated.resources.cloud_sync_delete_cloud_data
import showcaseapp.composeapp.generated.resources.cloud_sync_delete_cloud_data_hint
import showcaseapp.composeapp.generated.resources.cloud_sync_done
import showcaseapp.composeapp.generated.resources.cloud_sync_now
import showcaseapp.composeapp.generated.resources.cloud_sync_status_account_changed
import showcaseapp.composeapp.generated.resources.cloud_sync_status_failed
import showcaseapp.composeapp.generated.resources.cloud_sync_status_needs_upgrade
import showcaseapp.composeapp.generated.resources.cloud_sync_status_off
import showcaseapp.composeapp.generated.resources.cloud_sync_status_quota
import showcaseapp.composeapp.generated.resources.cloud_sync_status_synced
import showcaseapp.composeapp.generated.resources.cloud_sync_status_syncing
import showcaseapp.composeapp.generated.resources.cloud_sync_status_unavailable
import showcaseapp.composeapp.generated.resources.cloud_sync_status_waiting
import showcaseapp.composeapp.generated.resources.cloud_sync_synced_items
import showcaseapp.composeapp.generated.resources.cloud_sync_synced_items_title
import showcaseapp.composeapp.generated.resources.cloud_sync_title
import showcaseapp.composeapp.generated.resources.cloud_sync_toggle
import showcaseapp.composeapp.generated.resources.cloud_sync_unavailable_hint

/** Settings entry for configuration sync; renders nothing where the platform has no cloud store. */
@Composable
fun CloudSyncSettings() {
    val status by ConfigSync.status.collectAsState()
    if (status == ConfigSyncStatus.Unsupported) return
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.padding(horizontal = 18.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        IconItem(Icons.Outlined.Cloud, stringResource(Res.string.cloud_sync_title), onClick = { showDialog = true }) {
            Text(
                text = stringResource(status.label()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    if (showDialog) CloudSyncDialog(status) { showDialog = false }
}

@Composable
private fun CloudSyncDialog(status: ConfigSyncStatus, onDismiss: () -> Unit) {
    val enabled = status.isEnabled()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
        title = { Text(stringResource(Res.string.cloud_sync_title)) },
        text = {
            Column(
                // On TV, focus starts on the switch so the remote can reach every row.
                modifier = Modifier.verticalScroll(rememberScrollState()).tvScreenFocus(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SyncControlCard(status, enabled)
                when (status) {
                    ConfigSyncStatus.Unavailable -> NoticeCard(
                        Icons.Outlined.ErrorOutline,
                        stringResource(Res.string.cloud_sync_unavailable_hint),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                    ConfigSyncStatus.AccountChanged -> NoticeCard(
                        Icons.Outlined.Info,
                        stringResource(Res.string.cloud_sync_account_changed_hint),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    else -> Unit
                }
                DialogCard {
                    DialogRow(
                        icon = { RowIcon(Icons.Outlined.CloudUpload) },
                        title = stringResource(Res.string.cloud_sync_synced_items_title),
                        subtitle = stringResource(Res.string.cloud_sync_synced_items),
                    )
                    RowDivider()
                    DialogRow(
                        icon = { RowIcon(Icons.Outlined.Key) },
                        title = stringResource(Res.string.cloud_sync_credentials_title),
                        subtitle = stringResource(Res.string.cloud_sync_credentials_notice),
                    )
                }
                if (enabled) {
                    val error = MaterialTheme.colorScheme.error
                    DialogCard {
                        DialogRow(
                            modifier = Modifier.clickable(role = Role.Button) {
                                ConfigSync.clearCloudDataAndDisable()
                                onDismiss()
                            },
                            icon = { RowIcon(Icons.Outlined.DeleteOutline, error) },
                            title = stringResource(Res.string.cloud_sync_delete_cloud_data),
                            titleColor = error,
                            subtitle = stringResource(Res.string.cloud_sync_delete_cloud_data_hint),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cloud_sync_done)) }
        },
    )
}

/** The on/off switch with the live status, plus Sync Now while sync is on. */
@Composable
private fun SyncControlCard(status: ConfigSyncStatus, enabled: Boolean) {
    val performHaptic = rememberMobileHaptic()
    val accent = status.accent()
    DialogCard {
        DialogRow(
            modifier = Modifier.toggleable(value = enabled, role = Role.Switch) {
                performHaptic()
                ConfigSync.setEnabled(it)
            },
            icon = { RowIcon(status.icon(), accent) },
            title = stringResource(Res.string.cloud_sync_toggle),
            subtitle = stringResource(status.label()),
            subtitleColor = accent,
        ) { Switch(checked = enabled, onCheckedChange = null) }
        if (enabled) {
            val syncing = status == ConfigSyncStatus.Syncing
            val primary = MaterialTheme.colorScheme.primary
            RowDivider()
            DialogRow(
                modifier = Modifier.clickable(enabled = !syncing, role = Role.Button) { ConfigSync.syncNow() },
                icon = {
                    if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else RowIcon(Icons.Outlined.Sync, primary)
                },
                title = stringResource(Res.string.cloud_sync_now),
                titleColor = primary,
            )
        }
    }
}

private val ROW_PADDING_HORIZONTAL = 16.dp
private val ROW_ICON_SIZE = 24.dp
private val ROW_ICON_GAP = 14.dp

@Composable
private fun DialogCard(
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color) {
        Column { content() }
    }
}

/** Every row in the dialog shares this grid so icons and text line up across cards. */
@Composable
private fun DialogRow(
    icon: @Composable () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_PADDING_HORIZONTAL, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_ICON_GAP),
    ) {
        Box(Modifier.size(ROW_ICON_SIZE), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            if (subtitle != null) Text(subtitle, style = descriptionStyle(), color = subtitleColor)
        }
        trailing?.invoke()
    }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(ROW_ICON_SIZE))
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = ROW_PADDING_HORIZONTAL + ROW_ICON_SIZE + ROW_ICON_GAP),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun NoticeCard(icon: ImageVector, text: String, containerColor: Color, contentColor: Color) {
    DialogCard(color = containerColor) {
        DialogRow(icon = { RowIcon(icon, contentColor) }, title = null, subtitle = text, subtitleColor = contentColor)
    }
}

/** The app's bodySmall is bold; explanatory text reads lighter so the row titles stand out. */
@Composable
private fun descriptionStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp)

private fun ConfigSyncStatus.isEnabled(): Boolean = when (this) {
    ConfigSyncStatus.Unsupported, ConfigSyncStatus.Disabled, ConfigSyncStatus.AccountChanged -> false
    else -> true
}

private fun ConfigSyncStatus.icon(): ImageVector = when (this) {
    ConfigSyncStatus.Unsupported, ConfigSyncStatus.Disabled, ConfigSyncStatus.Unavailable -> Icons.Outlined.CloudOff
    ConfigSyncStatus.Syncing -> Icons.Outlined.CloudSync
    is ConfigSyncStatus.Synced -> Icons.Outlined.CloudDone
    ConfigSyncStatus.Waiting -> Icons.Outlined.CloudQueue
    ConfigSyncStatus.NeedsUpgrade, ConfigSyncStatus.QuotaExceeded,
    ConfigSyncStatus.AccountChanged, is ConfigSyncStatus.Failed -> Icons.Outlined.SyncProblem
}

@Composable
private fun ConfigSyncStatus.accent(): Color = when (this) {
    ConfigSyncStatus.Syncing, is ConfigSyncStatus.Synced -> MaterialTheme.colorScheme.primary
    ConfigSyncStatus.Unsupported, ConfigSyncStatus.Disabled, ConfigSyncStatus.Waiting ->
        MaterialTheme.colorScheme.onSurfaceVariant
    ConfigSyncStatus.Unavailable, ConfigSyncStatus.NeedsUpgrade, ConfigSyncStatus.QuotaExceeded,
    ConfigSyncStatus.AccountChanged, is ConfigSyncStatus.Failed -> MaterialTheme.colorScheme.error
}

private fun ConfigSyncStatus.label(): StringResource = when (this) {
    ConfigSyncStatus.Unsupported, ConfigSyncStatus.Disabled -> Res.string.cloud_sync_status_off
    ConfigSyncStatus.Unavailable -> Res.string.cloud_sync_status_unavailable
    ConfigSyncStatus.Syncing -> Res.string.cloud_sync_status_syncing
    is ConfigSyncStatus.Synced -> Res.string.cloud_sync_status_synced
    ConfigSyncStatus.Waiting -> Res.string.cloud_sync_status_waiting
    ConfigSyncStatus.NeedsUpgrade -> Res.string.cloud_sync_status_needs_upgrade
    ConfigSyncStatus.QuotaExceeded -> Res.string.cloud_sync_status_quota
    ConfigSyncStatus.AccountChanged -> Res.string.cloud_sync_status_account_changed
    is ConfigSyncStatus.Failed -> Res.string.cloud_sync_status_failed
}
