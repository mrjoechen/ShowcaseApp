package com.alpha.showcase.common.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.components.BackHandler
import com.alpha.showcase.common.ui.play.*
import isDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal expect fun AiPreviewSystemBars(chromeVisible: Boolean)

@Composable
internal fun AiCreationPreview(initialTaskId: String?, engineOverride: AiEngine? = null, onDismiss: () -> Unit) {
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    var imageIds by rememberSaveable(initialTaskId) { mutableStateOf<List<String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(engine) {
        try {
            engine.initialize()
            if (imageIds == null) imageIds = engine.library.value.tasks.filter { it.resultFile != null }
                .sortedByDescending { it.createdAt }.map { it.id }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    val ids = imageIds
    if (ids == null) {
        AiPage(stringResource(Res.string.ai_task_detail_title), onDismiss) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (failed) Text(stringResource(Res.string.ai_task_load_failed)) else CircularProgressIndicator()
            }
        }
    } else {
        val tasksById = remember(library.tasks) { library.tasks.associateBy { it.id } }
        val images = ids.mapNotNull { tasksById[it] }.filter { it.resultFile != null }
        if (images.isEmpty()) {
            LaunchedEffect(Unit) { onDismiss() }
        } else CreationImageBrowser(engine, images, initialTaskId, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreationImageBrowser(engine: AiEngine, images: List<AiTask>, initialTaskId: String?, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = images.indexOfFirst { it.id == initialTaskId }.coerceAtLeast(0), pageCount = { images.size })
    val task = images[pager.currentPage.coerceIn(images.indices)]
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var chrome by rememberSaveable { mutableStateOf(true) }
    AiPreviewSystemBars(chrome)
    var original by rememberSaveable(task.id) { mutableStateOf(false) }
    var menu by remember(task.id) { mutableStateOf(false) }
    var info by rememberSaveable(task.id) { mutableStateOf(false) }
    var deleting by remember(task.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) { snackbar.showSnackbar(messageText); message = null }
    }
    val sourceFile = task.originalFile ?: task.sourceFile
    val currentFile = if (original) sourceFile else checkNotNull(task.resultFile)
    val currentModel = remember(engine, currentFile) { DataWithType(engine.files.imageModel(currentFile), currentFile.substringAfterLast('.')) }
    val currentState = rememberMediaItemState(currentModel, fitSize = true)
    val canNavigate = !busy && !menu && !info && !deleting
    fun step(delta: Int) {
        val next = pager.currentPage + delta
        if (canNavigate && !pager.isScrollInProgress && next in images.indices) scope.launch { pager.animateScrollToPage(next) }
    }
    fun runAction(failure: StringResource, action: suspend () -> StringResource?) {
        busy = true
        scope.launch {
            try { message = action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = failure }
            finally { busy = false }
        }
    }
    BackHandler { onDismiss(); true }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key in listOf(Key.Escape, Key.Back) && canNavigate) {
            onDismiss(); true
        } else false
    }.playbackArrowKeys(::step)) {
        HorizontalPager(pager, key = { images[it].id }, modifier = Modifier.fillMaxSize(), userScrollEnabled = canNavigate) { page ->
            val image = images[page]
            val selected = image.id == task.id
            val file = if (selected) currentFile else checkNotNull(image.resultFile)
            val model = remember(file) { DataWithType(engine.files.imageModel(file), file.substringAfterLast('.')) }
            val state = if (selected) currentState else rememberMediaItemState(model, fitSize = true)
            val description = stringResource(if (selected && original) Res.string.ai_source_image_description else Res.string.ai_generated_image_description)
            PagerItem(state, active = selected, modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
                onInteraction = { if (canNavigate) { chrome = !chrome; menu = false } })
        }
        AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            AiPageTopBar(stringResource(Res.string.ai_task_detail_title), onDismiss,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.62f),
                    titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !pager.isScrollInProgress) {
                            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.MoreVert, stringResource(Res.string.ai_more_actions))
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                            DropdownMenuItem(text = { Text(stringResource(if (original) Res.string.ai_show_generated else Res.string.ai_show_original)) },
                                leadingIcon = { Icon(if (original) Icons.Outlined.AutoAwesome else Icons.Outlined.Image, null) },
                                enabled = !busy, onClick = { menu = false; original = !original })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.ai_creation_info)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = { menu = false; info = true })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.ai_save_generated_to_album)) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) },
                                enabled = !busy, onClick = {
                                    menu = false
                                    runAction(Res.string.ai_image_save_failed) {
                                        val file = checkNotNull(task.resultFile)
                                        if (exportAiImage(file, engine.files.read(file))) Res.string.ai_image_saved else null
                                    }
                                })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.ai_save_original_to_album)) }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                enabled = !busy && task.originalFile != null, onClick = {
                                    menu = false
                                    runAction(Res.string.ai_image_save_failed) {
                                        if (exportAiImage(sourceFile, engine.files.read(sourceFile))) Res.string.ai_image_saved else null
                                    }
                                })
                            DropdownMenuItem(text = { Text(stringResource(if (isDesktop()) Res.string.ai_copy_image else Res.string.share)) },
                                leadingIcon = { Icon(Icons.Default.Share, null) }, enabled = !busy, onClick = {
                                    menu = false
                                    runAction(Res.string.ai_share_failed) {
                                        shareAiImage(currentFile, engine.files.read(currentFile))
                                        if (isDesktop()) Res.string.ai_image_copied else null
                                    }
                                })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                enabled = !busy && task.status.terminal, onClick = { menu = false; deleting = true })
                        }
                    }
                })
        }
        if (isDesktop() && chrome && images.size > 1) {
            if (pager.currentPage > 0) IconButton(onClick = { step(-1) }, enabled = canNavigate,
                modifier = Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.ai_previous_creation), tint = Color.White)
            }
            if (pager.currentPage < images.lastIndex) IconButton(onClick = { step(1) }, enabled = canNavigate,
                modifier = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.ai_next_creation), tint = Color.White)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(8.dp))
    }
    if (info) ModalBottomSheet(onDismissRequest = { info = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (original) Res.string.ai_original_metadata else Res.string.ai_creation_info), style = MaterialTheme.typography.titleLarge)
            if (original) {
                Text(task.originalName ?: sourceFile, style = MaterialTheme.typography.titleMedium)
                if (task.originalFile == null) Text(stringResource(Res.string.ai_upload_snapshot_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (currentState.loading) CircularProgressIndicator()
                SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mediaMetadataRows(currentState).filter { it.kind != MediaMetadataKind.FileName }.forEach { Text(it.text) }
                } }
            } else {
                AssistChip(onClick = {}, label = { Text(stringResource(Res.string.ai_generated_badge)) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) })
                HorizontalDivider()
                Text(task.originalName ?: task.sourceFile)
                LocalAiStyleCatalog.styles().firstOrNull { it.key == task.styleKey }?.let { Text(stringResource(it.displayNameRes)) }
                Text(stringResource(Res.string.ai_created_at, formatAiCreatedAt(task.createdAt)))
                task.attempts.lastOrNull()?.let { attempt ->
                    Text(stringResource(Res.string.ai_processing_time, ((attempt.updatedAt - attempt.startedAt).coerceAtLeast(0) / 1000).toString()))
                }
            }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(Res.string.delete)) },
        text = { Text(stringResource(Res.string.ai_delete_image_confirm)) },
        confirmButton = { TextButton(onClick = {
            deleting = false
            runAction(Res.string.ai_delete_failed) { engine.deleteTask(task.id); null }
        }) { Text(stringResource(Res.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(Res.string.cancel)) } })
}
