package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import com.alpha.showcase.common.ui.play.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.showcase.common.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiTaskDetail(engine: AiEngine, library: AiLibrary, task: AiTask, onNewTask: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var original by remember(task.id) { mutableStateOf(task.resultFile == null) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    val profiles = library.activeProfiles.filter { aiProviderCapability(it.providerId) == AiCapability.IMAGE_TO_IMAGE }
    var selectedProfile by remember(task.id) { mutableStateOf(library.generationProfileId ?: profiles.firstOrNull()?.id) }
    val file = if (original) task.originalFile ?: task.sourceFile else task.resultFile ?: task.sourceFile
    AiPage(stringResource(Res.string.ai_task_detail_title), onDismiss) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val imageModel = remember(engine, file) { DataWithType(engine.files.imageModel(file), file.substringAfterLast('.')) }
            val imageState = rememberMediaItemState(imageModel)
            Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).aspectRatio(1.5f)) {
                PagerItem(state = imageState, modifier = Modifier.fillMaxSize(), onInteraction = {})
            }
            if (original && imageState.metadata != null) {
                AiDetailSection(stringResource(Res.string.ai_original_metadata)) {
                    imageState.metadata?.rows.orEmpty().forEach { Text(it.text, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (task.resultFile != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(original, { original = true }, label = { Text(stringResource(Res.string.ai_show_original)) })
                FilterChip(!original, { original = false }, label = { Text(stringResource(Res.string.ai_show_generated)) })
            }
            AiTaskProgress(task)
            AiDetailSection(stringResource(Res.string.ai_configuration_snapshot)) {
                library.profile(task)?.let { profile ->
                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                    Text("${stringResource(providerLabel(profile.providerId))} · ${profile.model}")
                    Text(profile.host(), style = MaterialTheme.typography.bodySmall)
                }
                LocalAiStyleCatalog.styles().firstOrNull { it.key == task.styleKey }?.let {
                    Text(stringResource(it.displayNameRes), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(Res.string.ai_provider_sent_prompt), style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(task.prompt, style = MaterialTheme.typography.bodySmall) }
            }
            AiDetailSection(stringResource(Res.string.ai_file_history)) {
                Text(stringResource(Res.string.ai_created_at, formatAiCreatedAt(task.createdAt)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(Res.string.ai_source_image_description) + ": " + task.sourceFile, style = MaterialTheme.typography.bodySmall)
                task.resultFile?.let { Text(stringResource(Res.string.ai_generated_image_description) + ": " + it, style = MaterialTheme.typography.bodySmall) }
            }
            if (task.attempts.isNotEmpty()) AiDetailSection(stringResource(Res.string.ai_attempt_history)) {
                task.attempts.asReversed().forEach { attempt ->
                    Text(stringResource(Res.string.ai_attempt_number, attempt.number), style = MaterialTheme.typography.titleSmall)
                    Text("${stringResource(attempt.status.label())} · ${stringResource(aiStageLabel(attempt.stage))}", style = MaterialTheme.typography.bodySmall)
                    Text("${formatAiCreatedAt(attempt.startedAt)} – ${formatAiCreatedAt(attempt.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                    attempt.errorCategory?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (task.status.terminal && task.status != AiTaskStatus.SUCCEEDED) AiProfileSelector(profiles, selectedProfile, onSelected = { selectedProfile = it })
            AiMessage(message)
        AiTaskActions(engine, task, selectedProfile, onNewTask)
        OutlinedButton(onClick = {
            busy = true
            scope.launch {
                try { if (exportAiImage(file, engine.files.read(file))) message = Res.string.ai_image_saved }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = Res.string.ai_image_save_failed }
                finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (original) Res.string.ai_save_original_to_album else Res.string.ai_save_generated_to_album))
        }
        if (task.status.terminal) TextButton(onClick = { deleting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.ai_delete_task))
        }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(Res.string.ai_delete_task)) }, text = { Text(stringResource(Res.string.ai_delete_image_confirm)) },
        confirmButton = { TextButton(onClick = {
            deleting = false; busy = true
            scope.launch {
                try { engine.deleteTask(task.id); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = Res.string.ai_delete_failed }
                finally { busy = false }
            }
        }) { Text(stringResource(Res.string.confirm)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(Res.string.cancel)) } })
}

@Composable
private fun AiDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun formatAiCreatedAt(timestamp: Long): String =
    Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace('T', ' ').substringBefore('.')
