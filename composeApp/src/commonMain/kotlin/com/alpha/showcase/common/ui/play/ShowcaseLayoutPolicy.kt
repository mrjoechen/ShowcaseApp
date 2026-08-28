package com.alpha.showcase.common.ui.play

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class WaterfallOrientation(val value: Int) {
    Vertical(0),
    Horizontal(1);

    companion object {
        fun fromValue(value: Int): WaterfallOrientation =
            entries.firstOrNull { it.value == value } ?: Vertical
    }
}

data class WaterfallContentPaddingDp(
    val horizontal: Int,
    val vertical: Int
)

object WaterfallLayoutPolicy {
    const val DEFAULT_LANE_COUNT = 3
    const val DEFAULT_SCROLL_SPEED = 5
    const val DEFAULT_SPACING_DP = 8
    val LANE_COUNT_RANGE = 2..6
    val SCROLL_SPEED_LEVELS = listOf(1, 3, 5, 7, 10)
    val SPACING_LEVELS_DP = listOf(4, 8, 16)

    fun laneCount(value: Int): Int =
        value.takeIf { it in LANE_COUNT_RANGE } ?: DEFAULT_LANE_COUNT

    fun scrollSpeed(value: Int): Int =
        nearestLevel(value, SCROLL_SPEED_LEVELS, DEFAULT_SCROLL_SPEED)

    fun spacingDp(value: Int): Int = nearestLevel(
        value = value,
        levels = SPACING_LEVELS_DP,
        defaultValue = DEFAULT_SPACING_DP,
        acceptedRange = 2..32
    )

    fun contentPaddingDp(
        value: Int,
        orientation: WaterfallOrientation
    ): WaterfallContentPaddingDp {
        val itemSpacing = spacingDp(value)
        return when (orientation) {
            WaterfallOrientation.Vertical -> WaterfallContentPaddingDp(
                horizontal = itemSpacing,
                vertical = itemSpacing
            )

            WaterfallOrientation.Horizontal -> WaterfallContentPaddingDp(
                horizontal = itemSpacing,
                vertical = itemSpacing
            )
        }
    }

    fun reachedEnd(canScrollForward: Boolean): Boolean = !canScrollForward
}

data class SquareCoordinate(
    val row: Int,
    val column: Int
) {
    operator fun plus(direction: SquareDirection): SquareCoordinate = SquareCoordinate(
        row = row + direction.rowDelta,
        column = column + direction.columnDelta
    )
}

enum class SquareDirection(
    val rowDelta: Int,
    val columnDelta: Int
) {
    Up(-1, 0),
    Down(1, 0),
    Left(0, -1),
    Right(0, 1)
}

data class SquareScrollOffset(
    val x: Float,
    val y: Float
)

data class SquareScrollBounds(
    val maxX: Float,
    val maxY: Float
)

object SquareLayoutPolicy {
    const val DEFAULT_ITEM_SIZE_DP = 320
    const val DEFAULT_FOCUS_SCALE_PERCENT = 120
    const val DEFAULT_SPACING_DP = 12
    const val DEFAULT_TRANSITION_MILLIS = 900
    val ITEM_SIZE_LEVELS_DP = listOf(160, 240, 320, 400, 480)
    val FOCUS_SCALE_LEVELS_PERCENT = listOf(110, 120, 140)
    val SPACING_LEVELS_DP = listOf(2, 4, 12, 24, 32)
    val TRANSITION_LEVELS_MILLIS = listOf(300, 600, 900, 1_200, 1_500)

    private const val FLING_PROJECTION_SECONDS = 0.18f
    private const val MAX_FLING_CELLS = 3
    private const val MAX_CELL_VIEWPORT_FRACTION = 0.9f

    fun itemSizeDp(value: Int): Int = nearestLevel(
        value = value,
        levels = ITEM_SIZE_LEVELS_DP,
        defaultValue = DEFAULT_ITEM_SIZE_DP,
        acceptedRange = 120..480
    )

    fun focusScalePercent(value: Int): Int = nearestLevel(
        value = value,
        levels = FOCUS_SCALE_LEVELS_PERCENT,
        defaultValue = DEFAULT_FOCUS_SCALE_PERCENT,
        acceptedRange = 110..140
    )

    fun spacingDp(value: Int): Int = nearestLevel(
        value = value,
        levels = SPACING_LEVELS_DP,
        defaultValue = DEFAULT_SPACING_DP,
        acceptedRange = 1..32
    )

    fun transitionMillis(value: Int): Int = nearestLevel(
        value = value,
        levels = TRANSITION_LEVELS_MILLIS,
        defaultValue = DEFAULT_TRANSITION_MILLIS,
        acceptedRange = 200..1_500
    )

    fun balancedColumnCount(itemCount: Int): Int {
        if (itemCount <= 0) return 1
        return ceil(sqrt(itemCount.toDouble())).toInt().coerceAtLeast(1)
    }

    fun cellStep(
        cellSize: Float,
        configuredSpacing: Float,
        focusScale: Float
    ): Float = cellSize.coerceAtLeast(0f) * focusScale.coerceAtLeast(1f) +
        configuredSpacing.coerceAtLeast(0f)

