package com.alpha.showcase.common.ui.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alpha.showcase.common.ai.*
import isWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiCreationCenter(
    engineOverride: AiEngine? = null,
    onOpenProviders: (() -> Unit)? = null,
    onPreview: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val library by engine.library.collectAsState()
    val navigation = LocalAiNavigation.current
    var loading by remember(engine) { mutableStateOf(true) }
    var failed by remember(engine) { mutableStateOf(false) }
    var configure by remember { mutableStateOf(false) }
    LaunchedEffect(engine) {
        try { engine.initialize() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val creations = remember(library.tasks) { library.tasks.filter { it.resultFile != null }.sortedByDescending { it.createdAt } }
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(creations) { aspectRatios.keys.retainAll(creations.map { it.id }.toSet()) }
    AiPage(stringResource(Res.string.ai_creation_center_title), onDismiss,
        contentMaxWidth = 1200.dp, contentPadding = PaddingValues(0.dp), actions = {
            IconButton(onClick = {
                val openProviders = onOpenProviders ?: navigation?.providers
                if (openProviders != null) openProviders() else configure = true
            }) {
                Icon(Icons.Outlined.Tune, stringResource(Res.string.ai_provider_settings_title))
            }
        }) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            var targetColumns by rememberSaveable { mutableIntStateOf(AiCreationGridPolicy.initialColumnCount(maxWidth.value.toInt())) }
            var columns by remember { mutableIntStateOf(targetColumns) }
            val scale = remember { Animatable(1f) }
            val alpha = remember { Animatable(1f) }
            val transform = rememberAiCreationPinchState(targetColumns) { targetColumns = it }
            LaunchedEffect(targetColumns) {
                if (columns == targetColumns) return@LaunchedEffect
                scale.snapTo(AiCreationGridPolicy.continuityScale(scale.value, columns, targetColumns))
                columns = targetColumns
                coroutineScope {
                    launch { scale.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) }
                    launch {
                        alpha.animateTo(minOf(alpha.value, 0.86f), spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                        alpha.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
                    }
                }
            }
            when {
                loading -> CircularProgressIndicator()
                failed -> Text(stringResource(Res.string.ai_task_load_failed), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                creations.isEmpty() -> Text(stringResource(Res.string.ai_creations_empty), Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> AiCreationOrderedGrid(
                    itemKeys = creations.map { it.id }, aspectRatios = creations.map { aspectRatios[it.id] ?: 1f },
                    columnCount = columns,
                    modifier = Modifier.fillMaxSize().transformable(transform, canPan = { false }, lockRotationOnZoomPan = true),
                ) { index ->
                    val task = creations[index]
                    AsyncImage(engine.files.imageModel(checkNotNull(task.resultFile)), stringResource(Res.string.ai_generated_image_description),
                        contentScale = ContentScale.Crop,
                        onSuccess = { result ->
                            val image = result.result.image
                            if (image.width > 0 && image.height > 0) aspectRatios[task.id] = image.width.toFloat() / image.height
                        },
                        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                            .fillMaxWidth().aspectRatio(aspectRatios[task.id] ?: 1f).clip(RoundedCornerShape(4.dp))
                            .clickable { (onPreview ?: navigation?.preview)?.invoke(task.id) })
                }
            }
        }
    }
    if (configure) AiProviderDialog(engineOverride) { configure = false }
}

@Composable
private fun rememberAiCreationPinchState(columns: Int, onChange: (Int) -> Unit): TransformableState {
    val currentColumns by rememberUpdatedState(columns)
    val currentOnChange by rememberUpdatedState(onChange)
    val gesture = remember { AiCreationPinchGesture() }
    val state = rememberTransformableState { zoom, _, _ -> gesture.onZoom(currentColumns, zoom)?.let(currentOnChange) }
    LaunchedEffect(state) {
        snapshotFlow { state.isTransformInProgress }.collect { if (!it) gesture.reset() }
    }
    return state
}
