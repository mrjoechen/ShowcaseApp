package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.BuiltInProviderCatalog
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.theme.Dimen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

private enum class ProfileAction { SAVE, TEST, MODELS }
private enum class ProfileField { URL, TOKEN, MODEL, ORIGIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiProfileEditorDialog(engine: AiEngine, capability: AiCapability, existing: AiProfile?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val providers = remember(capability) { BuiltInProviderCatalog.ids.filter { aiProviderCapability(it.value) == capability } }
    var provider by remember { mutableStateOf(existing?.providerId ?: providers.first().value) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: aiProviderBaseUrl(provider)) }
    var model by remember { mutableStateOf(existing?.model ?: aiProviderModel(provider)) }
    var token by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(existing?.allowInsecureHttp ?: false) }
    var originConfirmed by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<ProfileAction?>(null) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var errors by remember { mutableStateOf<Map<ProfileField, StringResource>>(emptyMap()) }
    var models by remember { mutableStateOf<List<ProviderModel>>(emptyList()) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var catalogMessage by remember { mutableStateOf<StringResource?>(null) }
    val busy = action != null
    val originChanged = existing != null && !canReuseAiCredential(existing.providerId, existing.baseUrl, provider, baseUrl.trim())
    val dismiss = { if (!busy) onDismiss() }

    fun clearFeedback() { errors = emptyMap(); message = null; catalogMessage = null }
    fun clearCatalog() { models = emptyList(); modelsExpanded = false; clearFeedback() }
    fun chooseProvider(id: String) {
        if (provider == id) return
        provider = id; baseUrl = aiProviderBaseUrl(id); model = aiProviderModel(id)
        token = ""; allowHttp = false; originConfirmed = false; clearCatalog()
    }
    fun draft(): AiProfile {
        val providerName = engine.client.providerDescriptor(ProviderId(provider))?.displayName ?: provider
        return AiProfile(existing?.id.orEmpty(), name = existing?.name ?: "$providerName · ${model.trim()}",
            providerId = provider, model = model.trim(), baseUrl = baseUrl.trim(), encryptedToken = "", allowInsecureHttp = allowHttp)
    }
    fun perform(next: ProfileAction) {
        errors = buildMap {
            if (!isValidAiBaseUrl(baseUrl.trim(), allowHttp)) put(ProfileField.URL,
                if (baseUrl.trim().startsWith("http://", ignoreCase = true) && !allowHttp) Res.string.ai_profile_error_cleartext_confirmation
                else Res.string.ai_profile_error_url_invalid)
            if (token.isBlank() && (existing == null || originChanged)) put(ProfileField.TOKEN, Res.string.ai_profile_error_token_required)
            if (next != ProfileAction.MODELS && model.isBlank()) put(ProfileField.MODEL, Res.string.ai_profile_error_model_invalid)
            if (originChanged && !originConfirmed) put(ProfileField.ORIGIN, Res.string.ai_credential_origin_change_warning)
        }
        if (errors.isNotEmpty()) return
        action = next; message = null; catalogMessage = null
        scope.launch {
            try {
                when (next) {
                    ProfileAction.SAVE -> { engine.saveProfile(draft(), token); token = ""; onDismiss() }
                    ProfileAction.TEST -> message = when (engine.testConnection(draft(), token)) {
                        is ProviderConnectionTestResult.Available -> Res.string.ai_connection_test_available
                        is ProviderConnectionTestResult.Unavailable -> Res.string.ai_connection_test_failed
                    }
                    ProfileAction.MODELS -> when (val result = engine.listModels(draft(), token)) {
                        is ProviderModelCatalogResult.Available -> {
                            models = result.models
                            modelsExpanded = models.isNotEmpty()
                            if (models.isEmpty()) catalogMessage = Res.string.ai_model_catalog_empty
                        }
                        ProviderModelCatalogResult.Unsupported -> catalogMessage = Res.string.ai_model_catalog_unsupported
                        is ProviderModelCatalogResult.Unavailable -> catalogMessage = Res.string.ai_model_catalog_failed
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                message = when (next) {
                    ProfileAction.SAVE -> Res.string.ai_profile_error_save_failed
                    ProfileAction.TEST -> Res.string.ai_connection_test_failed
                    ProfileAction.MODELS -> Res.string.ai_model_catalog_failed
                }
            } finally { action = null }
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy,
    )) {
        BoxWithConstraints(Modifier.imePadding(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight * 0.92f),
                shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (existing == null) Res.string.ai_new_configuration else Res.string.ai_profile_edit),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(capability.label()), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.Outlined.Close, stringResource(Res.string.close)) }
                    }
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { if (!busy) providerExpanded = it }) {
                            OutlinedTextField(value = stringResource(providerLabel(provider)), onValueChange = {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, !busy),
                                shape = ConfigurationFieldShape, enabled = !busy, singleLine = true,
                                label = { Text(stringResource(Res.string.ai_provider)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) })
                            ExposedDropdownMenu(providerExpanded, onDismissRequest = { providerExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp), shape = ConfigurationFieldShape) {
                                providers.forEach { id ->
                                    DropdownMenuItem(text = { Text(stringResource(providerLabel(id.value))) }, onClick = {
                                        providerExpanded = false; chooseProvider(id.value)
                                    }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                                }
                            }
                        }
                        OutlinedTextField(baseUrl, { baseUrl = it; originConfirmed = false; clearCatalog() },
                            label = { Text(stringResource(Res.string.ai_base_url)) }, singleLine = true, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = ConfigurationFieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = ProfileField.URL in errors)
                        FieldFeedback(errors[ProfileField.URL])
                        OutlinedTextField(token, { token = it; clearCatalog() }, label = { Text(stringResource(Res.string.ai_token)) },
                            placeholder = { if (existing != null) Text(stringResource(Res.string.ai_token_leave_blank)) },
                            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = ConfigurationFieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = ProfileField.TOKEN in errors)
                        FieldFeedback(errors[ProfileField.TOKEN])
                        ExposedDropdownMenuBox(modelsExpanded, onExpandedChange = { if (!busy && models.isNotEmpty()) modelsExpanded = it }) {
                            OutlinedTextField(model, { model = it; clearFeedback() }, label = { Text(stringResource(Res.string.ai_model)) },
                                singleLine = true, enabled = !busy, shape = ConfigurationFieldShape,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, !busy),
                                isError = ProfileField.MODEL in errors || catalogMessage == Res.string.ai_model_catalog_failed,
                                trailingIcon = {
                                    if (action == ProfileAction.MODELS) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else IconButton(onClick = { perform(ProfileAction.MODELS) }, enabled = !busy) {
                                        Icon(Icons.Outlined.Download, stringResource(Res.string.ai_model_catalog_load))
                                    }
                                })
                            ExposedDropdownMenu(modelsExpanded, onDismissRequest = { modelsExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp), shape = ConfigurationFieldShape) {
                                models.forEach { item ->
                                    DropdownMenuItem(text = {
                                        Column {
                                            Text(item.displayName ?: item.id)
                                            if (!item.displayName.isNullOrBlank() && item.displayName != item.id) Text(item.id,
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }, onClick = { model = item.id; modelsExpanded = false; clearFeedback() },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                                }
                            }
                        }
                        FieldFeedback(errors[ProfileField.MODEL] ?: catalogMessage,
                            isError = ProfileField.MODEL in errors || catalogMessage == Res.string.ai_model_catalog_failed)
                        if (baseUrl.trim().startsWith("http://", ignoreCase = true) || allowHttp) LabeledAiCheckbox(
                            allowHttp, !busy, Res.string.ai_allow_cleartext, Res.string.ai_cleartext_warning,
                            onChange = { allowHttp = it; clearFeedback() })
                        if (originChanged) LabeledAiCheckbox(originConfirmed, !busy, Res.string.ai_origin_change_confirm,
                            Res.string.ai_credential_origin_change_warning, onChange = { originConfirmed = it; clearFeedback() })
                        FieldFeedback(errors[ProfileField.ORIGIN])
                        Text(stringResource(Res.string.ai_connection_test_note), Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        message?.let {
                            val available = it == Res.string.ai_connection_test_available
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Icon(if (available) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = color)
                                Text(stringResource(it), color = color, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { perform(ProfileAction.TEST) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            if (action == ProfileAction.TEST) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(Res.string.ai_connection_test_action))
                        }
                        Button(onClick = { perform(ProfileAction.SAVE) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            if (action == ProfileAction.SAVE) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(Res.string.save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldFeedback(message: StringResource?, isError: Boolean = true) {
    if (message != null) Text(stringResource(message), Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LabeledAiCheckbox(checked: Boolean, enabled: Boolean, label: StringResource, supporting: StringResource, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.Top) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(top = 10.dp, end = 8.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(supporting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val ConfigurationFieldShape = RoundedCornerShape(Dimen.textFiledCorners)
