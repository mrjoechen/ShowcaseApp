package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.ui.view.IconItem
import isWeb
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiClientSettings(isBrowser: Boolean = isWeb(), onOpenProviders: (() -> Unit)? = null) {
    if (!aiFeaturesAvailable(isBrowser)) return
    var providers by remember { mutableStateOf(false) }
    var creations by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.padding(horizontal = 18.dp),
        tonalElevation = 2.dp, shadowElevation = 2.dp, shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            IconItem(Icons.Outlined.AutoFixHigh, stringResource(Res.string.ai_provider_settings_title), onClick = {
                if (onOpenProviders != null) onOpenProviders() else providers = true
            })
//            IconItem(Icons.Outlined.AutoAwesome, stringResource(Res.string.ai_creation_center_title), onClick = { creations = true })
        }
    }
    if (providers) AiProviderDialog { providers = false }
    if (creations) AiCreationCenter { creations = false }
}

@Composable
internal fun AiDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 650.dp).fillMaxWidth().heightIn(max = 900.dp)
            .fillMaxHeight(0.9f), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(Res.string.close)) }
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
internal fun AiMessage(message: StringResource?) {
    if (message != null) Text(stringResource(message), color = if (message in setOf(Res.string.ai_connection_test_available, Res.string.ai_image_saved)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
}

internal fun AiTaskStatus.label(): StringResource = when (this) {
    AiTaskStatus.QUEUED -> Res.string.ai_status_queued
    AiTaskStatus.RUNNING -> Res.string.ai_status_running
    AiTaskStatus.RETRY_WAIT -> Res.string.ai_status_retry_wait
    AiTaskStatus.SUCCEEDED -> Res.string.ai_status_succeeded
    AiTaskStatus.FAILED -> Res.string.ai_status_failed
    AiTaskStatus.RESULT_UNKNOWN -> Res.string.ai_status_result_unknown
    AiTaskStatus.CANCELLED -> Res.string.ai_status_cancelled
    AiTaskStatus.NEEDS_USER_ACTION -> Res.string.ai_status_needs_user_action
}

internal fun providerLabel(id: String): StringResource = when (id) {
    "openai" -> Res.string.ai_provider_openai
    "gemini-nano-banana" -> Res.string.ai_provider_gemini
    "openai-vision" -> Res.string.ai_provider_openai_vision
    "gemini-vision" -> Res.string.ai_provider_gemini_vision
    "deepseek-vision" -> Res.string.ai_provider_deepseek_vision
    else -> Res.string.ai_provider_unknown
}

internal fun aiStageLabel(stage: String): StringResource = when (stage) {
    "VALIDATING_CONFIGURATION" -> Res.string.ai_stage_validating
    "UPLOADING" -> Res.string.ai_stage_uploading
    "GENERATING" -> Res.string.ai_stage_generating
    "DOWNLOADING_RESULT" -> Res.string.ai_stage_downloading_result
    "PERSISTING_RESULT" -> Res.string.ai_stage_persisting_result
    else -> Res.string.ai_stage_preparing_input
}
