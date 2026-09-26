package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    LaunchedEffect(repository) { count = runCatching { repository.countSummaries() }.getOrNull() }
    fun launch(importing: Boolean) {
        working = true; onBusyChanged(true); message = null
        scope.launch {
            try {
                if (importing) {
                    importSummaryArchive(repository)?.let {
                        message = getString(Res.string.ai_summary_imported, it.added, it.alreadyPresent, it.conflicts)
                    }
                } else {
                    exportSummaryArchive(repository)?.let { message = getString(Res.string.ai_summary_exported, it.revisions) }
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
                OutlinedButton(enabled = enabled && !working, onClick = { launch(false) }) { Text(stringResource(Res.string.ai_summary_export)) }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
