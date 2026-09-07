package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.components.BackHandler
import com.alpha.showcase.common.theme.Dimen
import io.ktor.http.Url
import isWeb
import isDesktop
import isMacOS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiProviderDialog(engineOverride: AiEngine? = null, onDismiss: () -> Unit) {
    AiProviderPage(engineOverride, inDialog = true, onDismiss = onDismiss)
}

/** Shared page content; generation flows can also open it in a modal container. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiProviderPage(engineOverride: AiEngine? = null, inDialog: Boolean = false, onDismiss: () -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    val scope = rememberCoroutineScope()
    var capability by remember { mutableStateOf(AiCapability.IMAGE_TO_IMAGE) }
    var editing by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<AiProfile?>(null) }
    var deleting by remember { mutableStateOf<AiProfile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var ready by remember { mutableStateOf(false) }
    var showServiceInfo by rememberSaveable { mutableStateOf(false) }
    val profiles = library.activeProfiles.filter { aiProviderCapability(it.providerId) == capability }
    val selected = if (capability == AiCapability.IMAGE_TO_IMAGE) library.generationProfileId else library.understandingProfileId
    val dismiss = { if (!busy) onDismiss() }
    LaunchedEffect(engine) {
        try { engine.initialize(); ready = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = Res.string.ai_profile_error_load_failed }
    }
    fun updateProfile(failure: StringResource, action: suspend () -> Unit) {
        busy = true
        message = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = failure }
            finally { busy = false }
        }
    }
    // Decorated Windows/Linux main windows already reserve their title bar.
    // Dialogs and macOS full-window content still need space for window controls.
    val needsWindowControlInset = isDesktop() && (inDialog || isMacOS())
    AiProviderContainer(inDialog, busy, dismiss) {
        Scaffold(Modifier.fillMaxSize(), topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                TopAppBar(
                    modifier = if (isDesktop()) {
                        // Match the body's centered column and its 20.dp visual
                        // start inset (the app bar's back icon already uses 16.dp).
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 4.dp)
                    } else Modifier.fillMaxWidth(),
                    windowInsets = TopAppBarDefaults.windowInsets.union(
                        WindowInsets(top = if (needsWindowControlInset) 36.dp else 0.dp)),
                    actions = {
                        IconButton(onClick = { showServiceInfo = true }) {
                            Icon(Icons.Outlined.Info, stringResource(Res.string.ai_service_info_title))
                        }
                    },
                    title = { Text(stringResource(Res.string.ai_provider_settings_title)) }, navigationIcon = {
                    IconButton(onClick = dismiss, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                })
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    Text(stringResource(Res.string.ai_configuration_description), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val capabilities = listOf(AiCapability.IMAGE_TO_IMAGE, AiCapability.IMAGE_UNDERSTANDING)
                    PrimaryTabRow(selectedTabIndex = capabilities.indexOf(capability),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer, divider = {}) {
                        capabilities.forEach { value ->
                            Tab(selected = capability == value, enabled = !busy && !editing, onClick = { capability = value; message = null },
                                text = { Text(stringResource(value.label()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(if (value == AiCapability.IMAGE_TO_IMAGE) Icons.Outlined.AutoFixHigh else Icons.Outlined.AutoAwesome, null) })
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AiMessage(message)
                        if (!ready && message == null) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        if (ready) SavedAiProfiles(profiles, selected, enabled = !busy && !editing,
                            showFacePrivacy = capability == AiCapability.IMAGE_UNDERSTANDING,
                            facePrivacyEnabled = library.facePrivacyEnabled,
                            onFacePrivacyChanged = { enabled ->
                                updateProfile(Res.string.ai_profile_error_save_failed) { engine.setFacePrivacyEnabled(enabled) }
                            },
                            onNew = { existing = null; editing = true },
                            onSelect = { id -> updateProfile(Res.string.ai_profile_error_save_failed) { engine.selectProfile(id) } },
                            onEdit = { existing = it; editing = true }, onDelete = { deleting = it })
                    }
                }
            }
        }
        if (showServiceInfo) AiServiceInfoDialog(onDismiss = { showServiceInfo = false })
        if (editing) AiProfileEditorDialog(engine, capability, existing, onDismiss = { editing = false })
        deleting?.let { profile ->
            AlertDialog(onDismissRequest = { deleting = null },
                icon = { Icon(Icons.Outlined.DeleteOutline, null) },
                title = { Text(stringResource(Res.string.ai_profile_delete_confirm)) },
                text = { Text(stringResource(Res.string.ai_profile_delete_pending_tasks, profile.name)) },
                confirmButton = { Button(onClick = {
                    deleting = null
                    updateProfile(Res.string.ai_profile_error_archive_failed) { engine.archiveProfile(profile.id) }
                }) { Text(stringResource(Res.string.delete)) } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.cancel)) } })
        }
    }
}

@Composable
private fun AiServiceInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text(stringResource(Res.string.ai_service_info_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    Res.string.ai_service_info_photos_title to Res.string.ai_service_info_photos_body,
                    Res.string.ai_service_info_security_title to Res.string.ai_service_info_security_body,
                    Res.string.ai_service_info_disclaimer_title to Res.string.ai_service_info_disclaimer_body,
                ).forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

@Composable
private fun AiProviderContainer(inDialog: Boolean, busy: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (inDialog) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false,
        ), content = content)
    } else {
        BackHandler(enabled = busy) { true }
        content()
    }
}

@Composable
private fun SavedAiProfiles(
    profiles: List<AiProfile>, selected: String?, enabled: Boolean,
    showFacePrivacy: Boolean, facePrivacyEnabled: Boolean, onFacePrivacyChanged: (Boolean) -> Unit,
    onNew: () -> Unit, onSelect: (String) -> Unit, onEdit: (AiProfile) -> Unit, onDelete: (AiProfile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.ai_saved_profile), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (showFacePrivacy) {
                FilledTonalIconToggleButton(checked = facePrivacyEnabled, onCheckedChange = onFacePrivacyChanged,
                    enabled = enabled) {
                    Icon(painterResource(if (facePrivacyEnabled) Res.drawable.ic_face_privacy_checked else Res.drawable.ic_face_privacy),
                        contentDescription = stringResource(Res.string.ai_image_summary_face_privacy))
                }
            }
            TextButton(onClick = onNew, enabled = enabled) {
                Icon(Icons.Outlined.Add, null)
                Text(stringResource(Res.string.ai_new_configuration), Modifier.padding(start = 6.dp))
            }
        }
        if (showFacePrivacy && facePrivacyEnabled) {
            Text(stringResource(Res.string.ai_image_summary_face_privacy_description),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(Modifier.fillMaxWidth().selectableGroup(), shape = RoundedCornerShape(Dimen.textFiledCorners),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            if (profiles.isEmpty()) Text(stringResource(Res.string.ai_profile_empty_compact), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Column {
                profiles.forEachIndexed { index, profile ->
                    val active = profile.id == selected
                    ListItem(modifier = Modifier.selectable(active, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(profile.id) }),
                        colors = ListItemDefaults.colors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(if (active) stringResource(Res.string.ai_profile_in_use) else "${profile.host()} · ${profile.model}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { RadioButton(active, onClick = null, enabled = enabled) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEdit(profile) }, enabled = enabled) {
                                    Icon(Icons.Outlined.Edit, stringResource(Res.string.ai_profile_edit_named, profile.name))
                                }
                                IconButton(onClick = { onDelete(profile) }, enabled = enabled) {
                                    Icon(Icons.Outlined.DeleteOutline, stringResource(Res.string.ai_profile_delete_named, profile.name))
                                }
                            }
                        })
                    if (index < profiles.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

internal fun AiCapability.label() = if (this == AiCapability.IMAGE_TO_IMAGE) Res.string.ai_capability_image_to_image else Res.string.ai_capability_image_understanding
internal fun AiProfile.host(): String = runCatching { Url(baseUrl).host }.getOrDefault("")
