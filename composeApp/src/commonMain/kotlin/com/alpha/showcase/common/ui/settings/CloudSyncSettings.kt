package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.sync.ConfigSync
import com.alpha.showcase.common.sync.ConfigSyncStatus
import com.alpha.showcase.common.ui.view.IconItem
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.cloud_sync_account_changed_hint
import showcaseapp.composeapp.generated.resources.cloud_sync_close
import showcaseapp.composeapp.generated.resources.cloud_sync_credentials_notice
import showcaseapp.composeapp.generated.resources.cloud_sync_delete_cloud_data
import showcaseapp.composeapp.generated.resources.cloud_sync_delete_cloud_data_hint
import showcaseapp.composeapp.generated.resources.cloud_sync_description
import showcaseapp.composeapp.generated.resources.cloud_sync_disable
import showcaseapp.composeapp.generated.resources.cloud_sync_enable
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
import showcaseapp.composeapp.generated.resources.cloud_sync_title
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
    val scope = rememberCoroutineScope()
    val enabled = status.isEnabled()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cloud_sync_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.cloud_sync_description))
                Text(
                    stringResource(Res.string.cloud_sync_credentials_notice),
                    color = MaterialTheme.colorScheme.error,
                )
                when (status) {
                    ConfigSyncStatus.Unavailable -> Text(stringResource(Res.string.cloud_sync_unavailable_hint))
                    ConfigSyncStatus.AccountChanged -> Text(stringResource(Res.string.cloud_sync_account_changed_hint))
                    else -> Unit
                }
                if (enabled) {
                    Text(
                        stringResource(status.label()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { scope.launch { ConfigSync.syncNow() } },
                    ) { Text(stringResource(Res.string.cloud_sync_now)) }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch { ConfigSync.clearCloudDataAndDisable() }
                            onDismiss()
                        },
                    ) { Text(stringResource(Res.string.cloud_sync_delete_cloud_data), color = MaterialTheme.colorScheme.error) }
                    Text(
                        stringResource(Res.string.cloud_sync_delete_cloud_data_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { ConfigSync.setEnabled(!enabled) }
                if (enabled) onDismiss()
            }) {
                Text(stringResource(if (enabled) Res.string.cloud_sync_disable else Res.string.cloud_sync_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cloud_sync_close)) }
        },
    )
}

private fun ConfigSyncStatus.isEnabled(): Boolean = when (this) {
    ConfigSyncStatus.Unsupported, ConfigSyncStatus.Disabled, ConfigSyncStatus.AccountChanged -> false
    else -> true
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
