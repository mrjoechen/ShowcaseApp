package com.alpha.showcase.common.ui.ai

import LocalImageLoader
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.alpha.facedetection.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil3.Image
import coil3.compose.AsyncImage
import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.ui.focusScaleEffect
import isWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiGeneratorPage(image: Image, engineOverride: AiEngine? = null, originalInput: AiGenerationInput? = null, onDismiss: () -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    val scope = rememberCoroutineScope()
    val context = coil3.compose.LocalPlatformContext.current
    val imageLoader = LocalImageLoader.current ?: coil3.SingletonImageLoader.get(context)
    var ready by remember { mutableStateOf(false) }
    var taskId by rememberSaveable(image) { mutableStateOf<String?>(null) }
    var style by remember { mutableStateOf("ghibli") }
    var source by remember(image) { mutableStateOf<EncodedAiImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectingProfile by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var configure by remember { mutableStateOf(false) }
    val navigation = LocalAiNavigation.current
    var privacyInfo by remember { mutableStateOf(false) }
    val profiles = library.activeProfiles.filter { aiProviderCapability(it.providerId) == AiCapability.IMAGE_TO_IMAGE }
    val selectedProfile = library.generationProfileId?.takeIf { id -> profiles.any { it.id == id } }
        ?: profiles.firstOrNull()?.id
    val task = library.tasks.firstOrNull { it.id == taskId }
    var inspection by remember(image, task?.resultFile) { mutableStateOf<FaceInspectionResult?>(null) }
    LaunchedEffect(image) {
        try {
            engine.initialize()
            style = engine.library.value.styleKey
            source = encodeAiImage(image)
            ready = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = Res.string.ai_generation_enqueue_failed }
    }
    LaunchedEffect(source, task?.resultFile) {
        val encoded = source ?: return@LaunchedEffect
        inspection = null
        inspection = try {
            val bytes = task?.resultFile?.let { engine.files.read(it) } ?: encoded.bytes
            createFaceInspector().inspect(bytes)
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { FaceInspectionResult.INDETERMINATE }
    }
    AiPage(stringResource(Res.string.ai_generate_title), onDismiss) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                var ratio by remember(task?.resultFile) { mutableFloatStateOf(image.width.toFloat() / image.height.coerceAtLeast(1)) }
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.size(width, width / ratio).clip(RoundedCornerShape(16.dp))) {
                val resultModel = task?.resultFile?.let(engine.files::imageModel)
                if (resultModel != null || source != null) AsyncImage(resultModel ?: source!!.bytes,
                    contentDescription = stringResource(if (resultModel == null) Res.string.ai_source_image_description else Res.string.ai_generated_image_description),
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    onSuccess = { result ->
                        if (result.result.image.height > 0) ratio = result.result.image.width.toFloat() / result.result.image.height
                    })
                if (inspection == FaceInspectionResult.FACE_DETECTED) {
                    IconButton(onClick = { privacyInfo = true }, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Icon(painterResource(Res.drawable.ic_face_privacy),
                            stringResource(Res.string.ai_face_detected), tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                }
                if (!ready || busy || task?.status?.terminal == false) CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp))
                }
            }
            AiProfileSelector(profiles, selectedProfile, enabled = !busy && !selectingProfile && (task == null || task.status.terminal), onSelected = { id ->
                selectingProfile = true
                scope.launch {
                    try { engine.selectProfile(id); message = null }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = Res.string.ai_profile_error_save_failed }
                    finally { selectingProfile = false }
                }
            }, onConfigure = { if (navigation != null) navigation.providers() else configure = true })
            if (profiles.isEmpty()) {
                Text(stringResource(Res.string.ai_generation_profile_required), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { if (navigation != null) navigation.providers() else configure = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_configure_service)) }
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
                try {
                    val original = originalInput?.let { readAiOriginal(it, imageLoader, context) }
                    taskId = engine.enqueue(encoded, profileId, style, original, originalInput?.name)
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = Res.string.ai_generation_enqueue_failed }
                finally { busy = false }
            }
        }, enabled = ready && !busy && !selectingProfile && selectedProfile != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.ai_generate_action))
        } else AiTaskActions(engine, task, selectedProfile.takeUnless { selectingProfile }, onNewTask = { taskId = it })
        TextButton(onClick = { navigation?.creations?.invoke() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.ai_creation_center_title)) }
    }
    if (configure) AiProviderDialog(engineOverride = engine) { configure = false }
    if (privacyInfo) AlertDialog(onDismissRequest = { privacyInfo = false },
        title = { Text(stringResource(Res.string.ai_image_summary_face_privacy)) },
        text = { Text(stringResource(Res.string.ai_generation_privacy_notice)) },
        confirmButton = { TextButton(onClick = { privacyInfo = false }) { Text(stringResource(Res.string.confirm)) } })
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
        pageSize = object : PageSize {
            override fun androidx.compose.ui.unit.Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
                minOf(280.dp.roundToPx(), availableSpace)
        },
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
