package com.alpha.showcase.common.ui.ai

internal class AiCreationPinchGesture {
    private var accumulatedZoom = 1f
    private var committed = false

    fun onZoom(current: Int, zoomChange: Float): Int? {
        if (committed || !zoomChange.isFinite() || zoomChange <= 0f) return null
        accumulatedZoom *= zoomChange
        val updated = AiCreationGridPolicy.updatedColumnCount(current, accumulatedZoom) ?: return null
        committed = true
        return updated.takeIf { it != current }
    }

    fun reset() {
        accumulatedZoom = 1f
        committed = false
    }
}

internal object AiCreationGridPolicy {
    const val MIN_COLUMN_COUNT = 1
    const val MAX_COLUMN_COUNT = 6
    private const val DEFAULT_TILE_WIDTH_DP = 124
    private const val ZOOM_IN_THRESHOLD = 1.18f
    private const val ZOOM_OUT_THRESHOLD = 0.85f

    fun initialColumnCount(screenWidthDp: Int): Int =
        (screenWidthDp / DEFAULT_TILE_WIDTH_DP).coerceIn(2, MAX_COLUMN_COUNT)

    fun continuityScale(
        currentScale: Float,
        previousColumnCount: Int,
        targetColumnCount: Int,
    ): Float {
        val previous = previousColumnCount.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        val target = targetColumnCount.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        val visibleScale = currentScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        return visibleScale * target.toFloat() / previous
    }

    /** Returns null until a pinch has crossed enough hysteresis to commit a column change. */
    fun updatedColumnCount(current: Int, accumulatedZoom: Float): Int? {
        val boundedCurrent = current.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        return when {
            accumulatedZoom >= ZOOM_IN_THRESHOLD -> {
                (boundedCurrent - 1).coerceAtLeast(MIN_COLUMN_COUNT)
            }
            accumulatedZoom <= ZOOM_OUT_THRESHOLD -> {
                (boundedCurrent + 1).coerceAtMost(MAX_COLUMN_COUNT)
            }
            else -> null
        }
    }
}

