package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowcaseLayoutPolicyTest {
    @Test
    fun waterfallNormalizesPersistedValuesBeforeLayout() {
        assertEquals(3, WaterfallLayoutPolicy.laneCount(0))
        assertEquals(6, WaterfallLayoutPolicy.laneCount(6))
        assertEquals(7, WaterfallLayoutPolicy.scrollSpeed(6))
        assertEquals(8, WaterfallLayoutPolicy.spacingDp(10))
        assertEquals(8, WaterfallLayoutPolicy.spacingDp(100))
    }

    @Test
    fun waterfallKeepsItemAndContainerSpacingEqual() {
        assertEquals(
            WaterfallContentPaddingDp(horizontal = 8, vertical = 8),
            WaterfallLayoutPolicy.contentPaddingDp(8, WaterfallOrientation.Vertical)
        )
        assertEquals(
            WaterfallContentPaddingDp(horizontal = 8, vertical = 8),
            WaterfallLayoutPolicy.contentPaddingDp(8, WaterfallOrientation.Horizontal)
        )
    }

    @Test
    fun waterfallOnlyReportsTheEndWhenForwardScrollingStops() {
        assertFalse(WaterfallLayoutPolicy.reachedEnd(canScrollForward = true))
        assertTrue(WaterfallLayoutPolicy.reachedEnd(canScrollForward = false))
    }

    @Test
    fun squareUsesABalancedFiniteGrid() {
        assertEquals(4, SquareLayoutPolicy.balancedColumnCount(itemCount = 10))
        assertEquals(3, SquareLayoutPolicy.rowCount(itemCount = 10, columnCount = 4))
        assertEquals(
            SquareScrollBounds(maxX = 300f, maxY = 160f),
            SquareLayoutPolicy.scrollBounds(
                itemCount = 10,
                columnCount = 4,
                horizontalStep = 100f,
                verticalStep = 80f
            )
        )
    }

    @Test
    fun squareComposesOnlyTheViewportWindow() {
        assertContentEquals(
            intArrayOf(0, 1, 10, 11),
            SquareLayoutPolicy.visibleIndices(
                itemCount = 100,
                columnCount = 10,
                scrollX = 0f,
                scrollY = 0f,
                viewportWidth = 100f,
                viewportHeight = 100f,
                horizontalStep = 100f,
                verticalStep = 100f,
                overscanCells = 0
            )
        )
    }

    @Test
    fun squareNeverTargetsAnEmptyCellInTheLastRow() {
        assertEquals(
            9,
            SquareLayoutPolicy.releaseTargetIndex(
                scrollX = 300f,
                scrollY = 160f,
                fingerVelocityX = 0f,
                fingerVelocityY = 0f,
                columnCount = 4,
                itemCount = 10,
                horizontalStep = 100f,
                verticalStep = 80f
            )
        )
        assertNull(
            SquareLayoutPolicy.indexFor(
                coordinate = SquareCoordinate(row = 2, column = 2),
                columnCount = 4,
                itemCount = 10
            )
        )
    }

    @Test
    fun squareSettingsSnapToSupportedLevels() {
        assertEquals(320, SquareLayoutPolicy.itemSizeDp(0))
        assertEquals(240, SquareLayoutPolicy.itemSizeDp(250))
        assertEquals(140, SquareLayoutPolicy.focusScalePercent(135))
        assertEquals(12, SquareLayoutPolicy.spacingDp(10))
        assertEquals(900, SquareLayoutPolicy.transitionMillis(800))
    }

    @Test
    fun waterfallAspectRatioCacheKeepsStableStateAndRejectsInvalidUpdates() {
        val cache = WaterfallAspectRatioCache(maxEntries = 2)
        val state = cache.stateFor("first", fallbackRatio = 4f / 3f)

        assertTrue(state === cache.stateFor("first", fallbackRatio = 1f))
        cache.update(state, ratio = 16f / 9f)
        assertEquals(16f / 9f, state.value)
        cache.update(state, ratio = Float.NaN)
        assertEquals(16f / 9f, state.value)

        cache.stateFor("second", fallbackRatio = 1f)
        cache.stateFor("third", fallbackRatio = 1f)
        assertFalse(state === cache.stateFor("first", fallbackRatio = 1f))
    }

    @Test
    fun squareCanvasTracksTheCurrentAndPreviousFocus() {
        val state = SquareCanvasState(
            initialScrollX = 10f,
            initialScrollY = 20f,
            initialFocusedIndex = 4
        )

        state.updateScroll(x = 30f, y = 40f)
        state.settle(index = 5)

        assertEquals(30f, state.scrollX)
        assertEquals(40f, state.scrollY)
        assertEquals(5, state.focusedIndex)
        assertEquals(4, state.previousFocusedIndex)
    }
}
