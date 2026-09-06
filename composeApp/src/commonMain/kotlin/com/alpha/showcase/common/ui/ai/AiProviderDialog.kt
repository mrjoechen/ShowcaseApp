package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.BuiltInProviderCatalog
import com.alpha.showcase.common.ai.*
import isWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiProviderDialog(engineOverride: AiEngine? = null, onDismiss: () -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    var selected by remember { mutableStateOf<AiProfile?>(null) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(engine) {
        try { engine.initialize(); ready = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = Res.string.ai_profile_error_load_failed }
    }
    AiDialog(stringResource(Res.string.ai_configuration_title), onDismiss) {
        AiMessage(message)
        if (!ready) { CircularProgressIndicator(); return@AiDialog }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text(stringResource(Res.string.ai_new_configuration)) }) }
            items(library.activeProfiles, key = { it.id }) { profile ->
                FilterChip(selected = selected?.id == profile.id, onClick = { selected = profile; engine.scope.launch {
                    try { engine.selectProfile(profile.id) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = Res.string.ai_profile_error_save_failed }
                } }, label = { Text(profile.name) })
            }
        }
        key(selected?.id, selected?.revision) {
            AiProfileEditor(engine, selected, onSaved = { selected = null }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AiProfileEditor(engine: AiEngine, existing: AiProfile?, onSaved: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var capability by remember { mutableStateOf(existing?.let { aiProviderCapability(it.providerId) } ?: AiCapability.IMAGE_TO_IMAGE) }
    var provider by remember { mutableStateOf(existing?.providerId ?: "openai") }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: aiProviderBaseUrl(provider)) }
    var model by remember { mutableStateOf(existing?.model ?: aiProviderModel(provider)) }
    var token by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(existing?.allowInsecureHttp ?: false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var models by remember { mutableStateOf<List<ProviderModel>>(emptyList()) }
    var archive by remember { mutableStateOf(false) }

    fun chooseProvider(id: String) {
        provider = id; baseUrl = aiProviderBaseUrl(id); model = aiProviderModel(id)
        token = ""; models = emptyList(); allowHttp = false; message = null
    }
    fun draft() = AiProfile(existing?.id.orEmpty(), name = name, providerId = provider, model = model.trim(),
        baseUrl = baseUrl.trim(), encryptedToken = "", allowInsecureHttp = allowHttp)
    fun launchAction(failure: StringResource, block: suspend () -> Unit) {
        busy = true; message = null
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = failure }
            finally { busy = false }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.ai_configuration_description), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AiCapability.IMAGE_TO_IMAGE, AiCapability.IMAGE_UNDERSTANDING).forEach { value ->
                FilterChip(selected = capability == value, enabled = !busy, onClick = {
                    capability = value; chooseProvider(if (value == AiCapability.IMAGE_TO_IMAGE) "openai" else "openai-vision")
                }, label = { Text(stringResource(if (value == AiCapability.IMAGE_TO_IMAGE) Res.string.ai_capability_image_to_image else Res.string.ai_capability_image_understanding)) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BuiltInProviderCatalog.ids.filter { aiProviderCapability(it.value) == capability }) { id ->
                FilterChip(provider == id.value, { chooseProvider(id.value) }, enabled = !busy,
                    label = { Text(stringResource(providerLabel(id.value))) })
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.ai_profile_name)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(baseUrl, { baseUrl = it; models = emptyList() }, label = { Text(stringResource(Res.string.ai_base_url)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(allowHttp, { allowHttp = it }, enabled = !busy)
                Text(stringResource(Res.string.ai_allow_cleartext), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(Res.string.ai_cleartext_warning), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(token, { token = it }, label = { Text(stringResource(Res.string.ai_token)) },
            placeholder = { if (existing != null) Text(stringResource(Res.string.ai_token_leave_blank)) },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text(stringResource(Res.string.ai_model)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { launchAction(Res.string.ai_model_catalog_failed) {
            when (val result = engine.listModels(draft(), token)) {
                is ProviderModelCatalogResult.Available -> { models = result.models; if (models.isEmpty()) message = Res.string.ai_model_catalog_empty }
                ProviderModelCatalogResult.Unsupported -> message = Res.string.ai_model_catalog_unsupported
                is ProviderModelCatalogResult.Unavailable -> message = Res.string.ai_model_catalog_failed
            }
        } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_model_catalog_load)) }
        if (models.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models, key = { it.id }) { item -> FilterChip(model == item.id, { model = item.id }, label = { Text(item.displayName ?: item.id) }) }
        }
        Text(stringResource(Res.string.ai_connection_test_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { launchAction(Res.string.ai_connection_test_failed) {
            message = when (engine.testConnection(draft(), token)) {
                is ProviderConnectionTestResult.Available -> Res.string.ai_connection_test_available
                is ProviderConnectionTestResult.Unavailable -> Res.string.ai_connection_test_failed
            }
        } }, enabled = !busy && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_connection_test_action)) }
        AiMessage(message)
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        Button(onClick = {
            message = when {
                name.isBlank() -> Res.string.ai_profile_error_name_required
                model.isBlank() -> Res.string.ai_profile_error_model_invalid
                !isValidAiBaseUrl(baseUrl.trim(), allowHttp) -> Res.string.ai_profile_error_url_invalid
                token.isBlank() && (existing == null || !canReuseAiCredential(existing.providerId, existing.baseUrl, provider, baseUrl.trim())) -> Res.string.ai_profile_error_token_required
                else -> null
            }
            if (message == null) launchAction(Res.string.ai_profile_error_save_failed) { engine.saveProfile(draft(), token); token = ""; onSaved() }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_profile_save)) }
        if (existing != null) OutlinedButton(onClick = { archive = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.ai_profile_archive))
        }
    }
    if (archive) AlertDialog(onDismissRequest = { archive = false },
        title = { Text(stringResource(Res.string.ai_profile_archive_confirm)) },
        text = { Text(stringResource(Res.string.ai_profile_archive_pending_tasks)) },
        confirmButton = { TextButton(onClick = { archive = false; launchAction(Res.string.ai_profile_error_archive_failed) { engine.archiveProfile(existing!!.id); onSaved() } }) { Text(stringResource(Res.string.confirm)) } },
        dismissButton = { TextButton(onClick = { archive = false }) { Text(stringResource(Res.string.cancel)) } })
}
