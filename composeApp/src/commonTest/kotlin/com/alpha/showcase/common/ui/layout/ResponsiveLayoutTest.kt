package com.alpha.showcase.common.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponsiveLayoutTest {
    @Test
    fun compactWebBreakpointOnlyAppliesToNarrowWebViewports() {
        assertTrue(isCompactWebLayout(isWeb = true, viewportWidthDp = 359f))
        assertFalse(isCompactWebLayout(isWeb = true, viewportWidthDp = COMPACT_WEB_MAX_WIDTH_DP))
        assertFalse(isCompactWebLayout(isWeb = false, viewportWidthDp = 359f))
    }

    @Test
    fun homePaddingKeepsDesktopSpacingAndReducesNarrowWebSpacing() {
        assertEquals(8f, homeHorizontalPaddingDp(isWeb = true, isDesktop = false, viewportWidthDp = 390f))
        assertEquals(20f, homeHorizontalPaddingDp(isWeb = true, isDesktop = false, viewportWidthDp = 1024f))
        assertEquals(20f, homeHorizontalPaddingDp(isWeb = false, isDesktop = true, viewportWidthDp = 390f))
        assertEquals(0f, homeHorizontalPaddingDp(isWeb = false, isDesktop = false, viewportWidthDp = 390f))
    }

    @Test
    fun compactWebSourceCardsUseDenseSpacingAndPortraitRatio() {
        val policy = sourceGridLayoutPolicy(isWeb = true, viewportWidthDp = 390f, viewportHeightDp = 844f)

        assertTrue(policy.isVertical)
        assertEquals(3, policy.fixedColumnCount)
        assertEquals(128f, policy.minimumCellWidthDp)
        assertEquals(8f, policy.contentHorizontalPaddingDp)
        assertEquals(4f, policy.itemPaddingDp)
        assertEquals(3f / 4f, policy.cardAspectRatio)
    }

    @Test
    fun narrowWebViewKeepsTwoColumns() {
        val policy = sourceGridLayoutPolicy(isWeb = true, viewportWidthDp = 280f, viewportHeightDp = 653f)

        assertEquals(2, policy.fixedColumnCount)
    }

    @Test
    fun veryNarrowWebViewFallsBackToOneColumn() {
        val policy = sourceGridLayoutPolicy(isWeb = true, viewportWidthDp = 220f, viewportHeightDp = 653f)

        assertEquals(1, policy.fixedColumnCount)
    }

    @Test
    fun nonCompactWebSourceCardsRetainExistingSizing() {
        val policy = sourceGridLayoutPolicy(isWeb = true, viewportWidthDp = 1280f, viewportHeightDp = 800f)

        assertFalse(policy.isVertical)
        assertNull(policy.fixedColumnCount)
        assertEquals(140f, policy.minimumCellWidthDp)
        assertEquals(16f, policy.contentHorizontalPaddingDp)
        assertEquals(8f, policy.itemPaddingDp)
        assertNull(policy.cardAspectRatio)
        assertEquals(210f, policy.fixedCardHeightDp)
    }

    @Test
    fun nativeMobileSourceCardsRetainExistingSizing() {
        val policy = sourceGridLayoutPolicy(isWeb = false, viewportWidthDp = 390f, viewportHeightDp = 844f)

        assertTrue(policy.isVertical)
        assertNull(policy.fixedColumnCount)
        assertEquals(100f, policy.minimumCellWidthDp)
        assertEquals(16f, policy.contentHorizontalPaddingDp)
        assertEquals(8f, policy.itemPaddingDp)
        assertNull(policy.cardAspectRatio)
        assertEquals(150f, policy.fixedCardHeightDp)
    }
}
