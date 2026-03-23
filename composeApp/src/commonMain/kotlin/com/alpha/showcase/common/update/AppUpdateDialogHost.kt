package com.alpha.showcase.common.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.downloading_update
import showcaseapp.composeapp.generated.resources.published_at
import showcaseapp.composeapp.generated.resources.update
import showcaseapp.composeapp.generated.resources.update_version
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun AppUpdateDialogHost(viewModel: AppUpdateViewModel = AppUpdateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val updateInfo = uiState.latestUpdate ?: return
    val progress = uiState.installProgress
    val progressFraction = progress?.fraction

    AlertDialog(
        onDismissRequest = {
            if (!uiState.installing) {
                viewModel.dismissUpdateDialog()
            }
        },
        title = {
            Text(updateInfo.releaseTitle.ifBlank { updateInfo.tagName })
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${stringResource(Res.string.update_version)} ${updateInfo.tagName}",
                    fontSize = 13.sp,
                    color = LocalContentColor.current.copy(alpha = 0.8f)
                )

                updateInfo.publishedAt
                    ?.substringBefore("T")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { publishedAt ->
                        Text(
                            text = "${stringResource(Res.string.published_at)} $publishedAt",
                            fontSize = 13.sp,
                            color = LocalContentColor.current.copy(alpha = 0.8f)
                        )
                    }

                if (uiState.installing) {
                    Text(
                        text = stringResource(Res.string.downloading_update),
                        fontSize = 13.sp
                    )
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = { progressFraction }
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    progress?.let {
                        Text(
                            text = buildString {
                                append(formatFileSize(it.downloadedBytes))
                                it.totalBytes?.takeIf { total -> total > 0 }?.let { total ->
                                    append(" / ")
                                    append(formatFileSize(total))
                                }
                                progressFraction?.let { fraction ->
                                    append(" (")
                                    append((fraction * 100).roundToInt())
                                    append("%)")
                                }
                            },
                            fontSize = 12.sp,
                            color = LocalContentColor.current.copy(alpha = 0.7f)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.sizeIn(maxHeight = 300.dp, maxWidth = 500.dp)
                ) {
                    items(updateInfo.releaseNotes.ifBlank { "-" }.lines()) { line ->
                        Text(
                            text = if (line.isBlank()) " " else line,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !uiState.installing && updateInfo.canInstall,
                onClick = {
                    viewModel.installUpdate()
                }
            ) {
                Text(stringResource(Res.string.update))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !uiState.installing,
                onClick = {
                    viewModel.dismissUpdateDialog()
                }
            ) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 4)
    val value = bytes / 1024.0.pow(unit.toDouble())
    val suffix = listOf("KB", "MB", "GB", "TB")[unit - 1]
    val rounded = (value * 10).roundToInt() / 10.0
    return "$rounded $suffix"
}
