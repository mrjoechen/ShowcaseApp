package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.Image
import coil3.compose.AsyncImage
import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.ui.focusScaleEffect
import isWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiGeneratorDialog(image: Image, engineOverride: AiEngine? = null, onDismiss: () -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var taskId by remember(image) { mutableStateOf<String?>(null) }
    var style by remember { mutableStateOf("ghibli") }
    var source by remember(image) { mutableStateOf<EncodedAiImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectingProfile by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var configure by remember { mutableStateOf(false) }
    var creations by remember { mutableStateOf(false) }
    val profiles = library.activeProfiles.filter { aiProviderCapability(it.providerId) == AiCapability.IMAGE_TO_IMAGE }
    val selectedProfile = library.generationProfileId?.takeIf { id -> profiles.any { it.id == id } }
        ?: profiles.firstOrNull()?.id
    val task = library.tasks.firstOrNull { it.id == taskId }
    LaunchedEffect(image) {
        try {
            engine.initialize()
            style = engine.library.value.styleKey
            source = encodeAiImage(image)
            ready = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = Res.string.ai_generation_enqueue_failed }
    }
    AiGeneratorSurface(onDismiss) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 260.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                val resultModel = task?.resultFile?.let(engine.files::imageModel)
                if (resultModel != null || source != null) AsyncImage(resultModel ?: source!!.bytes,
                    contentDescription = stringResource(if (resultModel == null) Res.string.ai_source_image_description else Res.string.ai_generated_image_description),
                    modifier = Modifier.heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                if (!ready || busy || task?.status?.terminal == false) CircularProgressIndicator(Modifier.size(48.dp))
            }
            AiProfileSelector(profiles, selectedProfile, enabled = !busy && !selectingProfile && (task == null || task.status.terminal), onSelected = { id ->
                selectingProfile = true
                scope.launch {
                    try { engine.selectProfile(id); message = null }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = Res.string.ai_profile_error_save_failed }
                    finally { selectingProfile = false }
                }
            }, onConfigure = { configure = true })
            if (profiles.isEmpty()) {
                Text(stringResource(Res.string.ai_generation_profile_required), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { configure = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_configure_service)) }
            }
            if (ready) AiStyleChoices(LocalAiStyleCatalog.styles(), style, enabled = taskId == null && !busy, onSelected = { key ->
                style = key
                scope.launch {
                    try { engine.selectStyle(key) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = Res.string.ai_profile_error_save_failed }
                }
            })
            task?.let { AiTaskProgress(it) }
            AiMessage(message)
        }
        Spacer(Modifier.height(16.dp))
        if (task == null) Button(onClick = {
            val profileId = selectedProfile ?: return@Button
            val encoded = source ?: return@Button
            busy = true
            scope.launch {
                try { taskId = engine.enqueue(encoded, profileId, style) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = Res.string.ai_generation_enqueue_failed }
                finally { busy = false }
            }
        }, enabled = ready && !busy && !selectingProfile && selectedProfile != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.ai_generate_action))
        } else AiTaskActions(engine, task, selectedProfile.takeUnless { selectingProfile }, onNewTask = { taskId = it })
        TextButton(onClick = { creations = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_creation_center_title)) }
    }
    if (configure) AiProviderDialog(engineOverride = engine) { configure = false }
    if (creations) AiCreationCenter { creations = false }
}

@Composable
internal fun AiProfileSelector(profiles: List<AiProfile>, selected: String?, enabled: Boolean = true, onSelected: (String) -> Unit, onConfigure: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.ai_profile_selector), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (onConfigure != null) IconButton(onClick = onConfigure, enabled = enabled) {
                Icon(Icons.Outlined.Settings, stringResource(Res.string.ai_provider_settings_title))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(profiles, key = { it.id }) { profile ->
                FilterChip(profile.id == selected, { onSelected(profile.id) }, enabled = enabled, label = { Text(profile.name) })
            }
        }
        profiles.firstOrNull { it.id == selected }?.let { profile ->
            Text("${stringResource(providerLabel(profile.providerId))} · ${profile.model} · ${profile.host()}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiGeneratorSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp), tonalElevation = 8.dp) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.ai_generate_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(Res.string.close)) }
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
internal fun AiTaskProgress(task: AiTask) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(task.status.label()), style = MaterialTheme.typography.titleSmall)
            if (!task.status.terminal) {
                Text(stringResource(aiStageLabel(task.stage)), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (task.attempt > 0) Text(stringResource(Res.string.ai_attempt_number, task.attempt), style = MaterialTheme.typography.bodySmall)
            task.errorCategory?.let {
                Text("${stringResource(Res.string.ai_error_category)}: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (task.status == AiTaskStatus.RESULT_UNKNOWN) Text(stringResource(Res.string.ai_retry_unknown_warning), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AiStyleChoices(
    styles: List<AiStylePreset>,
    selectedStyleKey: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (styles.isEmpty()) return
    val initialPage = styles.indexOfFirst { it.key == selectedStyleKey }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = styles::size)
    LaunchedEffect(pagerState.currentPage, styles) {
        styles.getOrNull(pagerState.currentPage)?.let { onSelected(it.key) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .wrapContentHeight()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 36.dp),
        pageSpacing = 12.dp,
        beyondViewportPageCount = (styles.size - 1).coerceAtLeast(0),
        userScrollEnabled = enabled,
    ) { page ->
        val style = styles[page]
        val selected = style.key == selectedStyleKey
        val interactionSource = remember(style.key) { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .focusScaleEffect(
                        focusScale = 1.03f,
                        pressedScale = 0.98f,
                        interactionSource = interactionSource,
                    )
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(style.key) },
                    )
                    .border(
                        width = 3.dp,
                        color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(3.dp)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(11.dp),
                    )
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            ),
                            RoundedCornerShape(10.dp),
                        ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(style.displayNameRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
                Text(
                    text = stringResource(style.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
