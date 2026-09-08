package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
    val settings = LocalAiPlaybackSettings.current
    val initialMetadata = showMetadata ?: when (parentType) {
        SHOWCASE_MODE_SLIDE -> settings?.slideMode?.showContentMetaInfo
        SHOWCASE_MODE_FADE -> settings?.fadeMode?.showContentMetaInfo
        SHOWCASE_MODE_CALENDER -> settings?.calenderMode?.showContentMetaInfo
        else -> false
    } ?: false
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
    AnimatedVisibility(state.ready, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = allowed.metadata && state.showMetadata,
                enter = fadeIn(tween(400)), exit = fadeOut(tween(400)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.2f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        )
                    ),
                ) {
                    Column(
                        Modifier.align(Alignment.TopStart).padding(36.dp).widthIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        mediaMetadataRows(state).forEach { MetadataRow(it) }
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

private val MediaMetadataKind.icon: ImageVector
    get() = when (this) {
        MediaMetadataKind.FileName, MediaMetadataKind.Description -> Icons.Default.Description
        MediaMetadataKind.Date -> Icons.Default.DateRange
        MediaMetadataKind.Camera -> Icons.Default.CameraAlt
        MediaMetadataKind.Lens -> Icons.Default.Camera
        MediaMetadataKind.Exposure -> Icons.Default.Tune
        MediaMetadataKind.Dimensions -> Icons.Default.Image
        MediaMetadataKind.FileSize -> Icons.Default.Storage
        MediaMetadataKind.Location -> Icons.Default.LocationOn
        MediaMetadataKind.Film -> Icons.Default.Palette
        MediaMetadataKind.Author -> Icons.Default.Person
        MediaMetadataKind.Copyright -> Icons.Default.Copyright
    }
