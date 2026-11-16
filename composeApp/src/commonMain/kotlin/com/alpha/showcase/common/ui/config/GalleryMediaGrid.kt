package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alpha.showcase.common.cache.GalleryMediaRecord
import com.alpha.showcase.common.ui.source.toGalleryDisplayUri
import kotlinx.coroutines.isActive

@Composable
internal fun GalleryMediaGrid(
    mediaItems: List<GalleryMediaRecord>,
    selectedUris: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val mediaUris = remember(mediaItems) { mediaItems.map { it.mediaUri } }
    val currentSelection by rememberUpdatedState(selectedUris)
    val updateSelection by rememberUpdatedState(onSelectionChange)
    val haptics = LocalHapticFeedback.current
    var dragSelection by remember(mediaUris) { mutableStateOf<GalleryGridSelection?>(null) }
    var dragPosition by remember(mediaUris) { mutableStateOf(Offset.Zero) }
    val edgeSize = with(LocalDensity.current) { 48.dp.toPx() }
    val maxScrollSpeed = with(LocalDensity.current) { 800.dp.toPx() }

    fun toggle(mediaUri: String) {
        updateSelection(
            if (mediaUri in currentSelection) currentSelection - mediaUri else currentSelection + mediaUri
        )
    }

    // Keep scrolling when the finger is held still at an edge, and include newly revealed items.
    LaunchedEffect(dragSelection, edgeSize, maxScrollSpeed) {
        val selection = dragSelection ?: return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val elapsedSeconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            val height = gridState.layoutInfo.viewportSize.height
            val edge = minOf(edgeSize, height / 2f)
            if (edge <= 0f) continue
            val speed = when {
                dragPosition.y < edge -> -((edge - dragPosition.y) / edge).coerceIn(0f, 1f)
                dragPosition.y > height - edge -> ((dragPosition.y - height + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                gridState.scrollBy(speed * maxScrollSpeed * elapsedSeconds)
                gridState.mediaIndexAt(dragPosition, nearest = true)?.let {
                    updateSelection(selection.selectionAt(it))
                }
            }
        }
    }

    LazyVerticalGrid(
        modifier = modifier
            // Selection changes must not restart either pointer handler during an active gesture.
            .pointerInput(mediaUris, gridState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val index = gridState.mediaIndexAt(longPress.position) ?: return@awaitEachGesture
                    val selection = GalleryGridSelection(mediaUris, index, currentSelection)
                    dragPosition = longPress.position
                    dragSelection = selection
                    updateSelection(selection.selectionAt(index))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        while (true) {
                            // Once long press wins, consume before grid scrolling and item clicks.
                            // This also suppresses the click on release when no movement occurred.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                            if (change.isConsumed) break
                            change.consume()
                            if (!change.pressed) break
                            dragPosition = change.position
                            gridState.mediaIndexAt(change.position)?.let {
                                updateSelection(selection.selectionAt(it))
                            }
                        }
                    } finally {
                        dragSelection = null
                    }
                }
            }
            .pointerInput(mediaUris, gridState) {
                detectTapGestures { position ->
                    if (gridState.mediaIndexAt(position) == null) updateSelection(emptySet())
                }
            },
        state = gridState,
        columns = GridCells.Adaptive(minSize = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(mediaItems, key = { it.mediaUri }) { media ->
            val isSelected = media.mediaUri in selectedUris
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (currentSelection.isNotEmpty()) toggle(media.mediaUri)
                    }
                    .semantics {
                        selected = isSelected
                        // Expose long press to accessibility without a second touch recognizer.
                        onLongClick {
                            toggle(media.mediaUri)
                            true
                        }
                    }
            ) {
                AsyncImage(
                    model = toGalleryDisplayUri(media.mediaUri),
                    contentDescription = resolveGalleryDisplayName(media),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (isSelected) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridState.mediaIndexAt(position: Offset, nearest: Boolean = false): Int? {
    val items = layoutInfo.visibleItemsInfo
    val hit = items.firstOrNull {
        position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
            position.y >= it.offset.y && position.y < it.offset.y + it.size.height
    }
    if (hit != null || !nearest) return hit?.index
    // Gaps and the bottom padding should not interrupt selection while auto-scrolling.
    return items.minByOrNull {
        val dx = position.x - position.x.coerceIn(it.offset.x.toFloat(), (it.offset.x + it.size.width).toFloat())
        val dy = position.y - position.y.coerceIn(it.offset.y.toFloat(), (it.offset.y + it.size.height).toFloat())
        dx * dx + dy * dy
    }?.index
}
