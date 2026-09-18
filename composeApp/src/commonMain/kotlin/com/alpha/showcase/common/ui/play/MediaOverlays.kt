package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.ui.ai.AiMediaOverlays
import com.alpha.showcase.common.ui.ai.LocalAiPlaybackSettings
import com.alpha.showcase.common.ui.settings.*
import kotlinx.coroutines.delay

/** Independent viewport transition; never capture this in a page's scale/rotation/flip layer. */
@Composable
internal fun MediaOverlayTransition(
    state: MediaItemState,
    parentType: Int,
    showMetadata: Boolean? = null,
    config: MediaOverlayConfig = MediaOverlayConfig.forStyle(parentType),
) {
    Crossfade(state, animationSpec = tween(400), label = "media overlays") { entry ->
        MediaOverlays(entry, config, entry === state, parentType, showMetadata = showMetadata)
    }
}

@Composable
fun MediaOverlays(
    state: MediaItemState,
    config: MediaOverlayConfig = MediaOverlayConfig(),
    active: Boolean = true,
    parentType: Int = -1,
    editMode: Boolean = false,
    showMetadata: Boolean? = null,
) {
    val allowed = config.restrictedToStyle(parentType)
    LaunchedEffect(state, editMode, allowed) {
        if (editMode || !allowed.metadata) state.showMetadata = false
        if (editMode || !allowed.aiGenerate) state.showActions = false
    }
    if (editMode || allowed == MediaOverlayConfig.None) return
    RetainMediaItemState(state)
    val initialMetadata = showMetadata ?: false // Legacy settings never enable automatic metadata.
    LaunchedEffect(state, initialMetadata, allowed.metadata) {
        state.showMetadata = initialMetadata && allowed.metadata
    }
    LaunchedEffect(state, state.ready, state.showMetadata, state.showActions, state.interactionVersion) {
        if (state.ready && (state.showMetadata || state.showActions)) {
            delay(5000)
            state.showMetadata = false
            state.showActions = false
        }
    }
    val language = androidx.compose.ui.text.intl.Locale.current.toLanguageTag()
    var address by remember(state.metadata, language) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.metadata, language, state.showMetadata, active) {
        if (active && state.showMetadata) state.metadata?.coordinates?.let {
            address = photoAddresses.address(it, language)
        }
    }
    AnimatedVisibility(state.ready, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = allowed.metadata && state.showMetadata,
                enter = fadeIn(tween(400)), exit = fadeOut(tween(400)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier.fillMaxSize().background(mediaOverlayScrimBrush()),
                ) {
                    Column(
                        Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(24.dp).widthIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        mediaMetadataRows(state).groupBy { it.kind }.forEach { (kind, rows) ->
                            val text = if (kind == MediaMetadataKind.Location) address ?: rows.joinToString(" · ") { it.text }
                                else rows.map { it.text }.distinct().joinToString(" · ")
                            MetadataRow(MediaMetadataEntry(kind, text))
                        }
                    }
                }
            }
            AiMediaOverlays(state, active, parentType, allowed)
        }
    }
}

@Composable
private fun MetadataRow(entry: MediaMetadataEntry) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(entry.kind.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text(entry.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

internal fun mediaOverlayScrimBrush(
    start: Offset = Offset.Zero,
    end: Offset = Offset.Infinite,
) = Brush.linearGradient(
    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.2f), Color.Transparent),
    start = start,
    end = end,
)

/** Caption bars are wide and short; fade along that rectangle so the photo above stays clear. */
private const val SummaryScrimHeightToWidth = 0.4f
private val SummaryScrimMinHeight = 120.dp

internal fun Modifier.mediaOverlaySummaryScrim(captionWidth: Dp) = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val width = captionWidth.toPx().coerceIn(1f, size.width)
    val contentHeight = (width * SummaryScrimHeightToWidth)
        .coerceAtLeast(SummaryScrimMinHeight.toPx())
        .coerceAtMost(size.height * 0.4f)
    val height = (contentHeight * 1.4f).coerceAtMost(size.height * 0.55f)
    val top = (size.height - height).coerceAtLeast(0f)
    val start = Offset(0f, size.height)
    val end = Offset(width, top)
    // Both ramps end at transparent; a non-zero far edge reads as a stacked slab.
    onDrawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                start = start,
                end = Offset(start.x, end.y),
            ),
            topLeft = Offset(0f, top),
            size = Size(width, height),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White, Color.Transparent),
                start = start,
                end = end,
            ),
            topLeft = Offset(0f, top),
            size = Size(width, height),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val MediaMetadataKind.icon: ImageVector
    get() = when (this) {
        MediaMetadataKind.FileName -> Icons.AutoMirrored.Filled.InsertDriveFile
        MediaMetadataKind.Description -> Icons.AutoMirrored.Filled.Notes
        MediaMetadataKind.Date -> Icons.Default.DateRange
        MediaMetadataKind.Camera -> Icons.Default.CameraAlt
        MediaMetadataKind.Lens -> Icons.Default.Camera
        MediaMetadataKind.Exposure -> Icons.Default.Tune
        MediaMetadataKind.Dimensions -> Icons.Default.AspectRatio
        MediaMetadataKind.FileSize -> Icons.Default.Storage
        MediaMetadataKind.Location -> Icons.Default.LocationOn
        MediaMetadataKind.Film -> Icons.Default.Palette
        MediaMetadataKind.Author -> Icons.Default.Person
        MediaMetadataKind.Copyright -> Icons.Default.Copyright
    }
