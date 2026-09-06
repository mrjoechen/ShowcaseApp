package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.showcase.common.ai.*
import isWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiCreationCenter(onDismiss: () -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember { AiServices.engine }
    val library by engine.library.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var configure by remember { mutableStateOf(false) }
    LaunchedEffect(engine) {
        try { engine.initialize() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = Res.string.ai_task_load_failed }
    }
    AiDialog(stringResource(Res.string.ai_creation_center_title), onDismiss) {
        AiMessage(message)
        if (library.tasks.isEmpty()) {
            Text(stringResource(Res.string.ai_creations_empty), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { configure = true }) { Text(stringResource(Res.string.ai_configure_service)) }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(library.tasks, key = { it.id }) { task ->
                Card(Modifier.clickable { selected = task.id }, shape = RoundedCornerShape(16.dp)) {
                    AsyncImage(engine.files.imageModel(task.resultFile ?: task.sourceFile), stringResource(Res.string.ai_generated_image_description),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LocalAiStyleCatalog.styles().firstOrNull { it.key == task.styleKey }?.let { Text(stringResource(it.displayNameRes), style = MaterialTheme.typography.titleSmall) }
                        Text(stringResource(task.status.label()), style = MaterialTheme.typography.bodySmall)
                        if (!task.status.terminal) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    library.tasks.firstOrNull { it.id == selected }?.let { task ->
        AiTaskDetail(engine, library, task, onNewTask = { selected = it }, onDismiss = { selected = null })
    }
    if (configure) AiProviderDialog { configure = false }
}

@Composable
private fun AiTaskDetail(engine: AiEngine, library: AiLibrary, task: AiTask, onNewTask: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var original by remember(task.id) { mutableStateOf(task.resultFile == null) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    val profiles = library.activeProfiles.filter { aiProviderCapability(it.providerId) == AiCapability.IMAGE_TO_IMAGE }
    var selectedProfile by remember(task.id) { mutableStateOf(library.generationProfileId ?: profiles.firstOrNull()?.id) }
    val file = if (original) task.sourceFile else task.resultFile ?: task.sourceFile
    AiDialog(stringResource(Res.string.ai_task_detail_title), onDismiss) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(engine.files.imageModel(file), stringResource(if (original) Res.string.ai_source_image_description else Res.string.ai_generated_image_description),
                modifier = Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit)
            if (task.resultFile != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(original, { original = true }, label = { Text(stringResource(Res.string.ai_show_original)) })
                FilterChip(!original, { original = false }, label = { Text(stringResource(Res.string.ai_show_generated)) })
            }
            AiTaskProgress(task)
            library.profile(task)?.let { profile ->
                Text("${profile.name} · ${profile.model}", style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(Res.string.ai_provider_sent_prompt), style = MaterialTheme.typography.titleSmall)
            Text(task.prompt, style = MaterialTheme.typography.bodySmall)
            if (task.status.terminal && task.status != AiTaskStatus.SUCCEEDED) AiProfileSelector(profiles, selectedProfile, onSelected = { selectedProfile = it })
            AiMessage(message)
        }
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
