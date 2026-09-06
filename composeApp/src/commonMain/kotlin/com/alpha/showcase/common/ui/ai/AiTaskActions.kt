package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.alpha.showcase.common.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiTaskActions(engine: AiEngine, task: AiTask, profileId: String?, onNewTask: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmCancel by remember(task.id) { mutableStateOf(false) }
    var confirmRetry by remember(task.id) { mutableStateOf(false) }
    var busy by remember(task.id) { mutableStateOf(false) }
    var message by remember(task.id) { mutableStateOf<StringResource?>(null) }
    fun retry(confirmed: Boolean) {
        val selected = profileId ?: return
        busy = true
        scope.launch {
            try { onNewTask(engine.retry(task.id, selected, confirmed)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = Res.string.ai_generation_retry_failed }
            finally { busy = false }
        }
    }
    if (!task.status.terminal) OutlinedButton(onClick = { confirmCancel = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.ai_cancel_task))
    } else if (task.status != AiTaskStatus.SUCCEEDED) Button(onClick = {
        if (task.status == AiTaskStatus.RESULT_UNKNOWN) confirmRetry = true else retry(false)
    }, enabled = !busy && profileId != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_retry)) }
    AiMessage(message)
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text(stringResource(Res.string.ai_cancel_confirm)) },
        text = { Text(stringResource(Res.string.ai_cancel_upstream_warning)) },
        confirmButton = { TextButton(onClick = {
            confirmCancel = false; busy = true
            scope.launch {
                try { engine.cancel(task.id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = Res.string.ai_generation_cancel_failed }
                finally { busy = false }
            }
        }) { Text(stringResource(Res.string.confirm)) } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(Res.string.cancel)) } })
    if (confirmRetry) AlertDialog(onDismissRequest = { confirmRetry = false },
        title = { Text(stringResource(Res.string.ai_retry)) }, text = { Text(stringResource(Res.string.ai_retry_unknown_warning)) },
        confirmButton = { TextButton(onClick = { confirmRetry = false; retry(true) }) { Text(stringResource(Res.string.confirm)) } },
        dismissButton = { TextButton(onClick = { confirmRetry = false }) { Text(stringResource(Res.string.cancel)) } })
}
