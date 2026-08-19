package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasurePolicy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SQUARE
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val COMPOSITION_OVERSCAN_CELLS = 1
private const val SQUARE_ITEM_CONTENT_TYPE = "square-card"

@Stable
internal class SquareCanvasState(
    initialScrollX: Float,
    initialScrollY: Float,
    initialFocusedIndex: Int
) {
    var scrollX by mutableFloatStateOf(initialScrollX)
        private set

    var scrollY by mutableFloatStateOf(initialScrollY)
        private set

    var focusedIndex by mutableIntStateOf(initialFocusedIndex)
        private set

    var previousFocusedIndex by mutableIntStateOf(-1)
        private set

    fun updateScroll(x: Float, y: Float) {
        scrollX = x
        scrollY = y
    }

    fun settle(index: Int) {
        if (index == focusedIndex) return
        previousFocusedIndex = focusedIndex
        focusedIndex = index
    }

    fun reset(x: Float, y: Float, focusedIndex: Int) {
        scrollX = x
        scrollY = y
        if (this.focusedIndex != focusedIndex) {
            previousFocusedIndex = -1
        }
        this.focusedIndex = focusedIndex
    }
}

@Immutable
internal data class SquareCanvasMetrics(
    val columnCount: Int,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val horizontalStepPx: Float,
    val verticalStepPx: Float,
    val focusScale: Float
)

