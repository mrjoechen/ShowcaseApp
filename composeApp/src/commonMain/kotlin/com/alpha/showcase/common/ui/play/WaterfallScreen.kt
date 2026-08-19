package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_WATERFALL
import com.alpha.showcase.common.ui.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaterfallScreen(
    pagingItems: PagingPlayItems,
    waterfallMode: Settings.WaterfallMode,
    modifier: Modifier = Modifier,
    parentActive: Boolean = true,
    editMode: Boolean = false
) {
    val orientation = WaterfallOrientation.fromValue(waterfallMode.orientation)
    val laneCount = WaterfallLayoutPolicy.laneCount(waterfallMode.laneCount)
    val spacing = WaterfallLayoutPolicy.spacingDp(waterfallMode.spacing).dp
    val contentPadding = WaterfallLayoutPolicy.contentPaddingDp(
        value = waterfallMode.spacing,
        orientation = orientation
    )
    val contentPaddingValues = PaddingValues(
        horizontal = contentPadding.horizontal.dp,
        vertical = contentPadding.vertical.dp
    )
    val speedLevel = WaterfallLayoutPolicy.scrollSpeed(waterfallMode.scrollSpeed)
    val staggeredGridState = key(pagingItems) { rememberLazyStaggeredGridState() }
    val aspectRatioCache = remember(pagingItems) { WaterfallAspectRatioCache() }
    val density = LocalDensity.current

    LaunchedEffect(speedLevel, parentActive, orientation, pagingItems) {
        if (!parentActive) return@LaunchedEffect

        val pixelsPerSecond = with(density) { (12 + speedLevel * 10).dp.toPx() }
        var previousFrame = withFrameNanos { it }

        while (isActive && parentActive) {
            if (pagingItems.size <= 0) {
                delay(100)
                continue
            }

            val frame = withFrameNanos { it }
            val elapsedSeconds = (frame - previousFrame).coerceAtLeast(0L) / 1_000_000_000f
            previousFrame = frame
            try {
                staggeredGridState.scrollBy(pixelsPerSecond * elapsedSeconds)
            } catch (error: CancellationException) {
                if (!isActive) throw error
                previousFrame = withFrameNanos { it }
                delay(100)
                continue
            }

            if (WaterfallLayoutPolicy.reachedEnd(staggeredGridState.canScrollForward)) {
                staggeredGridState.scrollToItem(0)
                delay(100)
            } else {
                val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo
                    .maxOfOrNull { it.index }
                    ?: 0
                pagingItems.preload((lastVisibleIndex + 8).coerceAtMost(pagingItems.size - 1))
            }
        }
    }

    when (orientation) {
        WaterfallOrientation.Vertical -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(laneCount),
            state = staggeredGridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPaddingValues,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalItemSpacing = spacing
        ) {
            items(
                count = pagingItems.size,
                key = { index -> index }
            ) { index ->
                WaterfallTile(
                    data = pagingItems[index],
                    vertical = true,
                    aspectRatioCache = aspectRatioCache,
                    parentActive = parentActive,
                    editMode = editMode,
                    spacing = spacing
                )
            }
        }

        WaterfallOrientation.Horizontal -> LazyHorizontalStaggeredGrid(
            rows = StaggeredGridCells.Fixed(laneCount),
            state = staggeredGridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPaddingValues,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalItemSpacing = spacing
        ) {
            items(
                count = pagingItems.size,
                key = { index -> index }
            ) { index ->
                WaterfallTile(
                    data = pagingItems[index],
                    vertical = false,
                    aspectRatioCache = aspectRatioCache,
                    parentActive = parentActive,
                    editMode = editMode,
                    spacing = spacing
                )
            }
        }
    }
}

@Composable
private fun WaterfallTile(
    data: Any,
    vertical: Boolean,
    aspectRatioCache: WaterfallAspectRatioCache,
    parentActive: Boolean,
    editMode: Boolean,
    spacing: Dp
) {
    val fallbackRatio = if (data.isVideo()) 16f / 9f else 4f / 3f
    val aspectRatioState = remember(aspectRatioCache, data, fallbackRatio) {
        aspectRatioCache.stateFor(data.toString(), fallbackRatio)
    }
    val shape = RoundedCornerShape((spacing / 2).coerceAtMost(8.dp))
    val tileModifier = Modifier
        .aspectRatio(
            ratio = aspectRatioState.value,
            matchHeightConstraintsFirst = !vertical
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))

    Box(modifier = tileModifier) {
        PagerItem(
            modifier = Modifier.fillMaxSize(),
            data = data,
            fitSize = false,
            active = parentActive && data.isImage(),
            parentType = SHOWCASE_MODE_WATERFALL,
            editMode = editMode,
            onImageDimensionsAvailable = { width, height ->
                if (width > 0 && height > 0) {
                    aspectRatioCache.update(aspectRatioState, width.toFloat() / height)
                }
            }
        )
    }
}