    fun focusSafeCellSize(
        preferredCellSize: Float,
        viewportSize: Float,
        focusScale: Float
    ): Float {
        val safePreferredSize = preferredCellSize.coerceAtLeast(0f)
        val safeViewportSize = viewportSize
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: return safePreferredSize
        if (safeViewportSize == 0f) return 0f

        val unscaledLimit = safeViewportSize * MAX_CELL_VIEWPORT_FRACTION
        val focusedLimit = safeViewportSize / focusScale.coerceAtLeast(1f)
        return min(safePreferredSize, min(unscaledLimit, focusedLimit))
    }

    fun visibleIndices(
        itemCount: Int,
        columnCount: Int,
        scrollX: Float,
        scrollY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        horizontalStep: Float,
        verticalStep: Float,
        overscanCells: Int
    ): IntArray {
        if (itemCount <= 0) return IntArray(0)

        val safeColumns = columnCount.coerceAtLeast(1)
        val rows = rowCount(itemCount, safeColumns)
        val safeHorizontalStep = horizontalStep.coerceAtLeast(1f)
        val safeVerticalStep = verticalStep.coerceAtLeast(1f)
        val safeScrollX = scrollX.takeIf(Float::isFinite) ?: 0f
        val safeScrollY = scrollY.takeIf(Float::isFinite) ?: 0f
        val halfViewportWidth = viewportWidth.coerceAtLeast(0f) / 2f
        val halfViewportHeight = viewportHeight.coerceAtLeast(0f) / 2f
        val safeOverscanCells = overscanCells.coerceAtLeast(0)
        val overscanX = safeHorizontalStep * safeOverscanCells
        val overscanY = safeVerticalStep * safeOverscanCells

        val firstColumn = floor(
            (safeScrollX - halfViewportWidth - overscanX) / safeHorizontalStep
        ).toInt().coerceIn(0, safeColumns - 1)
        val lastColumn = ceil(
            (safeScrollX + halfViewportWidth + overscanX) / safeHorizontalStep
        ).toInt().coerceIn(firstColumn, safeColumns - 1)
        val firstRow = floor(
            (safeScrollY - halfViewportHeight - overscanY) / safeVerticalStep
        ).toInt().coerceIn(0, rows - 1)
        val lastRow = ceil(
            (safeScrollY + halfViewportHeight + overscanY) / safeVerticalStep
        ).toInt().coerceIn(firstRow, rows - 1)

        val result = IntArray(
            (lastColumn - firstColumn + 1) * (lastRow - firstRow + 1)
        )
        var resultSize = 0
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val index = row * safeColumns + column
                if (index in 0 until itemCount) {
                    result[resultSize++] = index
                }
            }
        }
        return if (resultSize == result.size) result else result.copyOf(resultSize)
    }

    fun coordinateForIndex(index: Int, columnCount: Int): SquareCoordinate {
        val safeColumns = columnCount.coerceAtLeast(1)
        val safeIndex = index.coerceAtLeast(0)
        return SquareCoordinate(
            row = safeIndex / safeColumns,
            column = safeIndex % safeColumns
        )
    }

    fun initialCenter(itemCount: Int, columnCount: Int): SquareCoordinate {
        if (itemCount <= 0) return SquareCoordinate(0, 0)
        val safeColumns = columnCount.coerceAtLeast(1)
        val rows = rowCount(itemCount, safeColumns)
        val centerIndex = (rows / 2 * safeColumns + safeColumns / 2)
            .coerceIn(0, itemCount - 1)
        return coordinateForIndex(centerIndex, safeColumns)
    }

    fun indexFor(
        coordinate: SquareCoordinate,
        columnCount: Int,
        itemCount: Int
    ): Int? {
        if (itemCount <= 0) return null
        val safeColumns = columnCount.coerceAtLeast(1)
        if (coordinate.row < 0 || coordinate.column !in 0 until safeColumns) return null
        val rawIndex = coordinate.row.toLong() * safeColumns + coordinate.column
        return rawIndex.takeIf { it in 0 until itemCount.toLong() }?.toInt()
    }

    fun validDirections(
        center: SquareCoordinate,
        columnCount: Int,
        itemCount: Int
    ): List<SquareDirection> = SquareDirection.entries.filter { direction ->
        indexFor(center + direction, columnCount, itemCount) != null
    }

    fun autoPlayCandidates(
        center: SquareCoordinate,
        previousFocusedIndex: Int,
        columnCount: Int,
        itemCount: Int
    ): List<Int> {
        val candidates = validDirections(center, columnCount, itemCount).mapNotNull { direction ->
            indexFor(center + direction, columnCount, itemCount)
        }
        val candidatesWithoutPrevious = candidates.filterNot { it == previousFocusedIndex }
        return candidatesWithoutPrevious.ifEmpty { candidates }
    }

    fun rowCount(itemCount: Int, columnCount: Int): Int {
        if (itemCount <= 0) return 0
        val safeColumns = columnCount.coerceAtLeast(1)
        return (itemCount + safeColumns - 1) / safeColumns
    }

    fun scrollBounds(
        itemCount: Int,
        columnCount: Int,
        horizontalStep: Float,
        verticalStep: Float
    ): SquareScrollBounds {
        val safeColumns = columnCount.coerceAtLeast(1)
        val rows = rowCount(itemCount, safeColumns)
        return SquareScrollBounds(
            maxX = (safeColumns - 1) * horizontalStep.coerceAtLeast(0f),
            maxY = (rows - 1).coerceAtLeast(0) * verticalStep.coerceAtLeast(0f)
        )
    }

    fun clampScrollOffset(
        x: Float,
        y: Float,
        bounds: SquareScrollBounds
    ): SquareScrollOffset = SquareScrollOffset(
        x = x.coerceIn(0f, bounds.maxX.coerceAtLeast(0f)),
        y = y.coerceIn(0f, bounds.maxY.coerceAtLeast(0f))
    )

    fun scrollOffsetForIndex(
        index: Int,
        columnCount: Int,
        itemCount: Int,
        horizontalStep: Float,
        verticalStep: Float
    ): SquareScrollOffset {
        if (itemCount <= 0) return SquareScrollOffset(0f, 0f)
        val coordinate = coordinateForIndex(index.coerceIn(0, itemCount - 1), columnCount)
        return SquareScrollOffset(
            x = coordinate.column * horizontalStep.coerceAtLeast(0f),
            y = coordinate.row * verticalStep.coerceAtLeast(0f)
        )
    }

    fun releaseTargetIndex(
        scrollX: Float,
        scrollY: Float,
        fingerVelocityX: Float,
        fingerVelocityY: Float,
        columnCount: Int,
        itemCount: Int,
        horizontalStep: Float,
        verticalStep: Float
    ): Int? {
        val currentIndex = nearestIndex(
            scrollX,
            scrollY,
            columnCount,
            itemCount,
            horizontalStep,
            verticalStep
        ) ?: return null
        val projectedIndex = nearestIndex(
            scrollX - fingerVelocityX * FLING_PROJECTION_SECONDS,
            scrollY - fingerVelocityY * FLING_PROJECTION_SECONDS,
            columnCount,
            itemCount,
            horizontalStep,
            verticalStep
        ) ?: currentIndex
        val current = coordinateForIndex(currentIndex, columnCount)
        val projected = coordinateForIndex(projectedIndex, columnCount)
        val target = SquareCoordinate(
            row = projected.row.coerceIn(
                current.row - MAX_FLING_CELLS,
                current.row + MAX_FLING_CELLS
            ),
            column = projected.column.coerceIn(
                current.column - MAX_FLING_CELLS,
                current.column + MAX_FLING_CELLS
            )
        )
        return nearestIndex(
            scrollX = target.column * horizontalStep,
            scrollY = target.row * verticalStep,
            columnCount = columnCount,
            itemCount = itemCount,
            horizontalStep = horizontalStep,
            verticalStep = verticalStep
        )
    }

    fun scaleForPosition(
        itemCenterX: Float,
        itemCenterY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        focusScale: Float
    ): Float {
        val halfWidth = (viewportWidth / 2f).coerceAtLeast(1f)
        val halfHeight = (viewportHeight / 2f).coerceAtLeast(1f)
        val normalizedDistance = max(
            abs(itemCenterX - halfWidth) / halfWidth,
            abs(itemCenterY - halfHeight) / halfHeight
        ).coerceIn(0f, 1f)
        return 1f + (focusScale.coerceAtLeast(1f) - 1f) * (1f - normalizedDistance)
    }

    private fun nearestIndex(
        scrollX: Float,
        scrollY: Float,
        columnCount: Int,
        itemCount: Int,
        horizontalStep: Float,
        verticalStep: Float
    ): Int? {
        if (itemCount <= 0) return null
        val safeColumns = columnCount.coerceAtLeast(1)
        val safeHorizontalStep = horizontalStep.coerceAtLeast(1f)
        val safeVerticalStep = verticalStep.coerceAtLeast(1f)
        val lastRow = rowCount(itemCount, safeColumns) - 1
        val row = (scrollY / safeVerticalStep).roundToInt().coerceIn(0, lastRow)
        val requestedColumn = (scrollX / safeHorizontalStep)
            .roundToInt()
            .coerceIn(0, safeColumns - 1)
        val lastColumnInRow = if (row == lastRow) {
            (itemCount - 1) % safeColumns
        } else {
            safeColumns - 1
        }
        val column = requestedColumn.coerceAtMost(lastColumnInRow)
        return row * safeColumns + column
    }
}

private fun nearestLevel(
    value: Int,
    levels: List<Int>,
    defaultValue: Int,
    acceptedRange: IntRange = levels.first()..levels.last()
): Int {
    val candidate = value.takeIf { it in acceptedRange } ?: defaultValue
    return levels.minWithOrNull(
        compareBy<Int> { abs(it - candidate) }.thenByDescending { it }
    ) ?: defaultValue
}
