package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.layout.layout
import com.alpha.showcase.common.ui.play.LocalPlaybackActive
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.savedstate.read
import coil3.Image
import com.alpha.showcase.common.ai.AiServices
import com.alpha.showcase.common.ai.AiEngine
import com.alpha.showcase.common.components.BackHandler
import isWeb
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

internal class AiNavigation(
    val generate: (Image) -> Unit,
    val generateMedia: (com.alpha.showcase.common.ui.play.MediaItemState) -> Unit,
    val creations: () -> Unit,
    val detail: (String) -> Unit,
    val preview: (String) -> Unit,
    val providers: () -> Unit,
)

internal val LocalAiNavigation = staticCompositionLocalOf<AiNavigation?> { null }

/** Window-owned navigation: decoded input stays in memory and never enters route arguments. */
@Composable
internal fun AiNavigationHost(engineOverride: AiEngine? = null, content: @Composable () -> Unit) {
    if (isWeb()) { content(); return }
    val nav = rememberNavController()
    var input by remember { mutableStateOf<AiGenerationInput?>(null) }
    val navigation = remember(nav) {
        AiNavigation(
            generate = { input = AiGenerationInput(it); nav.navigate("generate") { launchSingleTop = true } },
            generateMedia = { state ->
                state.displayedImage?.let { image ->
                    input = AiGenerationInput(image, state.data,
                        com.alpha.showcase.common.ui.play.mediaMetadataRows(state)
                            .firstOrNull { it.kind == com.alpha.showcase.common.ui.play.MediaMetadataKind.FileName }?.text)
                    nav.navigate("generate") { launchSingleTop = true }
                }
            },
            creations = { nav.navigate("creations") { launchSingleTop = true } },
            detail = { nav.navigate("creation/$it") },
            preview = { nav.navigate("preview/$it") },
            providers = { nav.navigate("providers") { launchSingleTop = true } },
        )
    }
    val entry by nav.currentBackStackEntryAsState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(entry) { focusManager.clearFocus(force = true) }
    LaunchedEffect(entry) { if (entry?.destination?.route == "content") input = null }
    CompositionLocalProvider(LocalAiNavigation provides navigation) {
        Box(Modifier.fillMaxSize()) {
            val showingContent = entry == null || entry?.destination?.route == "content"
            val parentActive = LocalPlaybackActive.current
            // Keep the source pager and decoded image alive while a tool page covers it.
            Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (showingContent) placeable.place(0, 0)
                }
            }) {
                CompositionLocalProvider(LocalPlaybackActive provides (parentActive && showingContent)) { content() }
            }
            NavHost(navController = nav, startDestination = "content") {
                composable("content") { }
                composable("generate") { destination ->
                    // The outgoing entry stays composed during its exit animation.
                    // Keep its image even after returning to content clears the pending input.
                    val image = remember(destination) { input }
                    if (image == null) LaunchedEffect(destination) {
                        if (nav.currentBackStackEntry == destination) nav.popBackStack()
                    }
                    else AiGeneratorPage(image.image, engineOverride, originalInput = image) { nav.popBackStack() }
                }
                composable("creations") { AiCreationCenter(engineOverride) { nav.popBackStack() } }
                composable("creation/{taskId}") { destination ->
                    val taskId = destination.arguments?.read { getStringOrNull("taskId") }
                    AiTaskDetailPage(taskId, engineOverride, onDismiss = { nav.popBackStack() })
                }
                composable("preview/{taskId}") { destination ->
                    val taskId = destination.arguments?.read { getStringOrNull("taskId") }
                    AiCreationPreview(taskId, engineOverride) { nav.popBackStack() }
                }
                composable("providers") { AiProviderPage(engineOverride) { nav.popBackStack() } }
            }
        }
    }
}

@Composable
internal fun AiPage(
    title: String,
    onBack: () -> Unit,
    contentMaxWidth: androidx.compose.ui.unit.Dp = AiPageContentMaxWidth,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle) { if (lifecycle == Lifecycle.State.RESUMED) focus.requestFocus() }
    BackHandler(true) { onBack(); true }
    Scaffold(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key in listOf(Key.Escape, Key.Back)) {
            onBack(); true
        } else false
    }.focusRequester(focus).focusable(), contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        AiPageTopBar(title = title, onBack = onBack, actions = actions)
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = contentMaxWidth).fillMaxSize().padding(contentPadding), content = content)
        }
    }
}

@Composable
private fun AiTaskDetailPage(taskId: String?, engineOverride: AiEngine?, onDismiss: () -> Unit) {
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    val navigation = LocalAiNavigation.current
    val task = library.tasks.firstOrNull { it.id == taskId }
    if (task != null) AiTaskDetail(engine, library, task, onNewTask = { navigation?.detail?.invoke(it) }, onDismiss)
    else AiPage(stringResource(Res.string.ai_task_detail_title), onDismiss) {
        Text(stringResource(Res.string.ai_task_load_failed))
    }
}
