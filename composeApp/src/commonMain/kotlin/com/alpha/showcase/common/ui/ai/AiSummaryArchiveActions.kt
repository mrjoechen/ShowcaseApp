package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiSummaryArchiveActions(engine: AiEngine, enabled: Boolean, onBusyChanged: (Boolean) -> Unit) {
    val repository = engine.summaryRepository as? DatabaseSummaryRepository ?: return
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf<Long?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    LaunchedEffect(repository) { count = runCatching { repository.countSummaries() }.getOrNull() }
    fun launch(importing: Boolean, request: SummaryExportRequest = SummaryExportRequest()) {
        working = true; onBusyChanged(true); message = null
        scope.launch {
            try {
                if (importing) {
                    importSummaryArchive(repository)?.let {
                        message = getString(Res.string.ai_summary_imported, it.added, it.alreadyPresent, it.conflicts)
                    }
                } else {
                    exportSummaryFile(repository, request)?.let {
                        message = if (request.format == SummaryExportFormat.Csv) getString(Res.string.ai_summary_export_csv_complete)
                            else getString(Res.string.ai_summary_exported, it.revisions)
                    }
                }
                count = repository.countSummaries()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = getString(if (importing) Res.string.ai_summary_import_failed else Res.string.ai_summary_export_failed) }
            finally { working = false; onBusyChanged(false) }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.ai_summary_archive_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.ai_summary_archive_description), style = MaterialTheme.typography.bodySmall)
            count?.let { Text(stringResource(Res.string.ai_summary_archive_count, it), style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = enabled && !working, onClick = { launch(true) }) { Text(stringResource(Res.string.ai_summary_import)) }
                OutlinedButton(enabled = enabled && !working, onClick = { showExportDialog = true }) { Text(stringResource(Res.string.ai_summary_export)) }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showExportDialog) AiSummaryExportDialog(
        onDismiss = { showExportDialog = false },
        onConfirm = { request ->
            showExportDialog = false
            launch(importing = false, request = request)
        },
    )
}

/** Keep the layout and choices aligned with Android's SummaryDocumentDialogs. */
@Composable
internal fun AiSummaryExportDialog(onDismiss: () -> Unit, onConfirm: (SummaryExportRequest) -> Unit) {
    var format by remember { mutableStateOf(SummaryExportFormat.Archive) }
    var includeDiagnostics by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.ai_summary_export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.ai_summary_export_explanation))
                Column(Modifier.selectableGroup()) {
                    SummaryExportFormat.entries.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(format == choice, role = Role.RadioButton, onClick = {
                                    if (format != choice) {
                                        format = choice
                                        includeDiagnostics = false
                                    }
                                }).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = format == choice, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (choice == SummaryExportFormat.Archive) Res.string.ai_summary_export_format_archive
                                else Res.string.ai_summary_export_format_csv), Modifier.weight(1f))
                        }
                    }
                }
                Text(stringResource(if (format == SummaryExportFormat.Archive) Res.string.ai_summary_export_archive_explanation
                    else Res.string.ai_summary_export_csv_explanation))
                if (format == SummaryExportFormat.Archive) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(includeDiagnostics, role = Role.Checkbox, onValueChange = { includeDiagnostics = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeDiagnostics, onCheckedChange = null)
                        Text(stringResource(Res.string.ai_summary_export_diagnostics), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(SummaryExportRequest(format, format == SummaryExportFormat.Archive && includeDiagnostics)) }) {
                Text(stringResource(Res.string.ai_summary_document_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ai_summary_cancel)) } },
    )
}
