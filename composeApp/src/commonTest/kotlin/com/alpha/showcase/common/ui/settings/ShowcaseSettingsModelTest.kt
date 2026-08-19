package com.alpha.showcase.common.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ShowcaseSettingsModelTest {
    @Test
    fun newStylesHaveUsableDefaultsForOlderConfigurations() {
        val settings = Settings()

        assertEquals(5, settings.squareMode.interval)
        assertEquals(320, settings.squareMode.squareSize)
        assertEquals(120, settings.squareMode.focusScale)
        assertEquals(12, settings.squareMode.spacing)
        assertEquals(900, settings.squareMode.transitionDuration)
        assertEquals(0, settings.squareMode.displayMode)

        assertEquals(0, settings.waterfallMode.orientation)
        assertEquals(3, settings.waterfallMode.laneCount)
        assertEquals(0, settings.waterfallMode.scrollMode)
        assertEquals(5, settings.waterfallMode.scrollSpeed)
        assertEquals(8, settings.waterfallMode.spacing)
    }

    @Test
    fun styleRegistryRoundTripsSquareAndWaterfallValues() {
        assertEquals(
            ShowcaseMode.Square,
            ShowcaseMode.fromValue(SHOWCASE_MODE_SQUARE)
        )
        assertEquals(
            ShowcaseMode.Waterfall,
            ShowcaseMode.fromValue(SHOWCASE_MODE_WATERFALL)
        )
        assertEquals("Square", getModeName(SHOWCASE_MODE_SQUARE))
        assertEquals("Waterfall", getModeName(SHOWCASE_MODE_WATERFALL))
    }
}
