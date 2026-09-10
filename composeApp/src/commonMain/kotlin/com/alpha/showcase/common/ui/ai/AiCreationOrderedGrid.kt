package com.alpha.showcase.common.ui.ai

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A waterfall whose lane is determined by source order, never by the shortest column. */
@Composable
internal fun AiCreationOrderedGrid(
    itemKeys: List<Any>,
    aspectRatios: List<Float>,
    columnCount: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val padding = with(density) { 8.dp.roundToPx() }
    val spacing = with(density) { 6.dp.roundToPx() }
    BoxWithConstraints(modifier) {
        val viewportWidth = constraints.maxWidth
        val viewportHeight = constraints.maxHeight
        val grid = remember(viewportWidth, columnCount, aspectRatios, padding, spacing) {
            calculateAiCreationGridLayout(viewportWidth, columnCount, aspectRatios, padding, spacing)
        }
        val indicesByKey = remember(itemKeys) { itemKeys.withIndex().associate { it.value to it.index } }
        val provider = remember(itemKeys, grid, columnCount, itemContent) {
            object : LazyLayoutItemProvider {
                override val itemCount = itemKeys.size
                override fun getKey(index: Int): Any = itemKeys[index]
                override fun getIndex(key: Any): Int = indicesByKey[key] ?: -1

                @Composable
                override fun Item(index: Int, key: Any) {
                    val tile = grid.tiles[index]
                    val targetOffset = IntOffset(tile.x, tile.y)
                    val animatedOffset by animateIntOffsetAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "creation-placement",
                    )
                    Box(
                        Modifier.offset { animatedOffset - targetOffset }.semantics {
                            collectionItemInfo = CollectionItemInfo(index / columnCount, 1, index % columnCount, 1)
                            traversalIndex = index.toFloat()
                        },
                    ) {
                        itemContent(index)
                    }
                }
            }
        }
        LazyLayout(
            itemProvider = { provider },
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).semantics {
                collectionInfo = CollectionInfo(-1, columnCount)
            },
        ) { _ ->
            // Keep nearby tiles composed while scrolling or animating a column change. Image
            // decoding remains lazy; only inexpensive dimensions are retained for the full list.
            val visible = grid.visibleIndices(
                top = scrollState.value - viewportHeight / 2,
                bottom = scrollState.value + viewportHeight + viewportHeight / 2,
            )
            val measured = visible.map { index ->
                val tile = grid.tiles[index]
                tile to compose(index).map { it.measure(Constraints.fixed(tile.width, tile.height)) }
            }
            layout(viewportWidth, maxOf(viewportHeight, grid.height)) {
                measured.forEach { (tile, placeables) ->
                    placeables.forEach { it.place(tile.x, tile.y) }
                }
            }
        }
    }
}

internal data class AiCreationGridTile(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class AiCreationGridLayout(val tiles: List<AiCreationGridTile>, val height: Int) {
    fun visibleIndices(top: Int, bottom: Int): List<Int> = tiles.indices.filter { index ->
        val tile = tiles[index]
        tile.y < bottom && tile.y + tile.height > top
    }
}

internal fun calculateAiCreationGridLayout(
    width: Int,
    columnCount: Int,
    aspectRatios: List<Float>,
    padding: Int,
    spacing: Int,
): AiCreationGridLayout {
    require(columnCount > 0)
    val availableWidth = (width - padding * 2 - spacing * (columnCount - 1)).coerceAtLeast(columnCount)
    val laneBottoms = IntArray(columnCount) { padding }
    val tiles = aspectRatios.mapIndexed { index, ratio ->
        val column = index % columnCount
        val laneStart = availableWidth * column / columnCount
        val tileWidth = availableWidth * (column + 1) / columnCount - laneStart
        val validRatio = ratio.takeIf { it.isFinite() && it > 0f } ?: 1f
        val tileHeight = (tileWidth / validRatio).roundToInt().coerceAtLeast(1)
        AiCreationGridTile(
            x = padding + laneStart + spacing * column,
            y = laneBottoms[column],
            width = tileWidth,
            height = tileHeight,
        ).also { laneBottoms[column] += tileHeight + spacing }
    }
    val height = if (tiles.isEmpty()) padding * 2 else laneBottoms.max() - spacing + padding
    return AiCreationGridLayout(tiles, height)
}