private class SquareLazyItemProvider(
    private val items: PagingPlayItems,
    private val canvasState: SquareCanvasState,
    private val parentActive: State<Boolean>,
    private val fitSize: State<Boolean>,
    private val editMode: State<Boolean>,
    private val onItemClick: State<(Int) -> Unit>
) : LazyLayoutItemProvider {
    override val itemCount: Int
        get() = items.size

    override fun getKey(index: Int): Any = index

    override fun getContentType(index: Int): Any = SQUARE_ITEM_CONTENT_TYPE

    @Composable
    override fun Item(index: Int, key: Any) {
        val data = items[index]
        val active by remember(index, canvasState) {
            derivedStateOf {
                parentActive.value && canvasState.focusedIndex == index
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .then(
                    if (parentActive.value) {
                        Modifier.clickable { onItemClick.value(index) }
                    } else {
                        Modifier
                    }
                )
        ) {
            PagerItem(
                modifier = Modifier.fillMaxSize(),
                data = data,
                fitSize = fitSize.value,
                active = active,
                parentType = SHOWCASE_MODE_SQUARE,
                editMode = editMode.value
            )
        }
    }
}

private data class MeasuredSquareItem(
    val placeable: Placeable,
    val x: Int,
    val y: Int,
    val scale: Float
)

@Composable
internal fun SquareLazyCanvas(
    items: PagingPlayItems,
    canvasState: SquareCanvasState,
    metrics: SquareCanvasMetrics,
    modifier: Modifier = Modifier,
    parentActive: Boolean,
    fitSize: Boolean,
    editMode: Boolean,
    onItemClick: (Int) -> Unit
) {
    val latestParentActive = rememberUpdatedState(parentActive)
    val latestFitSize = rememberUpdatedState(fitSize)
    val latestEditMode = rememberUpdatedState(editMode)
    val latestOnItemClick = rememberUpdatedState(onItemClick)
    val latestItemCount = rememberUpdatedState(items.size)
    val itemProvider = remember(items, canvasState) {
        SquareLazyItemProvider(
            items = items,
            canvasState = canvasState,
            parentActive = latestParentActive,
            fitSize = latestFitSize,
            editMode = latestEditMode,
            onItemClick = latestOnItemClick
        )
    }
    val itemProviderFactory = remember(itemProvider) { { itemProvider } }
    val prefetchState = remember { LazyLayoutPrefetchState() }

    LaunchedEffect(prefetchState, items, metrics.columnCount) {
        var handles = emptyList<LazyLayoutPrefetchState.PrefetchHandle>()
        try {
            snapshotFlow { canvasState.focusedIndex }.collectLatest { focusedIndex ->
                handles.forEach(LazyLayoutPrefetchState.PrefetchHandle::cancel)
                val currentItemCount = latestItemCount.value
                val center = SquareLayoutPolicy.coordinateForIndex(
                    index = focusedIndex,
                    columnCount = metrics.columnCount
                )
                handles = SquareLayoutPolicy.validDirections(
                    center = center,
                    columnCount = metrics.columnCount,
                    itemCount = currentItemCount
                ).mapNotNull { direction ->
                    SquareLayoutPolicy.indexFor(
                        coordinate = center + direction,
                        columnCount = metrics.columnCount,
                        itemCount = currentItemCount
                    )
                }.map { index ->
                    items.preload(index)
                    prefetchState.schedulePrecomposition(index)
                }
            }
        } finally {
            handles.forEach(LazyLayoutPrefetchState.PrefetchHandle::cancel)
        }
    }

    val measurePolicy = remember(canvasState, metrics, itemProvider) {
        LazyLayoutMeasurePolicy { constraints ->
            val layoutWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                constraints.constrainWidth(metrics.cellWidthPx.roundToInt())
            }
            val layoutHeight = if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                constraints.constrainHeight(metrics.cellHeightPx.roundToInt())
            }
            val scrollX = canvasState.scrollX
            val scrollY = canvasState.scrollY
            val visibleIndices = SquareLayoutPolicy.visibleIndices(
                itemCount = itemProvider.itemCount,
                columnCount = metrics.columnCount,
                scrollX = scrollX,
                scrollY = scrollY,
                viewportWidth = layoutWidth.toFloat(),
                viewportHeight = layoutHeight.toFloat(),
                horizontalStep = metrics.horizontalStepPx,
                verticalStep = metrics.verticalStepPx,
                overscanCells = COMPOSITION_OVERSCAN_CELLS
            )
            val childConstraints = Constraints.fixed(
                width = metrics.cellWidthPx.roundToInt().coerceAtLeast(1),
                height = metrics.cellHeightPx.roundToInt().coerceAtLeast(1)
            )
            val measuredItems = ArrayList<MeasuredSquareItem>(visibleIndices.size)

            visibleIndices.forEach { index ->
                val measurables = compose(index)
                require(measurables.size == 1) {
                    "Square items must emit exactly one root layout."
                }
                val placeable = measurables.single().measure(childConstraints)
                val coordinate = SquareLayoutPolicy.coordinateForIndex(
                    index = index,
                    columnCount = metrics.columnCount
                )
                val centerX =
                    layoutWidth / 2f + coordinate.column * metrics.horizontalStepPx - scrollX
                val centerY =
                    layoutHeight / 2f + coordinate.row * metrics.verticalStepPx - scrollY
                val scale = SquareLayoutPolicy.scaleForPosition(
                    itemCenterX = centerX,
                    itemCenterY = centerY,
                    viewportWidth = layoutWidth.toFloat(),
                    viewportHeight = layoutHeight.toFloat(),
                    focusScale = metrics.focusScale
                )
                measuredItems += MeasuredSquareItem(
                    placeable = placeable,
                    x = (centerX - placeable.width / 2f).roundToInt(),
                    y = (centerY - placeable.height / 2f).roundToInt(),
                    scale = scale
                )
            }

            layout(layoutWidth, layoutHeight) {
                measuredItems.forEach { item ->
                    item.placeable.placeWithLayer(
                        x = item.x,
                        y = item.y,
                        zIndex = item.scale
                    ) {
                        scaleX = item.scale
                        scaleY = item.scale
                    }
                }
            }
        }
    }

    LazyLayout(
        itemProvider = itemProviderFactory,
        modifier = modifier,
        prefetchState = prefetchState,
        measurePolicy = measurePolicy
    )
}
